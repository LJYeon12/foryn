ALTER TABLE users
    ADD time_zone VARCHAR(64) NOT NULL DEFAULT 'Asia/Seoul';

CREATE TABLE diaries
(
    id                BIGINT AUTO_INCREMENT,
    user_id           BIGINT       NOT NULL,
    diary_date        DATE         NOT NULL,
    draft_diary_date  DATE GENERATED ALWAYS AS (
        CASE WHEN status = 'DRAFT' THEN diary_date ELSE NULL END
    ) STORED,
    status            VARCHAR(20)  NOT NULL,
    moment_answer     VARCHAR(500) NOT NULL,
    thought_answer    VARCHAR(500) NOT NULL,
    emotion_answer    VARCHAR(500) NOT NULL,
    foryn_comment     VARCHAR(1000) NULL,
    emotion_score     INT          NULL,
    tag_codes         JSON         NULL,
    completed_at      DATETIME(6)  NULL,
    created_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_diaries PRIMARY KEY (id),
    CONSTRAINT fk_diaries_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_diaries_user_draft_date UNIQUE (user_id, draft_diary_date),
    CONSTRAINT chk_diaries_status CHECK (status IN ('DRAFT', 'COMPLETED')),
    CONSTRAINT chk_diaries_emotion_score CHECK (
        emotion_score IS NULL OR emotion_score IN (-2, -1, 0, 1, 2)
    )
);

CREATE INDEX idx_diaries_user_date
    ON diaries (user_id, diary_date);

CREATE INDEX idx_diaries_user_date_status
    ON diaries (user_id, diary_date, status);

CREATE INDEX idx_diaries_user_created_at
    ON diaries (user_id, created_at);

CREATE TABLE diary_reminders
(
    id            BIGINT AUTO_INCREMENT,
    user_id       BIGINT      NOT NULL,
    reminder_time TIME        NOT NULL,
    enabled       BIT         NOT NULL DEFAULT 1,
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_diary_reminders PRIMARY KEY (id),
    CONSTRAINT fk_diary_reminders_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_diary_reminders_user_time UNIQUE (user_id, reminder_time)
);

INSERT INTO diary_reminders (user_id, reminder_time, enabled)
SELECT id, '21:00:00', 1
FROM users;

CREATE TABLE diary_report_summary_cache
(
    id                    BIGINT AUTO_INCREMENT,
    user_id               BIGINT       NOT NULL,
    summary_date          DATE         NOT NULL,
    has_recent_diary_data BIT          NOT NULL,
    content               VARCHAR(500) NOT NULL,
    created_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_diary_report_summary_cache PRIMARY KEY (id),
    CONSTRAINT fk_diary_report_summary_cache_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_diary_report_summary_cache_user_date UNIQUE (user_id, summary_date)
);

CREATE TABLE diary_reminder_reschedule_jobs
(
    id               BIGINT AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    scheduled_date   DATE         NOT NULL,
    old_reminder_ids JSON         NOT NULL,
    retry_count      INT          NOT NULL DEFAULT 0,
    next_retry_at    DATETIME(6)  NULL,
    status           VARCHAR(30)  NOT NULL,
    last_error       VARCHAR(255) NULL,
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_diary_reminder_reschedule_jobs PRIMARY KEY (id),
    CONSTRAINT fk_diary_reminder_reschedule_jobs_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_diary_reminder_reschedule_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'RETRY_WAITING', 'SUCCEEDED', 'FAILED')
    )
);
