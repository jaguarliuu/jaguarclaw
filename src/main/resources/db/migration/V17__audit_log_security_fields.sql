-- V17__audit_log_security_fields.sql
-- 为安全审计扩展连接与请求维度

ALTER TABLE node_audit_logs
    ADD COLUMN IF NOT EXISTS connection_id VARCHAR(64);

ALTER TABLE node_audit_logs
    ADD COLUMN IF NOT EXISTS request_id VARCHAR(100);

ALTER TABLE node_audit_logs
    ALTER COLUMN event_type TYPE VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_audit_connection_id ON node_audit_logs(connection_id);
CREATE INDEX IF NOT EXISTS idx_audit_request_id ON node_audit_logs(request_id);
