-- ─────────────────────────────────────────────────────────────
-- 002_backfill_planner_fields.sql
--
-- Reviewable, plain SQL — NOT wired into Flyway/Liquibase, NOT required
-- for the app to run (ddl-auto=update already creates the columns; the
-- app's own per-category defaults in ItineraryAssemblyService already
-- cover rows that are never backfilled). This script exists so real
-- values can be reviewed/seeded manually ahead of collecting accurate
-- data, for the top-rated rows most likely to appear in generated trips.
--
-- Idempotent: every UPDATE is guarded with "... IS NULL" so re-running
-- this script never clobbers a real value once one has been entered.
--
-- entry_fee_usd values below are PLACEHOLDER ESTIMATES, not verified
-- real-world prices — use judgment, replace with real data as it's
-- collected. budget_level is set to 'MID_RANGE' for all backfilled rows
-- as a safe default; there is no real pricing signal yet to
-- differentiate BUDGET vs LUXURY tiers automatically.
-- visit_duration_minutes / travel_style tag defaults mirror the same
-- category-based fallback maps already used at runtime in
-- ItineraryAssemblyService (DEST_DEFAULT_MINUTES / GEM_DEFAULT_MINUTES)
-- so backfilled data is consistent with what the app already assumes.
-- ─────────────────────────────────────────────────────────────

-- ── destinations: top 50 rated rows ─────────────────────────────────────
UPDATE destinations
SET visit_duration_minutes = CASE category
        WHEN 'BEACH'     THEN 90
        WHEN 'CULTURAL'  THEN 60
        WHEN 'WILDLIFE'  THEN 120
        WHEN 'HILL'      THEN 90
        WHEN 'SURF'      THEN 90
        WHEN 'ADVENTURE' THEN 120
        WHEN 'HERITAGE'  THEN 60
        WHEN 'RELIGIOUS' THEN 45
        WHEN 'CITY'      THEN 60
        ELSE 60
    END
WHERE id IN (SELECT id FROM destinations ORDER BY rating DESC NULLS LAST LIMIT 50)
  AND visit_duration_minutes IS NULL;

UPDATE destinations
SET entry_fee_usd = CASE category
        -- placeholder estimates in USD, see header comment
        WHEN 'BEACH'     THEN 0
        WHEN 'CULTURAL'  THEN 2
        WHEN 'WILDLIFE'  THEN 20
        WHEN 'HILL'      THEN 2
        WHEN 'SURF'      THEN 0
        WHEN 'ADVENTURE' THEN 15
        WHEN 'HERITAGE'  THEN 2
        WHEN 'RELIGIOUS' THEN 1
        WHEN 'CITY'      THEN 0
        ELSE 2
    END
WHERE id IN (SELECT id FROM destinations ORDER BY rating DESC NULLS LAST LIMIT 50)
  AND entry_fee_usd IS NULL;

UPDATE destinations
SET budget_level = 'MID_RANGE'
WHERE id IN (SELECT id FROM destinations ORDER BY rating DESC NULLS LAST LIMIT 50)
  AND budget_level IS NULL;

UPDATE destinations
SET travel_style_tags = CASE category
        WHEN 'BEACH'     THEN 'RELAXATION,FAMILY'
        WHEN 'CULTURAL'  THEN 'CULTURAL'
        WHEN 'WILDLIFE'  THEN 'WILDLIFE,PHOTOGRAPHY'
        WHEN 'HILL'      THEN 'PHOTOGRAPHY,RELAXATION'
        WHEN 'SURF'      THEN 'ADVENTURE'
        WHEN 'ADVENTURE' THEN 'ADVENTURE'
        WHEN 'HERITAGE'  THEN 'CULTURAL,PHOTOGRAPHY'
        WHEN 'RELIGIOUS' THEN 'PILGRIMAGE,CULTURAL'
        WHEN 'CITY'      THEN 'FAMILY'
        ELSE NULL
    END
WHERE id IN (SELECT id FROM destinations ORDER BY rating DESC NULLS LAST LIMIT 50)
  AND travel_style_tags IS NULL;

-- ── hidden_gems: top 30 rated rows (fewer rows than destinations) ──────
UPDATE hidden_gems
SET visit_duration_minutes = CASE category
        WHEN 'BEACH'     THEN 90
        WHEN 'WATERFALL' THEN 60
        WHEN 'RUINS'     THEN 45
        WHEN 'VIEWPOINT' THEN 90
        WHEN 'VILLAGE'   THEN 45
        WHEN 'CAFE'      THEN 30
        WHEN 'TEMPLE'    THEN 45
        ELSE 45
    END
WHERE id IN (SELECT id FROM hidden_gems ORDER BY rating DESC NULLS LAST LIMIT 30)
  AND visit_duration_minutes IS NULL;

UPDATE hidden_gems
SET entry_fee_usd = CASE category
        -- placeholder estimates in USD, see header comment
        WHEN 'BEACH'     THEN 0
        WHEN 'WATERFALL' THEN 2
        WHEN 'RUINS'     THEN 1
        WHEN 'VIEWPOINT' THEN 0
        WHEN 'VILLAGE'   THEN 0
        WHEN 'CAFE'      THEN 0
        WHEN 'TEMPLE'    THEN 1
        ELSE 1
    END
WHERE id IN (SELECT id FROM hidden_gems ORDER BY rating DESC NULLS LAST LIMIT 30)
  AND entry_fee_usd IS NULL;

UPDATE hidden_gems
SET budget_level = 'MID_RANGE'
WHERE id IN (SELECT id FROM hidden_gems ORDER BY rating DESC NULLS LAST LIMIT 30)
  AND budget_level IS NULL;

UPDATE hidden_gems
SET travel_style_tags = CASE category
        WHEN 'BEACH'     THEN 'RELAXATION,FAMILY'
        WHEN 'WATERFALL' THEN 'ADVENTURE,PHOTOGRAPHY'
        WHEN 'RUINS'     THEN 'CULTURAL,PHOTOGRAPHY'
        WHEN 'VIEWPOINT' THEN 'PHOTOGRAPHY,RELAXATION'
        WHEN 'VILLAGE'   THEN 'CULTURAL,FAMILY'
        WHEN 'CAFE'      THEN 'RELAXATION'
        WHEN 'TEMPLE'    THEN 'PILGRIMAGE,CULTURAL'
        ELSE NULL
    END
WHERE id IN (SELECT id FROM hidden_gems ORDER BY rating DESC NULLS LAST LIMIT 30)
  AND travel_style_tags IS NULL;

-- season_months is intentionally left untouched — there is no real
-- seasonal data to backfill it from, and inventing placeholder months
-- would be worse than leaving it NULL (matchesSeason() already treats
-- NULL as "any season", so this is a safe no-op for trip planning).
