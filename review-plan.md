# PR Review Fix Plan: PR #11 (DT2-10 Backend Quản lý nhân viên)

## Context
- **PR**: [#11: feat(staff): CF-10 implement DT2-10 staff management backend](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/11)
- **Repository / branch**: `DTU-TEAM-2/smart-cafe-management` / `feature/dt2-10-staff-management`
- **Head commit**: `55fec92`
- **Reviewers**: `tranhoainam298`, `NV-DuyManh`

---

## Review comments

| # | Thread ID | Người review | Vị trí | Yêu cầu cốt lõi | Phân loại / Ưu tiên | Phương án xử lý | Trạng thái |
|---|---|---|---|---|---|---|---|
| 1 | 4536372577 | `tranhoainam298` | `StaffService.java:189` | Mật khẩu ban đầu dùng chung `Staff@123` có nguy cơ bảo mật. Cần sinh mật khẩu ngẫu nhiên riêng biệt cho từng tài khoản. | Bảo mật / **P1** | Dùng `SecureRandom` sinh mật khẩu ngẫu nhiên an toàn (chữ hoa, chữ thường, số, ký tự đặc biệt, >=10 ký tự), mã hóa BCrypt lưu DB. | Đã giải quyết & Đã kiểm thử |
| 2 | 4536372598 | `tranhoainam298` | `dt2-10-staff-schema.sql:44` | Câu `INSERT IGNORE ... SELECT` backfill gọi các cột `address, birthday, gender, salary` không có trong `users` của schema chuẩn. | Cơ sở dữ liệu / **P1** | Xóa bỏ đoạn backfill không hợp lệ khỏi `dt2-10-staff-schema.sql`, chỉ giữ lại DDL `CREATE TABLE staff_profiles` và khóa ngoại chuẩn. | Đã giải quyết & Đã kiểm thử |
| 3 | 4536372608 | `tranhoainam298` | `StaffService.java:248` | Chặn Admin tự hạ quyền chính mình thành STAFF; giới hạn tối đa 2 tài khoản Admin theo SRS Q&A #7. | Phân quyền / **P1** | 1. Trong `updateStaff`: nếu tài khoản đang cập nhật là Admin đang đăng nhập và payload gửi `role: "STAFF"`, từ chối với `400 Bad Request` hoặc `409 Conflict`.<br>2. Trong `createStaff` & `updateStaff`: kiểm tra số lượng Admin hiện có (`COUNT(users WHERE role='ADMIN' AND status='ACTIVE')`). Nếu đã có 2 Admin, từ chối cấp thêm Admin mới. | Đã giải quyết & Đã kiểm thử |
| 4 | 4536372624 | `tranhoainam298`, `NV-DuyManh` | `StaffService.java:138` | Giới hạn đối tượng xử lý là nhân sự (`role IN ('ADMIN', 'STAFF')`), tránh xem, sửa hoặc xóa nhầm tài khoản khách hàng `CUSTOMER` (probe thực tế của DuyManh: `GET /api/v1/staff/{customerId}` trả 200 thay vì 404). | Toàn vẹn dữ liệu / **P1** | Thêm điều kiện `u.role IN ('ADMIN', 'STAFF')` vào toàn bộ câu truy vấn: `getStaffList`, `getStaffById`, `updateStaff`, `deleteStaff`. Trả về `404 Not Found` nếu gọi thao tác trên ID của tài khoản khách hàng. | Đã giải quyết & Đã kiểm thử |
| 5 | 4536372637 | `tranhoainam298` | `StaffService.java:178` | Email thiếu validation dẫn đến nguy cơ lỗi constraint 500 do `users.email` là NOT NULL; thiếu validate độ dài tối đa (username 64, name 160, email 255, phone 20, address 255). | Validation / **P2** | Thêm validation vào `StaffValidation.java`: email bắt buộc, đúng định dạng regex email, kiểm tra độ dài tối đa cho tất cả các trường theo DDL. Trả về `400 Bad Request` rõ ràng. | Đã giải quyết & Đã kiểm thử |
| 6 | 4536372647 | `tranhoainam298` | `StaffImageService.java:64` | `ImageIO.read()` giải mã toàn bộ ảnh 5MB trước khi kiểm tra kích thước; thiếu kiểm tra magic bytes PNG/JPEG và giới hạn 20 triệu pixel. | Bảo mật tài nguyên / **P2** | Tái sử dụng kiểm tra magic bytes của PNG/JPEG, dùng `ImageReader` đọc metadata kích thước trước khi giải mã, giới hạn tối đa 20 triệu pixel và 5MB giống module `MenuImages`. | Đã giải quyết & Đã kiểm thử |
| 7 | 4536372655 | `tranhoainam298` | `StaffHttpReportTest.java:297` | TC-13 và TC-14 thiếu các trường `phone`, `address`, dẫn đến lỗi 400 do thiếu trường chứ chưa cô lập điều kiện username/salary; SRS chỉ cấm username bằng "admin"/"root". | Chất lượng test / **P2** | Bổ sung đầy đủ các trường bắt buộc vào payload test của TC-11, TC-12, TC-13, TC-14 để cô lập lỗi validation. Đổi test TC-13 thành thử username bằng chính xác `admin` hoặc `root`. | Đã giải quyết & Đã kiểm thử |
| 8 | 4536372663 | `tranhoainam298` | `dt2-10-staff-schema.sql:16` | DDL `media_assets` trong script lệch chuẩn so với `schema.sql` (`VARCHAR(2024)` vs `1024`, nullable dimensions, thiếu UNIQUE và CHECK constraints). | Database contract / **P2** | Đồng bộ định nghĩa bảng `media_assets` trong `dt2-10-staff-schema.sql` khớp 100% với `backend/src/test/resources/schema.sql`. | Đã giải quyết & Đã kiểm thử |
| 9 | 4538672870 | `NV-DuyManh` | `StaffService.java:33` | Phân trang trang rất lớn (`page=21474837&size=100`) gây tràn số `int offset = pageNumber * pageSize` thành số âm, làm lỗi cú pháp SQL và trả 503 thay vì danh sách rỗng / 400. | Ổn định & Hiệu năng / **P2** | 1. Dùng `long offset = (long) pageNumber * (long) pageSize;`<br>2. Nếu `offset >= totalElements`, trả về danh sách rỗng ngay không cần truy vấn bảng chi tiết.<br>3. Bổ sung testcase phân trang trang lớn không gây lỗi 503. | Đã giải quyết & Đã kiểm thử |

---

## Proposed changes

### 1. File mã nguồn cần chỉnh sửa:
- `backend/src/main/java/vn/codegym/smartcafe/staff/StaffValidation.java`:
  - Thêm kiểm tra `email` bắt buộc, định dạng email regex RFC 5322 cơ bản, kiểm tra max length: `username <= 64`, `fullName <= 160`, `email <= 255`, `phone <= 20`, `address <= 255`.
  - Cập nhật quy tắc username: cấm bằng `"admin"` hoặc `"root"` (không phân biệt hoa thường).
- `backend/src/main/java/vn/codegym/smartcafe/staff/StaffService.java`:
  - Sinh mật khẩu ngẫu nhiên an toàn bằng `SecureRandom` khi tạo tài khoản.
  - Lọc phạm vi nhân sự: thêm `u.role IN ('ADMIN', 'STAFF')` cho `getStaffList`, `getStaffById`, `updateStaff`, `deleteStaff`.
  - Kiểm soát quyền Admin: chặn Admin tự hạ quyền của chính mình thành STAFF; chặn tạo/nâng quyền Admin nếu hệ thống đã có đủ 2 Admin (theo SRS Q&A #7).
  - Sửa tính `offset`: dùng `long offset = (long) pageNumber * (long) pageSize;` và trả về danh sách rỗng nếu `offset >= totalElements`.
- `backend/src/main/java/vn/codegym/smartcafe/staff/StaffImageService.java`:
  - Kiểm tra magic bytes PNG/JPEG.
  - Đọc metadata kích thước trước khi giải mã, giới hạn tối đa 20 triệu pixel và 5MB.
- `backend/dev-fixtures/dt2-10-staff-schema.sql`:
  - Đồng bộ DDL `media_assets` chuẩn hóa theo `schema.sql`.
  - Xóa bỏ câu lệnh backfill không an toàn.
- `backend/src/test/java/vn/codegym/smartcafe/staff/StaffHttpReportTest.java` & `StaffControllerTest.java`:
  - Bổ sung testcase:
    1. Gọi `GET /api/v1/staff/{customerId}` với `role = 'CUSTOMER'` -> trả về `404 Not Found`.
    2. Gọi `GET /api/v1/staff?page=21474837&size=100` -> trả về `200 OK` với mảng `content: []`.
    3. Thử tạo Admin thứ 3 khi đã có 2 Admin -> trả về `400` / `409`.
    4. Admin tự hạ quyền thành STAFF -> trả về `400` / `409`.
  - Cập nhật payload các ca kiểm thử validation (TC-11 -> TC-14) có đủ trường hợp lệ để cô lập điều kiện test.

---

## Verification
- Chạy `mvnw test -Dtest=StaffHttpReportTest,StaffControllerTest` trên MySQL test độc lập.
- Render lại báo cáo `docs/reports/dt2-10-api/report.html`.
- Kiểm tra quy tắc Governance: `node scripts/skills.mjs check; node --test scripts/skills.test.mjs; git diff --check`.
- Push commit lên nhánh và theo dõi CI GitHub.

---

## Progress and verification
- [x] Dev duyệt kế hoạch (Điểm kiểm soát 1)
- [x] Triển khai chỉnh sửa mã nguồn
- [x] Chạy kiểm thử tự động đạt 100%
- [x] Cập nhật báo cáo & phản hồi các thread trên PR
- [ ] Dev nghiệm thu kết quả (Điểm kiểm soát 2)
