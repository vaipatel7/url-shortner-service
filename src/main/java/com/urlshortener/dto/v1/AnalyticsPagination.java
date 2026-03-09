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
@Schema(description = "Pagination metadata for analytics windows")
public class AnalyticsPagination {

    @Schema(description = "Zero-based index of the selected 7-day window (0 = most recent)", example = "0")
    private int currentPage;

    @Schema(description = "Total number of available 7-day windows", example = "4")
    private int totalPages;
}
