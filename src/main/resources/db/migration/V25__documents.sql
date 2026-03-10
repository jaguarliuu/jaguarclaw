CREATE TABLE documents (
    id          VARCHAR(36)  PRIMARY KEY,
    parent_id   VARCHAR(36)  REFERENCES documents(id) ON DELETE SET NULL,
    title       VARCHAR(500) NOT NULL DEFAULT 'Untitled',
    content     TEXT         NOT NULL DEFAULT '{}',
    sort_order  INT          NOT NULL DEFAULT 0,
    word_count  INT          NOT NULL DEFAULT 0,
    owner_id    VARCHAR(64)  NOT NULL DEFAULT 'local-default',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_documents_owner   ON documents(owner_id);
CREATE INDEX idx_documents_parent  ON documents(parent_id);
CREATE INDEX idx_documents_updated ON documents(updated_at DESC);
