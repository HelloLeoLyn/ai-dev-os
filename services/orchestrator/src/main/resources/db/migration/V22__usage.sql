CREATE TABLE IF NOT EXISTS usage_records (
    usage_id VARCHAR(255) PRIMARY KEY,
    task_id VARCHAR(255),
    project_id VARCHAR(255),
    agent_type VARCHAR(64),
    model VARCHAR(128),
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    total_tokens BIGINT NOT NULL DEFAULT 0,
    estimated_cost DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_usage_records_task
    ON usage_records (task_id, usage_id);

CREATE INDEX IF NOT EXISTS idx_usage_records_project
    ON usage_records (project_id);

CREATE INDEX IF NOT EXISTS idx_usage_records_agent
    ON usage_records (agent_type);
