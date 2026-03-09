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
@Schema(description = "Paginated list of short URL summaries")
public class ShortUrlPageResponse {

    @Schema(description = "Short URL entries on the current page")
    private List<ShortUrlSummary> data;

    @Schema(description = "Zero-based current page number", example = "0")
    private int page;

    @Schema(description = "Fixed page size", example = "10")
    private int pageSize;

    @Schema(description = "Total number of short URL records", example = "42")
    private long totalRecords;

    @Schema(description = "Total number of pages", example = "5")
    private int totalPages;
}
