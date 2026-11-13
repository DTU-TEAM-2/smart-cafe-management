-- DDL-only read-only snapshot of configured smart_cafe, MySQL8.0.45,2026-11-13.
-- Test reproduction only; NOT an approved target schema. No business rows/definers/auto-increment counters.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE `auth_otp_limits` (
  `email_key` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `purpose` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `next_send_at` timestamp(6) NOT NULL,
  `window_start` timestamp(6) NOT NULL,
  `request_count` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`email_key`,`purpose`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `cafe_tables` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `table_number` int NOT NULL,
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'EMPTY',
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `table_number` (`table_number`),
  CONSTRAINT `ck_cafe_tables_status` CHECK ((`status` in (_utf8mb4'EMPTY',_utf8mb4'ACTIVE')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `script` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `menu_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `price` decimal(12,0) NOT NULL,
  `image_url` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `active` tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`),
  KEY `ix_menu_items_active_created` (`active`,`created_at`,`id`),
  CONSTRAINT `ck_menu_items_price` CHECK ((`price` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `table_sessions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `table_id` bigint NOT NULL,
  `session_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OPEN',
  `started_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `ended_at` timestamp(6) NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `session_code` (`session_code`),
  KEY `fk_table_sessions_table` (`table_id`),
  CONSTRAINT `fk_table_sessions_table` FOREIGN KEY (`table_id`) REFERENCES `cafe_tables` (`id`),
  CONSTRAINT `ck_table_sessions_status` CHECK ((`status` in (_utf8mb4'OPEN',_utf8mb4'CLOSED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `full_name` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `role` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `avatar_media_id` bigint DEFAULT NULL,
  `email_verified_at` datetime(6) DEFAULT NULL,
  `password_changed_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `must_change_password` tinyint(1) NOT NULL DEFAULT '0',
  `auth_version` bigint NOT NULL DEFAULT '0',
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  UNIQUE KEY `email` (`email`),
  KEY `ix_users_username` (`username`),
  KEY `ix_users_email` (`email`),
  CONSTRAINT `ck_users_role` CHECK ((`role` in (_utf8mb4'ADMIN',_utf8mb4'STAFF',_utf8mb4'CUSTOMER'))),
  CONSTRAINT `ck_users_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'INACTIVE',_utf8mb4'PENDING_VERIFICATION')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `auth_otp_challenges` (
  `id` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `purpose` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `code_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `attempts` int NOT NULL DEFAULT '0',
  `sends` int NOT NULL DEFAULT '1',
  `expires_at` timestamp(6) NOT NULL,
  `next_send_at` timestamp(6) NOT NULL,
  `used_at` timestamp(6) NULL DEFAULT NULL,
  `delivery_status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `delivery_attempts` int NOT NULL DEFAULT '0',
  `next_delivery_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `otp_ciphertext` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_error_code` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_smtp_status` smallint DEFAULT NULL,
  `sent_at` timestamp(6) NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_auth_otp_rate` (`email`,`purpose`,`created_at`),
  KEY `ix_auth_otp_user` (`user_id`,`purpose`),
  KEY `ix_auth_otp_delivery` (`delivery_status`,`next_delivery_at`),
  CONSTRAINT `fk_auth_otp_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_auth_otp_purpose` CHECK ((`purpose` in (_utf8mb4'RESET',_utf8mb4'REGISTER')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `bills` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `bill_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `table_id` bigint NOT NULL,
  `session_id` bigint DEFAULT NULL,
  `creator_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `total_amount` decimal(12,0) NOT NULL DEFAULT '0',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `paid_at` timestamp(6) NULL DEFAULT NULL,
  `idempotency_key` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `table_number_snapshot` int NOT NULL,
  `table_name_snapshot` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `bill_code` (`bill_code`),
  UNIQUE KEY `uk_bills_idempotency` (`idempotency_key`),
  UNIQUE KEY `uk_bills_session` (`session_id`),
  KEY `fk_bills_table` (`table_id`),
  KEY `ix_bills_status_paid_at` (`status`,`paid_at`,`total_amount`),
  CONSTRAINT `fk_bills_session` FOREIGN KEY (`session_id`) REFERENCES `table_sessions` (`id`),
  CONSTRAINT `fk_bills_table` FOREIGN KEY (`table_id`) REFERENCES `cafe_tables` (`id`),
  CONSTRAINT `ck_bills_status` CHECK ((`status` in (_utf8mb4'PAID',_utf8mb4'PENDING',_utf8mb4'CANCELLED'))),
  CONSTRAINT `ck_bills_total` CHECK ((`total_amount` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `menu_item_order_totals` (
  `menu_item_id` bigint NOT NULL,
  `ordered_quantity` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`menu_item_id`),
  CONSTRAINT `fk_menu_item_order_totals_item` FOREIGN KEY (`menu_item_id`) REFERENCES `menu_items` (`id`),
  CONSTRAINT `ck_menu_item_order_totals_quantity` CHECK ((`ordered_quantity` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `password_reset_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `token_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `expires_at` timestamp(6) NOT NULL,
  `used_at` timestamp(6) NULL DEFAULT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `token_hash` (`token_hash`),
  KEY `ix_password_reset_tokens_lookup` (`token_hash`,`expires_at`,`used_at`),
  KEY `ix_password_reset_tokens_user` (`user_id`),
  CONSTRAINT `fk_password_reset_tokens_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `service_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `table_id` bigint NOT NULL,
  `request_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `acknowledged_at` timestamp(6) NULL DEFAULT NULL,
  `session_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_service_requests_table_status` (`table_id`,`status`),
  KEY `fk_service_requests_session` (`session_id`),
  CONSTRAINT `fk_service_requests_session` FOREIGN KEY (`session_id`) REFERENCES `table_sessions` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_service_requests_table` FOREIGN KEY (`table_id`) REFERENCES `cafe_tables` (`id`),
  CONSTRAINT `ck_service_requests_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'ACKNOWLEDGED'))),
  CONSTRAINT `ck_service_requests_type` CHECK ((`request_type` in (_utf8mb4'WAITER',_utf8mb4'ORDER',_utf8mb4'BILL')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `session_order_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` bigint NOT NULL,
  `menu_item_id` bigint DEFAULT NULL,
  `item_name` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `unit_price` decimal(12,0) NOT NULL,
  `quantity` int NOT NULL,
  `subtotal` decimal(12,0) NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ORDERED',
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `fk_session_order_items_menu` (`menu_item_id`),
  KEY `ix_session_order_items_session` (`session_id`),
  CONSTRAINT `fk_session_order_items_menu` FOREIGN KEY (`menu_item_id`) REFERENCES `menu_items` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_session_order_items_session` FOREIGN KEY (`session_id`) REFERENCES `table_sessions` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_session_order_items_price` CHECK ((`unit_price` >= 0)),
  CONSTRAINT `ck_session_order_items_qty` CHECK ((`quantity` > 0)),
  CONSTRAINT `ck_session_order_items_subtotal` CHECK ((`subtotal` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `bill_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `bill_id` bigint NOT NULL,
  `menu_item_id` bigint DEFAULT NULL,
  `item_name` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `unit_price` decimal(12,0) NOT NULL,
  `quantity` int NOT NULL,
  `subtotal` decimal(12,0) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_bill_items_bill` (`bill_id`),
  KEY `fk_bill_items_menu` (`menu_item_id`),
  CONSTRAINT `fk_bill_items_bill` FOREIGN KEY (`bill_id`) REFERENCES `bills` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_bill_items_menu` FOREIGN KEY (`menu_item_id`) REFERENCES `menu_items` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_bill_items_price` CHECK ((`unit_price` >= 0)),
  CONSTRAINT `ck_bill_items_qty` CHECK ((`quantity` > 0)),
  CONSTRAINT `ck_bill_items_subtotal` CHECK ((`subtotal` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
