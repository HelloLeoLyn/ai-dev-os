CREATE TABLE IF NOT EXISTS change_sets (
    change_id VARCHAR(255) PRIMARY KEY,
    task_id VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(255),
    project_id VARCHAR(255),
    execution_id VARCHAR(255),
    branch VARCHAR(255) NOT NULL DEFAULT '',
    diff TEXT NOT NULL DEFAULT '',
    diff_stat TEXT NOT NULL DEFAULT '',
    files_changed INTEGER NOT NULL DEFAULT 0,
    insertions INTEGER NOT NULL DEFAULT 0,
    deletions INTEGER NOT NULL DEFAULT 0,
    modified INTEGER NOT NULL DEFAULT 0,
    added INTEGER NOT NULL DEFAULT 0,
    deleted INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    reviewed_by VARCHAR(255),
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_change_sets_task
    ON change_sets (task_id, change_id);
