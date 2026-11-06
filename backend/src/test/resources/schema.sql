-- TEST CONTRACT FIXTURE ONLY. Imported explicitly into an isolated *_test database.
-- Source: database-design-20261106/01-schema.sql supplied in the local workspace.
-- The application never loads this resource; this is not a production migration.
-- Smart Cafe target schema, 2026-11-06. MySQL 8.0.16+ / InnoDB.
-- Run ONLY in a NEW, EMPTY schema selected by the operator.
-- This is a complete target design, NOT an upgrade to existing Flyway V001-V010.
-- All DATETIME values represent UTC; the application must set connection time_zone.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE media_assets (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Khóa nội bộ của ảnh',
  provider VARCHAR(16) NOT NULL COMMENT 'CLOUDINARY hoặc FIREBASE theo Plan',
  provider_asset_id VARCHAR(255) NOT NULL COMMENT 'Public id hoặc bucket/path để quản lý ảnh tại provider',
  secure_url VARCHAR(1024) NOT NULL COMMENT 'URL HTTPS công khai, không chứa secret',
  mime_type VARCHAR(80) NOT NULL COMMENT 'MIME ảnh đã được backend xác minh',
  size_bytes BIGINT NOT NULL COMMENT 'Kích thước file sau upload',
  width_px INT NULL COMMENT 'Chiều rộng nếu có metadata',
  height_px INT NULL COMMENT 'Chiều cao nếu có metadata',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Thời điểm tạo UTC',
  deleted_at DATETIME(6) NULL COMMENT 'Ảnh đã ngừng sử dụng; không tự xóa các bản ghi liên quan',
  CONSTRAINT uq_media_provider UNIQUE (provider, provider_asset_id),
  CONSTRAINT ck_media_provider CHECK (provider IN ('CLOUDINARY','FIREBASE')),
  CONSTRAINT ck_media_size CHECK (size_bytes > 0),
  CONSTRAINT ck_media_dimensions CHECK ((width_px IS NULL OR width_px > 0) AND (height_px IS NULL OR height_px > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Ảnh món, nhân viên, phản hồi và tin tức';

CREATE TABLE users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Khóa tài khoản',
  username VARCHAR(64) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL COMMENT 'Tên đăng nhập duy nhất; backend chuẩn hóa và validate',
  password_hash VARCHAR(255) NOT NULL COMMENT 'Password hash; tuyệt đối không lưu mật khẩu gốc',
  full_name VARCHAR(160) NOT NULL COMMENT 'Họ tên',
  email VARCHAR(255) COLLATE utf8mb4_0900_as_ci NOT NULL COMMENT 'Email đã trim/chuẩn hóa tại backend; không trùng',
  phone VARCHAR(20) NULL COMMENT 'Số điện thoại dạng chuỗi, giữ số 0 đầu',
  role VARCHAR(16) NOT NULL COMMENT 'Một quyền ADMIN, STAFF hoặc CUSTOMER theo repo hiện tại',
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, INACTIVE, PENDING_VERIFICATION',
  avatar_media_id BIGINT NULL COMMENT 'Ảnh đại diện trên dịch vụ ảnh',
  email_verified_at DATETIME(6) NULL COMMENT 'Thời điểm xác minh email',
  password_changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Căn cứ chính sách đổi mật khẩu 30 ngày của Plan',
  must_change_password BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Bắt đổi sau cấp/reset theo chính sách được duyệt',
  auth_version BIGINT NOT NULL DEFAULT 0 COMMENT 'Tăng để thu hồi token/session khi đổi quyền hoặc mật khẩu',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Ngày tạo',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Lần cập nhật cuối',
  version BIGINT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock khi cập nhật thông tin',
  CONSTRAINT uq_users_username UNIQUE (username),
  CONSTRAINT uq_users_email UNIQUE (email),
  CONSTRAINT fk_users_avatar FOREIGN KEY (avatar_media_id) REFERENCES media_assets(id),
  CONSTRAINT ck_users_role CHECK (role IN ('ADMIN','STAFF','CUSTOMER')),
  CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE','INACTIVE','PENDING_VERIFICATION')),
  CONSTRAINT ck_users_versions CHECK (auth_version >= 0 AND version >= 0),
  INDEX ix_users_role_status (role, status, id),
  INDEX ix_users_name (full_name),
  INDEX ix_users_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Tài khoản đăng nhập, gồm CUSTOMER mở rộng đã có trong repo';

CREATE TABLE staff_profiles (
  user_id BIGINT PRIMARY KEY COMMENT 'Một hồ sơ cho tài khoản STAFF hoặc ADMIN; kiểm tra role ở service',
  address VARCHAR(255) NOT NULL COMMENT 'Địa chỉ bắt buộc theo SRS',
  birthday DATE NULL COMMENT 'Ngày sinh tùy chọn',
  gender VARCHAR(8) NULL COMMENT 'MALE/FEMALE hoặc NULL theo SRS hiện tại',
  salary DECIMAL(18,0) NOT NULL COMMENT 'Lương VND; không phải hệ thống chấm công/tính lương',
  CONSTRAINT fk_staff_profiles_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT ck_staff_salary CHECK (salary > 0 AND MOD(salary,100000) = 0),
  CONSTRAINT ck_staff_gender CHECK (gender IS NULL OR gender IN ('MALE','FEMALE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Bổ sung địa chỉ, ngày sinh, giới tính, lương còn thiếu trong sơ đồ';

CREATE TABLE auth_otp_challenges (
  id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT 'Challenge id ngẫu nhiên; giữ tương thích auth hiện có',
  user_id BIGINT NULL COMMENT 'NULL cho yêu cầu không ánh xạ tài khoản; tránh tiết lộ tồn tại email',
  email VARCHAR(255) COLLATE utf8mb4_0900_as_ci NOT NULL COMMENT 'Email đích đã chuẩn hóa',
  purpose VARCHAR(16) NOT NULL COMMENT 'REGISTER hoặc RESET',
  code_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'HMAC-SHA256(challenge_id|purpose|otp); key outside DB',
  attempts INT NOT NULL DEFAULT 0 COMMENT 'Số lần nhập; service kiểm tra giới hạn đã cấu hình',
  sends INT NOT NULL DEFAULT 1 COMMENT 'Số đợt cấp/gửi lại mã',
  expires_at DATETIME(6) NOT NULL COMMENT 'Hạn dùng mã',
  next_send_at DATETIME(6) NOT NULL COMMENT 'Mốc được yêu cầu gửi lại',
  used_at DATETIME(6) NULL COMMENT 'Mốc đã xác minh hoặc vô hiệu hóa',
  delivery_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'Trạng thái queue mail, không đồng nghĩa đã vào Inbox',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Ngày cấp challenge',
  last_error_code VARCHAR(40) NULL COMMENT 'Mã lỗi an toàn; không lưu SMTP exception/secret',
  sent_at DATETIME(6) NULL COMMENT 'Mốc SMTP chấp nhận thư',
  CONSTRAINT fk_auth_otp_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT ck_auth_otp_purpose CHECK (purpose IN ('REGISTER','RESET')),
  CONSTRAINT ck_auth_otp_delivery CHECK (delivery_status IN ('PENDING','SENT','FAILED','EXPIRED','CANCELLED')),
  CONSTRAINT ck_auth_otp_counts CHECK (attempts >= 0 AND sends >= 1),
  CONSTRAINT ck_auth_otp_reset_user CHECK (purpose <> 'RESET' OR user_id IS NOT NULL),
  CONSTRAINT ck_auth_otp_expiry CHECK (expires_at > created_at),
  INDEX ix_auth_otp_rate (email, purpose, created_at),
  INDEX ix_auth_otp_user (user_id, purpose),
  INDEX ix_auth_otp_delivery (delivery_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='OTP đăng ký/khôi phục đã được yêu cầu trong repo, ngoài SRS gốc';

CREATE TABLE auth_otp_limits (
  email_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Khóa băm chuẩn hóa email cho rate limit',
  purpose VARCHAR(16) NOT NULL COMMENT 'REGISTER hoặc RESET',
  next_send_at DATETIME(6) NOT NULL COMMENT 'Mốc gửi tiếp theo',
  window_start DATETIME(6) NOT NULL COMMENT 'Đầu cửa sổ giới hạn',
  request_count INT NOT NULL DEFAULT 0 COMMENT 'Số yêu cầu trong cửa sổ',
  PRIMARY KEY (email_key, purpose),
  CONSTRAINT ck_otp_limits_purpose CHECK (purpose IN ('REGISTER','RESET')),
  CONSTRAINT ck_otp_limits_count CHECK (request_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Rate limit theo email và mục đích, kể cả email chưa có tài khoản';

CREATE TABLE menu_categories (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Khóa số ổn định để không dùng mã nghiệp vụ làm FK',
  code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL COMMENT 'Mã nhóm duy nhất, không tái sử dụng sau lưu trữ',
  name VARCHAR(120) NOT NULL COMMENT 'Tên nhóm',
  active BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Hiển thị/ẩn nhóm; không xóa lịch sử',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Ngày tạo',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Ngày cập nhật',
  version BIGINT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock',
  CONSTRAINT uq_categories_code UNIQUE (code),
  CONSTRAINT ck_categories_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Nhóm món theo SRS 6.1.8';

CREATE TABLE menu_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Khóa món ổn định',
  category_id BIGINT NOT NULL COMMENT 'Nhóm món; dữ liệu cũ có thể dùng nhóm UNCATEGORIZED khi chuyển đổi',
  code VARCHAR(40) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL COMMENT 'Mã món duy nhất, giữ mã sau ngừng sử dụng',
  name VARCHAR(160) NOT NULL COMMENT 'Tên món',
  price DECIMAL(18,0) NOT NULL COMMENT 'Giá niêm yết hiện hành VND; validation >5000 ở service theo ảnh SRS',
  image_media_id BIGINT NULL COMMENT 'Ảnh món; cho phép NULL khi nhập dữ liệu cũ, service bắt buộc cho tạo mới theo AC',
  active BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Ngừng sử dụng logic',
  available BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Tạm hết món; trường dự phòng chờ PO xác nhận sử dụng',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Căn cứ top món mới',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Ngày cập nhật',
  version BIGINT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock',
  CONSTRAINT uq_menu_items_code UNIQUE (code),
  CONSTRAINT fk_menu_items_category FOREIGN KEY (category_id) REFERENCES menu_categories(id),
  CONSTRAINT fk_menu_items_image FOREIGN KEY (image_media_id) REFERENCES media_assets(id),
  CONSTRAINT ck_menu_items_price CHECK (price > 0),
  CONSTRAINT ck_menu_items_version CHECK (version >= 0),
  INDEX ix_menu_active_created (active, created_at, id),
  INDEX ix_menu_category_active (category_id, active, id),
  INDEX ix_menu_name (name),
  INDEX ix_menu_price (price)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Menu hiện hành; không thay thế snapshot giá của order và bill';

CREATE TABLE cafe_tables (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Khóa bàn vật lý',
  code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL COMMENT 'Mã bàn, ví dụ T001',
  table_number INT NULL COMMENT 'Số bàn giữ từ schema hiện tại nếu cần',
  name VARCHAR(50) NOT NULL COMMENT 'Tên bàn hiển thị',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, OUT_OF_SERVICE, ARCHIVED; không lưu trạng thái có khách ở đây',
  qr_public_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Định danh QR công khai; KHÔNG dùng làm token gọi món',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Ngày tạo',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Ngày cập nhật',
  version BIGINT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock',
  CONSTRAINT uq_cafe_tables_code UNIQUE (code),
  CONSTRAINT uq_cafe_tables_number UNIQUE (table_number),
  CONSTRAINT uq_cafe_tables_qr UNIQUE (qr_public_id),
  CONSTRAINT ck_cafe_tables_status CHECK (status IN ('ACTIVE','OUT_OF_SERVICE','ARCHIVED')),
  CONSTRAINT ck_cafe_tables_number CHECK (table_number IS NULL OR table_number > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Bàn vật lý; tình trạng có khách suy từ phiên đang mở';

CREATE TABLE table_sessions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Một lượt phục vụ của một bàn',
  table_id BIGINT NOT NULL COMMENT 'Bàn vật lý',
  session_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Mã phiên hiển thị/tra cứu, không cấp quyền truy cập',
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN, CHECKOUT, CLOSED hoặc CANCELLED; luồng cụ thể chờ QA',
  opened_by_user_id BIGINT NOT NULL COMMENT 'Người mở phiên; giả định triển khai do nhân viên mở',
  closed_by_user_id BIGINT NULL COMMENT 'Người đóng/hủy phiên',
  started_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Mốc bắt đầu UTC',
  ended_at DATETIME(6) NULL COMMENT 'Mốc kết thúc; không xóa phiên sau reset',
  join_code_hash VARCHAR(255) NULL COMMENT 'Hash mã tham gia nếu chọn phương án QR + mã; có thể không dùng',
  version BIGINT NOT NULL DEFAULT 0 COMMENT 'Chống cập nhật mất dữ liệu',
  CONSTRAINT uq_table_sessions_code UNIQUE (session_code),
  UNIQUE INDEX uq_table_sessions_active ((CASE WHEN status IN ('OPEN','CHECKOUT') THEN table_id ELSE NULL END)),
  CONSTRAINT fk_sessions_table FOREIGN KEY (table_id) REFERENCES cafe_tables(id),
  CONSTRAINT fk_sessions_opened_by FOREIGN KEY (opened_by_user_id) REFERENCES users(id),
  CONSTRAINT fk_sessions_closed_by FOREIGN KEY (closed_by_user_id) REFERENCES users(id),
  CONSTRAINT ck_sessions_status CHECK (status IN ('OPEN','CHECKOUT','CLOSED','CANCELLED')),
  CONSTRAINT ck_sessions_end CHECK ((status IN ('OPEN','CHECKOUT') AND ended_at IS NULL AND closed_by_user_id IS NULL) OR (status IN ('CLOSED','CANCELLED') AND ended_at IS NOT NULL AND closed_by_user_id IS NOT NULL AND ended_at >= started_at)),
  INDEX ix_sessions_table_started (table_id, started_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Lượt phục vụ; không đồng nhất với tài khoản khách hoặc bàn';

CREATE TABLE session_clients (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Một thiết bị/trình duyệt tham gia phiên',
  session_id BIGINT NOT NULL COMMENT 'Phiên được cấp quyền',
  customer_user_id BIGINT NULL DEFAULT NULL COMMENT 'NULL: khách vãng lai',
  access_token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Hash token thiết bị ngẫu nhiên, không lưu token gốc',
  joined_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Mốc tham gia',
  expires_at DATETIME(6) NOT NULL COMMENT 'Hạn token theo chính sách phiên',
  revoked_at DATETIME(6) NULL COMMENT 'Thu hồi riêng thiết bị; đóng phiên còn kiểm tra status ở server',
  CONSTRAINT uq_session_clients_token UNIQUE (access_token_hash),
  CONSTRAINT uq_session_clients_scope UNIQUE (session_id, id),
  CONSTRAINT fk_session_clients_session FOREIGN KEY (session_id) REFERENCES table_sessions(id),
  CONSTRAINT fk_session_clients_customer FOREIGN KEY (customer_user_id) REFERENCES users(id),
  CONSTRAINT ck_session_clients_expiry CHECK (expires_at > joined_at),
  INDEX ix_session_clients_customer (customer_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Hỗ trợ nhiều điện thoại; quyền sửa món vẫn cần QA xác nhận';

CREATE TABLE order_batches (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Một lần bấm gửi order; gọi thêm là đợt khác',
  order_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Mã đợt gọi',
  session_id BIGINT NOT NULL COMMENT 'Phiên sở hữu order',
  submitted_by_client_id BIGINT NULL COMMENT 'Thiết bị khách gửi; XOR submitted_by_user_id',
  submitted_by_user_id BIGINT NULL COMMENT 'Nhân viên nhập giúp; XOR submitted_by_client_id',
  submitted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Server ghi nhận UTC; không lấy đồng hồ client',
  editable_until DATETIME(6) NOT NULL COMMENT 'Hạn sửa snapshot; chưa tự chốt luôn là 180 giây',
  edit_policy VARCHAR(24) NOT NULL COMMENT 'DEADLINE_ONLY hoặc BEFORE_PREPARATION; bắt buộc truyền theo quyết định QA',
  idempotency_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Khóa gửi lặp do client tạo cho cùng một request',
  request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Hash nội dung request gốc; cùng khóa khác payload phải trả 409',
  version BIGINT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock',
  CONSTRAINT uq_order_batches_code UNIQUE (order_code),
  CONSTRAINT uq_order_batches_scope UNIQUE (session_id, id),
  CONSTRAINT uq_order_batches_request UNIQUE (session_id, idempotency_key),
  CONSTRAINT fk_order_batches_session FOREIGN KEY (session_id) REFERENCES table_sessions(id),
  CONSTRAINT fk_order_batches_client FOREIGN KEY (session_id, submitted_by_client_id) REFERENCES session_clients(session_id, id),
  CONSTRAINT fk_order_batches_user FOREIGN KEY (submitted_by_user_id) REFERENCES users(id),
  CONSTRAINT ck_order_batches_actor CHECK ((submitted_by_client_id IS NOT NULL AND submitted_by_user_id IS NULL) OR (submitted_by_client_id IS NULL AND submitted_by_user_id IS NOT NULL)),
  CONSTRAINT ck_order_batches_policy CHECK (edit_policy IN ('DEADLINE_ONLY','BEFORE_PREPARATION')),
  CONSTRAINT ck_order_batches_expiry CHECK (editable_until >= submitted_at),
  INDEX ix_order_batches_session_time (session_id, submitted_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Tách đợt gọi để biểu diễn 3 phút và gửi lại chính xác';

CREATE TABLE order_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Dòng món của một đợt gọi',
  session_id BIGINT NOT NULL COMMENT 'Lặp có kiểm soát để ràng buộc bill và actor cùng phiên bằng FK kép',
  order_batch_id BIGINT NOT NULL COMMENT 'Đợt gọi sở hữu',
  menu_item_id BIGINT NOT NULL COMMENT 'Món gốc; không xóa vật lý khi có lịch sử',
  item_code VARCHAR(40) NOT NULL COMMENT 'Snapshot mã món lúc gửi',
  item_name VARCHAR(160) NOT NULL COMMENT 'Snapshot tên món lúc gửi',
  unit_price DECIMAL(18,0) NOT NULL COMMENT 'Snapshot giá do server lấy',
  quantity INT NOT NULL COMMENT 'Số lượng nguyên dương',
  line_total DECIMAL(18,0) GENERATED ALWAYS AS (unit_price * quantity) STORED COMMENT 'Thành tiền tự tính; không nhận từ frontend',
  status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED' COMMENT 'SUBMITTED, PREPARING, SERVED, CANCELLED',
  preparation_started_at DATETIME(6) NULL COMMENT 'Mốc bắt đầu pha nếu chức năng này được duyệt',
  served_at DATETIME(6) NULL COMMENT 'Mốc đã phục vụ nếu được dùng',
  cancelled_at DATETIME(6) NULL COMMENT 'Mốc hủy; giữ dòng và số lượng lịch sử',
  cancellation_reason VARCHAR(500) NULL COMMENT 'Lý do hủy',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Lần thay đổi cuối',
  version BIGINT NOT NULL DEFAULT 0 COMMENT 'Chống ghi đè khi nhiều thiết bị sửa',
  CONSTRAINT uq_order_items_scope UNIQUE (session_id, id),
  CONSTRAINT uq_order_items_batch_item UNIQUE (order_batch_id, menu_item_id),
  CONSTRAINT fk_order_items_batch FOREIGN KEY (session_id, order_batch_id) REFERENCES order_batches(session_id, id),
  CONSTRAINT fk_order_items_menu FOREIGN KEY (menu_item_id) REFERENCES menu_items(id),
  CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
  CONSTRAINT ck_order_items_price CHECK (unit_price > 0),
  CONSTRAINT ck_order_items_status CHECK (status IN ('SUBMITTED','PREPARING','SERVED','CANCELLED')),
  CONSTRAINT ck_order_items_cancel CHECK ((status = 'CANCELLED' AND cancelled_at IS NOT NULL AND cancellation_reason IS NOT NULL) OR (status <> 'CANCELLED' AND cancelled_at IS NULL AND cancellation_reason IS NULL)),
  CONSTRAINT ck_order_items_preparing CHECK (status <> 'PREPARING' OR preparation_started_at IS NOT NULL),
  CONSTRAINT ck_order_items_served CHECK (status <> 'SERVED' OR served_at IS NOT NULL),
  INDEX ix_order_items_session_status (session_id, status, id),
  INDEX ix_order_items_menu (menu_item_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Cùng món ở hai đợt gọi vẫn là hai dòng; không cộng mất mốc sửa';

CREATE TABLE service_requests (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Yêu cầu gọi phục vụ hoặc tính tiền',
  session_id BIGINT NOT NULL COMMENT 'Suy ra bàn từ phiên; không lặp table_id gây lệch',
  requested_by_client_id BIGINT NOT NULL COMMENT 'Thiết bị yêu cầu của đúng phiên',
  request_type VARCHAR(16) NOT NULL COMMENT 'WAITER hoặc BILL; order mới được biểu diễn bởi order_batches',
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, ACKNOWLEDGED, RESOLVED, CANCELLED',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Mốc gửi',
  acknowledged_at DATETIME(6) NULL COMMENT 'Mốc đã tiếp nhận, không đồng nghĩa đã xử lý',
  acknowledged_by_user_id BIGINT NULL COMMENT 'Người tiếp nhận',
  resolved_at DATETIME(6) NULL COMMENT 'Mốc hoàn tất/hủy yêu cầu',
  resolved_by_user_id BIGINT NULL COMMENT 'Người hoàn tất/hủy yêu cầu',
  idempotency_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Khóa request chống gửi lặp',
  active_request_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS (CASE WHEN status IN ('PENDING','ACKNOWLEDGED') THEN CONCAT(session_id, ':', request_type) ELSE NULL END) STORED COMMENT 'Mỗi phiên chỉ có một yêu cầu chưa xử lý cho mỗi loại',
  CONSTRAINT uq_service_request_active UNIQUE (active_request_key),
  CONSTRAINT uq_service_request_retry UNIQUE (session_id, idempotency_key),
  CONSTRAINT fk_service_requests_session FOREIGN KEY (session_id) REFERENCES table_sessions(id),
  CONSTRAINT fk_service_requests_client FOREIGN KEY (session_id, requested_by_client_id) REFERENCES session_clients(session_id, id),
  CONSTRAINT fk_service_requests_ack FOREIGN KEY (acknowledged_by_user_id) REFERENCES users(id),
  CONSTRAINT fk_service_requests_resolved FOREIGN KEY (resolved_by_user_id) REFERENCES users(id),
  CONSTRAINT ck_service_request_type CHECK (request_type IN ('WAITER','BILL')),
  CONSTRAINT ck_service_request_status CHECK (status IN ('PENDING','ACKNOWLEDGED','RESOLVED','CANCELLED')),
  CONSTRAINT ck_service_request_ack CHECK ((acknowledged_at IS NULL AND acknowledged_by_user_id IS NULL) OR (acknowledged_at IS NOT NULL AND acknowledged_by_user_id IS NOT NULL AND acknowledged_at >= created_at)),
  CONSTRAINT ck_service_request_ack_state CHECK (status <> 'ACKNOWLEDGED' OR acknowledged_at IS NOT NULL),
  CONSTRAINT ck_service_request_end CHECK ((status IN ('PENDING','ACKNOWLEDGED') AND resolved_at IS NULL AND resolved_by_user_id IS NULL) OR (status IN ('RESOLVED','CANCELLED') AND resolved_at IS NOT NULL AND resolved_by_user_id IS NOT NULL AND resolved_at >= created_at)),
  INDEX ix_service_requests_status_created (status, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Phân biệt đọc thông báo và hoàn thành phục vụ';

CREATE TABLE bills (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Khóa hóa đơn',
  bill_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Số hóa đơn duy nhất',
  session_id BIGINT NOT NULL COMMENT 'Phiên được tính tiền',
  created_by_user_id BIGINT NOT NULL COMMENT 'FK người lập thay cho chỉ lưu tên',
  creator_name_snapshot VARCHAR(160) NOT NULL COMMENT 'Giữ tên người lập tại thời điểm xuất',
  table_code_snapshot VARCHAR(32) NOT NULL COMMENT 'Giữ mã bàn tại thời điểm xuất',
  table_name_snapshot VARCHAR(50) NOT NULL COMMENT 'Giữ tên bàn tại thời điểm xuất',
  total_amount DECIMAL(18,0) NOT NULL COMMENT 'Tổng dòng bill do server tính trong transaction; không phải dữ liệu client',
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, PAID, VOID; không mặc định PAID',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Mốc lập hóa đơn',
  paid_at DATETIME(6) NULL COMMENT 'Mốc xác nhận nhận tiền, cập nhật cùng payment trong một transaction',
  voided_at DATETIME(6) NULL COMMENT 'Chỉ hủy hóa đơn chưa thu tiền trong phiên bản này',
  voided_by_user_id BIGINT NULL COMMENT 'Người hủy hóa đơn',
  void_reason VARCHAR(500) NULL COMMENT 'Lý do hủy',
  idempotency_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Khóa tạo hóa đơn gửi lặp trong phiên',
  request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Hash request gốc',
  version BIGINT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock',
  current_session_id BIGINT GENERATED ALWAYS AS (CASE WHEN status <> 'VOID' THEN session_id ELSE NULL END) STORED COMMENT 'Tối đa một bill hiệu lực mỗi phiên; vẫn giữ bill hủy',
  CONSTRAINT uq_bills_code UNIQUE (bill_code),
  CONSTRAINT uq_bills_scope UNIQUE (session_id, id),
  CONSTRAINT uq_bills_current_session UNIQUE (current_session_id),
  CONSTRAINT uq_bills_request UNIQUE (session_id, idempotency_key),
  CONSTRAINT fk_bills_session FOREIGN KEY (session_id) REFERENCES table_sessions(id),
  CONSTRAINT fk_bills_creator FOREIGN KEY (created_by_user_id) REFERENCES users(id),
  CONSTRAINT fk_bills_voider FOREIGN KEY (voided_by_user_id) REFERENCES users(id),
  CONSTRAINT ck_bills_total CHECK (total_amount > 0),
  CONSTRAINT ck_bills_status CHECK (status IN ('PENDING','PAID','VOID')),
  CONSTRAINT ck_bills_paid CHECK ((status = 'PAID' AND paid_at IS NOT NULL AND paid_at >= created_at) OR (status <> 'PAID' AND paid_at IS NULL)),
  CONSTRAINT ck_bills_void CHECK ((status = 'VOID' AND voided_at IS NOT NULL AND voided_by_user_id IS NOT NULL AND void_reason IS NOT NULL AND voided_at >= created_at) OR (status <> 'VOID' AND voided_at IS NULL AND voided_by_user_id IS NULL AND void_reason IS NULL)),
  INDEX ix_bills_status_paid (status, paid_at, id),
  INDEX ix_bills_created (created_at, id),
  INDEX ix_bills_creator_created (created_by_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Hóa đơn một phiên; giả định không chia hóa đơn/hoàn tiền, chờ QA';

CREATE TABLE bill_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Khóa dòng hóa đơn',
  bill_id BIGINT NOT NULL COMMENT 'Hóa đơn sở hữu',
  session_id BIGINT NOT NULL COMMENT 'FK kép bắt buộc bill và order line cùng phiên',
  order_item_id BIGINT NOT NULL COMMENT 'Dòng order nguồn; không có dòng bill trôi nổi',
  item_code VARCHAR(40) NOT NULL COMMENT 'Snapshot mã món',
  item_name VARCHAR(160) NOT NULL COMMENT 'Snapshot tên món',
  unit_price DECIMAL(18,0) NOT NULL COMMENT 'Snapshot đơn giá',
  quantity INT NOT NULL COMMENT 'Số lượng chốt hóa đơn',
  line_total DECIMAL(18,0) GENERATED ALWAYS AS (unit_price * quantity) STORED COMMENT 'Thành tiền tự tính',
  CONSTRAINT uq_bill_items_source UNIQUE (bill_id, order_item_id),
  CONSTRAINT fk_bill_items_bill_scope FOREIGN KEY (session_id, bill_id) REFERENCES bills(session_id, id),
  CONSTRAINT fk_bill_items_order_scope FOREIGN KEY (session_id, order_item_id) REFERENCES order_items(session_id, id),
  CONSTRAINT ck_bill_items_quantity CHECK (quantity > 0),
  CONSTRAINT ck_bill_items_price CHECK (unit_price > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Snapshot bất biến sau xuất; không cascade xóa lịch sử tài chính';

CREATE TABLE payments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Một xác nhận thu đủ tiền cho hóa đơn',
  bill_id BIGINT NOT NULL COMMENT 'Chỉ một payment thành công trên mỗi bill ở phạm vi hiện tại',
  method VARCHAR(20) NOT NULL COMMENT 'CASH hoặc BANK_TRANSFER xác nhận thủ công',
  amount DECIMAL(18,0) NOT NULL COMMENT 'Phải bằng bill.total_amount; service kiểm tra trong row lock',
  reference_code VARCHAR(128) NULL COMMENT 'Mã tham chiếu chuyển khoản nếu có; không lưu dữ liệu thẻ',
  received_by_user_id BIGINT NOT NULL COMMENT 'Người xác nhận tiền',
  received_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC; cùng giá trị bills.paid_at trong transaction',
  idempotency_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Khóa xác nhận thanh toán chống gửi lặp',
  request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Hash nội dung request thanh toán',
  CONSTRAINT uq_payments_bill UNIQUE (bill_id),
  CONSTRAINT uq_payments_request UNIQUE (idempotency_key),
  CONSTRAINT fk_payments_bill FOREIGN KEY (bill_id) REFERENCES bills(id),
  CONSTRAINT fk_payments_receiver FOREIGN KEY (received_by_user_id) REFERENCES users(id),
  CONSTRAINT ck_payments_method CHECK (method IN ('CASH','BANK_TRANSFER')),
  CONSTRAINT ck_payments_amount CHECK (amount > 0),
  INDEX ix_payments_received (received_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Tách yêu cầu tính tiền, lập bill và nhận tiền; chưa có cổng thanh toán';

CREATE TABLE feedback (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Khóa phản hồi',
  session_id BIGINT NULL COMMENT 'Phiên phát sinh nếu có; quyền gửi công khai chờ PO',
  customer_user_id BIGINT NULL DEFAULT NULL COMMENT 'NULL: khách vãng lai',
  author_name VARCHAR(160) NOT NULL COMMENT 'Tên người gửi theo SRS',
  author_email VARCHAR(255) NOT NULL COMMENT 'Email người gửi theo SRS',
  content TEXT NOT NULL COMMENT 'Nội dung phản hồi',
  rating TINYINT UNSIGNED NOT NULL COMMENT 'So sao tu 1 den 5',
  status VARCHAR(16) NOT NULL DEFAULT 'UNREAD' COMMENT 'UNREAD hoac READ',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Dùng tìm phản hồi theo ngày',
  CONSTRAINT fk_feedback_session FOREIGN KEY (session_id) REFERENCES table_sessions(id),
  CONSTRAINT fk_feedback_customer FOREIGN KEY (customer_user_id) REFERENCES users(id),
  CONSTRAINT ck_feedback_content CHECK (CHAR_LENGTH(TRIM(content)) > 0),
  CONSTRAINT ck_feedback_rating CHECK (rating BETWEEN 1 AND 5),
  CONSTRAINT ck_feedback_status CHECK (status IN ('UNREAD','READ')),
  INDEX ix_feedback_status_created (status, created_at, id),
  INDEX ix_feedback_created (created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SRS 6.1.10, 6.2.2; không tự thêm điểm sao chưa đặc tả';

CREATE TABLE feedback_images (
  feedback_id BIGINT NOT NULL COMMENT 'Phản hồi sở hữu ảnh',
  media_id BIGINT NOT NULL COMMENT 'Ảnh trên Firebase theo Plan',
  sort_order INT NOT NULL DEFAULT 0 COMMENT 'Thứ tự hiển thị',
  PRIMARY KEY (feedback_id, media_id),
  CONSTRAINT uq_feedback_images_order UNIQUE (feedback_id, sort_order),
  CONSTRAINT fk_feedback_images_feedback FOREIGN KEY (feedback_id) REFERENCES feedback(id),
  CONSTRAINT fk_feedback_images_media FOREIGN KEY (media_id) REFERENCES media_assets(id),
  CONSTRAINT ck_feedback_images_order CHECK (sort_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Ảnh phản hồi bổ sung tại Plan Tasks_Sprint_1 E6';

CREATE TABLE news_posts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Khóa bài tin',
  slug VARCHAR(180) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL COMMENT 'Đường dẫn bài duy nhất',
  title VARCHAR(200) NOT NULL COMMENT 'Tiêu đề; giới hạn thiết kế đề xuất, chờ đặc tả tin tức',
  summary VARCHAR(500) NULL COMMENT 'Tóm tắt tùy chọn',
  body LONGTEXT NOT NULL COMMENT 'Nội dung; backend kiểm soát/sanitize định dạng trước hiển thị',
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT, PUBLISHED, ARCHIVED; thiết kế đề xuất',
  author_user_id BIGINT NOT NULL COMMENT 'Người tạo bài',
  updated_by_user_id BIGINT NULL COMMENT 'Người sửa gần nhất',
  published_at DATETIME(6) NULL COMMENT 'Mốc xuất bản',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Mốc tạo',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Mốc cập nhật',
  version BIGINT NOT NULL DEFAULT 0 COMMENT 'Chống sửa đè',
  CONSTRAINT uq_news_slug UNIQUE (slug),
  CONSTRAINT fk_news_author FOREIGN KEY (author_user_id) REFERENCES users(id),
  CONSTRAINT fk_news_updater FOREIGN KEY (updated_by_user_id) REFERENCES users(id),
  CONSTRAINT ck_news_status CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED')),
  CONSTRAINT ck_news_publication CHECK ((status = 'DRAFT' AND published_at IS NULL) OR (status = 'PUBLISHED' AND published_at IS NOT NULL) OR status = 'ARCHIVED'),
  CONSTRAINT ck_news_body CHECK (CHAR_LENGTH(TRIM(body)) > 0),
  INDEX ix_news_status_published (status, published_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Plan B9/E9; bố cục và quyền còn chờ PO xác nhận';

CREATE TABLE news_images (
  news_id BIGINT NOT NULL COMMENT 'Bài tin sở hữu',
  media_id BIGINT NOT NULL COMMENT 'Ảnh trên Firebase theo Plan',
  sort_order INT NOT NULL DEFAULT 0 COMMENT 'Ảnh số 0 có thể dùng làm ảnh bìa',
  PRIMARY KEY (news_id, media_id),
  CONSTRAINT uq_news_images_order UNIQUE (news_id, sort_order),
  CONSTRAINT fk_news_images_post FOREIGN KEY (news_id) REFERENCES news_posts(id),
  CONSTRAINT fk_news_images_media FOREIGN KEY (media_id) REFERENCES media_assets(id),
  CONSTRAINT ck_news_images_order CHECK (sort_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Ảnh tin tức, không lưu nhiều URL trong một ô';

-- Derived projections: no manually maintained totals table as the source of truth.
CREATE VIEW v_menu_item_order_totals AS
SELECT menu_item_id, SUM(quantity) AS ordered_quantity
FROM order_items WHERE status <> 'CANCELLED' GROUP BY menu_item_id;

CREATE VIEW v_paid_item_sales AS
SELECT oi.menu_item_id, SUM(bi.quantity) AS paid_quantity, SUM(bi.line_total) AS sales_amount
FROM bill_items bi
JOIN bills b ON b.id = bi.bill_id AND b.status = 'PAID'
JOIN order_items oi ON oi.id = bi.order_item_id
GROUP BY oi.menu_item_id;

CREATE VIEW v_daily_revenue AS
SELECT DATE(CONVERT_TZ(paid_at, '+00:00', '+07:00')) AS business_date,
       COUNT(*) AS bill_count, SUM(total_amount) AS revenue_amount
FROM bills WHERE status = 'PAID'
GROUP BY DATE(CONVERT_TZ(paid_at, '+00:00', '+07:00'));

CREATE VIEW v_cafe_table_occupancy AS
SELECT t.id, t.code, t.name, t.status AS operational_status,
       s.id AS active_session_id, s.status AS session_status,
       CASE WHEN s.id IS NULL THEN 'EMPTY' ELSE 'OCCUPIED' END AS occupancy
FROM cafe_tables t LEFT JOIN table_sessions s ON s.table_id = t.id AND s.status IN ('OPEN','CHECKOUT');
