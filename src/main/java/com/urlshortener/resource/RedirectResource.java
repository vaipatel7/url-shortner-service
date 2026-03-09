package com.urlshortener.resource;

import com.urlshortener.model.ShortUrl;
import com.urlshortener.service.v1.AnalyticsService;
import com.urlshortener.service.v1.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

import jakarta.ws.rs.WebApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * Handles short-code redirection at the root path.
 *
 * GET /{short_code} → 302 to the original long URL.
 *
 * The URL lookup is synchronous (must complete before the redirect is issued).
 * Click analytics recording is delegated to {@link AnalyticsService} and
 * executed asynchronously, so it does not add latency to the redirect.
 */
@Path("/")
public class RedirectResource {

    private static final Logger LOG = LoggerFactory.getLogger(RedirectResource.class);

    private final UrlService urlService;
    private final AnalyticsService analyticsService;

    public RedirectResource(UrlService urlService, AnalyticsService analyticsService) {
        this.urlService = urlService;
        this.analyticsService = analyticsService;
    }

    @GET
    @Path("/{short_code}")
    public Response redirect(
            @PathParam("short_code") String shortCode,
            @Context HttpServletRequest httpRequest) {

        ShortUrl shortUrl;
        try {
            shortUrl = urlService.resolve(shortCode);
        } catch (WebApplicationException e) {
            LOG.warn("Redirect failed: short code '{}' not found", shortCode);
            throw e;
        }

        // Fire-and-forget: analytics write happens in a background thread
        analyticsService.recordClickAsync(
                shortUrl,
                httpRequest.getHeader("User-Agent"),
                resolveClientIp(httpRequest)
        );

        return Response
                .status(Response.Status.FOUND)
                .location(URI.create(shortUrl.getLongUrl()))
                .build();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }
}
