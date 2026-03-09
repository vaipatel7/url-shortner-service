package com.urlshortener.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClickEvent {

    private Long id;

    /** FK to short_urls.id */
    private Long shortUrlId;

    private String deviceType;
    private String deviceModel;
    private String os;
    private String osVersion;
    private String browser;
    private String browserVersion;

    /** Raw User-Agent header value */
    private String userAgent;

    /** Client IP address */
    private String ipAddress;

    /** UTC timestamp when the click_events row was created (set by DB default) */
    private Instant createdAt;

    /** UTC timestamp when the click/redirect actually happened */
    private Instant clickedAt;
}
