-- Migration: V2 – Create click_events table
-- Runs automatically at startup via Flyway.

CREATE TABLE IF NOT EXISTS click_events
(
    id              BIGSERIAL PRIMARY KEY,
    short_url_id    BIGINT                   NOT NULL REFERENCES short_urls (id) ON DELETE CASCADE,
    device_type     VARCHAR(30),
    device_model    VARCHAR(50),
    os              VARCHAR(30),
    os_version      VARCHAR(30),
    browser         VARCHAR(30),
    browser_version VARCHAR(30),
    user_agent      TEXT                     NOT NULL,
    ip_address      TEXT                     NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    clicked_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Index for analytics queries per short URL
CREATE INDEX IF NOT EXISTS idx_click_events_short_url_id ON click_events (short_url_id);

-- Index for time-series analytics
CREATE INDEX IF NOT EXISTS idx_click_events_clicked_at ON click_events (clicked_at);
