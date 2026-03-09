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
@Schema(description = "Summary of a single short URL entry")
public class ShortUrlSummary {

    @Schema(description = "The short alias/code", example = "aB3xK9mZ")
    private String alias;

    @Schema(description = "The original long URL", example = "https://www.example.com/some/long/path")
    private String longUrl;

    @Schema(description = "The fully-qualified short URL", example = "https://short.ly/aB3xK9mZ")
    private String shortUrl;

    @Schema(description = "Total number of clicks recorded for this short URL", example = "17")
    private long clickCount;
}
