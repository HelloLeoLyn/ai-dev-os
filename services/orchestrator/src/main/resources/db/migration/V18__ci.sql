CREATE TABLE IF NOT EXISTS ci_runs (
    ci_run_id VARCHAR(255) PRIMARY KEY,
    task_id VARCHAR(255),
    pull_request_id VARCHAR(255),
    provider VARCHAR(64) NOT NULL DEFAULT '',
    pipeline_id VARCHAR(255) NOT NULL DEFAULT '',
    status VARCHAR(32) NOT NULL,
    branch VARCHAR(255) NOT NULL DEFAULT '',
    commit_hash VARCHAR(255) NOT NULL DEFAULT '',
    report_url VARCHAR(1024) NOT NULL DEFAULT '',
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_ci_runs_task
    ON ci_runs (task_id, ci_run_id);

CREATE INDEX IF NOT EXISTS idx_ci_runs_pr
    ON ci_runs (pull_request_id, ci_run_id);
