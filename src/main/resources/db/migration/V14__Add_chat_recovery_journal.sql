CREATE TABLE chat_operation_guards (
    user_id BIGINT PRIMARY KEY,
    operation_id VARCHAR(36) NULL,
    fence BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE chat_operations (
    operation_id VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(128) NULL,
    kind VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    fence BIGINT NOT NULL,
    sql_committed BOOLEAN NOT NULL DEFAULT FALSE,
    attempts INT NOT NULL DEFAULT 0,
    lease_until BIGINT NOT NULL,
    next_attempt_at BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    last_error VARCHAR(80) NULL,
    INDEX idx_chat_operations_due (status, lease_until, next_attempt_at),
    INDEX idx_chat_operations_user (user_id, created_at),
    CONSTRAINT chk_chat_operations_status CHECK (
        status IN ('PREPARING', 'RECOVERING', 'RETRY', 'COMPLETED', 'CANCELLED', 'MANUAL')
    )
);
