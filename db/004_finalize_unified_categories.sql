-- ─────────────────────────────────────────────────────────────
-- 004_finalize_unified_categories.sql
--
-- Phase 4 schema cleanup: Restricts PostgreSQL category check constraints
-- on 'destinations' and 'hidden_gems' to ONLY the 8 unified categories.
--
-- TARGET UNIFIED 8-CATEGORY SET:
--   1. ADVENTURE
--   2. CULTURE_HERITAGE
--   3. RELIGIOUS
--   4. WILDLIFE_NATURE
--   5. BEACH_COAST
--   6. HILL_COUNTRY
--   7. SCENIC_VIEWS
--   8. CITY_URBAN
-- ─────────────────────────────────────────────────────────────

-- ── destinations: final strict category constraint ───────────
ALTER TABLE destinations DROP CONSTRAINT IF EXISTS destinations_category_check;

ALTER TABLE destinations ADD CONSTRAINT destinations_category_check
CHECK ((category)::text = ANY (ARRAY[
    'ADVENTURE', 'CULTURE_HERITAGE', 'RELIGIOUS', 'WILDLIFE_NATURE',
    'BEACH_COAST', 'HILL_COUNTRY', 'SCENIC_VIEWS', 'CITY_URBAN'
]::text[]));

-- ── hidden_gems: final strict category constraint ────────────
ALTER TABLE hidden_gems DROP CONSTRAINT IF EXISTS hidden_gems_category_check;

ALTER TABLE hidden_gems ADD CONSTRAINT hidden_gems_category_check
CHECK ((category)::text = ANY (ARRAY[
    'ADVENTURE', 'CULTURE_HERITAGE', 'RELIGIOUS', 'WILDLIFE_NATURE',
    'BEACH_COAST', 'HILL_COUNTRY', 'SCENIC_VIEWS', 'CITY_URBAN'
]::text[]));
