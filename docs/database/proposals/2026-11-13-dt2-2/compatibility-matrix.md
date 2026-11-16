# DT2-2 compatibility matrix —13/11/2026

Model: ERD11/11,20 tables. Current: read-only DDL snapshot13/11,13 base tables including history,0 views. Historical20-table fixture is comparison/rehearsal material, **not approved target DDL**. Model types below agree with the displayed ERD for these five tables; nullable/default/check/collation details come from the DDL sources, not inferred from the image.

## Table presence

| Table from ERD | Current shared | Consumer / classification |
| --- | --- | --- |
| media_assets | Missing | Shared media/Staff/Menu; Home account reader; MIGRATION REQUIRED / OWNER DECISION REQUIRED |
| users | Present | Auth/common identity; column/constraint reconciliation still required |
| staff_profiles | Missing | DT2-10 Staff; Home account reader; MIGRATION REQUIRED / OWNER DECISION REQUIRED |
| auth_otp_challenges | Present | Auth/common identity; column/constraint reconciliation still required |
| auth_otp_limits | Present | Auth/common identity; column/constraint reconciliation still required |
| menu_categories | Missing | Other integrated modules; MIGRATION REQUIRED / OWNER DECISION REQUIRED |
| menu_items | Present | Other integrated modules; column/constraint reconciliation still required |
| cafe_tables | Present | Other integrated modules; column/constraint reconciliation still required |
| table_sessions | Present | Other integrated modules; column/constraint reconciliation still required |
| session_clients | Missing | Other integrated modules; MIGRATION REQUIRED / OWNER DECISION REQUIRED |
| order_batches | Missing | Other integrated modules; MIGRATION REQUIRED / OWNER DECISION REQUIRED |
| order_items | Missing | Other integrated modules; MIGRATION REQUIRED / OWNER DECISION REQUIRED |
| service_requests | Present | Other integrated modules; column/constraint reconciliation still required |
| bills | Present | Other integrated modules; column/constraint reconciliation still required |
| bill_items | Present | Other integrated modules; column/constraint reconciliation still required |
| payments | Missing | Other integrated modules; MIGRATION REQUIRED / OWNER DECISION REQUIRED |
| feedback | Missing | Other integrated modules; MIGRATION REQUIRED / OWNER DECISION REQUIRED |
| feedback_images | Missing | Other integrated modules; MIGRATION REQUIRED / OWNER DECISION REQUIRED |
| news_posts | Missing | Other integrated modules; MIGRATION REQUIRED / OWNER DECISION REQUIRED |
| news_images | Missing | Other integrated modules; MIGRATION REQUIRED / OWNER DECISION REQUIRED |

Current-only tables: flyway_schema_history (metadata), menu_item_order_totals, password_reset_tokens, session_order_items. Preserve them pending owner review; their presence is not permission to use old contracts or delete data.

## Auth and Home-account columns

