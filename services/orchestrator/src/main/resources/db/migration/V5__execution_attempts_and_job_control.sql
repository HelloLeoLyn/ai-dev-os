CREATE TABLE IF NOT EXISTS jobs (
    id VARCHAR(255) PRIMARY KEY,
    task_snapshot JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    result JSONB,
    execution_record_id VARCHAR(255),
    result_summary TEXT,
    error_message TEXT,
    approval_id VARCHAR(255),
    attempt_no INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 1,
    available_at TIMESTAMPTZ,
    priority INTEGER NOT NULL DEFAULT 0,
    lease_owner VARCHAR(255),
    lease_token BIGINT,
    lease_expires_at TIMESTAMPTZ,
    heartbeat_at TIMESTAMPTZ,
    version INTEGER NOT NULL DEFAULT 0,
    recovery_count INTEGER NOT NULL DEFAULT 0,
    last_failure_code VARCHAR(255),
    recovery_policy VARCHAR(32) NOT NULL DEFAULT 'MANUAL'
);

CREATE TABLE IF NOT EXISTS execution_attempts (
    id VARCHAR(255) PRIMARY KEY,
    job_id VARCHAR(255) NOT NULL,
    attempt_no INTEGER NOT NULL,
    execution_id VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    lease_owner VARCHAR(255),
    lease_token BIGINT,
    lease_expires_at TIMESTAMPTZ,
    heartbeat_at TIMESTAMPTZ,
    failure_code VARCHAR(255),
    recovery_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_jobs_claim
    ON jobs (created_at, id)
    WHERE status IN ('QUEUED', 'RETRY_WAIT');

CREATE INDEX IF NOT EXISTS idx_jobs_stale
    ON jobs (lease_expires_at, id)
    WHERE status = 'RUNNING';

CREATE INDEX IF NOT EXISTS idx_execution_attempts_job
    ON execution_attempts (job_id, attempt_no, id);

CREATE INDEX IF NOT EXISTS idx_execution_attempts_status
    ON execution_attempts (status, created_at, id);
