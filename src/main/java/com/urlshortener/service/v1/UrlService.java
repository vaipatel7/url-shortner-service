package com.urlshortener.service.v1;

import com.urlshortener.model.AliasType;
import com.urlshortener.model.ShortUrl;
import com.urlshortener.repository.UrlRepository;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class UrlService {

    private static final Logger LOG = LoggerFactory.getLogger(UrlService.class);

    private static final String ALIAS_CHARS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    /** Generated aliases are always exactly 8 characters. */
    private static final int GENERATED_ALIAS_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UrlRepository urlRepository;
    private final AnalyticsService analyticsService;
    private final String defaultDomain;

    /**
     * In-memory set of all aliases known to this instance.
     * Acts as a fast first-level check before hitting the database.
     */
    private final Set<String> usedAliases = ConcurrentHashMap.newKeySet();

    public UrlService(UrlRepository urlRepository, AnalyticsService analyticsService, String defaultDomain) {
        this.urlRepository = urlRepository;
        this.analyticsService = analyticsService;
        this.defaultDomain = defaultDomain;
    }

    /**
     * Creates a new short URL record in {@code short_urls}.
     *
     * <ul>
     *   <li>{@code longUrl}    – required (validated by the resource layer)</li>
     *   <li>{@code domain}     – optional; falls back to {@code defaultDomain}</li>
     *   <li>{@code customAlias}– optional; if provided must be ≥ 6 chars (validated
     *       by the resource layer).  Returns 422 if the alias is already taken.</li>
     * </ul>
     */
    public ShortUrl createShortUrl(String longUrl, String domain, String customAlias) {
        String resolvedDomain = (domain != null && !domain.isBlank()) ? domain : defaultDomain;

        String alias;
        AliasType aliasType;

        if (customAlias != null && !customAlias.isBlank()) {
            assertAliasAvailable(customAlias);
            alias = customAlias;
            aliasType = AliasType.CUSTOM;
        } else {
            alias = generateUniqueAlias();
            aliasType = AliasType.GENERATED;
        }

        ShortUrl shortUrl = ShortUrl.builder()
                .domain(resolvedDomain)
                .alias(alias)
                .aliasType(aliasType)
                .longUrl(longUrl)
                .build();

        ShortUrl saved;
        try {
            saved = urlRepository.insert(shortUrl);
        } catch (Exception e) {
            if (isUniqueConstraintViolation(e)) {
                LOG.warn("DB unique constraint violation on insert for alias='{}' — concurrent race condition", alias);
                throw new WebApplicationException(
                        Response.status(Response.Status.CONFLICT)
                                .entity("{\"code\":409,\"message\":\"Alias is not available\"}")
                                .type(MediaType.APPLICATION_JSON)
                                .build()
                );
            }
            LOG.error("Unexpected DB error inserting alias='{}': {}", alias, e.getMessage(), e);
            throw e;
        }

        // Track in the in-memory set after a successful DB write
        usedAliases.add(alias);
        LOG.info("Created short URL alias='{}' type={} domain='{}' longUrl='{}'",
                saved.getAlias(), aliasType, resolvedDomain, longUrl);

        return saved;
    }

    /**
     * Returns a page of short URL records ordered by created_at DESC.
     */
    public List<ShortUrl> listAll(int page, int pageSize) {
        return urlRepository.findAll(pageSize, page * pageSize);
    }

    /** Total count of short_urls rows (used to compute totalPages). */
    public long countAll() {
        return urlRepository.countAll();
    }

    /** Total click count for a given short URL. */
    public long getClickCount(Long shortUrlId) {
        return analyticsService.getClickCount(shortUrlId);
    }

    /**
     * Looks up a short URL by alias and returns the entity so the caller can
     * issue a redirect. Click recording is handled separately by {@link AnalyticsService}.
     */
    public ShortUrl resolve(String alias) {
        return urlRepository.findByAlias(alias)
                .orElseThrow(() -> new WebApplicationException(
                        "Short URL '" + alias + "' not found.",
                        Response.Status.NOT_FOUND
                ));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Throws 422 Unprocessable Entity if the alias is already taken,
     * checking the in-memory set first, then the database.
     */
    private void assertAliasAvailable(String alias) {
        if (usedAliases.contains(alias) || urlRepository.countByAlias(alias) > 0) {
            LOG.warn("Alias conflict: alias='{}' is already taken", alias);
            throw new WebApplicationException(
                    Response.status(422)
                            .entity("{\"code\":422,\"message\":\"Alias is not available\"}")
                            .type(MediaType.APPLICATION_JSON)
                            .build()
            );
        }
    }

    /**
     * Returns true if {@code t} or any cause in its chain is a SQL unique-constraint
     * violation (SQLState 23505).  Used to catch races where the in-memory cache was
     * cleared and a duplicate alias slips through to the INSERT.
     */
    private static boolean isUniqueConstraintViolation(Throwable t) {
        while (t != null) {
            if (t instanceof SQLException && "23505".equals(((SQLException) t).getSQLState())) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    /**
     * Generates a random 8-character alphanumeric alias that is not already
     * present in the in-memory set or the database.
     */
    private String generateUniqueAlias() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = randomAlias();
            if (!usedAliases.contains(candidate) && urlRepository.countByAlias(candidate) == 0) {
                return candidate;
            }
        }
        LOG.error("Exhausted 10 attempts to generate a unique alias — alias space may be saturated");
        throw new WebApplicationException(
                "Unable to generate a unique alias. Please try again.",
                Response.Status.INTERNAL_SERVER_ERROR
        );
    }

    private String randomAlias() {
        StringBuilder sb = new StringBuilder(GENERATED_ALIAS_LENGTH);
        for (int i = 0; i < GENERATED_ALIAS_LENGTH; i++) {
            sb.append(ALIAS_CHARS.charAt(RANDOM.nextInt(ALIAS_CHARS.length())));
        }
        return sb.toString();
    }
}
