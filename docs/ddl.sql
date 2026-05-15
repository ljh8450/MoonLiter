-- MoonLiter의 최신 목표 MySQL 스키마를 기록한다.
-- 백엔드 API 동작에 스키마 지원이 필요하면 이 파일을 항상 최신으로 유지한다.
-- 이 파일을 변경할 때는 이전 버전 기준으로 생성된 기존 로컬 데이터베이스에
-- 적용해야 하는 마이그레이션 SQL도 함께 제시한다.

CREATE TABLE app_users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(100) NOT NULL,
    auth_provider VARCHAR(30) NOT NULL,
    provider_user_id VARCHAR(120) NOT NULL,
    role VARCHAR(30) NOT NULL DEFAULT 'USER',
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_users_provider_user (auth_provider, provider_user_id),
    KEY idx_app_users_email (email),
    KEY idx_app_users_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE media_files (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_user_id BIGINT NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    original_filename VARCHAR(255) NULL,
    content_type VARCHAR(100) NOT NULL,
    byte_size BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    uploaded_at TIMESTAMP(6) NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_media_files_storage_key (storage_key),
    KEY idx_media_files_owner_status (owner_user_id, status),
    KEY idx_media_files_status_created (status, created_at),
    CONSTRAINT fk_media_files_owner
        FOREIGN KEY (owner_user_id) REFERENCES app_users (id),
    CONSTRAINT ck_media_files_byte_size_positive
        CHECK (byte_size > 0),
    CONSTRAINT ck_media_files_status
        CHECK (status IN ('PENDING', 'UPLOADED', 'FAILED')),
    CONSTRAINT ck_media_files_content_type
        CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE postcards (
    id BIGINT NOT NULL AUTO_INCREMENT,
    author_id BIGINT NOT NULL,
    media_file_id BIGINT NULL,
    content VARCHAR(500) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DELIVERABLE',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_postcards_media_file (media_file_id),
    KEY idx_postcards_author_created (author_id, created_at),
    KEY idx_postcards_status_created (status, created_at),
    CONSTRAINT fk_postcards_author
        FOREIGN KEY (author_id) REFERENCES app_users (id),
    CONSTRAINT fk_postcards_media_file
        FOREIGN KEY (media_file_id) REFERENCES media_files (id),
    CONSTRAINT ck_postcards_status
        CHECK (status IN ('DELIVERABLE', 'HIDDEN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE postcard_deliveries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    postcard_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    delivered_date DATE NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_postcard_deliveries_receiver_date (receiver_id, delivered_date),
    KEY idx_postcard_deliveries_postcard (postcard_id),
    KEY idx_postcard_deliveries_receiver_created (receiver_id, created_at),
    CONSTRAINT fk_postcard_deliveries_postcard
        FOREIGN KEY (postcard_id) REFERENCES postcards (id),
    CONSTRAINT fk_postcard_deliveries_receiver
        FOREIGN KEY (receiver_id) REFERENCES app_users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 마이그레이션 SQL
-- 기존 로컬 데이터베이스에 app_users만 있는 경우 아래 순서로 적용한다.

CREATE TABLE IF NOT EXISTS media_files (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_user_id BIGINT NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    original_filename VARCHAR(255) NULL,
    content_type VARCHAR(100) NOT NULL,
    byte_size BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    uploaded_at TIMESTAMP(6) NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_media_files_storage_key (storage_key),
    KEY idx_media_files_owner_status (owner_user_id, status),
    KEY idx_media_files_status_created (status, created_at),
    CONSTRAINT fk_media_files_owner
        FOREIGN KEY (owner_user_id) REFERENCES app_users (id),
    CONSTRAINT ck_media_files_byte_size_positive
        CHECK (byte_size > 0),
    CONSTRAINT ck_media_files_status
        CHECK (status IN ('PENDING', 'UPLOADED', 'FAILED')),
    CONSTRAINT ck_media_files_content_type
        CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS postcards (
    id BIGINT NOT NULL AUTO_INCREMENT,
    author_id BIGINT NOT NULL,
    media_file_id BIGINT NULL,
    content VARCHAR(500) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DELIVERABLE',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_postcards_media_file (media_file_id),
    KEY idx_postcards_author_created (author_id, created_at),
    KEY idx_postcards_status_created (status, created_at),
    CONSTRAINT fk_postcards_author
        FOREIGN KEY (author_id) REFERENCES app_users (id),
    CONSTRAINT fk_postcards_media_file
        FOREIGN KEY (media_file_id) REFERENCES media_files (id),
    CONSTRAINT ck_postcards_status
        CHECK (status IN ('DELIVERABLE', 'HIDDEN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS postcard_deliveries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    postcard_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    delivered_date DATE NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_postcard_deliveries_receiver_date (receiver_id, delivered_date),
    KEY idx_postcard_deliveries_postcard (postcard_id),
    KEY idx_postcard_deliveries_receiver_created (receiver_id, created_at),
    CONSTRAINT fk_postcard_deliveries_postcard
        FOREIGN KEY (postcard_id) REFERENCES postcards (id),
    CONSTRAINT fk_postcard_deliveries_receiver
        FOREIGN KEY (receiver_id) REFERENCES app_users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 이미 postcards 테이블이 있고 media_file_id 컬럼만 없는 로컬 데이터베이스는
-- 아래 SQL을 별도로 적용한다. 컬럼과 제약이 이미 있으면 중복 적용하지 않는다.
--
-- ALTER TABLE postcards
--     ADD COLUMN media_file_id BIGINT NULL AFTER author_id,
--     ADD UNIQUE KEY uk_postcards_media_file (media_file_id),
--     ADD CONSTRAINT fk_postcards_media_file
--         FOREIGN KEY (media_file_id) REFERENCES media_files (id);
