-- SYNTHETIC ONLY. The profile-postman-live.py runner first verifies its newly owned *_test schema.
-- @profile_fixture_hash must be generated with the application's BCrypt encoder, never a real account hash.
-- This script does not provision DDL; native DatabaseRehearsal must run first.
SET NAMES utf8mb4;
SET SESSION time_zone='+00:00';
INSERT INTO media_assets(id,provider,provider_asset_id,secure_url,mime_type,size_bytes,deleted_at) VALUES
  (1001,'CLOUDINARY','profile-fixture-visible','https://example.test/profile-visible.png','image/png',10,NULL),
  (1002,'CLOUDINARY','profile-fixture-deleted','https://example.test/profile-deleted.png','image/png',10,UTC_TIMESTAMP(6));
INSERT INTO users(id,username,password_hash,full_name,email,phone,role,status,password_changed_at,must_change_password,avatar_media_id) VALUES
  (101,'pm_profile_admin',@profile_fixture_hash,'Synthetic Admin','pm_profile_admin@example.test','0900000001','ADMIN','ACTIVE',UTC_TIMESTAMP(6),0,1001),
  (102,'pm_profile_admin2',@profile_fixture_hash,'Synthetic Admin Two','pm_profile_admin2@example.test','0900000002','ADMIN','ACTIVE',UTC_TIMESTAMP(6),0,NULL),
  (103,'pm_profile_staff',@profile_fixture_hash,'Synthetic Staff','pm_profile_staff@example.test','0900000003','STAFF','ACTIVE',UTC_TIMESTAMP(6),0,1002),
  (104,'pm_profile_staff2',@profile_fixture_hash,'Synthetic Staff Two','pm_profile_staff2@example.test','0900000004','STAFF','ACTIVE',UTC_TIMESTAMP(6),0,NULL),
  (105,'pm_profile_customer',@profile_fixture_hash,'Synthetic Customer','pm_profile_customer@example.test',NULL,'CUSTOMER','ACTIVE',UTC_TIMESTAMP(6),0,NULL),
  (106,'pm_profile_inactive',@profile_fixture_hash,'Synthetic Inactive','pm_profile_inactive@example.test',NULL,'STAFF','INACTIVE',UTC_TIMESTAMP(6),0,NULL),
  (107,'pm_profile_expired',@profile_fixture_hash,'Synthetic Expired','pm_profile_expired@example.test','0900000007','STAFF','ACTIVE',DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 31 DAY),0,NULL),
  (108,'pm_profile_flagged',@profile_fixture_hash,'Synthetic Flagged','pm_profile_flagged@example.test','0900000008','STAFF','ACTIVE',UTC_TIMESTAMP(6),1,NULL),
  (109,'pm_profile_missing',@profile_fixture_hash,'Synthetic Missing','pm_profile_missing@example.test',NULL,'ADMIN','ACTIVE',UTC_TIMESTAMP(6),0,NULL),
  (110,'pm_profile_deactivated',@profile_fixture_hash,'Synthetic Deactivated','pm_profile_deactivated@example.test',NULL,'STAFF','ACTIVE',UTC_TIMESTAMP(6),0,NULL),
  (9007199254740993,'pm_profile_bigint',@profile_fixture_hash,'Synthetic Large ID','pm_profile_bigint@example.test',NULL,'ADMIN','ACTIVE',UTC_TIMESTAMP(6),0,NULL);
INSERT INTO staff_profiles(user_id,address,birthday,gender,salary) VALUES
  (101,'Synthetic address',NULL,NULL,10000000), (102,'Synthetic address','1995-01-01','MALE',10000000),
  (103,'Synthetic address',NULL,NULL,10000000), (104,'Synthetic address',NULL,NULL,10000000),
  (107,'Synthetic address',NULL,NULL,10000000), (108,'Synthetic address',NULL,NULL,10000000),
  (9007199254740993,'Synthetic address',NULL,NULL,999999999999900000);
