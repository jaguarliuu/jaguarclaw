-- V19__create_agent_profile.sql
-- Multi-Agent 控制面：Agent Profile 持久化模型（PostgreSQL）

CREATE TABLE IF NOT EXISTS agent_profile (
    id                      VARCHAR(36) PRIMARY KEY,
    name                    VARCHAR(100) NOT NULL,
    display_name            VARCHAR(255) NOT NULL,
    description             TEXT,
    workspace_path          TEXT NOT NULL,
    model                   VARCHAR(120),
    enabled                 BOOLEAN NOT NULL DEFAULT TRUE,
    is_default              BOOLEAN NOT NULL DEFAULT FALSE,
    allowed_tools           TEXT,
    excluded_tools          TEXT,
    heartbeat_interval      INT,
    heartbeat_active_hours  VARCHAR(64),
    daily_token_limit       INT,
    monthly_cost_limit      DOUBLE PRECISION,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_agent_profile_name_unique ON agent_profile(name);
CREATE INDEX IF NOT EXISTS idx_agent_profile_enabled ON agent_profile(enabled);
CREATE INDEX IF NOT EXISTS idx_agent_profile_is_default ON agent_profile(is_default);

