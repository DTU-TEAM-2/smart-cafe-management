-- Synthetic rows only for the fresh smart_cafe_postman_test service in CI.
-- Import only after verifying SELECT DATABASE() = 'smart_cafe_postman_test'.
-- Never run against smart_cafe or another shared/system database.
SET NAMES utf8mb4;
INSERT INTO menu_categories (id, code, name, active) VALUES
  (1, 'POSTMAN_COFFEE', 'Cà phê kiểm thử', 1),
  (2, 'POSTMAN_EMPTY', 'Danh mục rỗng kiểm thử', 1),
  (3, 'POSTMAN_HIDDEN', 'Danh mục ẩn kiểm thử', 0);
INSERT INTO media_assets (id, provider, provider_asset_id, secure_url, mime_type, size_bytes) VALUES
  (1, 'CLOUDINARY', 'postman-test-latte', 'https://example.invalid/postman-latte.jpg', 'image/jpeg', 1024);
INSERT INTO menu_items (id, category_id, code, name, price, image_media_id, active, available) VALUES
  (1, 1, 'PM_CF01', 'Caramel Latte kiểm thử', 45000, 1, 1, 1),
  (2, 1, 'PM_CF02', 'Bạc xỉu 50% kiểm thử', 40000, NULL, 1, 0),
  (3, 1, 'PM_HIDDEN', 'Món ẩn kiểm thử', 35000, NULL, 0, 1),
  (4, 3, 'PM_HIDDEN_GROUP', 'Món thuộc nhóm ẩn kiểm thử', 30000, NULL, 1, 1);
