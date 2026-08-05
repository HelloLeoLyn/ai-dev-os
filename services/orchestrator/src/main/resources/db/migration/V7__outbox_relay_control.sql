ALTER TABLE audit_outbox
    ADD COLUMN IF NOT EXISTS topic VARCHAR(64) NOT NULL DEFAULT 'audit',
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS dead_lettered_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_audit_outbox_claim
    ON audit_outbox (next_attempt_at, created_at)
    WHERE published_at IS NULL AND dead_lettered_at IS NULL;
