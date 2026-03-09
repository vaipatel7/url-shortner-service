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
@Schema(description = "Response returned after successfully creating a short URL")
public class CreateUrlResponse {

    @Schema(description = "Auto-generated primary key of the short URL record", example = "42")
    private Long id;

    @Schema(description = "The alias/short code portion of the URL", example = "abc123")
    private String shortCode;

    @Schema(description = "The fully-qualified short URL ready to share", example = "https://short.ly/abc123")
    private String shortUrl;

    @Schema(description = "The original long URL", example = "https://www.example.com/very/long/path")
    private String longUrl;

    @Schema(description = "The domain used for this short URL", example = "short.ly")
    private String domain;

    @Schema(description = "Whether the alias was user-supplied or auto-generated",
            allowableValues = {"CUSTOM", "GENERATED"}, example = "GENERATED")
    private String aliasType;

    @Schema(description = "UTC ISO-8601 timestamp when the record was created",
            example = "2025-03-08T12:00:00Z")
    private String createdAt;
}
