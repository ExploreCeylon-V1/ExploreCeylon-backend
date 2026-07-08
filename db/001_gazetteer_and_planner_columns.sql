-- ─────────────────────────────────────────────────────────────
-- 001_gazetteer_and_planner_columns.sql
--
-- Reviewable, plain SQL documenting the schema changes the trip-planning
-- engine relies on. This is NOT wired into Flyway/Liquibase and does NOT
-- need to be run for the app to work — spring.jpa.hibernate.ddl-auto=update
-- already applies all of this automatically at application startup. This
-- script exists purely so the schema diff can be reviewed/run manually in
-- environments where auto-DDL is disabled (e.g. production).
--
-- Idempotent: safe to run multiple times (IF NOT EXISTS / ADD COLUMN IF
-- NOT EXISTS guards throughout).
-- ─────────────────────────────────────────────────────────────

-- ── New table: locations (place-name gazetteer, fix 1) ─────────────────
CREATE TABLE IF NOT EXISTS locations (
    id        BIGSERIAL PRIMARY KEY,
    name      VARCHAR(255) NOT NULL,
    latitude  DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_locations_name'
    ) THEN
        ALTER TABLE locations ADD CONSTRAINT uk_locations_name UNIQUE (name);
    END IF;
END $$;

-- ── destinations: planner-support columns ───────────────────────────────
ALTER TABLE destinations ADD COLUMN IF NOT EXISTS entry_fee_usd DOUBLE PRECISION;
ALTER TABLE destinations ADD COLUMN IF NOT EXISTS visit_duration_minutes INTEGER;
ALTER TABLE destinations ADD COLUMN IF NOT EXISTS budget_level VARCHAR(20);
ALTER TABLE destinations ADD COLUMN IF NOT EXISTS travel_style_tags VARCHAR(255);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_destinations_budget_level'
    ) THEN
        ALTER TABLE destinations ADD CONSTRAINT chk_destinations_budget_level
            CHECK (budget_level IS NULL OR budget_level IN ('BUDGET', 'MID_RANGE', 'LUXURY'));
    END IF;
END $$;

-- ── hidden_gems: planner-support columns ────────────────────────────────
ALTER TABLE hidden_gems ADD COLUMN IF NOT EXISTS entry_fee_usd DOUBLE PRECISION;
ALTER TABLE hidden_gems ADD COLUMN IF NOT EXISTS visit_duration_minutes INTEGER;
ALTER TABLE hidden_gems ADD COLUMN IF NOT EXISTS budget_level VARCHAR(20);
ALTER TABLE hidden_gems ADD COLUMN IF NOT EXISTS travel_style_tags VARCHAR(255);
ALTER TABLE hidden_gems ADD COLUMN IF NOT EXISTS season_months VARCHAR(255);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_hidden_gems_budget_level'
    ) THEN
        ALTER TABLE hidden_gems ADD CONSTRAINT chk_hidden_gems_budget_level
            CHECK (budget_level IS NULL OR budget_level IN ('BUDGET', 'MID_RANGE', 'LUXURY'));
    END IF;
END $$;

-- ── events: planner-support columns ─────────────────────────────────────
ALTER TABLE events ADD COLUMN IF NOT EXISTS travel_style_tags VARCHAR(255);
ALTER TABLE events ADD COLUMN IF NOT EXISTS estimated_cost DOUBLE PRECISION;

-- ── trips: numeric budget target (fix 4) ────────────────────────────────
ALTER TABLE trips ADD COLUMN IF NOT EXISTS budget_amount_lkr DOUBLE PRECISION;
