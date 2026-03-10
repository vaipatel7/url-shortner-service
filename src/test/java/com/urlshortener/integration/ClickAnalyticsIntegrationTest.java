package com.urlshortener.integration;

import com.urlshortener.UrlShortenerApplication;
import com.urlshortener.UrlShortenerConfiguration;
import com.urlshortener.dto.v1.AnalyticsResponse;
import com.urlshortener.dto.v1.CreateUrlRequest;
import com.urlshortener.dto.v1.CreateUrlResponse;
import com.urlshortener.dto.v1.TimeseriesPoint;
import com.urlshortener.dto.v1.TopUrlsResponse;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying the full click-analytics pipeline with a real PostgreSQL
 * instance (Testcontainers) and a live Dropwizard server (DropwizardAppExtension).
 *
 * <p>Covers three scenarios:
 * <ol>
 *   <li>Click events are recorded correctly and independently per short URL.</li>
 *   <li>Daily aggregation (clicks per day) is computed correctly by the analytics endpoint.</li>
 *   <li>Aggregated data persists consistently across repeated queries.</li>
 * </ol>
 */
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(DropwizardExtensionsSupport.class)
class ClickAnalyticsIntegrationTest {

    // -------------------------------------------------------------------------
    // Infrastructure — started once for the entire test class
    // -------------------------------------------------------------------------

    @Container
    @SuppressWarnings("resource") // Ryuk/Testcontainers manages lifecycle; no manual close needed
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("urlshortener")
            .withUsername("urlshortener")
            .withPassword("password");

