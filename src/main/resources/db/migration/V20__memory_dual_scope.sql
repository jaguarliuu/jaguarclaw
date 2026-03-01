-- V20__memory_dual_scope.sql
-- Memory 双层作用域：GLOBAL（共享）+ AGENT（私有）

ALTER TABLE memory_chunks
    ADD COLUMN scope VARCHAR(16);

ALTER TABLE memory_chunks
    ADD COLUMN agent_id VARCHAR(64);

-- 旧数据回填到共享作用域
UPDATE memory_chunks
SET scope = 'GLOBAL'
WHERE scope IS NULL;

ALTER TABLE memory_chunks
    ALTER COLUMN scope SET DEFAULT 'GLOBAL';

ALTER TABLE memory_chunks
    ALTER COLUMN scope SET NOT NULL;

ALTER TABLE memory_chunks
    ADD CONSTRAINT chk_memory_chunks_scope
    CHECK (scope IN ('GLOBAL', 'AGENT'));

CREATE INDEX idx_memory_chunks_scope ON memory_chunks (scope);
CREATE INDEX idx_memory_chunks_scope_agent ON memory_chunks (scope, agent_id);
