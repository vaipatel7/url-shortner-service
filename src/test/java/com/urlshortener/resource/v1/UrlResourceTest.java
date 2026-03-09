package com.urlshortener.resource.v1;

import com.urlshortener.dto.v1.CreateUrlRequest;
import com.urlshortener.dto.v1.CreateUrlResponse;
import com.urlshortener.dto.v1.ShortUrlPageResponse;
import com.urlshortener.model.AliasType;
import com.urlshortener.model.ShortUrl;
import com.urlshortener.service.v1.UrlService;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlResourceTest {

    @Mock
    UrlService urlService;

    UrlResource urlResource;

    @BeforeEach
    void setUp() {
        urlResource = new UrlResource(urlService);
    }

    // -------------------------------------------------------------------------
    // POST /v1/url/create
    // -------------------------------------------------------------------------

    @Test
    void createUrl_validRequest_returns201WithCreatedBody() {
        ShortUrl saved = ShortUrl.builder()
                .id(1L).domain("localhost:8080").alias("abcd1234")
                .aliasType(AliasType.GENERATED).longUrl("https://example.com")
                .createdAt(Instant.parse("2025-01-01T00:00:00Z"))
                .build();
        when(urlService.createShortUrl("https://example.com", null, null)).thenReturn(saved);

        Response response = urlResource.createUrl(request("https://example.com", null, null));

        assertThat(response.getStatus()).isEqualTo(201);
        CreateUrlResponse body = (CreateUrlResponse) response.getEntity();
        assertThat(body.getId()).isEqualTo(1L);
        assertThat(body.getShortCode()).isEqualTo("abcd1234");
        assertThat(body.getLongUrl()).isEqualTo("https://example.com");
        assertThat(body.getDomain()).isEqualTo("localhost:8080");
        assertThat(body.getAliasType()).isEqualTo("GENERATED");
        assertThat(body.getCreatedAt()).isEqualTo("2025-01-01T00:00:00Z");
    }

    @Test
    void createUrl_withCustomAlias_returns201WithCustomAliasType() {
        ShortUrl saved = ShortUrl.builder()
                .id(2L).domain("localhost:8080").alias("my-link")
                .aliasType(AliasType.CUSTOM).longUrl("https://example.com")
                .createdAt(Instant.now())
                .build();
        when(urlService.createShortUrl("https://example.com", null, "my-link")).thenReturn(saved);

        Response response = urlResource.createUrl(request("https://example.com", null, "my-link"));

        assertThat(response.getStatus()).isEqualTo(201);
        CreateUrlResponse body = (CreateUrlResponse) response.getEntity();
        assertThat(body.getAliasType()).isEqualTo("CUSTOM");
        assertThat(body.getShortCode()).isEqualTo("my-link");
        verify(urlService).createShortUrl("https://example.com", null, "my-link");
    }

    @Test
    void createUrl_withCustomDomain_returns400WithoutCallingService() {
        Response response = urlResource.createUrl(request("https://example.com", "custom.io", null));

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(urlService);
    }

    @Test
    void createUrl_localhostDomain_shortUrlUsesHttpScheme() {
        ShortUrl saved = ShortUrl.builder()
                .id(1L).domain("localhost:8080").alias("abcd1234")
                .aliasType(AliasType.GENERATED).longUrl("https://example.com")
                .createdAt(Instant.now())
                .build();
        when(urlService.createShortUrl(any(), any(), any())).thenReturn(saved);

        Response response = urlResource.createUrl(request("https://example.com", null, null));

        CreateUrlResponse body = (CreateUrlResponse) response.getEntity();
        assertThat(body.getShortUrl()).isEqualTo("http://localhost:8080/abcd1234");
    }

    @Test
    void createUrl_nonLocalhostDomain_shortUrlUsesHttpsScheme() {
        ShortUrl saved = ShortUrl.builder()
                .id(1L).domain("short.ly").alias("abcd1234")
                .aliasType(AliasType.GENERATED).longUrl("https://example.com")
                .createdAt(Instant.now())
                .build();
        when(urlService.createShortUrl(any(), any(), any())).thenReturn(saved);

        Response response = urlResource.createUrl(request("https://example.com", null, null));

        CreateUrlResponse body = (CreateUrlResponse) response.getEntity();
        assertThat(body.getShortUrl()).isEqualTo("https://short.ly/abcd1234");
    }

    @Test
    void createUrl_nullCreatedAt_fallsBackToCurrentTimestamp() {
        // createdAt is null on the saved entity (edge case)
        ShortUrl saved = ShortUrl.builder()
                .id(1L).domain("localhost:8080").alias("abcd1234")
                .aliasType(AliasType.GENERATED).longUrl("https://example.com")
                .createdAt(null)
                .build();
        when(urlService.createShortUrl(any(), any(), any())).thenReturn(saved);

        Response response = urlResource.createUrl(request("https://example.com", null, null));

        assertThat(response.getStatus()).isEqualTo(201);
        CreateUrlResponse body = (CreateUrlResponse) response.getEntity();
        assertThat(body.getCreatedAt()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // GET /v1/url
    // -------------------------------------------------------------------------

    @Test
    void listAll_negativePage_returns400WithoutCallingService() {
        Response response = urlResource.listAll(-1);

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(urlService);
    }

    @Test
    void listAll_page0_returns200WithPaginatedBody() {
        ShortUrl url = ShortUrl.builder()
                .id(1L).domain("localhost:8080").alias("abc")
                .aliasType(AliasType.GENERATED).longUrl("https://example.com")
                .createdAt(Instant.now())
                .build();
        when(urlService.listAll(0, 10)).thenReturn(List.of(url));
        when(urlService.getClickCount(1L)).thenReturn(5L);
        when(urlService.countAll()).thenReturn(1L);

        Response response = urlResource.listAll(0);

        assertThat(response.getStatus()).isEqualTo(200);
        ShortUrlPageResponse body = (ShortUrlPageResponse) response.getEntity();
        assertThat(body.getPage()).isEqualTo(0);
        assertThat(body.getPageSize()).isEqualTo(10);
        assertThat(body.getTotalRecords()).isEqualTo(1L);
        assertThat(body.getTotalPages()).isEqualTo(1);
        assertThat(body.getData()).hasSize(1);
        assertThat(body.getData().get(0).getAlias()).isEqualTo("abc");
        assertThat(body.getData().get(0).getClickCount()).isEqualTo(5L);
    }

    @Test
    void listAll_emptyDatabase_returnsEmptyDataList() {
        when(urlService.listAll(0, 10)).thenReturn(List.of());
        when(urlService.countAll()).thenReturn(0L);

        Response response = urlResource.listAll(0);

        assertThat(response.getStatus()).isEqualTo(200);
        ShortUrlPageResponse body = (ShortUrlPageResponse) response.getEntity();
        assertThat(body.getData()).isEmpty();
        assertThat(body.getTotalRecords()).isEqualTo(0L);
        assertThat(body.getTotalPages()).isEqualTo(0);
    }

    @Test
    void listAll_25Records_totalPagesIs3() {
        when(urlService.listAll(0, 10)).thenReturn(List.of());
        when(urlService.countAll()).thenReturn(25L);

        ShortUrlPageResponse body = (ShortUrlPageResponse) urlResource.listAll(0).getEntity();

        assertThat(body.getTotalPages()).isEqualTo(3); // ceil(25/10)
    }

    @Test
    void listAll_shortUrlBuiltCorrectly_forLocalhostDomain() {
        ShortUrl url = ShortUrl.builder()
                .id(1L).domain("localhost:8080").alias("xyz")
                .aliasType(AliasType.GENERATED).longUrl("https://example.com")
                .createdAt(Instant.now())
                .build();
        when(urlService.listAll(0, 10)).thenReturn(List.of(url));
        when(urlService.getClickCount(1L)).thenReturn(0L);
        when(urlService.countAll()).thenReturn(1L);

        ShortUrlPageResponse body = (ShortUrlPageResponse) urlResource.listAll(0).getEntity();

        assertThat(body.getData().get(0).getShortUrl()).isEqualTo("http://localhost:8080/xyz");
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private CreateUrlRequest request(String longUrl, String domain, String alias) {
        CreateUrlRequest req = new CreateUrlRequest();
        req.setLongUrl(longUrl);
        req.setDomain(domain);
        req.setAlias(alias);
        return req;
    }
}
