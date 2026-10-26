/* 1. 사용자 */
CREATE TABLE users
(
    id                      BIGINT AUTO_INCREMENT,
    created_at              DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    birth_year              INT          NULL,
    deleted_at              DATETIME(6)  NULL,
    disorders               JSON         NULL,
    email                   VARCHAR(255) NOT NULL,
    first_name              VARCHAR(255) NULL,
    full_name               VARCHAR(255) NOT NULL,
    gender                  VARCHAR(50)  DEFAULT 'UNKNOWN' NOT NULL,
    is_deleted              BIT          NOT NULL DEFAULT 0,
    is_notification_enabled BIT          NULL,
    jobs                    JSON         NULL,
    last_name               VARCHAR(255) NULL,
    nickname                VARCHAR(100) NULL,
    symptoms                JSON         NULL,
    provider_type           VARCHAR(50)  NOT NULL,
    provider_user_id        VARCHAR(255) NULL,

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_provider_user_id UNIQUE (provider_user_id)
);

/* 2. 차단된 토큰 */
CREATE TABLE blacklisted_tokens
(
    id         BIGINT AUTO_INCREMENT,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6)  NOT NULL,
    token      VARCHAR(500) NOT NULL,
    user_id    BIGINT       NULL,

    CONSTRAINT pk_blacklisted_tokens PRIMARY KEY (id)
);

/* 3. 갱신 토큰 */
CREATE TABLE refresh_tokens
(
    id         BIGINT AUTO_INCREMENT,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6)  NULL,
    token      VARCHAR(255) NOT NULL,
    user_id    BIGINT       NOT NULL,

    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token UNIQUE (token),
    -- 유저 삭제 시 리프레시 토큰도 같이 삭제 (CASCADE)
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

/* 4. 기기 토큰 */
CREATE TABLE device_tokens
(
    id              BIGINT AUTO_INCREMENT,
    device_token    VARCHAR(255) NOT NULL,
    last_updated_at DATETIME(6)  NOT NULL,
    user_id         BIGINT       NOT NULL,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_device_tokens PRIMARY KEY (id),
    CONSTRAINT uk_device_tokens_token UNIQUE (device_token),
    -- 유저 삭제 시 디바이스 토큰도 같이 삭제
    CONSTRAINT fk_device_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

/* 5. 복약 묶음 */
CREATE TABLE medication_bundles
(
    id             BIGINT AUTO_INCREMENT,
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    alarm_enabled  BIT          NOT NULL DEFAULT 1,
    alarm_time     TIME(6)      NULL,
    bundle_name    VARCHAR(50)  NOT NULL,
    day_of_week    VARCHAR(255) NULL,
    is_deleted     BIT          NULL DEFAULT 0,
    scheduled_time TIME(6)      NULL,
    user_id        BIGINT       NOT NULL,

    CONSTRAINT pk_medication_bundles PRIMARY KEY (id),
    CONSTRAINT fk_medication_bundles_user FOREIGN KEY (user_id) REFERENCES users (id)
);

/* 6. 복약 항목 */
CREATE TABLE medication_items
(
    id                   BIGINT AUTO_INCREMENT,
    created_at           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    medication_name      VARCHAR(50) NOT NULL,
    medication_bundle_id BIGINT      NOT NULL,

    CONSTRAINT pk_medication_items PRIMARY KEY (id),
    -- 번들 삭제 시 약 아이템도 같이 삭제
    CONSTRAINT fk_medication_items_bundle FOREIGN KEY (medication_bundle_id) REFERENCES medication_bundles (id) ON DELETE CASCADE
);

/* 7. 복약 기록 */
CREATE TABLE medication_logs
(
    id                   BIGINT AUTO_INCREMENT,
    created_at           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    date                 DATE        NOT NULL,
    is_taken             BIT         NOT NULL DEFAULT 0,
    med_condition        INT         NULL,
    medication_bundle_id BIGINT      NULL,

    CONSTRAINT pk_medication_logs PRIMARY KEY (id),
    CONSTRAINT fk_medication_logs_bundle FOREIGN KEY (medication_bundle_id) REFERENCES medication_bundles (id)
);

/* 8. 구독 */
CREATE TABLE subscriptions
(
    id                      BIGINT AUTO_INCREMENT,
    created_at              DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    expires_at              DATETIME(6)  NOT NULL,
    is_auto_renew           BIT          NOT NULL,
    is_trial                BIT          NOT NULL,
    original_transaction_id VARCHAR(255) NOT NULL,
    product_id              VARCHAR(255) NOT NULL,
    start_at                DATETIME(6)  NOT NULL,
    status                  VARCHAR(50)  NOT NULL,
    transaction_id          VARCHAR(255) NOT NULL,
    user_id                 BIGINT       NOT NULL,

    CONSTRAINT pk_subscriptions PRIMARY KEY (id),
    CONSTRAINT uk_subscriptions_product_id UNIQUE (product_id),
    CONSTRAINT fk_subscriptions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);