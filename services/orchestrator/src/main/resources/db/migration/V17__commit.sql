CREATE TABLE IF NOT EXISTS commits (
    commit_id VARCHAR(255) PRIMARY KEY,
    change_id VARCHAR(255),
    task_id VARCHAR(255),
    workspace_id VARCHAR(255),
    branch VARCHAR(255) NOT NULL DEFAULT '',
    message TEXT,
    git_hash VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_commits_task
    ON commits (task_id, commit_id);
