CREATE TABLE IF NOT EXISTS audit_outbox (
    idempotency_key VARCHAR(512) PRIMARY KEY,
    event_payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT
);

CREATE INDEX IF NOT EXISTS idx_audit_outbox_pending
    ON audit_outbox (created_at)
    WHERE published_at IS NULL;
