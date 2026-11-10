CREATE TABLE documents (
    id                UUID          PRIMARY KEY,
    original_filename VARCHAR(255)  NOT NULL,
    s3_key            VARCHAR(512)  NOT NULL UNIQUE,
    content_type      VARCHAR(150)  NOT NULL,
    size_bytes        BIGINT        NOT NULL,
    checksum          VARCHAR(100),
    owner_id          VARCHAR(100)  NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    deleted_at        TIMESTAMPTZ
);

-- Most queries are "documents of this owner with this status, newest first"
CREATE INDEX idx_documents_owner_status_created
    ON documents (owner_id, status, created_at DESC);
