# Database hệ thống — nguồn chuẩn

**Bản chuẩn chủ repo cung cấp ngày 11/11/2026:** [sơ đồ database](../handover/evidence/system-database-reference-2026-11-11.jpg). Áp dụng cho toàn bộ dự án theo [rule database bắt buộc](../../.agents/rules/database-source-of-truth.md), không chỉ DT2-1. Runtime hiện được cấu hình dùng MySQL `smart_cafe`.

## Danh mục trong sơ đồ mới

| Nhóm | Bảng |
| --- | --- |
| Menu | `menu_categories`, `menu_items` |
| Vận hành | `cafe_tables`, `table_sessions`, `order_batches`, `order_items`, `session_clients`, `service_requests` |
| Thanh toán | `bills`, `bill_items`, `payments` |
| Tài khoản và xác thực | `users`, `staff_profiles`, `auth_otp_challenges`, `auth_otp_limits` |
| Nội dung và ảnh | `media_assets`, `news_posts`, `news_images`, `feedback`, `feedback_images` |

Tổng cộng **20 bảng**. Danh mục này giúp tìm nguồn, không thay DDL: tên/kiểu cột, nullability, khóa, default và giá trị trạng thái phải được đối chiếu chi tiết trước khi sử dụng. Không suy ra tất cả constraint/enum chỉ từ kiểu varchar hay đường nối trong ảnh. Ví dụ `session_clients.customer_user_id` và `feedback.customer_user_id` cho phép khách vãng lai theo ghi chú ảnh; không tự ép thành tài khoản đã đăng nhập. `feedback.rating` và `feedback.status` đã có trong bản mới, không tự tạo thêm trường tương tự.

## Quan hệ DT2-1 tái sử dụng

- `menu_categories` → `menu_items` → `media_assets`: tên/giá/ảnh/active/created_at.
- `menu_items` → `order_items` → `order_batches` → `table_sessions` → `cafe_tables`: top gọi nhiều và thông báo order đang chờ.
- `service_requests` → `table_sessions`/`session_clients`: thông báo gọi phục vụ.
- `users` → `staff_profiles`/`media_assets`: hồ sơ đúng người đăng nhập, role/status/auth_version tại DB quyết định quyền.

Không tạo bảng Header/Footer/notification trùng lặp. Không thêm trường hoặc thay FK/precision/constraint để khớp code. SQL `backend/src/test/resources/schema.sql` là fixture cấu trúc dùng cho DB test riêng, không phải migration hoặc nguồn có quyền thay thế sơ đồ chủ repo.

## Bằng chứng DDL và lịch sử đối chiếu

Lần kiểm tra DDL trước đây (06/11/2026) đã đọc `information_schema`/`SHOW CREATE` của database lúc đó: có 20 bảng và 4 view `v_menu_item_order_totals`, `v_paid_item_sales`, `v_daily_revenue`, `v_cafe_table_occupancy`. Fixture test lưu cấu trúc không chứa row; view bỏ definer và qualifier `smart_cafe` chỉ để chạy trong DB test riêng. Các view này có nguồn DDL được ghi nhận, không phải bảng nghiệp vụ bổ sung từ ảnh. Không tự tạo thêm view dựa trên suy đoán.

[Ảnh ngày 06/11](../handover/evidence/system-database-reference.png) chỉ giữ làm lịch sử: có `password_reset_tokens`, một số precision khác và `table_sessions.active_table_id`; các chi tiết này không có trong ảnh chuẩn mới, không dùng chúng để tự bổ sung schema. **Ảnh mới vẫn có `bills.current_session_id`**; trường này phải được đối chiếu riêng với DDL trước khi sử dụng, không được tự bỏ hoặc thêm vì dựa vào nhận xét của lần đối chiếu cũ. Những chênh lệch ghi nhận trước đây không chứng minh cấu trúc database ngày 11/11.

Lần cập nhật rule ngày 11/11 chỉ lưu/đối chiếu tài liệu và fixture đã có; **không đọc lại DB đang chạy, không xác nhận DB hiện tại khớp hoàn toàn sơ đồ mới và không chạy DDL/seed**. Task tiếp theo có persistence phải đọc DDL read-only, ghi chênh lệch cụ thể tại đây hoặc câu hỏi mở và dừng phần phụ thuộc cho tới khi được chốt. Đừng lấy bản fixture cũ để che chênh lệch.

## Khi cần bổ sung hoặc có chênh lệch

