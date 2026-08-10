CREATE TABLE IF NOT EXISTS traces (
    trace_id VARCHAR(255) PRIMARY KEY,
    task_id VARCHAR(255),
    project_id VARCHAR(255),
    graph_id VARCHAR(255),
    node_id VARCHAR(255),
    agent_type VARCHAR(64),
    tool_id VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ,
    duration BIGINT NOT NULL DEFAULT 0,
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_traces_task
    ON traces (task_id, start_time);

CREATE INDEX IF NOT EXISTS idx_traces_project
    ON traces (project_id, start_time);

CREATE INDEX IF NOT EXISTS idx_traces_agent
    ON traces (agent_type, start_time);
