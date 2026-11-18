-- Synthetic tables only. Import only into a fresh isolated *_test database after verifying its name.
-- Not application seed, not a migration, never run on the system/shared database.
SET NAMES utf8mb4;
INSERT INTO cafe_tables(id,code,name,status,qr_public_id) VALUES
  (81001,'QR_TEST_A','Bàn QR A','ACTIVE','7d57e0b0-dfca-4ed3-846a-5b77e4450001'),
  (81002,'QR_TEST_B','Bàn QR B','ACTIVE','7d57e0b0-dfca-4ed3-846a-5b77e4450002'),
  (81003,'QR_TEST_STOP','Bàn tạm ngừng','OUT_OF_SERVICE','7d57e0b0-dfca-4ed3-846a-5b77e4450003'),
  (81004,'QR_TEST_ARCHIVE','Bàn lưu trữ','ARCHIVED','7d57e0b0-dfca-4ed3-846a-5b77e4450004');
