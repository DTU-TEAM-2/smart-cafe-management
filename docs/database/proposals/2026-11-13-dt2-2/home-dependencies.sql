-- PROPOSAL ONLY. NOT approved deployment DDL; never in Flyway runtime locations.
-- Reproduces Home account + notifications dependencies from historical SHOW CREATE fixture
-- backend/src/test/resources/schema.sql, captured2026-11-06, reviewed against ERD2026-11-11.
-- Column types/PK/FK/checks have that historical provenance, not DBA approval for current DB.
-- Owner must approve nullability/defaults/collation/checks and existing users BIGINT signed FK.
-- No user/profile/media records, backfill or invented salary/address defaults.
-- CREATE deliberately fails on an existing table; preflight must check shape rather than silently skip.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE `media_assets` (
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

CREATE TABLE `staff_profiles` (
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

CREATE TABLE `session_clients` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` bigint NOT NULL,
  `customer_user_id` bigint DEFAULT NULL COMMENT 'NULL: khách vãng lai',
  `access_token_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `joined_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `expires_at` datetime(6) NOT NULL,
  `revoked_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_session_clients_token` (`access_token_hash`),
  UNIQUE KEY `uq_session_clients_scope` (`session_id`,`id`),
  KEY `ix_session_clients_customer` (`customer_user_id`),
  CONSTRAINT `fk_session_clients_customer` FOREIGN KEY (`customer_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_session_clients_session` FOREIGN KEY (`session_id`) REFERENCES `table_sessions` (`id`),
  CONSTRAINT `ck_session_clients_expiry` CHECK ((`expires_at` > `joined_at`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `order_batches` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `session_id` bigint NOT NULL,
  `submitted_by_client_id` bigint DEFAULT NULL,
  `submitted_by_user_id` bigint DEFAULT NULL,
  `submitted_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `editable_until` datetime(6) NOT NULL,
  `edit_policy` varchar(24) NOT NULL,
  `idempotency_key` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_order_batches_code` (`order_code`),
  UNIQUE KEY `uq_order_batches_scope` (`session_id`,`id`),
  UNIQUE KEY `uq_order_batches_request` (`session_id`,`idempotency_key`),
  KEY `fk_order_batches_client` (`session_id`,`submitted_by_client_id`),
  KEY `fk_order_batches_user` (`submitted_by_user_id`),
  KEY `ix_order_batches_session_time` (`session_id`,`submitted_at`,`id`),
  CONSTRAINT `fk_order_batches_client` FOREIGN KEY (`session_id`, `submitted_by_client_id`) REFERENCES `session_clients` (`session_id`, `id`),
  CONSTRAINT `fk_order_batches_session` FOREIGN KEY (`session_id`) REFERENCES `table_sessions` (`id`),
  CONSTRAINT `fk_order_batches_user` FOREIGN KEY (`submitted_by_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `ck_order_batches_actor` CHECK ((((`submitted_by_client_id` is not null) and (`submitted_by_user_id` is null)) or ((`submitted_by_client_id` is null) and (`submitted_by_user_id` is not null)))),
  CONSTRAINT `ck_order_batches_expiry` CHECK ((`editable_until` >= `submitted_at`)),
  CONSTRAINT `ck_order_batches_policy` CHECK ((`edit_policy` in (_utf8mb4'DEADLINE_ONLY',_utf8mb4'BEFORE_PREPARATION')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `order_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` bigint NOT NULL,
  `order_batch_id` bigint NOT NULL,
  `menu_item_id` bigint NOT NULL,
  `item_code` varchar(40) NOT NULL,
  `item_name` varchar(160) NOT NULL,
  `unit_price` decimal(18,0) NOT NULL,
  `quantity` int NOT NULL,
  `line_total` decimal(18,0) GENERATED ALWAYS AS ((`unit_price` * `quantity`)) STORED,
  `status` varchar(20) NOT NULL DEFAULT 'SUBMITTED',
  `preparation_started_at` datetime(6) DEFAULT NULL,
  `served_at` datetime(6) DEFAULT NULL,
  `cancelled_at` datetime(6) DEFAULT NULL,
  `cancellation_reason` varchar(500) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_order_items_scope` (`session_id`,`id`),
  UNIQUE KEY `uq_order_items_batch_item` (`order_batch_id`,`menu_item_id`),
  KEY `fk_order_items_batch` (`session_id`,`order_batch_id`),
  KEY `ix_order_items_session_status` (`session_id`,`status`,`id`),
  KEY `ix_order_items_menu` (`menu_item_id`,`status`),
  CONSTRAINT `fk_order_items_batch` FOREIGN KEY (`session_id`, `order_batch_id`) REFERENCES `order_batches` (`session_id`, `id`),
  CONSTRAINT `fk_order_items_menu` FOREIGN KEY (`menu_item_id`) REFERENCES `menu_items` (`id`),
  CONSTRAINT `ck_order_items_cancel` CHECK ((((`status` = _utf8mb4'CANCELLED') and (`cancelled_at` is not null) and (`cancellation_reason` is not null)) or ((`status` <> _utf8mb4'CANCELLED') and (`cancelled_at` is null) and (`cancellation_reason` is null)))),
  CONSTRAINT `ck_order_items_preparing` CHECK (((`status` <> _utf8mb4'PREPARING') or (`preparation_started_at` is not null))),
  CONSTRAINT `ck_order_items_price` CHECK ((`unit_price` > 0)),
  CONSTRAINT `ck_order_items_quantity` CHECK ((`quantity` > 0)),
  CONSTRAINT `ck_order_items_served` CHECK (((`status` <> _utf8mb4'SERVED') or (`served_at` is not null))),
  CONSTRAINT `ck_order_items_status` CHECK ((`status` in (_utf8mb4'SUBMITTED',_utf8mb4'PREPARING',_utf8mb4'SERVED',_utf8mb4'CANCELLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
