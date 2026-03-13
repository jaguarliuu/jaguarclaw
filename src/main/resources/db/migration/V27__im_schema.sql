-- V27__im_schema.sql
-- 内网 IM 模块：本地身份、联系人、会话、消息

CREATE TABLE IF NOT EXISTS im_identity (
    node_id               VARCHAR(36)  PRIMARY KEY,
    display_name          TEXT         NOT NULL,
    public_key_ed25519    TEXT         NOT NULL,   -- Base64-encoded DER
    public_key_x25519     TEXT         NOT NULL,   -- Base64-encoded DER
    private_key_ed25519   TEXT         NOT NULL,   -- Base64-encoded DER (PKCS#8)
    private_key_x25519    TEXT         NOT NULL,   -- Base64-encoded DER (PKCS#8)
    redis_url             TEXT,                    -- e.g. redis://192.168.1.10:6379
    redis_password        TEXT,
    created_at            TIMESTAMP    NOT NULL    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS im_contacts (
    node_id               VARCHAR(36)  PRIMARY KEY,
    display_name          TEXT         NOT NULL,
    public_key_ed25519    TEXT         NOT NULL,
    public_key_x25519     TEXT         NOT NULL,
    paired_at             TIMESTAMP    NOT NULL    DEFAULT CURRENT_TIMESTAMP,
    status                VARCHAR(16)  NOT NULL    DEFAULT 'active'  -- 'active' | 'blocked'
);

CREATE TABLE IF NOT EXISTS im_conversations (
    id                    VARCHAR(36)  PRIMARY KEY,  -- peer nodeId
    display_name          TEXT,
    last_msg              TEXT,
    last_msg_at           TIMESTAMP,
    unread_count          INTEGER      NOT NULL    DEFAULT 0
);

CREATE TABLE IF NOT EXISTS im_messages (
    id                    VARCHAR(36)  PRIMARY KEY,  -- messageId (UUID from sender)
    conversation_id       VARCHAR(36)  NOT NULL,
    sender_node_id        VARCHAR(36)  NOT NULL,
    type                  VARCHAR(16)  NOT NULL,     -- TEXT | IMAGE | FILE | AGENT_MESSAGE
    content               TEXT         NOT NULL,     -- 解密后明文 JSON
    local_file_path       TEXT,
    created_at            TIMESTAMP    NOT NULL,
    status                VARCHAR(16)  NOT NULL      -- 'sent' | 'delivered' | 'failed'
);

CREATE INDEX IF NOT EXISTS idx_im_messages_conversation ON im_messages(conversation_id, created_at);