| Table.Column | ERD type | Current DDL type/default/nullability | Code use | Classification |
| --- | --- | --- | --- | --- |
| users.id | bigint | bigint; NOT NULL AUTO_INCREMENT | Read/write by Auth or Home | COMPATIBLE for current SQL |
| users.username | varchar(64) | varchar(64); COLLATE utf8mb4_unicode_ci NOT NULL | Read/write by Auth or Home | COMPATIBLE for current SQL |
| users.password_hash | varchar(255) | varchar(255); COLLATE utf8mb4_unicode_ci NOT NULL | Read/write by Auth or Home | COMPATIBLE for current SQL |
| users.full_name | varchar(160) | varchar(160); COLLATE utf8mb4_unicode_ci NOT NULL | Read/write by Auth or Home | COMPATIBLE for current SQL |
| users.email | varchar(255) | varchar(255); COLLATE utf8mb4_unicode_ci NOT NULL | Read/write by Auth or Home | COMPATIBLE for current SQL |
| users.phone | varchar(20) | varchar(20); COLLATE utf8mb4_unicode_ci DEFAULT NULL | Read/write by Auth or Home | COMPATIBLE for current SQL |
| users.role | varchar(16) | varchar(32); COLLATE utf8mb4_unicode_ci NOT NULL | Read/write by Auth or Home | OWNER DECISION REQUIRED for canonical type; common SQL tested separately |
| users.status | varchar(24) | varchar(32); COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' | Read/write by Auth or Home | OWNER DECISION REQUIRED for canonical type; common SQL tested separately |
| users.avatar_media_id | bigint | bigint; DEFAULT NULL | Read/write by Auth or Home | COMPATIBLE for current SQL |
| users.email_verified_at | datetime(6) | datetime(6); DEFAULT NULL | Preserved; no new writer | COMPATIBLE for current SQL |
| users.password_changed_at | datetime(6) | datetime(6); NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | Read/write by Auth or Home | COMPATIBLE for current SQL |
| users.must_change_password | tinyint(1) | tinyint(1); NOT NULL DEFAULT '0' | Read/write by Auth or Home | COMPATIBLE for current SQL |
| users.auth_version | bigint | bigint; NOT NULL DEFAULT '0' | Read/write by Auth or Home | COMPATIBLE for current SQL |
| users.created_at | datetime(6) | timestamp(6); NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | Preserved; no new writer | OWNER DECISION REQUIRED for canonical type; common SQL tested separately |
| users.updated_at | datetime(6) | timestamp(6); NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | Read/write by Auth or Home | OWNER DECISION REQUIRED for canonical type; common SQL tested separately |
| users.version | bigint | bigint; NOT NULL DEFAULT '0' | Preserved; no new writer | COMPATIBLE for current SQL |
| staff_profiles.user_id | bigint | Missing table/column | Read/write by Auth or Home | MIGRATION REQUIRED / OWNER DECISION REQUIRED |
| staff_profiles.address | varchar(255) | Missing table/column | Read/write by Auth or Home | MIGRATION REQUIRED / OWNER DECISION REQUIRED |
| staff_profiles.birthday | date | Missing table/column | Read/write by Auth or Home | MIGRATION REQUIRED / OWNER DECISION REQUIRED |
| staff_profiles.gender | varchar(8) | Missing table/column | Read/write by Auth or Home | MIGRATION REQUIRED / OWNER DECISION REQUIRED |
| staff_profiles.salary | decimal(18,0) | Missing table/column | Read/write by Auth or Home | MIGRATION REQUIRED / OWNER DECISION REQUIRED |
| media_assets.id | bigint | Missing table/column | Read/write by Auth or Home | MIGRATION REQUIRED / OWNER DECISION REQUIRED |
| media_assets.provider | varchar(16) | Missing table/column | Preserved; no new writer | MIGRATION REQUIRED / OWNER DECISION REQUIRED |
| media_assets.provider_asset_id | varchar(255) | Missing table/column | Preserved; no new writer | MIGRATION REQUIRED / OWNER DECISION REQUIRED |
| media_assets.secure_url | varchar(1024) | Missing table/column | Read/write by Auth or Home | MIGRATION REQUIRED / OWNER DECISION REQUIRED |
| media_assets.mime_type | varchar(80) | Missing table/column | Preserved; no new writer | MIGRATION REQUIRED / OWNER DECISION REQUIRED |
| media_assets.size_bytes | bigint | Missing table/column | Preserved; no new writer | MIGRATION REQUIRED / OWNER DECISION REQUIRED |
| media_assets.width_px | int | Missing table/column | Preserved; no new writer | MIGRATION REQUIRED / OWNER DECISION REQUIRED |
| media_assets.height_px | int | Missing table/column | Preserved; no new writer | MIGRATION REQUIRED / OWNER DECISION REQUIRED |
| media_assets.created_at | datetime(6) | Missing table/column | Preserved; no new writer | MIGRATION REQUIRED / OWNER DECISION REQUIRED |
| media_assets.deleted_at | datetime(6) | Missing table/column | Read/write by Auth or Home | MIGRATION REQUIRED / OWNER DECISION REQUIRED |
| auth_otp_challenges.id | char(64) | char(64); COLLATE utf8mb4_unicode_ci NOT NULL | Read/write by Auth or Home | COMPATIBLE for current SQL |
| auth_otp_challenges.user_id | bigint | bigint; DEFAULT NULL | Read/write by Auth or Home | COMPATIBLE for current SQL |
| auth_otp_challenges.email | varchar(255) | varchar(255); COLLATE utf8mb4_unicode_ci NOT NULL | Read/write by Auth or Home | COMPATIBLE for current SQL |
| auth_otp_challenges.purpose | varchar(16) | varchar(16); COLLATE utf8mb4_unicode_ci NOT NULL | Read/write by Auth or Home | COMPATIBLE for current SQL |
| auth_otp_challenges.code_hash | char(64) | char(64); COLLATE utf8mb4_unicode_ci NOT NULL | Read/write by Auth or Home | COMPATIBLE for current SQL |
| auth_otp_challenges.attempts | int | int; NOT NULL DEFAULT '0' | Read/write by Auth or Home | COMPATIBLE for current SQL |
| auth_otp_challenges.sends | int | int; NOT NULL DEFAULT '1' | Read/write by Auth or Home | COMPATIBLE for current SQL |
| auth_otp_challenges.expires_at | datetime(6) | timestamp(6); NOT NULL | Read/write by Auth or Home | OWNER DECISION REQUIRED for canonical type; common SQL tested separately |
| auth_otp_challenges.next_send_at | datetime(6) | timestamp(6); NOT NULL | Read/write by Auth or Home | OWNER DECISION REQUIRED for canonical type; common SQL tested separately |
| auth_otp_challenges.used_at | datetime(6) | timestamp(6); NULL DEFAULT NULL | Read/write by Auth or Home | OWNER DECISION REQUIRED for canonical type; common SQL tested separately |
| auth_otp_challenges.delivery_status | varchar(16) | varchar(16); COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' | Read/write by Auth or Home | COMPATIBLE for current SQL |
| auth_otp_challenges.created_at | datetime(6) | timestamp(6); NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | Read/write by Auth or Home | OWNER DECISION REQUIRED for canonical type; common SQL tested separately |
| auth_otp_challenges.last_error_code | varchar(40) | varchar(40); COLLATE utf8mb4_unicode_ci DEFAULT NULL | Read/write by Auth or Home | COMPATIBLE for current SQL |
| auth_otp_challenges.sent_at | datetime(6) | timestamp(6); NULL DEFAULT NULL | Read/write by Auth or Home | OWNER DECISION REQUIRED for canonical type; common SQL tested separately |
| auth_otp_challenges.delivery_attempts | Not in ERD | int; NOT NULL DEFAULT '0' | Legacy delivery dependency; no deletion | OWNER DECISION REQUIRED |
| auth_otp_challenges.next_delivery_at | Not in ERD | timestamp(6); NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | Legacy delivery dependency; no deletion | OWNER DECISION REQUIRED |
| auth_otp_challenges.otp_ciphertext | Not in ERD | varchar(256); COLLATE utf8mb4_unicode_ci DEFAULT NULL | Legacy delivery dependency; no deletion | OWNER DECISION REQUIRED |
| auth_otp_challenges.last_smtp_status | Not in ERD | smallint; DEFAULT NULL | Legacy delivery dependency; no deletion | OWNER DECISION REQUIRED |
| auth_otp_limits.email_key | char(64) | char(64); COLLATE utf8mb4_unicode_ci NOT NULL | Read/write by Auth or Home | COMPATIBLE for current SQL |
| auth_otp_limits.purpose | varchar(16) | varchar(16); COLLATE utf8mb4_unicode_ci NOT NULL | Read/write by Auth or Home | COMPATIBLE for current SQL |
| auth_otp_limits.next_send_at | datetime(6) | timestamp(6); NOT NULL | Read/write by Auth or Home | OWNER DECISION REQUIRED for canonical type; common SQL tested separately |
| auth_otp_limits.window_start | datetime(6) | timestamp(6); NOT NULL | Read/write by Auth or Home | OWNER DECISION REQUIRED for canonical type; common SQL tested separately |
| auth_otp_limits.request_count | int | int; NOT NULL DEFAULT '0' | Read/write by Auth or Home | COMPATIBLE for current SQL |