Ghi task, nguồn ảnh/DDL và ngày đối chiếu, bảng/cột/constraint, khác biệt, hành vi bị ảnh hưởng, người cần quyết định và link kết luận. Trước khi có kết luận, không tự thêm migration, đổi entity để tạo schema, tắt constraint hay nạp dữ liệu cho code chạy tạm.

Giữ tiền decimal/BigDecimal và biểu diễn chính xác qua API theo DDL đã chốt. Dữ liệu kiểm thử chỉ ở `_test`; không tự tạo người dùng, đơn, thanh toán hay thông báo vào DB hệ thống để lấp UI. Việc nhập dữ liệu ngày 06/11 là [yêu cầu riêng đã ghi nhận của chủ repo](../handover/2026-11-06-owner-requested-home-data.md), không phải quyền seed mặc định cho các task khác.

## DT2-9 — đối chiếu ngày 11/11/2026 cho PR #8

Theo yêu cầu chủ repo, đã đọc cấu hình kết nối legacy và chạy SHOW CREATE TABLE / metadata read-only trên MySQL local 8.0.46; không đọc row nghiệp vụ hoặc thay đổi database. DB local hiện chỉ có `flyway_schema_history`, `menu_item_order_totals`, `menu_items`. Bảng món còn `price decimal(12,0)` và `image_url`, thiếu `category_id`, `image_media_id`, `available`, `version`; thiếu toàn bộ `menu_categories`, `media_assets`, `users`. Đây là DDL cũ, không khớp mô hình mới và không thể dùng để smoke module hiện tại.

Sau khi được báo chênh lệch, chủ repo gửi lại sơ đồ DB mới và xác nhận “tôi đã gửi sơ đồ db mới”. PR #8 lấy [sơ đồ ngày 11/11](../handover/evidence/system-database-reference-2026-11-11.jpg) làm mô hình đích: PK số, category_id, image_media_id, price decimal(18,0), media_assets; không có description hoặc cột image_url của menu_items. Không nâng cấp DB local, không thêm migration/runtime DDL. Fixture main giữ nguyên để provision schema *_test mới trên instance kiểm thử riêng. Constraint/default/index của fixture chỉ là điều kiện kiểm thử lịch sử, không được tuyên bố đã xác minh cho DB triển khai từ ảnh. Triển khai thực cần chủ DB cung cấp DDL phù hợp mô hình đích và đối chiếu lại trước khi chạy.

## DT2-2 — Quy trình Flyway Baseline và Nâng cấp Migration cho bảng Auth/OTP

**Historical `_test` scenarios only. The 12/11 hardening audit below found a different migration lineage on `smart_cafe`; this section is not approval to baseline/migrate that database. CREATE TABLE IF NOT EXISTS does not reconcile existing definitions or prove compatibility. Deployment is BLOCKED pending DB owner decision.**

Đối chiếu cho PR #7 (DT2-2 Authentication & Password Recovery):
1. Nhóm bảng tài khoản & xác thực gồm 4 bảng trong sơ đồ 20 bảng: `users`, `staff_profiles`, `auth_otp_challenges`, `auth_otp_limits`.
2. Hỗ trợ đa kịch bản triển khai và nâng cấp Flyway:
   - **Kịch bản 1 — Schema sạch hoặc chưa từng có Flyway**:
     - `spring.flyway.baseline-on-migrate=true` và `spring.flyway.baseline-version=0` ghi nhận baseline tại version 0.
     - Flyway thực thi tuần tự `V001__create_users_and_auth_tables.sql` và `V002__create_users_and_auth_tables.sql`. Nhờ `CREATE TABLE IF NOT EXISTS`, toàn bộ bảng được khởi tạo an toàn và trơn tru.
   - **Kịch bản 2 — Schema đã ghi nhận migration version 001 trong `flyway_schema_history`** (ví dụ môi trường thử nghiệm hoặc lịch sử schema trước đó):
     - Flyway sẽ bỏ qua `V001` vì version <= 001.
     - Để bảo đảm không bị lỗi thiếu bảng (`Table 'smart_cafe.users' doesn't exist`), migration `V002__create_users_and_auth_tables.sql` được bổ sung. Flyway nhận diện version 002 > 001 và tự động thực thi `V002`, bảo đảm các bảng `users`, `auth_otp_challenges`, `auth_otp_limits` luôn được khởi tạo đầy đủ.
     - Cơ chế nâng cấp này đã được kiểm chứng tự động bằng bộ test hồi quy `FlywayUpgradeRegressionTest`.
   - **Kịch bản 3 — Môi trường quản lý DDL thủ công (tắt Flyway / DBA provision riêng)**:
     - Trước khi khởi động ứng dụng, người phụ trách DB / DBA xác nhận sự tồn tại của 3 bảng Auth bằng câu lệnh:
       ```sql
       SHOW TABLES LIKE 'users';
       SHOW TABLES LIKE 'auth_otp_challenges';
       SHOW TABLES LIKE 'auth_otp_limits';
       ```
     - Nếu thiếu bảng, dừng phần triển khai phụ thuộc và yêu cầu chủ DB đối chiếu sơ đồ/DDL/history; không tự áp dụng V001 lên DB chung.
