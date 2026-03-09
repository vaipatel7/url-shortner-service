package com.urlshortener.integration;

import com.urlshortener.UrlShortenerApplication;
import com.urlshortener.UrlShortenerConfiguration;
import com.urlshortener.dto.v1.CreateUrlRequest;
import com.urlshortener.dto.v1.CreateUrlResponse;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test: POST /v1/url/create → GET /{alias} redirect → click event recorded.
 *
 * <p>Uses a real PostgreSQL instance (via Testcontainers) and a full Dropwizard server
 * (via DropwizardAppExtension) so no mocking is involved.
 */
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(DropwizardExtensionsSupport.class)
class CreateAndRedirectIntegrationTest {

    // -------------------------------------------------------------------------
    // Infrastructure — started once for the entire test class
    // -------------------------------------------------------------------------

    // @Container lets the Testcontainers JUnit 5 extension manage the lifecycle;
    // Ryuk handles cleanup. disabledWithoutDocker=true skips the class gracefully
    // when Docker is unavailable instead of blowing up with ExceptionInInitializerError.
    @Container
    @SuppressWarnings("resource") // Ryuk/Testcontainers manages container lifecycle; no manual close needed
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("urlshortener")
            .withUsername("urlshortener")
            .withPassword("password");

    // Suppliers are evaluated lazily at app-start time (after POSTGRES is running).
    static final DropwizardAppExtension<UrlShortenerConfiguration> APP =
            new DropwizardAppExtension<>(
                    UrlShortenerApplication.class,
                    ResourceHelpers.resourceFilePath("config-test.yml"),
                    ConfigOverride.config("database.url",      () -> POSTGRES.getJdbcUrl()),
                    ConfigOverride.config("database.user",     () -> POSTGRES.getUsername()),
                    ConfigOverride.config("database.password", () -> POSTGRES.getPassword())
            );

    // Per-test HTTP client configured to NOT follow 3xx redirects
    private Client noRedirectClient;

    @BeforeEach
    void setUp() throws SQLException {
        noRedirectClient = ClientBuilder.newBuilder()
                .property(ClientProperties.FOLLOW_REDIRECTS, false)
                .build();
        // Ensure a clean DB state before every test
        truncateTables();
    }

    @AfterEach
    void tearDown() {
        noRedirectClient.close();
    }

    // -------------------------------------------------------------------------
    // Scenario: Create a short URL, resolve it, and verify a click event
    // -------------------------------------------------------------------------

    /**
     * Full happy-path scenario:
     * 1. POST /v1/url/create with a custom alias → 201 + correct body
     * 2. short_urls row persisted in DB
     * 3. GET /{alias} → 302 with Location: {longUrl}
     * 4. click_events row recorded asynchronously
     */
    @Test
    void createShortUrl_thenRedirect_thenRecordsClickEvent() throws Exception {
        final String longUrl = "https://example.com";
        final String alias   = "abc123";

        // ── Step 1: Create the short URL ──────────────────────────────────────
        CreateUrlRequest req = buildRequest(longUrl, alias);

        Response createResp = APP.client()
                .target(baseUrl() + "/v1/url/create")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(req));

        assertThat(createResp.getStatus())
                .as("POST /v1/url/create should return 201 Created")
                .isEqualTo(201);

        CreateUrlResponse body = createResp.readEntity(CreateUrlResponse.class);
        assertThat(body.getId()).as("id should be a positive DB-generated value").isPositive();
        assertThat(body.getShortCode()).isEqualTo(alias);
        assertThat(body.getLongUrl()).isEqualTo(longUrl);
        assertThat(body.getAliasType()).isEqualTo("CUSTOM");
        assertThat(body.getShortUrl()).as("shortUrl should contain the alias").contains(alias);
        assertThat(body.getCreatedAt()).isNotNull();

        // ── Step 2: Verify DB record ──────────────────────────────────────────
        try (Connection conn = dbConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT alias, long_url, alias_type FROM short_urls WHERE alias = ?")) {
            ps.setString(1, alias);
            ResultSet rs = ps.executeQuery();
            assertThat(rs.next()).as("short_urls row should exist for alias '%s'", alias).isTrue();
            assertThat(rs.getString("alias")).isEqualTo(alias);
            assertThat(rs.getString("long_url")).isEqualTo(longUrl);
            assertThat(rs.getString("alias_type")).isEqualTo("CUSTOM");
        }

        // ── Step 3: Resolve the short URL → expect 302 ───────────────────────
        Response redirectResp = noRedirectClient
                .target(baseUrl() + "/" + alias)
                .request()
                .get();

