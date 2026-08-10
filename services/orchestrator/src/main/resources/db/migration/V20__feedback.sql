CREATE TABLE IF NOT EXISTS pr_feedback (
    feedback_id VARCHAR(255) PRIMARY KEY,
    task_id VARCHAR(255),
    pull_request_id VARCHAR(255) NOT NULL DEFAULT '',
    repair_task_id VARCHAR(255) NOT NULL DEFAULT '',
    change_id VARCHAR(255) NOT NULL DEFAULT '',
    commit_id VARCHAR(255) NOT NULL DEFAULT '',
    ci_run_id VARCHAR(255) NOT NULL DEFAULT '',
    status VARCHAR(32) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pr_feedback_task
    ON pr_feedback (task_id, feedback_id);

CREATE INDEX IF NOT EXISTS idx_pr_feedback_pr
    ON pr_feedback (pull_request_id);

CREATE INDEX IF NOT EXISTS idx_pr_feedback_ci
    ON pr_feedback (ci_run_id);
