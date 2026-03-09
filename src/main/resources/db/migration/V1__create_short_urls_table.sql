-- Migration: V1 – Create short_urls table
-- Runs automatically at startup via Flyway.

CREATE TABLE IF NOT EXISTS short_urls
(
    id         BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    domain     TEXT                     NOT NULL,
    alias      TEXT                     NOT NULL UNIQUE,
    alias_type VARCHAR(20)              NOT NULL CHECK (alias_type IN ('CUSTOM', 'GENERATED')),
    long_url   TEXT                     NOT NULL
);

-- Index on alias for fast lookups during redirection
CREATE INDEX IF NOT EXISTS idx_short_urls_alias ON short_urls (alias);
