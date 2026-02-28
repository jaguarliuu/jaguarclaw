-- V16__owner_principal_fields.sql
-- 为桌面端主体隔离增加 owner_principal_id

ALTER TABLE sessions ADD COLUMN IF NOT EXISTS owner_principal_id VARCHAR(100);
ALTER TABLE runs ADD COLUMN IF NOT EXISTS owner_principal_id VARCHAR(100);
ALTER TABLE messages ADD COLUMN IF NOT EXISTS owner_principal_id VARCHAR(100);

-- 历史数据回填默认主体
UPDATE sessions SET owner_principal_id = 'local-default' WHERE owner_principal_id IS NULL;
UPDATE runs SET owner_principal_id = 'local-default' WHERE owner_principal_id IS NULL;
UPDATE messages SET owner_principal_id = 'local-default' WHERE owner_principal_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_sessions_owner_principal_id ON sessions(owner_principal_id);
CREATE INDEX IF NOT EXISTS idx_runs_owner_principal_id ON runs(owner_principal_id);
CREATE INDEX IF NOT EXISTS idx_messages_owner_principal_id ON messages(owner_principal_id);
