-- Migration: V3 – Analytics performance indexes
-- These indexes accelerate the analytics queries introduced in AnalyticsService.
--
-- Query patterns targeted:
--   1. countInRange / dailyClicksInRange
--         WHERE clicked_at >= :from AND clicked_at < :to
--         → covered by existing idx_click_events_clicked_at (no change needed)
--
--   2. deviceTypeDistributionSince
--         WHERE clicked_at >= :since GROUP BY device_type
--         → composite (clicked_at, device_type) allows an index-only scan:
--           PostgreSQL filters by clicked_at range, then reads device_type
--           directly from the index without touching heap pages.
--
--   3. findEarliestClickedAt  (SELECT MIN(clicked_at))
--         → satisfied by idx_click_events_clicked_at via a backward index scan.
--
--   4. countByShortUrlId  (WHERE short_url_id = :id)
--         → covered by existing idx_click_events_short_url_id.

-- Composite index for device-type distribution with time filter.
-- For append-heavy workloads consider replacing this with a BRIN index on
-- clicked_at (far smaller, still effective for sequential inserts):
--   CREATE INDEX idx_click_events_clicked_at_brin ON click_events
--   USING brin (clicked_at) WITH (pages_per_range = 128);
CREATE INDEX IF NOT EXISTS idx_click_events_device_type_clicked_at
    ON click_events (clicked_at, device_type);
