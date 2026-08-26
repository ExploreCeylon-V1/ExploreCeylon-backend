-- ─────────────────────────────────────────────────────────────
-- 005_user_verification_kyc.sql
--
-- Schema additions for Identity Verification (KYC) gate:
-- 1. Adds kyc_status column to users table with DEFAULT 'NOT_SUBMITTED'
-- 2. Creates user_verifications table with UUID PK and audit fields
--
-- Idempotent: safe to run multiple times.
-- ─────────────────────────────────────────────────────────────

-- ── 1. users: kyc_status column ────────────────────────────────
ALTER TABLE users ADD COLUMN IF NOT EXISTS kyc_status VARCHAR(20) NOT NULL DEFAULT 'NOT_SUBMITTED';

-- Backfill any existing null rows
UPDATE users SET kyc_status = 'NOT_SUBMITTED' WHERE kyc_status IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_users_kyc_status'
    ) THEN
        ALTER TABLE users ADD CONSTRAINT chk_users_kyc_status
            CHECK (kyc_status IN ('NOT_SUBMITTED', 'PENDING', 'APPROVED', 'REJECTED'));
    END IF;
END $$;

-- ── 2. user_verifications table ────────────────────────────────
CREATE TABLE IF NOT EXISTS user_verifications (
    id                UUID PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    nationality       VARCHAR(100) NOT NULL,
    document_type     VARCHAR(30) NOT NULL,
    front_image_url   VARCHAR(500) NOT NULL,
    back_image_url    VARCHAR(500),
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    rejection_reason  TEXT,
    submitted_at      TIMESTAMP NOT NULL,
    reviewed_at       TIMESTAMP,
    reviewed_by_id    BIGINT REFERENCES users(id) ON DELETE SET NULL
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_user_verifications_doc_type'
    ) THEN
        ALTER TABLE user_verifications ADD CONSTRAINT chk_user_verifications_doc_type
            CHECK (document_type IN ('NIC', 'DRIVING_LICENSE', 'PASSPORT'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_user_verifications_status'
    ) THEN
        ALTER TABLE user_verifications ADD CONSTRAINT chk_user_verifications_status
            CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_user_verifications_user_id ON user_verifications(user_id);
CREATE INDEX IF NOT EXISTS idx_user_verifications_status ON user_verifications(status);
CREATE INDEX IF NOT EXISTS idx_user_verifications_submitted_at ON user_verifications(submitted_at DESC);
