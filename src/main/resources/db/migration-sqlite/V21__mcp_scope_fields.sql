-- MCP server scope fields (SQLite)
ALTER TABLE mcp_servers ADD COLUMN scope VARCHAR(20) NOT NULL DEFAULT 'GLOBAL';
ALTER TABLE mcp_servers ADD COLUMN agent_id VARCHAR(100);

UPDATE mcp_servers
SET scope = 'GLOBAL'
WHERE scope IS NULL OR TRIM(scope) = '';

CREATE INDEX IF NOT EXISTS idx_mcp_servers_scope
    ON mcp_servers(scope);

CREATE INDEX IF NOT EXISTS idx_mcp_servers_scope_agent
    ON mcp_servers(scope, agent_id);
