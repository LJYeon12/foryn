CREATE TABLE notification_send_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bundle_id BIGINT NOT NULL,
    device_token_id BIGINT NOT NULL,
    scheduled_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    lock_until TIMESTAMP NULL,
    sent_at TIMESTAMP NULL,
    last_error VARCHAR(255) NULL,
    UNIQUE KEY uk_bundle_date_token (bundle_id, scheduled_date, device_token_id)
);
