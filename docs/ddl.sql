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
