ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS source_backlog_item_id VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_tasks_source_backlog
    ON tasks(source_backlog_item_id);
