ALTER TABLE notification_send_log
    ADD COLUMN notification_type VARCHAR(30) NOT NULL DEFAULT 'MEDICATION';

ALTER TABLE notification_send_log
    ADD COLUMN target_id BIGINT NULL;

ALTER TABLE notification_send_log
    ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

ALTER TABLE notification_send_log
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6);

UPDATE notification_send_log
SET target_id = bundle_id
WHERE target_id IS NULL;

ALTER TABLE notification_send_log
    MODIFY target_id BIGINT NOT NULL;

ALTER TABLE notification_send_log
    DROP INDEX uk_bundle_date_token;

ALTER TABLE notification_send_log
    ADD UNIQUE KEY uk_notification_target_date_token
        (notification_type, target_id, scheduled_date, device_token_id);

ALTER TABLE notification_send_log
    MODIFY bundle_id BIGINT NULL;
