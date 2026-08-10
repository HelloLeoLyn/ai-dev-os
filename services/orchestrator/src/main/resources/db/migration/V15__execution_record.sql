CREATE TABLE IF NOT EXISTS execution_records (
    id VARCHAR(255) PRIMARY KEY,
    task_id VARCHAR(255),
    agent_name VARCHAR(255),
    status VARCHAR(32),
    message TEXT,
    output TEXT,
    report TEXT,
    artifacts TEXT,
    execution_id VARCHAR(255),
    job_id VARCHAR(255),
    plan_run_id VARCHAR(255),
    step_run_id VARCHAR(255),
    attempt_id VARCHAR(255),
    workspace VARCHAR(1024),
    sandbox VARCHAR(255),
    approval_id VARCHAR(255),
    branch VARCHAR(255),
    before_head VARCHAR(255),
    after_head VARCHAR(255),
    exit_code INTEGER,
    codex_thread_id VARCHAR(255),
    git_status TEXT,
    git_diff_stat TEXT,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_execution_records_task
    ON execution_records (task_id, id);

CREATE INDEX IF NOT EXISTS idx_execution_records_agent
    ON execution_records (agent_name, status);