    // ConfigOverride suppliers are evaluated lazily after POSTGRES is up
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
        truncateTables();
    }

    @AfterEach
    void tearDown() {
        noRedirectClient.close();
    }

    // -------------------------------------------------------------------------
    // Scenario 1: Clicks are recorded correctly per URL
    // -------------------------------------------------------------------------

    /**
     * Creates two short URLs, performs a different number of redirects on each,
     * and verifies that every redirect produces exactly one click_events row
     * associated with the correct short URL.
     */
    @Test
    void clicksAreRecordedCorrectlyPerUrl() throws Exception {
        CreateUrlResponse urlA = createShortUrl("https://example-a.com", "click-url-a");
        CreateUrlResponse urlB = createShortUrl("https://example-b.com", "click-url-b");

        // Redirect URL A three times, URL B twice
        redirect("click-url-a");
        redirect("click-url-a");
        redirect("click-url-a");
        redirect("click-url-b");
        redirect("click-url-b");

        awaitClickCount(urlA.getId(), 3);
        awaitClickCount(urlB.getId(), 2);

        assertThat(dbClickCount(urlA.getId()))
                .as("URL A should have exactly 3 recorded clicks")
                .isEqualTo(3);
        assertThat(dbClickCount(urlB.getId()))
                .as("URL B should have exactly 2 recorded clicks")
                .isEqualTo(2);
    }

    /**
     * Verifies that click events are scoped to their owning URL: clicking one URL
     * must not increment the click count of another URL.
     */
    @Test
    void clicksDoNotCrossContaminateBetweenUrls() throws Exception {
        CreateUrlResponse urlA = createShortUrl("https://isolation-a.com", "iso-url-a");
        CreateUrlResponse urlB = createShortUrl("https://isolation-b.com", "iso-url-b");

        // Click only URL A
        redirect("iso-url-a");
        awaitClickCount(urlA.getId(), 1);

        assertThat(dbClickCount(urlA.getId())).as("URL A should have 1 click").isEqualTo(1);
        assertThat(dbClickCount(urlB.getId())).as("URL B should have 0 clicks").isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // Scenario 2: Daily aggregation is computed correctly
    // -------------------------------------------------------------------------

    /**
     * Creates a short URL, clicks it {@code CLICK_COUNT} times, then verifies via
     * the {@code GET /v1/analytics?page=0} endpoint that:
     * <ul>
     *   <li>The timeseries contains exactly 7 entries (missing days filled with 0).</li>
     *   <li>Today's entry reflects the expected click count.</li>
     *   <li>The summary's current-period total is at least {@code CLICK_COUNT}.</li>
     * </ul>
     */
    @Test
    void dailyAggregationReflectsActualClicksViaAnalyticsEndpoint() throws Exception {
        final int clickCount = 5;
        CreateUrlResponse url = createShortUrl("https://daily-agg.example.com", "daily-agg-url");

        for (int i = 0; i < clickCount; i++) {
            redirect("daily-agg-url");
        }
        awaitClickCount(url.getId(), clickCount);

        Response resp = APP.client()
                .target(baseUrl() + "/v1/analytics")
                .queryParam("page", 0)
                .request(MediaType.APPLICATION_JSON)
                .get();
        assertThat(resp.getStatus()).as("GET /v1/analytics should return 200").isEqualTo(200);

        AnalyticsResponse analytics = resp.readEntity(AnalyticsResponse.class);

        // Summary: current-period clicks should include our clicks
        assertThat(analytics.getSummary().getCurrentPeriodClicks())
                .as("Current-period summary should include all recorded clicks")
                .isGreaterThanOrEqualTo(clickCount);

        // Timeseries: service always returns a 7-point series (gaps = 0)
        assertThat(analytics.getTimeseries())
                .as("Timeseries should always contain exactly 7 daily data points")
                .hasSize(7);

        // Today's bucket must carry exactly our clicks (table is truncated before each test)
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        TimeseriesPoint todayPoint = analytics.getTimeseries().stream()
                .filter(p -> today.equals(p.getDate()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No timeseries entry found for today (" + today + "). Points: "
                        + analytics.getTimeseries()));

        assertThat(todayPoint.getClicks())
                .as("Today's timeseries bucket should equal the number of clicks recorded")
                .isEqualTo(clickCount);
    }

    /**
     * Verifies the daily aggregation directly against PostgreSQL using the same
     * {@code date_trunc('day', clicked_at AT TIME ZONE 'UTC')} expression the
     * production query uses, ensuring the DB-level grouping matches expectations.
     */
    @Test
    void dailyAggregationIsCorrectAtDatabaseLevel() throws Exception {
        final int clickCount = 4;
        CreateUrlResponse url = createShortUrl("https://db-agg.example.com", "db-agg-url");

        for (int i = 0; i < clickCount; i++) {
            redirect("db-agg-url");
        }
        awaitClickCount(url.getId(), clickCount);

        String today = LocalDate.now(ZoneOffset.UTC).toString();
        try (Connection conn = dbConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT date_trunc('day', clicked_at AT TIME ZONE 'UTC')::date AS day, " +
                     "       COUNT(*) AS clicks " +
                     "FROM click_events " +
                     "WHERE short_url_id = ? " +
                     "GROUP BY 1")) {

            ps.setLong(1, url.getId());
            ResultSet rs = ps.executeQuery();

            assertThat(rs.next())
                    .as("DB aggregation should return at least one row for the URL")
                    .isTrue();
            assertThat(rs.getString("day"))
                    .as("All clicks recorded today should be bucketed under today's UTC date")
                    .isEqualTo(today);
            assertThat(rs.getLong("clicks"))
                    .as("DB daily aggregation should match the number of redirects performed")
                    .isEqualTo(clickCount);
            assertThat(rs.next())
                    .as("All clicks made in this test should fall into a single day bucket")
                    .isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 3: Aggregated data persists correctly for reporting
    // -------------------------------------------------------------------------

    /**
     * Verifies that aggregated analytics data persists correctly:
     * <ul>
     *   <li>The top-URLs endpoint lists the created URL with the correct click count.</li>
     *   <li>Two consecutive calls to the analytics endpoint return identical results,
     *       confirming data is stored durably rather than recomputed from ephemeral state.</li>
     * </ul>
     */
    @Test
    void aggregatedDataPersistsCorrectlyForReporting() throws Exception {
        final int clickCount = 6;
        CreateUrlResponse url = createShortUrl("https://persist.example.com", "persist-url");

        for (int i = 0; i < clickCount; i++) {
            redirect("persist-url");
        }
        awaitClickCount(url.getId(), clickCount);

        // ── Verify top-URLs endpoint reflects persisted clicks ────────────────
        Response topResp = APP.client()
                .target(baseUrl() + "/v1/analytics/top-urls")
                .request(MediaType.APPLICATION_JSON)
                .get();
        assertThat(topResp.getStatus()).isEqualTo(200);

        TopUrlsResponse topUrls = topResp.readEntity(TopUrlsResponse.class);
        assertThat(topUrls.getData())
                .as("Top URLs should include our test URL")
                .isNotEmpty();
        assertThat(topUrls.getData())
                .as("Top URL entry for 'persist-url' should report correct click count")
                .anySatisfy(stat -> {
                    assertThat(stat.getAlias()).isEqualTo("persist-url");
                    assertThat(stat.getClicks()).isEqualTo(clickCount);
                });

        // ── Verify analytics are stable across repeated queries ───────────────
        AnalyticsResponse firstCall = APP.client()
                .target(baseUrl() + "/v1/analytics")
                .queryParam("page", 0)
                .request(MediaType.APPLICATION_JSON)
                .get(AnalyticsResponse.class);

        AnalyticsResponse secondCall = APP.client()
                .target(baseUrl() + "/v1/analytics")
                .queryParam("page", 0)
                .request(MediaType.APPLICATION_JSON)
                .get(AnalyticsResponse.class);

        assertThat(secondCall.getSummary().getCurrentPeriodClicks())
                .as("Repeated analytics calls should return the same current-period total")
                .isEqualTo(firstCall.getSummary().getCurrentPeriodClicks());
        assertThat(secondCall.getTimeseries())
                .as("Timeseries must be identical across repeated calls (data is persisted, not ephemeral)")
                .isEqualTo(firstCall.getTimeseries());
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
            // CASCADE clears click_events which FK-references short_urls
            stmt.execute("TRUNCATE TABLE short_urls CASCADE");
        }
    }

    /** Creates a short URL via POST /v1/url/create and returns the response body. */
    private CreateUrlResponse createShortUrl(String longUrl, String alias) {
        CreateUrlRequest req = new CreateUrlRequest();
        req.setLongUrl(longUrl);
        req.setAlias(alias);

        Response resp = APP.client()
                .target(baseUrl() + "/v1/url/create")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(req));

        assertThat(resp.getStatus())
                .as("POST /v1/url/create should return 201 for alias '%s'", alias)
                .isEqualTo(201);
        return resp.readEntity(CreateUrlResponse.class);
    }

    /** Performs a GET /{alias} redirect (302 expected) without following the redirect. */
    private void redirect(String alias) {
        Response resp = noRedirectClient
                .target(baseUrl() + "/" + alias)
                .request()
                .get();
        assertThat(resp.getStatus())
                .as("GET /%s should return 302", alias)
                .isEqualTo(302);
    }

    /** Returns the raw click count for a short URL id from click_events. */
    private long dbClickCount(long shortUrlId) throws SQLException {
        try (Connection conn = dbConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM click_events WHERE short_url_id = ?")) {
            ps.setLong(1, shortUrlId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getLong(1);
        }
    }

    /**
     * Polls {@code click_events} until the row count for {@code shortUrlId} reaches
     * {@code expectedCount} or the 3-second deadline is exceeded.
     */
    private void awaitClickCount(long shortUrlId, long expectedCount) throws Exception {
        long deadline = System.currentTimeMillis() + 3_000;
        while (System.currentTimeMillis() < deadline) {
            if (dbClickCount(shortUrlId) >= expectedCount) return;
            Thread.sleep(100);
        }
        throw new AssertionError(String.format(
                "Expected %d click(s) for shortUrlId=%d within 3 s, but got %d",
                expectedCount, shortUrlId, dbClickCount(shortUrlId)));
    }
}
