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
@Schema(description = "Standard error response body")
public class ErrorResponse {

    @Schema(description = "HTTP status code", example = "404")
    private int code;

    @Schema(description = "Human-readable error description", example = "Short URL 'xyz' not found.")
    private String message;
}