3. Tính toàn vẹn dữ liệu:
   - Mọi câu lệnh DDL trong `V001` và `V002` đều dùng `CREATE TABLE IF NOT EXISTS`, tuyệt đối không drop hoặc ghi đè cấu trúc/dữ liệu của bảng `users` đã tồn tại trên database hệ thống.

## DT2-2 — đối chiếu read-only cho OTP retry ngày 12/11/2026

Task sửa P1 của PR #7, bắt đầu tại `2ee793d5d0365c5dfc43f4ae59a13cd8d8aaefa8`, đã đọc `SHOW CREATE TABLE users`, `auth_otp_challenges`, `auth_otp_limits` trên MySQL 8.0.45. Không đọc dữ liệu tài khoản hoặc chạy DDL/DML trên `smart_cafe`.

- `smart_cafe_auth_test`: các cột của ba bảng liên quan khớp fixture đã có; OTP dùng `datetime(6)`, ID/hash ASCII và các check counts/delivery/expiry/reset-user. Đây là môi trường kiểm thử, không chứng minh DDL triển khai đã khớp sơ đồ ngày 11/11.
- `smart_cafe`: OTP/limits dùng `timestamp(6)` và `utf8mb4_unicode_ci`, khác `datetime(6)` trong sơ đồ/fixture. OTP còn có `delivery_attempts`, `next_delivery_at`, `otp_ciphertext`, `last_smtp_status`; FK OTP → users có `ON DELETE CASCADE`; thiếu các check counts/delivery/expiry/reset-user của fixture. `users.role/status` là varchar(32)/varchar(32), khác 16/24 trong sơ đồ; username/hash và timestamp/collation/index/FK cũng khác fixture.
- Sửa retry chỉ dùng các cột chung đã xác nhận: `sends`, `next_send_at`, `expires_at`, `used_at`, `code_hash`, `delivery_status`, `sent_at`. Không thay fixture/schema/migration để hợp thức hóa các khác biệt. Connection kiểm thử dùng UTC.
- **Giữ phần reconciliation/provisioning/deployment phụ thuộc DDL cho chủ repo/người phụ trách DB quyết định**: xác nhận DDL đích và chính sách cho các cột delivery bổ sung trước khi triển khai. Kết quả `_test` không phải nghiệm thu runtime `smart_cafe`. Xem [bàn giao retry](../handover/dt2-2-otp-retry-lifecycle.md).

## DT2-2 — hardening audit read-only, 12/11/2026, base 62ca3f1

Read-only MySQL 8.0.45 audit of both configured `smart_cafe` and `smart_cafe_auth_test`: `SHOW CREATE TABLE`, `information_schema.COLUMNS`/table existence, indexes/FK/checks from DDL, selected migration metadata, aggregate OTP counts only. No business/account rows or credentials copied. No baseline, migrate, validate, repair, DDL or DML executed on `smart_cafe`. Runtime settings currently enable Flyway with baseline-on-migrate/version=0 and validate-on-migrate=false; these settings have **not** been changed by this audit.

