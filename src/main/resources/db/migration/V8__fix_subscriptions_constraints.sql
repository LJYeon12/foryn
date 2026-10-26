-- 1. 잘못 설정된 product_id 고유키 삭제
ALTER TABLE subscriptions DROP INDEX uk_subscriptions_product_id;

-- 2. 엔티티 설정에 맞춰 original_transaction_id에 고유키 추가
-- Subscription.java 내 @Column(name = "original_transaction_id", nullable = false, unique = true) 반영
ALTER TABLE subscriptions ADD CONSTRAINT uk_subscriptions_original_transaction_id UNIQUE (original_transaction_id);
