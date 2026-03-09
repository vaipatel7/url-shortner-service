package com.urlshortener.resource.v1;

import com.urlshortener.dto.v1.AnalyticsPagination;
import com.urlshortener.dto.v1.AnalyticsResponse;
import com.urlshortener.dto.v1.AnalyticsSummary;
import com.urlshortener.dto.v1.DeviceDistributionResponse;
import com.urlshortener.dto.v1.TimeseriesPoint;
import com.urlshortener.model.DeviceTypeStat;
import com.urlshortener.service.v1.AnalyticsService;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsResourceTest {

    @Mock
    AnalyticsService analyticsService;

    AnalyticsResource analyticsResource;

    @BeforeEach
    void setUp() {
        analyticsResource = new AnalyticsResource(analyticsService);
    }

    // -------------------------------------------------------------------------
    // GET /v1/analytics
    // -------------------------------------------------------------------------

    @Test
    void getAnalytics_page0_returns200WithAnalyticsResponse() {
        AnalyticsResponse analyticsResponse = analyticsResponse(0, 3, 100L, 80L, 25.0);
        when(analyticsService.getAnalytics(0)).thenReturn(analyticsResponse);

        Response response = analyticsResource.getAnalytics(0);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isEqualTo(analyticsResponse);
    }

    @Test
    void getAnalytics_negativePage_returns400WithoutCallingService() {
        Response response = analyticsResource.getAnalytics(-1);

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(analyticsService);
    }

    @Test
    void getAnalytics_page1_delegatesToServiceWithCorrectPage() {
        AnalyticsResponse analyticsResponse = analyticsResponse(1, 3, 50L, 60L, -16.67);
        when(analyticsService.getAnalytics(1)).thenReturn(analyticsResponse);

        Response response = analyticsResource.getAnalytics(1);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(analyticsService).getAnalytics(1);
    }

    @Test
    void getAnalytics_page0_returnsResponseBodyFromService() {
        AnalyticsResponse analyticsResponse = analyticsResponse(0, 2, 42L, 38L, 10.53);
        when(analyticsService.getAnalytics(0)).thenReturn(analyticsResponse);

        Response response = analyticsResource.getAnalytics(0);

        AnalyticsResponse body = (AnalyticsResponse) response.getEntity();
        assertThat(body.getSummary().getCurrentPeriodClicks()).isEqualTo(42L);
        assertThat(body.getSummary().getPreviousPeriodClicks()).isEqualTo(38L);
        assertThat(body.getPagination().getCurrentPage()).isEqualTo(0);
        assertThat(body.getPagination().getTotalPages()).isEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // GET /v1/analytics/devices
    // -------------------------------------------------------------------------

    @Test
    void getDeviceDistribution_returns200WithDeviceStats() {
        List<DeviceTypeStat> stats = List.of(
                DeviceTypeStat.builder().deviceType("DESKTOP").clicks(50L).build(),
                DeviceTypeStat.builder().deviceType("MOBILE").clicks(30L).build(),
                DeviceTypeStat.builder().deviceType("TABLET").clicks(10L).build(),
                DeviceTypeStat.builder().deviceType("BOT").clicks(5L).build()
        );
        when(analyticsService.getDeviceTypeDistribution()).thenReturn(stats);

        Response response = analyticsResource.getDeviceDistribution();

        assertThat(response.getStatus()).isEqualTo(200);
        DeviceDistributionResponse body = (DeviceDistributionResponse) response.getEntity();
        assertThat(body.getData()).isEqualTo(stats);
        assertThat(body.getData()).hasSize(4);
    }

    @Test
    void getDeviceDistribution_emptyStats_returns200WithEmptyList() {
        when(analyticsService.getDeviceTypeDistribution()).thenReturn(List.of());

        Response response = analyticsResource.getDeviceDistribution();

        assertThat(response.getStatus()).isEqualTo(200);
        DeviceDistributionResponse body = (DeviceDistributionResponse) response.getEntity();
        assertThat(body.getData()).isEmpty();
    }

    @Test
    void getDeviceDistribution_delegatesToService() {
        when(analyticsService.getDeviceTypeDistribution()).thenReturn(List.of());

        analyticsResource.getDeviceDistribution();

        verify(analyticsService).getDeviceTypeDistribution();
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private AnalyticsResponse analyticsResponse(int currentPage, int totalPages,
                                                 long current, long previous, double pct) {
        return AnalyticsResponse.builder()
                .summary(AnalyticsSummary.builder()
                        .currentPeriodClicks(current)
                        .previousPeriodClicks(previous)
                        .percentChange(pct)
                        .build())
                .timeseries(List.of(
                        TimeseriesPoint.builder().date("2025-01-01").clicks(current).build()
                ))
                .pagination(AnalyticsPagination.builder()
                        .currentPage(currentPage)
                        .totalPages(totalPages)
                        .build())
                .build();
    }
}
