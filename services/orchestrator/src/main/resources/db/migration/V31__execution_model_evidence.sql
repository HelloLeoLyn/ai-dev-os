ALTER TABLE execution_records
    ADD COLUMN IF NOT EXISTS requested_model_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS resolved_model_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS model_provider VARCHAR(255),
    ADD COLUMN IF NOT EXISTS model_executor VARCHAR(255),
    ADD COLUMN IF NOT EXISTS error_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS error_message TEXT;
