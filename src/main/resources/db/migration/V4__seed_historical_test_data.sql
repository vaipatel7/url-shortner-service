-- Migration: V4 – Seed 2 months of historical test data
--
-- Inserts 6 sample short URLs and ~1 400 click events spread across
-- the past 60 days so the Analytics charts have realistic data to display.
--
-- Guard: checks for the sentinel alias 'gh-claude' that only this migration
-- creates.  User-created URLs do NOT block the seed, so the migration is safe
-- to apply on an already-populated database.
--
-- Click pattern design
--   • Growing trend  – base clicks increase linearly from ~2/day (60 days ago)
--                      to ~7/day (today) per URL.
--   • Weekend dip    – Saturday and Sunday get 55 % of weekday traffic.
--   • Device mix     – DESKTOP 50 %, MOBILE 35 %, TABLET 10 %, BOT 5 %.
--   • 6 URLs         – spread across the full period; each created at a
--                      different point in the past so "created_at" dates vary.
--
-- Estimated row count: 6 URLs × 60 days × ~4.5 clicks (incl. weekend penalty)
--                      ≈ 1 400 click_events rows.

DO $$
BEGIN
    -- -----------------------------------------------------------------
    -- Guard: skip only if THIS seed's sentinel alias already exists.
    -- User-created URLs are ignored so a pre-populated DB is fine.
    -- -----------------------------------------------------------------
    IF (SELECT COUNT(*) FROM short_urls WHERE alias = 'gh-claude') > 0 THEN
        RAISE NOTICE 'Seed alias ''gh-claude'' already present — skipping seed migration V4.';
        RETURN;
    END IF;

    -- -----------------------------------------------------------------
    -- 1. Sample short URLs
    --    Six URLs created at various points over the past two months so
    --    the "Short URLs" tab shows a realistic spread of created_at dates.
    -- -----------------------------------------------------------------
    INSERT INTO short_urls (created_at, domain, alias, alias_type, long_url)
    VALUES
        (NOW() - INTERVAL '60 days', 'localhost:8080', 'gh-claude',   'CUSTOM',
             'https://github.com/anthropics/claude-code'),
        (NOW() - INTERVAL '53 days', 'localhost:8080', 'anthro-docs', 'CUSTOM',
             'https://docs.anthropic.com/'),
        (NOW() - INTERVAL '42 days', 'localhost:8080', 'a1b2c3d4',    'GENERATED',
             'https://en.wikipedia.org/wiki/URL_shortening'),
        (NOW() - INTERVAL '34 days', 'localhost:8080', 'e5f6g7h8',    'GENERATED',
             'https://news.ycombinator.com/'),
        (NOW() - INTERVAL '21 days', 'localhost:8080', 'yt-shorts',   'CUSTOM',
             'https://www.youtube.com/shorts'),
        (NOW() - INTERVAL '10 days', 'localhost:8080', 'x9y0z1w2',    'GENERATED',
             'https://developer.mozilla.org/en-US/');

    -- -----------------------------------------------------------------
    -- 2. Click events
    --
    -- Strategy
    --   day_series  – one row per calendar day for the past 60 days.
    --   url_roster  – the 6 URLs with a 0-based sequential rank.
    --   url_days    – cross-product (url × day) with a computed base
    --                 click count that embeds the trend + weekend dip.
    --   click_rows  – lateral GENERATE_SERIES expands each (url, day)
    --                 into individual click rows; each row gets:
    --                   • a random timestamp within that calendar day
    --                   • a single random() value (rnd) reused for all
    --                     CASE expressions so device/os/browser stay
    --                     internally consistent per click.
    -- -----------------------------------------------------------------
    INSERT INTO click_events (
        short_url_id,
        device_type, device_model,
        os, os_version,
        browser, browser_version,
        user_agent,
        ip_address,
        clicked_at
    )
    WITH
    -- One row per day, 60 days back to today
    day_series AS (
        SELECT
            gs::date                            AS day,
            EXTRACT(DOW FROM gs)::int           AS dow,       -- 0 = Sunday, 6 = Saturday
            (CURRENT_DATE - gs::date)::int      AS days_ago
        FROM generate_series(
            CURRENT_DATE - 59,
            CURRENT_DATE,
            INTERVAL '1 day'
        ) AS gs
    ),

    -- URLs with 0-based rank for round-robin assignment
    url_roster AS (
        SELECT
            id,
            (ROW_NUMBER() OVER (ORDER BY id) - 1)::int AS rn
        FROM short_urls
    ),

    -- Per-(url, day) click budget
    --   Trend formula: 2 + (60 - days_ago) * 0.08
    --     → days_ago = 59 (oldest) : ~2.1 → rounds to 2
    --     → days_ago =  0 (today)  : ~6.8 → rounds to 7
    --   Weekend penalty: 55 % of weekday volume
    url_days AS (
        SELECT
            u.id                                        AS url_id,
            d.day,
            d.dow,
            d.days_ago,
            GREATEST(
                0,
                ROUND(
                    (2.0 + (60 - d.days_ago) * 0.08)
                    * CASE WHEN d.dow IN (0, 6) THEN 0.55 ELSE 1.0 END
                )::int
            )                                           AS base_clicks
        FROM url_roster   u
        CROSS JOIN day_series d
        -- Only generate clicks after the URL was created
        WHERE d.day >= (
            SELECT created_at::date
            FROM   short_urls
            WHERE  id = u.id
        )
    ),

    -- Expand each (url, day) into individual click events
    click_rows AS (
        SELECT
            url_id,
            day,
            -- Random timestamp within the calendar day (UTC)
            (day::timestamp + random() * INTERVAL '24 hours') AT TIME ZONE 'UTC'  AS clicked_at,
            -- One random value per click, reused across all CASE expressions
            random()                                                               AS rnd
        FROM  url_days,
        LATERAL generate_series(1, base_clicks) AS gs(n)
        WHERE base_clicks > 0
    )

    -- Final projection: map rnd → device / os / browser consistently
    SELECT
        url_id                                              AS short_url_id,

        -- Device type  (DESKTOP 50 %, MOBILE 35 %, TABLET 10 %, BOT 5 %)
        CASE
            WHEN rnd < 0.50 THEN 'DESKTOP'
            WHEN rnd < 0.85 THEN 'MOBILE'
            WHEN rnd < 0.95 THEN 'TABLET'
            ELSE                 'BOT'
        END                                                 AS device_type,

        'Generic'                                           AS device_model,

        -- OS aligned to device
        CASE
            WHEN rnd < 0.50 THEN 'Windows'
            WHEN rnd < 0.85 THEN 'Android'
            WHEN rnd < 0.95 THEN 'iOS'
            ELSE                 'Linux'
        END                                                 AS os,

        '10'                                                AS os_version,

        -- Browser aligned to device
        CASE
            WHEN rnd < 0.50 THEN 'Chrome'
            WHEN rnd < 0.85 THEN 'Mobile Chrome'
            WHEN rnd < 0.95 THEN 'Safari'
            ELSE                 'Googlebot'
        END                                                 AS browser,

        '120.0'                                             AS browser_version,
        'Mozilla/5.0 (seed-data; rv:120.0)'                 AS user_agent,

        -- Deterministic-ish IP so the same rnd bin always yields the same subnet
        '10.0.' || FLOOR(rnd * 4)::int::text
              || '.' || (1 + FLOOR(rnd * 253))::int::text   AS ip_address,

        clicked_at

    FROM click_rows
    WHERE clicked_at <= NOW();

    RAISE NOTICE
        'Seed data inserted: % short_urls, % click_events.',
        (SELECT COUNT(*) FROM short_urls),
        (SELECT COUNT(*) FROM click_events);

END $$;
