CREATE TABLE IF NOT EXISTS memory_records (
    id VARCHAR(255) PRIMARY KEY,
    project_id VARCHAR(255) NOT NULL DEFAULT 'default',
    type VARCHAR(32) NOT NULL,
    key VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_memory_records_project_type
    ON memory_records (project_id, type, created_at, id);

CREATE INDEX IF NOT EXISTS idx_memory_records_project_key
    ON memory_records (project_id, key);
