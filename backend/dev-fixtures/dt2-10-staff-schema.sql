-- DT2-68: Schema đồng bộ mô hình chuẩn users + staff_profiles + media_assets
-- LƯU Ý: File này CHỈ là DDL fixture cho môi trường phát triển cục bộ và kiểm thử cô lập (dev/test).
-- TUYỆT ĐỐI KHÔNG CHẠY script này trên database hệ thống/production `smart_cafe`.
-- Việc áp dụng cơ sở dữ liệu dùng chung tuân thủ DDL 20 bảng và Flyway lineage được phê duyệt của dự án.

-- 1. Bảng media_assets (lưu trữ metadata ảnh Cloudinary/Firebase)
CREATE TABLE IF NOT EXISTS `media_assets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `provider` varchar(16) NOT NULL,
  `provider_asset_id` varchar(255) NOT NULL,
  `secure_url` varchar(1024) NOT NULL,
  `mime_type` varchar(80) NOT NULL,
  `size_bytes` bigint NOT NULL,
  `width_px` int DEFAULT NULL,
  `height_px` int DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `deleted_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_media_provider` (`provider`,`provider_asset_id`),
  CONSTRAINT `ck_media_dimensions` CHECK ((((`width_px` is null) or (`width_px` > 0)) and ((`height_px` is null) or (`height_px` > 0)))),
  CONSTRAINT `ck_media_provider` CHECK ((`provider` in (_utf8mb4'CLOUDINARY',_utf8mb4'FIREBASE'))),
  CONSTRAINT `ck_media_size` CHECK ((`size_bytes` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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
  CONSTRAINT `ck_staff_gender` CHECK (((`gender` is null) or (`gender` in (_utf8mb4'MALE',_utf8mb4'FEMALE')))),
  CONSTRAINT `ck_staff_salary` CHECK (((`salary` > 0) and ((`salary` % 100000) = 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
