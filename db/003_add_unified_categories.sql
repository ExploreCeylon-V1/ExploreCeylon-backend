-- ─────────────────────────────────────────────────────────────
-- 003_add_unified_categories.sql
--
-- Phase 1 schema update: Additively extends category check constraints
-- on 'destinations' and 'hidden_gems' tables to permit the 8 new unified
-- categories in addition to all existing legacy categories.
--
-- UNIFIED 8-CATEGORY SET:
--   1. ADVENTURE
--   2. CULTURE_HERITAGE
--   3. RELIGIOUS
--   4. WILDLIFE_NATURE
--   5. BEACH_COAST
--   6. HILL_COUNTRY
--   7. SCENIC_VIEWS
--   8. CITY_URBAN
--
-- This script does NOT update or migrate any existing row data.
-- ─────────────────────────────────────────────────────────────

-- ── destinations: update category check constraint ───────────
ALTER TABLE destinations DROP CONSTRAINT IF EXISTS destinations_category_check;

ALTER TABLE destinations ADD CONSTRAINT destinations_category_check
CHECK ((category)::text = ANY (ARRAY[
    -- Existing legacy categories
    'BEACH', 'CULTURAL', 'WILDLIFE', 'HILL', 'SURF', 'ADVENTURE', 'HERITAGE', 'RELIGIOUS', 'CITY',
    -- New unified categories
    'CULTURE_HERITAGE', 'WILDLIFE_NATURE', 'BEACH_COAST', 'HILL_COUNTRY', 'SCENIC_VIEWS', 'CITY_URBAN'
]::text[]));

-- ── hidden_gems: update category check constraint ────────────
ALTER TABLE hidden_gems DROP CONSTRAINT IF EXISTS hidden_gems_category_check;

ALTER TABLE hidden_gems ADD CONSTRAINT hidden_gems_category_check
CHECK ((category)::text = ANY (ARRAY[
    -- Existing legacy categories
    'BEACH', 'WATERFALL', 'RUINS', 'VIEWPOINT', 'VILLAGE', 'CAFE', 'TEMPLE',
    -- New unified categories
    'ADVENTURE', 'CULTURE_HERITAGE', 'RELIGIOUS', 'WILDLIFE_NATURE', 'BEACH_COAST', 'HILL_COUNTRY', 'SCENIC_VIEWS', 'CITY_URBAN'
]::text[]));
