CREATE TABLE IF NOT EXISTS repair_tasks (
    repair_id VARCHAR(255) PRIMARY KEY,
    task_id VARCHAR(255),
    workspace_id VARCHAR(255),
    failure_context TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_result TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_repair_tasks_task
    ON repair_tasks (task_id, repair_id);
