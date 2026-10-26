-- 1. 동일 user_id 중복 레코드 정리 (가장 최근 updated_at 1건만 유지)
DELETE s1 FROM subscriptions s1
INNER JOIN subscriptions s2
  ON s1.user_id = s2.user_id
  AND s1.id < s2.id;

-- 2. 1유저=1구독 정책을 DB 레벨에서 강제
ALTER TABLE subscriptions ADD CONSTRAINT uk_subscriptions_user_id UNIQUE (user_id);
