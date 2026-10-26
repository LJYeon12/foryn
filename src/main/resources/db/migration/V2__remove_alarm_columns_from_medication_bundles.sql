-- 복약 묶음에서 알람 열 제거

ALTER TABLE medication_bundles DROP COLUMN alarm_enabled;
ALTER TABLE medication_bundles DROP COLUMN alarm_time;