CREATE TABLE IF NOT EXISTS analysis_insight_sets (
    analysis_id VARCHAR(128) PRIMARY KEY,
    source_task_id VARCHAR(128) NOT NULL,
    source_execution_record_id VARCHAR(128) NOT NULL,
    project_id VARCHAR(128),
    workspace_id VARCHAR(128),
    schema_version VARCHAR(64) NOT NULL,
    extractor_type VARCHAR(32) NOT NULL,
    extractor_version VARCHAR(64) NOT NULL,
    extraction_status VARCHAR(32) NOT NULL,
    content_fingerprint VARCHAR(64),
    payload JSONB NOT NULL,
    error_code VARCHAR(128),
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_analysis_insight_source UNIQUE
        (source_task_id, source_execution_record_id, extractor_version)
);
CREATE INDEX IF NOT EXISTS idx_analysis_insight_task ON analysis_insight_sets(source_task_id);
CREATE INDEX IF NOT EXISTS idx_analysis_insight_project_created
    ON analysis_insight_sets(project_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_analysis_insight_status ON analysis_insight_sets(extraction_status);
