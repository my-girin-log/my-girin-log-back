-- 우테고치 백엔드 — MySQL 8.x 스키마 (스펙 §2.3)
-- prod 프로필에서 ddl-auto=validate 와 함께 사용. 실제 적용은 Flyway/Liquibase 권장.
-- JSON 컬럼들은 MySQL 8 native JSON 사용. dev(H2) 에서는 CLOB 로 대체 (엔티티에 @Lob 적용).

CREATE TABLE IF NOT EXISTS users (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    github_id   VARCHAR(100) NOT NULL,
    nickname    VARCHAR(100) NOT NULL,
    avatar_url  VARCHAR(512)     NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_github_id (github_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS persona (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    markdown    MEDIUMTEXT  NOT NULL,
    summary     VARCHAR(500)    NULL,
    sources     JSON            NULL,
    analysis    JSON            NULL,            -- 카키 레퍼런스 PersonaAnalysis (tone/ending_dominant/signature_phrases/...)
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_persona_user (user_id),
    CONSTRAINT fk_persona_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS daily_chat_session (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    date_key    DATE        NOT NULL,
    status      VARCHAR(16) NOT NULL,
    started_at  DATETIME(6) NOT NULL,
    closed_at   DATETIME(6)     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_user_date (user_id, date_key),
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_message (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    session_id  BIGINT      NOT NULL,
    role        VARCHAR(16) NOT NULL,
    content     TEXT        NOT NULL,
    source      VARCHAR(16) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_msg_session_created (session_id, created_at),
    CONSTRAINT fk_msg_session FOREIGN KEY (session_id) REFERENCES daily_chat_session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS diary (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    date_key       DATE         NOT NULL,
    title          VARCHAR(255) NOT NULL,
    raw_text       MEDIUMTEXT       NULL,
    markdown       TEXT         NOT NULL,
    emotion_emoji  VARCHAR(16)      NULL,
    tags           JSON             NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_diary_user_date (user_id, date_key),
    CONSTRAINT fk_diary_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS retrospective (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    user_id           BIGINT       NOT NULL,
    title             VARCHAR(255) NOT NULL,
    markdown          MEDIUMTEXT   NOT NULL,
    summary           VARCHAR(500)     NULL,
    tags              JSON             NULL,
    type              VARCHAR(32)  NOT NULL,
    prompt_options    JSON             NULL,
    range_start_date  DATE         NOT NULL,
    range_end_date    DATE         NOT NULL,
    source_diary_ids  JSON             NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_retro_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS pet_state (
    user_id          BIGINT      NOT NULL,
    level            INT         NOT NULL DEFAULT 0,
    exp              INT         NOT NULL DEFAULT 0,
    condition_cache  VARCHAR(16) NOT NULL DEFAULT 'good',
    last_activity_at DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_pet_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
