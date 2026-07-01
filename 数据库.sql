-- ===========================
-- Snake 数据库建表脚本（已同步实体类）
-- ===========================

CREATE DATABASE IF NOT EXISTS snake CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE snake;

-- 1. 用户表
CREATE TABLE IF NOT EXISTS users (
  id VARCHAR(36) NOT NULL PRIMARY KEY,                  -- UUID string (MyBatis-Plus 赋值)
  user_code INT DEFAULT NULL UNIQUE,                    -- 6 位数字用户编号
  username VARCHAR(255) NOT NULL UNIQUE,
  email VARCHAR(255) UNIQUE,
  password_hash VARCHAR(512) NOT NULL,
  display_name VARCHAR(255) DEFAULT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  failed_login_count INT NOT NULL DEFAULT 0,
  lockout_until DATETIME(6) DEFAULT NULL,
  max_length INT NOT NULL DEFAULT 0,                    -- 单人模式最大蛇长
  password_changed_at DATETIME(6) DEFAULT NULL,
  last_login_at DATETIME(6) DEFAULT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Refresh Token 表
CREATE TABLE IF NOT EXISTS refresh_tokens (
  id VARCHAR(36) NOT NULL PRIMARY KEY,                  -- UUID string
  user_id VARCHAR(36) NOT NULL,                         -- FK → users.id
  token_hash VARCHAR(64) NOT NULL,                      -- SHA-256 hex string (64 chars)
  issued_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  expires_at DATETIME(6) NOT NULL,
  revoked TINYINT(1) NOT NULL DEFAULT 0,
  revoked_at DATETIME(6) DEFAULT NULL,
  replaced_by VARCHAR(36) DEFAULT NULL,                 -- FK → refresh_tokens.id
  device_info VARCHAR(512) DEFAULT NULL,
  ip VARCHAR(45) DEFAULT NULL,
  last_used_at DATETIME(6) DEFAULT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. 好友申请表
CREATE TABLE IF NOT EXISTS friend_requests (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  from_user_code INT NOT NULL,
  to_user_code INT NOT NULL,
  status ENUM('pending', 'accepted', 'rejected', 'canceled') NOT NULL DEFAULT 'pending',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX idx_fr_from (from_user_code),
  INDEX idx_fr_to (to_user_code),
  INDEX idx_fr_status (status),
  UNIQUE KEY uk_fr_pair (from_user_code, to_user_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. 好友关系表
CREATE TABLE IF NOT EXISTS friendships (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_code_1 INT NOT NULL,
  user_code_2 INT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_fs_pair (user_code_1, user_code_2),
  INDEX idx_fs_1 (user_code_1),
  INDEX idx_fs_2 (user_code_2)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. 私聊消息表
CREATE TABLE IF NOT EXISTS private_messages (
  id VARCHAR(64) NOT NULL PRIMARY KEY,
  from_user_code INT NOT NULL,
  to_user_code INT NOT NULL,
  content TEXT NOT NULL,
  created_at VARCHAR(32) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
