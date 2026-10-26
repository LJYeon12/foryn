ALTER TABLE medication_bundles
    ADD COLUMN schedule_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE medication_schedule_outbox
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    bundle_id        BIGINT       NOT NULL,
    scheduled_date   DATE         NOT NULL,
    schedule_version BIGINT       NOT NULL,
    scheduled_at     DATETIME(6)  NOT NULL,
    status           VARCHAR(30)  NOT NULL,
    attempts         INT          NOT NULL DEFAULT 0,
    next_attempt_at  DATETIME(6)  NULL,
    lock_owner       VARCHAR(36)  NULL,
    lock_until       DATETIME(6)  NULL,
    last_error       VARCHAR(100) NULL,
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_medication_outbox_bundle
        FOREIGN KEY (bundle_id) REFERENCES medication_bundles (id) ON DELETE CASCADE,
    CONSTRAINT uk_medication_outbox_schedule UNIQUE (bundle_id, scheduled_date, schedule_version),
    CONSTRAINT chk_medication_outbox_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'RETRY_WAITING', 'COMPLETED', 'SUPERSEDED', 'EXPIRED', 'FAILED')
    ),
    INDEX idx_medication_outbox_runnable (status, next_attempt_at),
    INDEX idx_medication_outbox_lease (status, lock_until)
);
