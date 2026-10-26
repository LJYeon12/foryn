ALTER TABLE device_tokens ADD COLUMN is_push_enabled BOOLEAN DEFAULT TRUE;

UPDATE device_tokens dt
SET is_push_enabled = (SELECT u.is_notification_enabled FROM users u WHERE u.id = dt.user_id);

ALTER TABLE users DROP COLUMN is_notification_enabled;

ALTER TABLE device_tokens DROP COLUMN last_updated_at;