## Constraints, indexes and temporal semantics

- Full PK/FK/unique/index/check definitions are retained in [current DDL-only snapshot](../../../../backend/src/test/resources/db/current-shared-2026-11-13.sql). Detailed read-only metadata inventory remains outside Git (`pr7-db-constraints-indexes.tsv`). No business records or AUTO_INCREMENT counters are copied.
- users: unique username/email and role/status checks exist; avatar FK cannot exist without media_assets. Widths32/32 differ from16/24; index names/collation/defaults are not inferred from ERD.
- OTP: primary id and user FK (ON DELETE CASCADE) plus purpose check exist. Current DB lacks historical counts/delivery/expiry/reset-user checks; delivery index uses next_delivery_at rather than created_at. Limits composite PK exists, count/purpose checks are absent. Do not add these checks before DBA audits existing data and approves policy.
- Current id/hash/email collation is utf8mb4_unicode_ci; historical fixture uses ascii_bin for opaque id/hash and utf8mb4_0900_as_ci for email. ERD does not approve a collation by itself. Canonical choice/data audit requires owner decision. Existing CAS/count/expiry behavior is verified on each engine shape separately.
- UTC/microsecond tests demonstrate DATETIME(6) and TIMESTAMP(6) preserve the same2030 instant with UTC sessions. Switching session time_zone shifts the TIMESTAMP SQL representation by7h, while DATETIME stays unchanged. TIMESTAMP rejects2040 while DATETIME accepts it. This is a real semantic difference, not permission to rewrite existing timestamps.
- No current Auth SQL mapping defect was reproduced solely from the different common column types. Auth/RBAC/OTP tests and their limits are recorded in the final handoff; missing Home dependencies and migration safety are separate problems.

