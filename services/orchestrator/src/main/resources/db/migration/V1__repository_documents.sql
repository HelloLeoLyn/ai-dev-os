CREATE TABLE IF NOT EXISTS repository_documents (
    repository_type VARCHAR(64) NOT NULL,
    entity_id VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    secondary_key VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (repository_type, entity_id)
);

CREATE INDEX IF NOT EXISTS idx_repository_documents_type_secondary
    ON repository_documents (repository_type, secondary_key);

CREATE UNIQUE INDEX IF NOT EXISTS uq_plan_run_approval
    ON repository_documents (secondary_key)
    WHERE repository_type = 'plan-run';
