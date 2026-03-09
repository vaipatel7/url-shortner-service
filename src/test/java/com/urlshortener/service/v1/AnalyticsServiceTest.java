package com.urlshortener.service.v1;

import com.urlshortener.dto.v1.AnalyticsResponse;
import com.urlshortener.model.DailyClickCount;
import com.urlshortener.model.DeviceTypeStat;
import com.urlshortener.repository.ClickEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    ClickEventRepository clickEventRepository;

    AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(clickEventRepository);
    }

    // -------------------------------------------------------------------------
    // Delegation tests
    // -------------------------------------------------------------------------

    @Test
    void getClickCount_delegatesToRepository() {
        when(clickEventRepository.countByShortUrlId(3L)).thenReturn(17L);

        assertThat(analyticsService.getClickCount(3L)).isEqualTo(17L);
    }

    @Test
    void getTotalClickCount_delegatesToRepository() {
        when(clickEventRepository.countAll()).thenReturn(1234L);

        assertThat(analyticsService.getTotalClickCount()).isEqualTo(1234L);
    }

    @Test
    void getDeviceTypeDistribution_delegatesToRepository() {
        List<DeviceTypeStat> expected = List.of(
                DeviceTypeStat.builder().deviceType("DESKTOP").clicks(100L).build(),
                DeviceTypeStat.builder().deviceType("MOBILE").clicks(60L).build()
        );
        when(clickEventRepository.deviceTypeDistributionSince(any())).thenReturn(expected);

        List<DeviceTypeStat> result = analyticsService.getDeviceTypeDistribution();

        assertThat(result).isEqualTo(expected);
        verify(clickEventRepository).deviceTypeDistributionSince(any(Instant.class));
    }

    // -------------------------------------------------------------------------
    // Percent change calculation
    // -------------------------------------------------------------------------

    @Test
    void getAnalytics_noPreviousOrCurrentClicks_percentChangeIsZero() {
        stubAnalytics(0L, 0L, Optional.empty(), List.of());

        AnalyticsResponse response = analyticsService.getAnalytics(0);

        assertThat(response.getSummary().getPercentChange()).isEqualTo(0.0);
        assertThat(response.getSummary().getCurrentPeriodClicks()).isEqualTo(0L);
        assertThat(response.getSummary().getPreviousPeriodClicks()).isEqualTo(0L);
    }

    @Test
    void getAnalytics_currentClicksButNoPreviousClicks_percentChangeIs100() {
        stubAnalytics(10L, 0L, Optional.empty(), List.of());

        AnalyticsResponse response = analyticsService.getAnalytics(0);

        assertThat(response.getSummary().getPercentChange()).isEqualTo(100.0);
        assertThat(response.getSummary().getCurrentPeriodClicks()).isEqualTo(10L);
    }

    @Test
    void getAnalytics_currentHigherThanPrevious_positivePercentChange() {
        // (15 - 10) / 10 * 100 = 50.0%
        stubAnalytics(15L, 10L, Optional.empty(), List.of());

        AnalyticsResponse response = analyticsService.getAnalytics(0);

        assertThat(response.getSummary().getPercentChange()).isEqualTo(50.0);
    }

    @Test
    void getAnalytics_currentLowerThanPrevious_negativePercentChange() {
        // (5 - 10) / 10 * 100 = -50.0%
        stubAnalytics(5L, 10L, Optional.empty(), List.of());

        AnalyticsResponse response = analyticsService.getAnalytics(0);

        assertThat(response.getSummary().getPercentChange()).isEqualTo(-50.0);
    }

    @Test
    void getAnalytics_equalPeriods_zeroPercentChange() {
        stubAnalytics(20L, 20L, Optional.empty(), List.of());

        AnalyticsResponse response = analyticsService.getAnalytics(0);

        assertThat(response.getSummary().getPercentChange()).isEqualTo(0.0);
    }

    // -------------------------------------------------------------------------
    // Timeseries construction
    // -------------------------------------------------------------------------

    @Test
    void getAnalytics_emptyDailyData_timeseriesHas7ZeroPoints() {
        stubAnalytics(0L, 0L, Optional.empty(), List.of());

        AnalyticsResponse response = analyticsService.getAnalytics(0);

        assertThat(response.getTimeseries()).hasSize(7);
        assertThat(response.getTimeseries()).allMatch(p -> p.getClicks() == 0L);
    }

    @Test
    void getAnalytics_partialDailyData_missingDaysFilledWithZero() {
        // Provide data for 2 out of 7 days — the rest should come back as 0
        LocalDate startDate = LocalDate.now(ZoneOffset.UTC).minusDays(7);
        List<DailyClickCount> rawData = List.of(
                DailyClickCount.builder().date(startDate).clicks(5L).build(),
                DailyClickCount.builder().date(startDate.plusDays(3)).clicks(12L).build()
        );
        stubAnalytics(17L, 0L, Optional.empty(), rawData);

        AnalyticsResponse response = analyticsService.getAnalytics(0);

        assertThat(response.getTimeseries()).hasSize(7);
        long zeroCount = response.getTimeseries().stream().filter(p -> p.getClicks() == 0L).count();
        assertThat(zeroCount).isEqualTo(5); // 7 - 2 provided days
    }

    @Test
    void getAnalytics_timeseriesPointsHaveConsecutiveDates() {
        stubAnalytics(0L, 0L, Optional.empty(), List.of());

        AnalyticsResponse response = analyticsService.getAnalytics(0);

        List<LocalDate> dates = response.getTimeseries().stream()
                .map(p -> LocalDate.parse(p.getDate()))
                .toList();
        for (int i = 1; i < dates.size(); i++) {
            assertThat(dates.get(i))
                    .as("date at index %d should follow date at index %d", i, i - 1)
                    .isEqualTo(dates.get(i - 1).plusDays(1));
        }
    }

    @Test
    void getAnalytics_timeseriesDateStringsAreIsoLocalDate() {
        stubAnalytics(0L, 0L, Optional.empty(), List.of());

        AnalyticsResponse response = analyticsService.getAnalytics(0);

        // Each date string should be parseable as a LocalDate (YYYY-MM-DD)
        assertThat(response.getTimeseries())
                .allSatisfy(p -> assertThat(LocalDate.parse(p.getDate())).isNotNull());
    }

    // -------------------------------------------------------------------------
    // Pagination
    // -------------------------------------------------------------------------

    @Test
    void getAnalytics_noClicksEver_totalPagesIs1() {
        stubAnalytics(0L, 0L, Optional.empty(), List.of());

        AnalyticsResponse response = analyticsService.getAnalytics(0);

        assertThat(response.getPagination().getTotalPages()).isEqualTo(1);
    }

    @Test
    void getAnalytics_currentPageReflectedInPagination() {
        stubAnalytics(0L, 0L, Optional.empty(), List.of());

        AnalyticsResponse response = analyticsService.getAnalytics(2);

        assertThat(response.getPagination().getCurrentPage()).isEqualTo(2);
    }

    @Test
    void getAnalytics_earliestClickAt21DaysAgo_totalPagesIs3() {
        // ceil(21 / 7) = 3
        Instant earliest = Instant.now().minusSeconds(21L * 24 * 3600);
        stubAnalytics(0L, 0L, Optional.of(earliest), List.of());

        AnalyticsResponse response = analyticsService.getAnalytics(0);

        assertThat(response.getPagination().getTotalPages()).isEqualTo(3);
    }

    @Test
    void getAnalytics_earliestClickAt8DaysAgo_totalPagesIs2() {
        // ceil(8 / 7) = 2
        Instant earliest = Instant.now().minusSeconds(8L * 24 * 3600);
        stubAnalytics(0L, 0L, Optional.of(earliest), List.of());

        AnalyticsResponse response = analyticsService.getAnalytics(0);

        assertThat(response.getPagination().getTotalPages()).isEqualTo(2);
    }

    @Test
    void getAnalytics_earliestClickAtExactly7DaysAgo_totalPagesIs1() {
        // ceil(7 / 7) = 1; max(1, 1) = 1
        Instant earliest = Instant.now().minusSeconds(7L * 24 * 3600);
        stubAnalytics(0L, 0L, Optional.of(earliest), List.of());

        AnalyticsResponse response = analyticsService.getAnalytics(0);

        assertThat(response.getPagination().getTotalPages()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /**
     * Stubs the repository for a single getAnalytics call.
     * countInRange is called twice: first for current period, then for previous.
     */
    private void stubAnalytics(long currentClicks, long previousClicks,
                                Optional<Instant> earliest,
                                List<DailyClickCount> daily) {
        when(clickEventRepository.countInRange(any(), any()))
                .thenReturn(currentClicks)
                .thenReturn(previousClicks);
        when(clickEventRepository.findEarliestClickedAt()).thenReturn(earliest);
        when(clickEventRepository.dailyClicksInRange(any(), any())).thenReturn(daily);
    }
}
