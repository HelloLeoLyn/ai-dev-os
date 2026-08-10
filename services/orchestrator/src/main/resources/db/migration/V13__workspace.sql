CREATE TABLE IF NOT EXISTS workspaces (
    workspace_id VARCHAR(255) PRIMARY KEY,
    project_id VARCHAR(255) NOT NULL,
    path VARCHAR(1024) NOT NULL,
    branch VARCHAR(255) NOT NULL DEFAULT '',
    repository_url VARCHAR(1024) NOT NULL DEFAULT '',
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_workspaces_project
    ON workspaces (project_id, workspace_id);
