-- Migration: V5 – Seed today's top-10 click data
--
-- Inserts 4 additional short URLs and seeds today's (UTC) click events for all
-- 10 URLs with a deliberate descending click count so the top-URLs chart
-- displays a fully populated, clearly ranked top-10 list on the day the
-- migration runs.
--
-- Click counts assigned (rank 1 → 10):
--   100, 80, 65, 52, 42, 34, 27, 20, 14, 9
--
-- Guard: checks for sentinel alias 'v5-top10-gh' – skips cleanly if already present.
-- The migration is safe on a pre-populated database; it only touches its own
-- sentinel alias and the four new short URLs it introduces.

DO $$
DECLARE
    v_id      BIGINT;
    v_alias   TEXT;
    v_url     TEXT;
    v_clicks  INT;
    v_today   DATE := CURRENT_DATE;

    -- New URLs added by this migration (4 entries to reach 10 total alongside V4's 6)
    new_urls TEXT[][] := ARRAY[
        ARRAY['v5-top10-gh',      'https://github.com/trending'],
        ARRAY['v5-top10-so',      'https://stackoverflow.com/questions'],
        ARRAY['v5-top10-mdntypes','https://developer.mozilla.org/en-US/docs/Web/TypeScript'],
        ARRAY['v5-top10-cldocs',  'https://docs.anthropic.com/en/docs/about-claude/models']
    ];

    -- Final ordered roster: alias → today's click target
    -- Rows are in descending rank so the chart renders a clear staircase.
    ranked_urls TEXT[][] := ARRAY[
        ARRAY['gh-claude',          '100'],
        ARRAY['anthro-docs',         '80'],
        ARRAY['v5-top10-gh',         '65'],
        ARRAY['yt-shorts',           '52'],
        ARRAY['v5-top10-so',         '42'],
        ARRAY['v5-top10-mdntypes',   '34'],
        ARRAY['a1b2c3d4',            '27'],
        ARRAY['e5f6g7h8',            '20'],
        ARRAY['v5-top10-cldocs',     '14'],
        ARRAY['x9y0z1w2',             '9']
    ];

    i INT;
BEGIN
    -- -----------------------------------------------------------------
    -- Guard: skip if sentinel alias already exists
    -- -----------------------------------------------------------------
    IF (SELECT COUNT(*) FROM short_urls WHERE alias = 'v5-top10-gh') > 0 THEN
        RAISE NOTICE 'V5 sentinel alias ''v5-top10-gh'' already present — skipping migration V5.';
        RETURN;
    END IF;

    -- -----------------------------------------------------------------
    -- 1. Insert the 4 new short URLs introduced by this migration
    -- -----------------------------------------------------------------
    FOR i IN 1 .. array_length(new_urls, 1) LOOP
        INSERT INTO short_urls (created_at, domain, alias, alias_type, long_url)
        VALUES (
            NOW(),
            'localhost:8080',
            new_urls[i][1],
            'CUSTOM',
            new_urls[i][2]
        );
    END LOOP;

    -- -----------------------------------------------------------------
    -- 2. Seed today's click events for each of the 10 ranked URLs
    --
    -- Each click gets:
    --   • clicked_at – a random timestamp within today's UTC calendar day
    --   • device / OS / browser – round-robined across the four tiers so
    --     the device-distribution chart stays realistic (no 100 % DESKTOP)
    -- -----------------------------------------------------------------
    FOR i IN 1 .. array_length(ranked_urls, 1) LOOP
        v_alias  := ranked_urls[i][1];
        v_clicks := ranked_urls[i][2]::INT;

        SELECT id INTO v_id FROM short_urls WHERE alias = v_alias;

        IF v_id IS NULL THEN
            RAISE WARNING 'V5 seed: alias ''%'' not found — skipping.', v_alias;
            CONTINUE;
        END IF;

        INSERT INTO click_events (
            short_url_id,
            device_type, device_model,
            os, os_version,
            browser, browser_version,
            user_agent,
            ip_address,
            clicked_at
        )
        SELECT
            v_id,
            -- Rotate through device tiers: DESKTOP 50%, MOBILE 35%, TABLET 10%, BOT 5%
            CASE
                WHEN (gs.n % 20) < 10 THEN 'DESKTOP'
                WHEN (gs.n % 20) < 17 THEN 'MOBILE'
                WHEN (gs.n % 20) < 19 THEN 'TABLET'
                ELSE                       'BOT'
            END,
            'Generic',
            CASE
                WHEN (gs.n % 20) < 10 THEN 'Windows'
                WHEN (gs.n % 20) < 17 THEN 'Android'
                WHEN (gs.n % 20) < 19 THEN 'iOS'
                ELSE                       'Linux'
            END,
            '10',
            CASE
                WHEN (gs.n % 20) < 10 THEN 'Chrome'
                WHEN (gs.n % 20) < 17 THEN 'Mobile Chrome'
                WHEN (gs.n % 20) < 19 THEN 'Safari'
                ELSE                       'Googlebot'
            END,
            '120.0',
            'Mozilla/5.0 (v5-seed; rv:120.0)',
            '10.0.' || (gs.n % 4)::text || '.' || (1 + gs.n % 253)::text,
            -- Spread clicks evenly across today's UTC calendar day
            (v_today::timestamp + (gs.n::numeric / v_clicks) * INTERVAL '24 hours')
                AT TIME ZONE 'UTC'
        FROM generate_series(1, v_clicks) AS gs(n);
    END LOOP;

    RAISE NOTICE
        'V5 seed complete: inserted % new short_urls and today''s top-10 click events.',
        array_length(new_urls, 1);

END $$;
