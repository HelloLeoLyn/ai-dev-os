CREATE TABLE IF NOT EXISTS recommendation_decisions (
    recommendation_id VARCHAR(128) PRIMARY KEY,
    analysis_id VARCHAR(128) NOT NULL,
    source_task_id VARCHAR(128) NOT NULL,
    project_id VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    defer_until TIMESTAMPTZ,
    defer_reason TEXT,
    ignore_reason TEXT,
    converted_backlog_item_id VARCHAR(128) UNIQUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_recommendation_decision_analysis
    ON recommendation_decisions(analysis_id);
CREATE INDEX IF NOT EXISTS idx_recommendation_decision_status
    ON recommendation_decisions(status);
