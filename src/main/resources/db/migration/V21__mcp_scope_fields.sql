-- MCP server scope fields (PostgreSQL)
ALTER TABLE mcp_servers
    ADD COLUMN IF NOT EXISTS scope VARCHAR(20) NOT NULL DEFAULT 'GLOBAL';

ALTER TABLE mcp_servers
    ADD COLUMN IF NOT EXISTS agent_id VARCHAR(100);

UPDATE mcp_servers
SET scope = 'GLOBAL'
WHERE scope IS NULL OR scope = '';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_mcp_servers_scope_agent'
    ) THEN
        ALTER TABLE mcp_servers
            ADD CONSTRAINT chk_mcp_servers_scope_agent
            CHECK (
                scope IN ('GLOBAL', 'AGENT')
                AND (scope = 'GLOBAL' OR agent_id IS NOT NULL)
            );
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_mcp_servers_scope
    ON mcp_servers(scope);

CREATE INDEX IF NOT EXISTS idx_mcp_servers_scope_agent
    ON mcp_servers(scope, agent_id);
