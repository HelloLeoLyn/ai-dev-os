CREATE TABLE IF NOT EXISTS agent_packages (
    agent_id VARCHAR(255) PRIMARY KEY,
    version VARCHAR(64),
    installed BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_packages_installed
    ON agent_packages (installed, agent_id);
