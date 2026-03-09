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
public class ShortUrl {

    private Long id;

    /** UTC timestamp when this record was created (set by DB default) */
    private Instant createdAt;

    /** The domain used for the short URL (e.g. "short.ly") */
    private String domain;

    /** The short alias/code (unique) */
    private String alias;

    /** Whether this alias was CUSTOM (user-specified) or GENERATED */
    private AliasType aliasType;

    /** The original long URL this short URL redirects to */
    private String longUrl;
}
