-- medication_bundles 테이블에 deleted_at 컬럼 추가
ALTER TABLE medication_bundles ADD COLUMN deleted_at DATETIME NULL;

-- 기존에 삭제된 데이터(is_deleted = true)들의 deleted_at을 updated_at으로 채움
UPDATE medication_bundles
SET deleted_at = updated_at
WHERE is_deleted = TRUE;
