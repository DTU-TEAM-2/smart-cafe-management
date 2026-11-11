-- Isolated test schema snapshot for smart_cafe auth testing
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(160) NOT NULL,
    email VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci NOT NULL,
    phone VARCHAR(20) DEFAULT NULL,
    role VARCHAR(16) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    avatar_media_id BIGINT DEFAULT NULL,
    email_verified_at DATETIME(6) DEFAULT NULL,
    password_changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    must_change_password TINYINT(1) NOT NULL DEFAULT '0',
    auth_version BIGINT NOT NULL DEFAULT '0',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT '0',
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_username (username),
    UNIQUE KEY uq_users_email (email),
    KEY ix_users_role_status (role, status, id),
    KEY ix_users_name (full_name),
    KEY ix_users_phone (phone),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'STAFF', 'CUSTOMER')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'PENDING_VERIFICATION')),
    CONSTRAINT ck_users_versions CHECK (auth_version >= 0 AND version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS auth_otp_challenges (
    id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT DEFAULT NULL,
    email VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci NOT NULL,
    purpose VARCHAR(16) NOT NULL,
    code_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    sends INT NOT NULL DEFAULT 1,
    expires_at DATETIME(6) NOT NULL,
    next_send_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) DEFAULT NULL,
    delivery_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_error_code VARCHAR(40) DEFAULT NULL,
    sent_at DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (id),
    KEY ix_auth_otp_rate (email, purpose, created_at),
    KEY ix_auth_otp_user (user_id, purpose),
    KEY ix_auth_otp_delivery (delivery_status, created_at),
    CONSTRAINT fk_auth_otp_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_auth_otp_counts CHECK (attempts >= 0 AND sends >= 1),
    CONSTRAINT ck_auth_otp_delivery CHECK (delivery_status IN ('PENDING', 'SENT', 'FAILED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_auth_otp_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_auth_otp_purpose CHECK (purpose IN ('REGISTER', 'RESET'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS auth_otp_limits (
    email_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    purpose VARCHAR(16) NOT NULL,
    next_send_at DATETIME(6) NOT NULL,
    window_start DATETIME(6) NOT NULL,
    request_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (email_key, purpose),
    CONSTRAINT ck_auth_otp_limits_count CHECK (request_count >= 0),
    CONSTRAINT ck_auth_otp_limits_purpose CHECK (purpose IN ('REGISTER', 'RESET'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_reset_token_hash (token_hash),
    KEY ix_reset_token_lookup (token_hash, expires_at, used_at),
    KEY ix_reset_token_user (user_id),
    CONSTRAINT fk_reset_token_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
