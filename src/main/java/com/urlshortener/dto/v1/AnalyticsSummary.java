package com.urlshortener.dto.v1;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Click-count summary comparing the current and previous 7-day windows")
public class AnalyticsSummary {

    @Schema(description = "Clicks recorded in the selected 7-day period", example = "128")
    private long currentPeriodClicks;

    @Schema(description = "Clicks recorded in the preceding 7-day period", example = "95")
    private long previousPeriodClicks;

    @Schema(description = "Percentage change between periods (positive = increase)", example = "34.74")
    private double percentChange;
}
