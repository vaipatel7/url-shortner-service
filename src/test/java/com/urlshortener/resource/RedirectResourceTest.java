package com.urlshortener.resource;

import com.urlshortener.model.AliasType;
import com.urlshortener.model.ShortUrl;
import com.urlshortener.service.v1.AnalyticsService;
import com.urlshortener.service.v1.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedirectResourceTest {

    @Mock
    UrlService urlService;

    @Mock
    AnalyticsService analyticsService;

    @Mock
    HttpServletRequest httpRequest;

    RedirectResource redirectResource;

    @BeforeEach
    void setUp() {
        redirectResource = new RedirectResource(urlService, analyticsService);
    }

    // -------------------------------------------------------------------------
    // Redirect behaviour
    // -------------------------------------------------------------------------

    @Test
    void redirect_knownAlias_returns302() {
        when(urlService.resolve("abc123")).thenReturn(shortUrl("abc123", "https://example.com"));

        Response response = redirectResource.redirect("abc123", httpRequest);

        assertThat(response.getStatus()).isEqualTo(302);
    }

    @Test
    void redirect_knownAlias_locationIsLongUrl() {
        when(urlService.resolve("abc123")).thenReturn(shortUrl("abc123", "https://example.com/path"));

        Response response = redirectResource.redirect("abc123", httpRequest);

        assertThat(response.getLocation().toString()).isEqualTo("https://example.com/path");
    }

    @Test
    void redirect_unknownAlias_rethrows404() {
        when(urlService.resolve("missing")).thenThrow(
                new WebApplicationException("not found", Response.Status.NOT_FOUND)
        );

        assertThatExceptionOfType(WebApplicationException.class)
                .isThrownBy(() -> redirectResource.redirect("missing", httpRequest))
                .satisfies(ex -> assertThat(ex.getResponse().getStatus()).isEqualTo(404));
    }

    // -------------------------------------------------------------------------
    // Async click recording
    // -------------------------------------------------------------------------

    @Test
    void redirect_recordsClickWithUserAgentAndIp() {
        ShortUrl shortUrl = shortUrl("abc123", "https://example.com");
        when(urlService.resolve("abc123")).thenReturn(shortUrl);
        when(httpRequest.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1");

        redirectResource.redirect("abc123", httpRequest);

        verify(analyticsService).recordClickAsync(eq(shortUrl), eq("Mozilla/5.0"), eq("10.0.0.1"));
    }

    @Test
    void redirect_noUserAgent_recordsClickWithNullUserAgent() {
        ShortUrl shortUrl = shortUrl("abc123", "https://example.com");
        when(urlService.resolve("abc123")).thenReturn(shortUrl);
        // User-Agent not stubbed → returns null (default); X-Forwarded-For likewise
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("127.0.0.1");

        redirectResource.redirect("abc123", httpRequest);

        verify(analyticsService).recordClickAsync(eq(shortUrl), isNull(), eq("127.0.0.1"));
    }

    // -------------------------------------------------------------------------
    // Client IP resolution
    // -------------------------------------------------------------------------

    @Test
    void redirect_xForwardedFor_usesFirstIpInChain() {
        ShortUrl shortUrl = shortUrl("abc123", "https://example.com");
        when(urlService.resolve("abc123")).thenReturn(shortUrl);
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1, 10.0.0.1, 192.168.1.1");

        redirectResource.redirect("abc123", httpRequest);

        verify(analyticsService).recordClickAsync(any(), any(), eq("203.0.113.1"));
    }

    @Test
    void redirect_xRealIp_usedWhenForwardedForAbsent() {
        // X-Forwarded-For not stubbed → returns null (default); fall through to X-Real-IP
        ShortUrl shortUrl = shortUrl("abc123", "https://example.com");
        when(urlService.resolve("abc123")).thenReturn(shortUrl);
        when(httpRequest.getHeader("X-Real-IP")).thenReturn("198.51.100.5");

        redirectResource.redirect("abc123", httpRequest);

        verify(analyticsService).recordClickAsync(any(), any(), eq("198.51.100.5"));
    }

    @Test
    void redirect_noProxyHeaders_usesRemoteAddr() {
        // X-Forwarded-For and X-Real-IP not stubbed → both return null (default)
        ShortUrl shortUrl = shortUrl("abc123", "https://example.com");
        when(urlService.resolve("abc123")).thenReturn(shortUrl);
        when(httpRequest.getRemoteAddr()).thenReturn("172.16.0.5");

        redirectResource.redirect("abc123", httpRequest);

        verify(analyticsService).recordClickAsync(any(), any(), eq("172.16.0.5"));
    }

    @Test
    void redirect_blankXForwardedFor_fallsThroughToRemoteAddr() {
        // Blank (whitespace-only) X-Forwarded-For is treated as absent; no X-Real-IP either
        ShortUrl shortUrl = shortUrl("abc123", "https://example.com");
        when(urlService.resolve("abc123")).thenReturn(shortUrl);
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("   ");
        when(httpRequest.getRemoteAddr()).thenReturn("10.1.2.3");

        redirectResource.redirect("abc123", httpRequest);

        verify(analyticsService).recordClickAsync(any(), any(), eq("10.1.2.3"));
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private ShortUrl shortUrl(String alias, String longUrl) {
        return ShortUrl.builder()
                .id(1L)
                .domain("localhost:8080")
                .alias(alias)
                .aliasType(AliasType.GENERATED)
                .longUrl(longUrl)
                .createdAt(Instant.now())
                .build();
    }
}
