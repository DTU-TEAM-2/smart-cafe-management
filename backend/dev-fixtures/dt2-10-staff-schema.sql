-- DT2-68: Schema đồng bộ mô hình chuẩn users + staff_profiles + media_assets
-- Cho database smart_cafe

-- 1. Bảng media_assets (lưu trữ metadata ảnh Cloudinary)
CREATE TABLE IF NOT EXISTS `media_assets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `provider` varchar(16) NOT NULL DEFAULT 'CLOUDINARY',
  `provider_asset_id` varchar(255) NOT NULL,
  `secure_url` varchar(2024) NOT NULL,
  `mime_type` varchar(80) NOT NULL,
  `size_bytes` bigint NOT NULL,
  `width_px` int NOT NULL,
  `height_px` int NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `deleted_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Đảm bảo bảng users có cột avatar_media_id nếu chưa có
SET @exist_avatar := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'avatar_media_id'
);
SET @sql_avatar := IF(@exist_avatar = 0, 'ALTER TABLE users ADD COLUMN avatar_media_id bigint DEFAULT NULL, ADD CONSTRAINT fk_users_avatar FOREIGN KEY (avatar_media_id) REFERENCES media_assets(id)', 'SELECT 1');
PREPARE stmt_avatar FROM @sql_avatar;
EXECUTE stmt_avatar;
DEALLOCATE PREPARE stmt_avatar;

-- 3. Bảng staff_profiles (quan hệ 1-1 với users qua user_id)
CREATE TABLE IF NOT EXISTS `staff_profiles` (
  `user_id` bigint NOT NULL,
  `address` varchar(255) NOT NULL,
  `birthday` date DEFAULT NULL,
  `gender` varchar(8) DEFAULT NULL,
  `salary` decimal(18,0) NOT NULL,
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_staff_profiles_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `ck_staff_gender` CHECK ((`gender` IS NULL) OR (`gender` IN ('MALE', 'FEMALE'))),
  CONSTRAINT `ck_staff_salary` CHECK ((`salary` > 0) AND ((`salary` % 100000) = 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Đồng bộ dữ liệu hồ sơ từ bảng users cũ sang staff_profiles (nếu users có các cột address, salary)
INSERT IGNORE INTO `staff_profiles` (`user_id`, `address`, `birthday`, `gender`, `salary`)
SELECT `id`, COALESCE(`address`, 'Đà Nẵng'), `birthday`, `gender`, COALESCE(`salary`, 6000000)
FROM `users`;