## Other consumers / policy dependencies

- Header account SQL genuinely depends on staff_profiles and media_assets; notifications also need order_batches/order_items and their session_clients FK parent. The five-table candidate restores these SQL seams on a copy, but does not add the users-avatar FK or reconcile the remaining6 model tables/other legacy shapes. JOINs are retained.
- Menu needs menu_categories, media references/version columns and the current menu contract. Sales needs session_clients/order_batches/order_items/payments, current bill/session columns and views. These cannot be replaced by legacy menu_item_order_totals/session_order_items without an approved model/data conversion.
- Legacy OtpService at ref db0e80b reads/writes otp_ciphertext and contains deliverQueued; source evidence exists for those delivery fields. Whether any old process is deployed/running remains NOT VERIFIED. Owner must stop/coordinate old workers before shared rollout. No ciphertext/record was read, copied or deleted.
- Official complete target DDL, global version reservation, DBA/execution authority, data conversion and old worker retirement remain OWNER DECISION REQUIRED.

## Confirmed code mapping defect

HeaderService serialized a JDBC timestamp through `toLocalDateTime().atOffset(UTC)`. A real MySQL notice at2030-04-05T06:07:08Z returned13:07:08Z when the JVM zone was Asia/Ho_Chi_Minh. This is **CODE FIX REQUIRED**, independent of approving DDL. The mapper now uses `toInstant()`; a real authenticated MockMvc/MySQL regression sets/restores the JVM zone and verifies the exact instant. Test execution is isolated from other JUnit classes. No SQL JOIN, role or business timezone policy is changed.
