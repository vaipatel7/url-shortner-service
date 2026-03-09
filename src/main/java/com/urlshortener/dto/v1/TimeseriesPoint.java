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
@Schema(description = "Click count for a single calendar day")
public class TimeseriesPoint {

    @Schema(description = "ISO-8601 date (UTC)", example = "2025-03-08")
    private String date;

    @Schema(description = "Number of clicks recorded on this date", example = "23")
    private long clicks;
}
