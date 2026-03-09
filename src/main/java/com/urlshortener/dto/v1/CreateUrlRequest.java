package com.urlshortener.dto.v1;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateUrlRequest {

    @NotBlank(message = "is required and must not be blank")
    @Pattern(
        regexp = "^https?://\\S+",
        message = "must be a valid HTTP or HTTPS URL"
    )
    private String longUrl;

    /** Optional — falls back to the server's configured defaultDomain. */
    private String domain;

    /**
     * Optional custom alias. If provided it must be between 6 and 20 characters.
     * If omitted an 8-character alias is auto-generated.
     */
    @Size(min = 6, max = 20, message = "must be between 6 and 20 characters when provided")
    private String alias;
}
