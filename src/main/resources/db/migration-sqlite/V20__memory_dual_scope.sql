-- V20__memory_dual_scope.sql
-- Memory 双层作用域：GLOBAL（共享）+ AGENT（私有）

ALTER TABLE memory_chunks
    ADD COLUMN scope TEXT NOT NULL DEFAULT 'GLOBAL';

ALTER TABLE memory_chunks
    ADD COLUMN agent_id TEXT;

-- 显式回填，兼容历史数据
UPDATE memory_chunks
SET scope = 'GLOBAL'
WHERE scope IS NULL;

CREATE INDEX idx_memory_chunks_scope ON memory_chunks(scope);
CREATE INDEX idx_memory_chunks_scope_agent ON memory_chunks(scope, agent_id);