        assertThat(redirectResp.getStatus())
                .as("GET /{alias} should return 302 Found")
                .isEqualTo(302);
        assertThat(redirectResp.getHeaderString("Location"))
                .as("Location header should be the original long URL")
                .isEqualTo(longUrl);

        // ── Step 4: Verify click event recorded (async — poll up to 3 s) ──────
        awaitClickEvent(body.getId());

        try (Connection conn = dbConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT short_url_id, ip_address, clicked_at FROM click_events WHERE short_url_id = ?")) {
            ps.setLong(1, body.getId());
            ResultSet rs = ps.executeQuery();
            assertThat(rs.next()).as("click_events row should be recorded").isTrue();
            assertThat(rs.getLong("short_url_id")).isEqualTo(body.getId());
            assertThat(rs.getTimestamp("clicked_at")).isNotNull();
            assertThat(rs.getString("ip_address")).isNotBlank();
        }
    }

    // -------------------------------------------------------------------------
    // Additional edge-case scenarios
    // -------------------------------------------------------------------------

    /** POST without an alias → service auto-generates an 8-character code. */
    @Test
    void createShortUrl_withoutAlias_generatesEightCharCode() {
        Response resp = APP.client()
                .target(baseUrl() + "/v1/url/create")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(buildRequest("https://example.com", null)));

        assertThat(resp.getStatus()).isEqualTo(201);
        CreateUrlResponse body = resp.readEntity(CreateUrlResponse.class);
        assertThat(body.getShortCode()).hasSize(8);
        assertThat(body.getAliasType()).isEqualTo("GENERATED");
    }

    /** POST twice with the same custom alias → second call returns 409 Conflict. */
    @Test
    void createShortUrl_duplicateAlias_returns409() {
        CreateUrlRequest req = buildRequest("https://example.com", "dupalias");

        Response first = APP.client()
                .target(baseUrl() + "/v1/url/create")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(req));
        assertThat(first.getStatus()).isEqualTo(201);

        Response second = APP.client()
                .target(baseUrl() + "/v1/url/create")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(req));
        // 422 from in-memory cache hit, 409 from DB constraint race — both are valid alias-conflict responses
        assertThat(second.getStatus())
                .as("duplicate alias should return 409 or 422")
                .isIn(409, 422);
    }

    /** GET with an alias that does not exist → 404 Not Found. */
    @Test
    void redirect_unknownAlias_returns404() {
        Response resp = noRedirectClient
                .target(baseUrl() + "/nonexistent99")
                .request()
                .get();

        assertThat(resp.getStatus()).isEqualTo(404);
    }

    /** POST with an invalid URL → 422 Unprocessable Entity from bean validation. */
    @Test
    void createShortUrl_invalidLongUrl_returns422() {
        Response resp = APP.client()
                .target(baseUrl() + "/v1/url/create")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(buildRequest("not-a-url", null)));

        assertThat(resp.getStatus()).isEqualTo(422);
    }

    /** POST with a custom domain → 400; custom domains are not supported. */
    @Test
    void createShortUrl_withCustomDomain_returns400() {
        CreateUrlRequest req = buildRequest("https://example.com", null);
        req.setDomain("custom.io");

        Response resp = APP.client()
                .target(baseUrl() + "/v1/url/create")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(req));

        assertThat(resp.getStatus()).isEqualTo(400);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String baseUrl() {
        return "http://localhost:" + APP.getLocalPort();
    }

    private Connection dbConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
    }

    private void truncateTables() throws SQLException {
        try (Connection conn = dbConnection();
             Statement stmt = conn.createStatement()) {
            // CASCADE automatically clears click_events which FK-references short_urls
            stmt.execute("TRUNCATE TABLE short_urls CASCADE");
        }
    }

    /**
     * Polls {@code click_events} until a row for {@code shortUrlId} appears or
     * the 3-second timeout expires, then fails with a clear message.
     */
    private void awaitClickEvent(long shortUrlId) throws Exception {
        long deadline = System.currentTimeMillis() + 3_000;
        while (System.currentTimeMillis() < deadline) {
            try (Connection conn = dbConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT COUNT(*) FROM click_events WHERE short_url_id = ?")) {
                ps.setLong(1, shortUrlId);
                ResultSet rs = ps.executeQuery();
                rs.next();
                if (rs.getLong(1) > 0) return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError(
                "Click event was not recorded within 3 s for shortUrlId=" + shortUrlId);
    }

    private static CreateUrlRequest buildRequest(String longUrl, String alias) {
        CreateUrlRequest req = new CreateUrlRequest();
        req.setLongUrl(longUrl);
        req.setAlias(alias);
        return req;
    }
}
