# DT2-10 — Bàn giao Backend Quản lý nhân viên

> Ngày 11/11/2026. Nhánh `feature/dt2-10-staff-management`. Tách từ `main` (`027a05f`).
> Phụ trách: **Nguyễn Thị Yến Nhi** (Jira: DT2-10 / Sprint 1 ID 39, STT 10 trong Tasks_Sprint_1).
> Tiêu chí đối chiếu: SRS v4.1 (Mục 6.1.4, 6.1.5, 6.1.6, Other/Xóa nhân viên), Sơ đồ cơ sở dữ liệu hệ thống ngày 11/11/2026.

---

## 1. Kết quả thực hiện

- **Cơ sở dữ liệu (MySQL / Subtask DT2-68):**
  - Tuân thủ sơ đồ dữ liệu chuẩn hệ thống ngày 11/11/2026: Tách bạch giữa tài khoản xác thực `users`, hồ sơ nhân sự `staff_profiles` và bảng lưu trữ tài nguyên đa phương tiện `media_assets`.
  - Khởi tạo bảng `staff_profiles` (PK `user_id` FK tới `users.id`) kèm ràng buộc Check Constraint:
    - `ck_staff_salary`: Lương > 0 và là bội số của 100.000 VNĐ (`salary > 0 AND salary % 100000 = 0`).
    - `ck_staff_gender`: Giới tính thuộc `MALE` hoặc `FEMALE`.
  - Script DDL chuẩn hóa tại [`backend/dev-fixtures/dt2-10-staff-schema.sql`](../../backend/dev-fixtures/dt2-10-staff-schema.sql).

- **Phần mềm Backend (Spring Boot / Subtask DT2-69):**
  - Package: `vn.codegym.smartcafe.staff`.
  - **Phân quyền & Bảo mật (AC-1):** Cấu hình `SecurityConfig.java` bảo vệ endpoint `/api/v1/staff/**` bắt buộc có quyền `ROLE_ADMIN`. Role `STAFF` hoặc Anonymous đều bị từ chối với mã `403 Forbidden`. Tích hợp xác thực HMAC Bearer Token qua `HeaderTokenFilter.java` và cấp `CSRF Token` qua `GET /api/v1/staff/csrf`.
  - **Danh sách & Phân trang (AC-2):** Lấy danh sách nhân viên mặc định 10 bản ghi/trang (`size=10`), sắp xếp tăng dần theo `username`.
  - **Tìm kiếm đa tiêu chí OR (AC-3):** Tìm kiếm kết hợp `username OR full_name OR phone`. Xử lý chuyển đổi charset `CONVERT(u.username USING utf8mb4)` chống lỗi collation MySQL khi tìm kiếm từ khóa tiếng Việt UTF-8 có dấu.
  - **Thêm nhân viên (AC-4):**
    - Kiểm tra tính hợp lệ: Username > 6 ký tự, không bắt đầu bằng chữ số, không trùng lặp, cấm từ khóa `admin`/`root`. Lương > 0 và bội số 100k VNĐ.
    - Mã hóa mật khẩu ban đầu an toàn bằng BCrypt.
  - **Sửa thông tin (AC-5):** Khóa `username` (read-only, bất biến); cập nhật an toàn các thông tin họ tên, số điện thoại, địa chỉ, giới tính, ngày sinh, lương, trạng thái.
  - **Xóa an toàn & Bảo toàn dữ liệu (AC-6):** Chặn Admin tự xóa tài khoản của chính mình (`409 Conflict`); áp dụng cơ chế soft delete (`status = 'INACTIVE'`) để bảo toàn tính toàn vẹn và khóa ngoại với các bảng lịch sử (như hóa đơn thanh toán `bills`).
  - **Upload ảnh đại diện (AC-7):** Tích hợp dịch vụ `StaffImageService` xử lý upload ảnh Cloudinary, kiểm tra định dạng và lưu vào `media_assets`.

---

## 2. Kiểm chứng và bằng chứng (Subtask DT2-71)

1. **Bộ kiểm thử tích hợp HTTP thật tự động ([`StaffHttpReportTest.java`](../../backend/src/test/java/vn/codegym/smartcafe/staff/StaffHttpReportTest.java)):**
   - Chạy trên Tomcat nhúng ngẫu nhiên cổng + MySQL instance test cách ly (`cafe_dt2_test`).
   - Kết quả: **`27 / 27 PASS` (100% ĐẠT)**, bao gồm 19 ca cơ bản và 8 ca kiểm thử nâng cao/hồi quy:
     - Cô lập tài khoản khách hàng `CUSTOMER` khỏi staff API (`404 Not Found`).
     - Phân trang chỉ số trang rất lớn không tràn số integer (`200 OK` danh sách rỗng).
     - Chặn Quản trị viên tự hạ quyền và hạ quyền Quản trị viên khác (`400 Bad Request`).
     - Ràng buộc tối đa 2 Quản trị viên và chống race condition bằng `SELECT COUNT(*) ... FOR UPDATE`.
     - Kiểm tra Concurrency: Hai request tạo Admin đồng thời vẫn bảo đảm không vượt quá 2 Admin.
     - Kiểm tra lương vượt quá 18 chữ số (`DECIMAL(18,0)`) bị từ chối `400 Bad Request`.
     - Kích hoạt cờ `must_change_password = 1` bảo mật onboarding cho nhân viên mới.
   - Báo cáo HTML trực quan xuất ra tại: [`docs/reports/dt2-10-api/report.html`](dt2-10-api/report.html).
2. **Bộ kiểm thử MockMvc ([`StaffControllerTest.java`](../../backend/src/test/java/vn/codegym/smartcafe/staff/StaffControllerTest.java)):**
   - Kết quả: **`13 / 13 PASS`**.
3. **Bộ kiểm thử hồi quy xác thực ([`HeaderMySqlTest.java`](../../backend/src/test/java/vn/codegym/smartcafe/home/HeaderMySqlTest.java)):**
   - Kết quả: **`5 / 5 PASS`**.
4. **Kiểm tra quy tắc Governance & Whitespace:**
   - Lệnh: `node scripts/skills.mjs check; node --test scripts/skills.test.mjs; git diff --check`.
   - Kết quả: **`9 / 9 PASS`**, diff hoàn toàn sạch lỗi whitespace.
5. **Kiểm tra Frontend:**
   - Thư mục `frontend/` hoàn toàn nguyên vẹn so với `main` (`git diff main -- frontend` rỗng).
   - `npm run lint` (0 lỗi, 0 cảnh báo), `npm run build` thành công 100%.
6. **Bộ kịch bản kiểm thử Postman:**
   - Đã đóng gói tại: [`docs/api/dt2-10-staff-management.postman_collection.json`](../api/dt2-10-staff-management.postman_collection.json).
