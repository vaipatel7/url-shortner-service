package com.urlshortener.dto.v1;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Full analytics response for a selected 7-day window")
public class AnalyticsResponse {

    @Schema(description = "Click-count comparison between current and previous periods")
    private AnalyticsSummary summary;

    @Schema(description = "Daily click counts for every day in the selected 7-day window")
    private List<TimeseriesPoint> timeseries;

    @Schema(description = "Pagination details for navigating between weekly windows")
    private AnalyticsPagination pagination;
}