| Table / contract | Expected (current source / approved diagram) | Actual configured smart_cafe | Impact / classification | Decision required |
| --- | --- | --- | --- | --- |
| `users` columns used by AuthRepository | id/username/hash/email/role/status/avatar/auth_version/password_changed_at; password update also must_change_password/updated_at | All mapped columns exist; role/status are varchar(32) rather than 16/24; timestamps/collation/index names differ | **COMPATIBLE at SQL-column mapping level**, **DRIFT** for semantics/DDL. No shared runtime mutation smoke performed | DB owner confirm target collation, widths and constraints; mapping presence is not deployment approval |
| `staff_profiles` | Diagram and HeaderService account query require table with user_id | **Missing** in smart_cafe; present in test fixture | **INCOMPATIBLE** with integrated Home account SQL (unconditional LEFT JOIN), therefore **BLOCKED** for integration deployment; not hidden by auth-only tests | DB owner supply approved DDL/deployment plan; do not create it from this audit |
| `auth_otp_challenges` common mapping | id/user/email/purpose/hash/attempts/sends/expiry/cooldown/used/delivery/created/error/sent | Common columns present | **COMPATIBLE at mapping level**, not an authenticated shared runtime acceptance | Confirm complete DDL and lineage first |
| OTP temporal types | Diagram/fixture/migrations DATETIME(6), UTC connection | TIMESTAMP(6) in shared OTP/limits | **DRIFT**: timezone/range semantics differ; UTC used in tests does not prove every deployment connection is equivalent | DB owner approve temporal contract; do not alter silently |
| OTP id/hash collation | Source fixture/migrations ASCII binary | utf8mb4_unicode_ci | **DRIFT**: equality semantics differ; no demonstrated runtime collision for generated lowercase hashes/hex IDs | Decide authoritative binary comparison/index contract |
| OTP delivery/index | Diagram has last_error_code/sent_at; current source manages synchronous delivery; fixture delivery index uses delivery_status/created_at | Extra delivery_attempts/next_delivery_at/otp_ciphertext/last_smtp_status; index uses delivery_status/next_delivery_at | **DRIFT / BLOCKED** pending ownership of former queue state and any external worker | Confirm worker/deployment configuration; do not assume extra columns inert or start another sender |
| OTP constraints/FK | Fixture counts/delivery/expiry/reset-user checks; source V001/V002 counts/delivery/expiry/purpose and FK ON DELETE CASCADE | Only purpose check in shared OTP, FK ON DELETE CASCADE; fixture FK has no cascade | **DRIFT**, not proof of current invalid data. CREATE IF NOT EXISTS cannot reconcile it | Owner reconcile approved constraints/deletion policy. No data deletion audit performed |
| `auth_otp_limits` | email_key/purpose/next_send_at/window_start/request_count with checks in fixture/migrations | Common columns and primary key exist; no count/purpose checks, timestamp/collation differ | Mapping **COMPATIBLE**; DDL **DRIFT** | Approve target checks/types and cleanup policy |
| `flyway_schema_history` | Repository only V001/V002 `create_users_and_auth_tables` | Shared history contains 001 home-highlights, 005 users/password-recovery, 006 registration/OTP, 007 mail queue, 008 delivery failure, then 002 bills/tables, 003 idempotency, 004 bill snapshot; all success=1 | **DRIFT / BLOCKED**: different lineage and colliding versions; disabled validation/IF NOT EXISTS do not establish safe upgrade | DB owner/leader decide lineage and upgrade procedure; never repair checksums or baseline over this history |

Local version/checksum comparison uses the installed Flyway 12.4.0 algorithm (CRC32 over UTF-8 text lines, BOM/newlines excluded; verified against `ChecksumCalculator` bytecode). Current V001 checksum **-197269156**, V002 **1239506062**. Shared history version 001 has script `V001__create_home_highlights.sql`, checksum **-796760827**; 002 has `V002__create_bills_and_tables.sql`, checksum **176605066**. Shared-only 003–008 checksums: 003=1309426143, 004=-369513734, 005=2104374474, 006=-1469299444, 007=-1858867750, 008=1642156858. This is evidence of different scripts/history, not authority to edit them. No Flyway validation/migration was invoked on shared DB; no claim that a tested upgrade succeeded there. `smart_cafe_auth_test` has no normal flyway_schema_history; its isolated migration regression uses a separate temporary history table.

Aggregate observations before acceptance tests: shared OTP 13 rows (13 expired, 8 used), limits 5; test DB OTP/limits 0/0. No shared rows were deleted. Counts are a dated snapshot, not a retention guarantee. Current repo has no scheduler cleanup or approved IP/global admission limit. The existing per-email forgot limit remains unchanged. Retention/admission proposals require the explicit decisions in [open questions](../product/open-questions.md#dt2-2-security-hardening--owner-clarification-12112026).

Overall deployment status: **BLOCKED** for DB owner/leader reconciliation and deployment evidence. Auth/OTP column mapping can be inspected independently; a green isolated suite does not close this blocker. Raw DDL evidence remains outside Git under the local review-guides evidence directory.
