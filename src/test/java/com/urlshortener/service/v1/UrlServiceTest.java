package com.urlshortener.service.v1;

import com.urlshortener.model.AliasType;
import com.urlshortener.model.ShortUrl;
import com.urlshortener.repository.UrlRepository;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    @Mock
    UrlRepository urlRepository;

    @Mock
    AnalyticsService analyticsService;

    UrlService urlService;

    @BeforeEach
    void setUp() {
        urlService = new UrlService(urlRepository, analyticsService, "localhost:8080");
    }

    // -------------------------------------------------------------------------
    // createShortUrl — generated alias
    // -------------------------------------------------------------------------

    @Test
    void createShortUrl_withoutAlias_generatesAliasAndPersists() {
        ShortUrl saved = shortUrl(1L, "localhost:8080", "abcd1234", AliasType.GENERATED, "https://example.com");
        when(urlRepository.countByAlias(anyString())).thenReturn(0);
        when(urlRepository.insert(any())).thenReturn(saved);

        ShortUrl result = urlService.createShortUrl("https://example.com", null, null);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getAliasType()).isEqualTo(AliasType.GENERATED);
        assertThat(result.getLongUrl()).isEqualTo("https://example.com");
        verify(urlRepository).insert(argThat(u -> u.getAliasType() == AliasType.GENERATED
                && u.getAlias() != null
                && u.getAlias().length() == 8));
    }

    @Test
    void createShortUrl_nullDomain_usesDefaultDomain() {
        ShortUrl saved = shortUrl(1L, "localhost:8080", "abcd1234", AliasType.GENERATED, "https://example.com");
        when(urlRepository.countByAlias(anyString())).thenReturn(0);
        when(urlRepository.insert(any())).thenReturn(saved);

        urlService.createShortUrl("https://example.com", null, null);

        verify(urlRepository).insert(argThat(u -> "localhost:8080".equals(u.getDomain())));
    }

    @Test
    void createShortUrl_blankDomain_usesDefaultDomain() {
        ShortUrl saved = shortUrl(1L, "localhost:8080", "abcd1234", AliasType.GENERATED, "https://example.com");
        when(urlRepository.countByAlias(anyString())).thenReturn(0);
        when(urlRepository.insert(any())).thenReturn(saved);

        urlService.createShortUrl("https://example.com", "   ", null);

        verify(urlRepository).insert(argThat(u -> "localhost:8080".equals(u.getDomain())));
    }

    @Test
    void createShortUrl_withProvidedDomain_usesProvidedDomain() {
        ShortUrl saved = shortUrl(1L, "short.ly", "abcd1234", AliasType.GENERATED, "https://example.com");
        when(urlRepository.countByAlias(anyString())).thenReturn(0);
        when(urlRepository.insert(any())).thenReturn(saved);

        urlService.createShortUrl("https://example.com", "short.ly", null);

        verify(urlRepository).insert(argThat(u -> "short.ly".equals(u.getDomain())));
    }

    // -------------------------------------------------------------------------
    // createShortUrl — custom alias
    // -------------------------------------------------------------------------

    @Test
    void createShortUrl_withCustomAlias_persistsWithCustomType() {
        ShortUrl saved = shortUrl(1L, "localhost:8080", "my-link", AliasType.CUSTOM, "https://example.com");
        when(urlRepository.countByAlias("my-link")).thenReturn(0);
        when(urlRepository.insert(any())).thenReturn(saved);

        ShortUrl result = urlService.createShortUrl("https://example.com", null, "my-link");

        assertThat(result.getAliasType()).isEqualTo(AliasType.CUSTOM);
        assertThat(result.getAlias()).isEqualTo("my-link");
        verify(urlRepository).countByAlias("my-link");
        verify(urlRepository).insert(argThat(u -> "my-link".equals(u.getAlias())
                && u.getAliasType() == AliasType.CUSTOM));
    }

    @Test
    void createShortUrl_customAliasAlreadyInDb_throws422() {
        when(urlRepository.countByAlias("taken-alias")).thenReturn(1);

        assertThatExceptionOfType(WebApplicationException.class)
                .isThrownBy(() -> urlService.createShortUrl("https://example.com", null, "taken-alias"))
                .satisfies(ex -> assertThat(ex.getResponse().getStatus()).isEqualTo(422));

        verify(urlRepository, times(0)).insert(any());
    }

    @Test
    void createShortUrl_customAliasAlreadyInMemoryCache_throws422WithoutDbCheck() {
        // Seed the in-memory cache by completing a successful creation
        ShortUrl first = shortUrl(1L, "localhost:8080", "cached-alias", AliasType.CUSTOM, "https://a.com");
        when(urlRepository.countByAlias("cached-alias")).thenReturn(0);
        when(urlRepository.insert(any())).thenReturn(first);
        urlService.createShortUrl("https://a.com", null, "cached-alias");

        // Second attempt: alias is now in-memory → throws 422 without hitting the DB
        assertThatExceptionOfType(WebApplicationException.class)
                .isThrownBy(() -> urlService.createShortUrl("https://b.com", null, "cached-alias"))
                .satisfies(ex -> assertThat(ex.getResponse().getStatus()).isEqualTo(422));

        // countByAlias was called only once (for the first creation, not the second)
        verify(urlRepository, times(1)).countByAlias("cached-alias");
    }

    @Test
    void createShortUrl_uniqueConstraintViolationDuringInsert_throws409() {
        when(urlRepository.countByAlias(anyString())).thenReturn(0);
        SQLException sqlEx = new SQLException("duplicate key value violates unique constraint", "23505");
        when(urlRepository.insert(any())).thenThrow(new RuntimeException("DB error", sqlEx));

        assertThatExceptionOfType(WebApplicationException.class)
                .isThrownBy(() -> urlService.createShortUrl("https://example.com", null, "race-alias"))
                .satisfies(ex -> assertThat(ex.getResponse().getStatus()).isEqualTo(409));
    }

    @Test
    void createShortUrl_nestedUniqueConstraintViolation_throws409() {
        // Verifies that the cause chain is traversed (not just direct cause)
        when(urlRepository.countByAlias(anyString())).thenReturn(0);
        SQLException sqlEx = new SQLException("duplicate key", "23505");
        RuntimeException wrapper = new RuntimeException("outer", new RuntimeException("inner", sqlEx));
        when(urlRepository.insert(any())).thenThrow(wrapper);

        assertThatExceptionOfType(WebApplicationException.class)
                .isThrownBy(() -> urlService.createShortUrl("https://example.com", null, "deep-race"))
                .satisfies(ex -> assertThat(ex.getResponse().getStatus()).isEqualTo(409));
    }

    // -------------------------------------------------------------------------
    // resolve
    // -------------------------------------------------------------------------

    @Test
    void resolve_knownAlias_returnsShortUrl() {
        ShortUrl expected = shortUrl(1L, "localhost:8080", "abc123", AliasType.GENERATED, "https://example.com");
        when(urlRepository.findByAlias("abc123")).thenReturn(Optional.of(expected));

        ShortUrl result = urlService.resolve("abc123");

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void resolve_unknownAlias_throws404() {
        when(urlRepository.findByAlias("unknown")).thenReturn(Optional.empty());

        assertThatExceptionOfType(WebApplicationException.class)
                .isThrownBy(() -> urlService.resolve("unknown"))
                .satisfies(ex -> assertThat(ex.getResponse().getStatus()).isEqualTo(404));
    }

    // -------------------------------------------------------------------------
    // listAll / countAll / getClickCount
    // -------------------------------------------------------------------------

    @Test
    void listAll_page0_callsRepositoryWithLimitAndZeroOffset() {
        when(urlRepository.findAll(10, 0)).thenReturn(List.of());

        urlService.listAll(0, 10);

        verify(urlRepository).findAll(10, 0);
    }

    @Test
    void listAll_page2_callsRepositoryWithCorrectOffset() {
        when(urlRepository.findAll(10, 20)).thenReturn(List.of());

        urlService.listAll(2, 10);

        verify(urlRepository).findAll(10, 20);
    }

    @Test
    void listAll_returnsRepositoryResults() {
        ShortUrl url = shortUrl(1L, "localhost:8080", "abc", AliasType.GENERATED, "https://example.com");
        when(urlRepository.findAll(eq(10), eq(0))).thenReturn(List.of(url));

        List<ShortUrl> result = urlService.listAll(0, 10);

        assertThat(result).containsExactly(url);
    }

    @Test
    void countAll_delegatesToRepository() {
        when(urlRepository.countAll()).thenReturn(42L);

        assertThat(urlService.countAll()).isEqualTo(42L);
    }

    @Test
    void getClickCount_delegatesToAnalyticsService() {
        when(analyticsService.getClickCount(7L)).thenReturn(99L);

        assertThat(urlService.getClickCount(7L)).isEqualTo(99L);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private ShortUrl shortUrl(Long id, String domain, String alias, AliasType type, String longUrl) {
        return ShortUrl.builder()
                .id(id)
                .domain(domain)
                .alias(alias)
                .aliasType(type)
                .longUrl(longUrl)
                .createdAt(Instant.now())
                .build();
    }
}
