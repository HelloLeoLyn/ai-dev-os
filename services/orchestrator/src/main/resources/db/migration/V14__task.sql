CREATE TABLE IF NOT EXISTS tasks (
    task_id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    project_id VARCHAR(255) NOT NULL DEFAULT 'default',
    workspace_id VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    approval_id VARCHAR(255),
    plan_run_id VARCHAR(255),
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_tasks_project
    ON tasks (project_id, status, created_at, task_id);
