CREATE TABLE IF NOT EXISTS audit_events (
    sequence_id BIGINT GENERATED ALWAYS AS IDENTITY,
    id VARCHAR(255) PRIMARY KEY,
    event_type VARCHAR(96) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    plan_run_id VARCHAR(255),
    step_run_id VARCHAR(255),
    attempt_id VARCHAR(255),
    job_id VARCHAR(255),
    execution_id VARCHAR(255),
    execution_record_id VARCHAR(255),
    invocation_id VARCHAR(255),
    approval_id VARCHAR(255),
    idempotency_key VARCHAR(512) NOT NULL UNIQUE,
    payload JSONB NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_events_aggregate
    ON audit_events (aggregate_type, aggregate_id, occurred_at, sequence_id);
CREATE INDEX IF NOT EXISTS idx_audit_events_plan_run
    ON audit_events (plan_run_id, occurred_at, sequence_id);
CREATE INDEX IF NOT EXISTS idx_audit_events_step_run
    ON audit_events (step_run_id, occurred_at, sequence_id);
CREATE INDEX IF NOT EXISTS idx_audit_events_job
    ON audit_events (job_id, occurred_at, sequence_id);
CREATE INDEX IF NOT EXISTS idx_audit_events_execution
    ON audit_events (execution_id, occurred_at, sequence_id);
CREATE INDEX IF NOT EXISTS idx_audit_events_execution_record
    ON audit_events (execution_record_id, occurred_at, sequence_id);
CREATE INDEX IF NOT EXISTS idx_audit_events_invocation
    ON audit_events (invocation_id, occurred_at, sequence_id);
CREATE INDEX IF NOT EXISTS idx_audit_events_approval
    ON audit_events (approval_id, occurred_at, sequence_id);
CREATE INDEX IF NOT EXISTS idx_audit_events_type
    ON audit_events (event_type, occurred_at, sequence_id);
