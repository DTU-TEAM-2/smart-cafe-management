# DT2-10 — Bàn giao Backend Quản lý nhân viên

> Khởi tạo 11/11/2026; đối chiếu lại với develop và CI 18/11/2026. Nhánh `feature/dt2-10-staff-management`. Tách từ `main` (`027a05f`).
> Phụ trách: **Nguyễn Thị Yến Nhi** (Jira: DT2-10 / Sprint 1 ID 39, STT 10 trong Tasks_Sprint_1).
> Tiêu chí đối chiếu: SRS v4.1 (Mục 6.1.4, 6.1.5, 6.1.6, Other/Xóa nhân viên), Sơ đồ cơ sở dữ liệu hệ thống ngày 11/11/2026.

---

## 1. Kết quả thực hiện

- **Cơ sở dữ liệu (MySQL / Subtask DT2-68):**
  - Tuân thủ sơ đồ dữ liệu chuẩn hệ thống ngày 11/11/2026: Tách bạch giữa tài khoản xác thực `users`, hồ sơ nhân sự `staff_profiles` và bảng lưu trữ tài nguyên đa phương tiện `media_assets`.
  - Khởi tạo bảng `staff_profiles` (PK `user_id` FK tới `users.id`) kèm ràng buộc Check Constraint:
    - `ck_staff_salary`: Lương > 0 và là bội số của 100.000 VNĐ (`salary > 0 AND salary % 100000 = 0`).
    - `ck_staff_gender`: Giới tính thuộc `MALE` hoặc `FEMALE`.
  - Script DDL tại [`backend/dev-fixtures/dt2-10-staff-schema.sql`](../../backend/dev-fixtures/dt2-10-staff-schema.sql) (chỉ dùng cho môi trường dev/test fixture cô lập; việc áp dụng cơ sở dữ liệu dùng chung tuân thủ DDL 20 bảng và Flyway lineage được phê duyệt của dự án, tuyệt đối không chạy script này trên database hệ thống `smart_cafe`).

- **Phần mềm Backend (Spring Boot / Subtask DT2-69):**
  - Package: `vn.codegym.smartcafe.staff`.
  - **Phân quyền & Bảo mật (AC-1):** Cấu hình `SecurityConfig.java` bảo vệ endpoint `/api/v1/staff/**` bắt buộc có quyền `ROLE_ADMIN`. Role `STAFF` hoặc Anonymous đều bị từ chối với mã `403 Forbidden`. Tích hợp xác thực HMAC Bearer Token qua `HeaderTokenFilter.java` và cấp `CSRF Token` qua `GET /api/v1/staff/csrf`.
  - **Danh sách & Phân trang (AC-2):** Lấy danh sách nhân viên mặc định 10 bản ghi/trang (`size=10`), sắp xếp tăng dần theo `username`.
  - **Tìm kiếm đa tiêu chí OR (AC-3):** Tìm kiếm kết hợp `username OR full_name OR phone`. Xử lý chuyển đổi charset `CONVERT(u.username USING utf8mb4)` chống lỗi collation MySQL khi tìm kiếm từ khóa tiếng Việt UTF-8 có dấu. Tích hợp cơ chế escape ký tự đặc biệt (`!`, `%`, `_`) với mệnh đề `LIKE ? ESCAPE '!'` chống hiểu nhầm ký tự literal thành SQL wildcard.
  - **Thêm nhân viên (AC-4):**
    - Kiểm tra tính hợp lệ: Username > 6 ký tự, không bắt đầu bằng chữ số, không trùng lặp, cấm từ khóa `admin`/`root`. Lương > 0 và bội số 100k VNĐ, giới hạn tối đa 18 chữ số theo `DECIMAL(18,0)`.
    - Sinh mật khẩu ngẫu nhiên bảo đảm 100% tuân thủ chính sách mật khẩu (bắt buộc gồm chữ hoa, chữ thường, chữ số, ký tự đặc biệt, xáo trộn bằng Fisher–Yates shuffle qua `SecureRandom`), gán cờ `must_change_password=1` và mã hóa BCrypt.
  - **Sửa thông tin (AC-5):** Khóa `username` (read-only, bất biến); cập nhật họ tên, email, số điện thoại, địa chỉ, giới tính, ngày sinh, lương, ảnh đại diện; bắt buộc `version` để chống cập nhật đè (stale version → 409). Trạng thái nhân viên thay đổi qua thao tác xóa mềm, không phải trường PUT.
  - **Xóa an toàn & Bảo toàn dữ liệu (AC-6):** Chặn Admin tự xóa tài khoản của chính mình (`409 Conflict`); áp dụng cơ chế soft delete (`status = 'INACTIVE'`) để bảo toàn tính toàn vẹn và khóa ngoại với các bảng lịch sử (như hóa đơn thanh toán `bills`).
  - **Upload ảnh đại diện (AC-7):** Tích hợp dịch vụ `StaffImageService` xử lý upload ảnh Cloudinary, kiểm tra định dạng và lưu vào `media_assets`. Tách biệt gọi HTTP external khỏi transaction DB và xử lý dọn dẹp asset orphan khi DB insert thất bại.

---

## 2. Kiểm chứng và bằng chứng (Subtask DT2-71)

1. **Bộ kiểm thử tích hợp HTTP thật tự động ([`StaffHttpReportTest.java`](../../backend/src/test/java/vn/codegym/smartcafe/staff/StaffHttpReportTest.java)):**
   - Chạy trên Tomcat nhúng ngẫu nhiên cổng + MySQL instance test cách ly (`cafe_dt2_test`).
   - Kết quả: **`28 / 28 PASS` (100% ĐẠT)**, bao gồm 19 ca cơ bản và 9 ca kiểm thử nâng cao/hồi quy:
     - Cô lập tài khoản khách hàng `CUSTOMER` khỏi staff API (`404 Not Found`).
     - Phân trang chỉ số trang rất lớn không tràn số integer (`200 OK` danh sách rỗng).
     - Chặn Quản trị viên tự hạ quyền và hạ quyền Quản trị viên khác (`400 Bad Request`).
     - Ràng buộc tối đa 2 Quản trị viên và chống race condition bằng `SELECT COUNT(*) ... FOR UPDATE`.
     - Kiểm tra Concurrency: Hai request tạo Admin đồng thời vẫn bảo đảm không vượt quá 2 Admin.
     - Kiểm tra lương vượt quá 18 chữ số (`DECIMAL(18,0)`) bị từ chối `400 Bad Request`.
     - Kích hoạt cờ `must_change_password = 1` bảo mật onboarding cho nhân viên mới.
     - Chặn xóa tài khoản Quản trị viên (ADMIN) qua Staff API (`400 Bad Request`) và kiểm tra Concurrency: Hai request xóa chéo Admin A↔B đồng thời đều bị từ chối, bảo đảm bất biến hệ thống luôn duy trì ít nhất 1 ADMIN ACTIVE.
   - Báo cáo HTML trực quan xuất ra tại: [`docs/reports/dt2-10-api/report.html`](dt2-10-api/report.html).
2. **Bộ kiểm thử MockMvc ([`StaffControllerTest.java`](../../backend/src/test/java/vn/codegym/smartcafe/staff/StaffControllerTest.java)):**
   - Kết quả: **`21 / 21 PASS`** (tìm kiếm literal `%`, `_`, `!`; tìm kiếm độc lập username/name/phone kết hợp OR; avatarMediaId; version CAS/409; auth_version khi đổi role và đổi email, thu hồi OTP challenge; salary serialize JSON string).
3. **Bộ kiểm thử Policy Mật khẩu & Wildcard ([`StaffPasswordPolicyTest.java`](../../backend/src/test/java/vn/codegym/smartcafe/staff/StaffPasswordPolicyTest.java)):**
   - Kết quả: **`2 / 2 PASS`** (kiểm chứng 1.000 lần sinh mật khẩu ngẫu nhiên đều thỏa mãn đầy đủ hoa/thường/số/ký tự đặc biệt và hàm escape LIKE).
4. **Bộ kiểm thử Upload Avatar ([`StaffImageServiceTest.java`](../../backend/src/test/java/vn/codegym/smartcafe/staff/StaffImageServiceTest.java)):**
   - Kết quả CI HEAD `f5226c8`: **`9 / 9 PASS`** (ảnh hợp lệ, từ chối file lỗi, Cloudinary lỗi, xử lý orphan; thêm 2 kiểm thử tránh xóa ảnh đã được ghi DB hoặc khi trạng thái commit chưa xác định). Chưa kiểm chứng Cloudinary thật.
5. **Bộ kiểm thử hồi quy xác thực ([`HeaderMySqlTest.java`](../../backend/src/test/java/vn/codegym/smartcafe/home/HeaderMySqlTest.java)):**
   - Kết quả: **`5 / 5 PASS`**.
6. **Kiểm tra quy tắc Governance & Whitespace:**
   - Lệnh: `node scripts/skills.mjs check; node --test scripts/skills.test.mjs; git diff --check`.
   - CI mới nhất: Git Flow và Governance **PASS** (không sử dụng số test cũ cho lần chạy mới).
7. **Kiểm tra Frontend:**
   - PR #11 không thêm/sửa file frontend trong PR diff; frontend của develop đã thay đổi qua các PR khác đã merge.
   - CI tại synthetic merge `38152af` (Staff `f5226c8` + `develop@b96a821`): frontend lint/build/Playwright **PASS**.
8. **Bộ kịch bản kiểm thử Postman:**
   - Đã đóng gói tại: [`docs/api/dt2-10-staff-management.postman_collection.json`](../api/dt2-10-staff-management.postman_collection.json).

---

## 3. Ranh giới kiến trúc, Handoff & Rollback

- **Tích hợp với những PR đã merge vào develop:** #7 (Auth BE), #17 (Auth FE), #14 (Customer). Không dùng trạng thái PR chưa merge để đánh giá tính đúng của code hiện tại. CI [44268573892](https://github.com/DTU-TEAM-2/smart-cafe-management/actions/runs/44268573892) đã chạy trên synthetic merge `38152af` = Staff `f5226c8` + `develop@b96a821`: **295/295 Backend tests PASS**, **5/5 CI jobs PASS**. Trên kết quả merge, GET `/api/v1/staff/dashboard` cho ADMIN/STAFF; CRUD và avatar API dưới `/api/v1/staff` chỉ ADMIN; các route và filter Customer của PR #14 vẫn được bảo toàn. Đây là kiểm chứng CI, chưa thay thế nghiệm thu database/Cloudinary/SMTP production.
- **Onboarding nhân viên — Chốt Phương án (B) Onboarding thủ công qua Forgot Password (DT2-2):**
  - **Quyết định nghiệm thu:** Tác giả và PO thống nhất áp dụng **Phương án (B)** cho phase backend-only của PR #11: Sử dụng luồng Quên mật khẩu đã merge của DT2-2 để nhân viên thiết lập mật khẩu lần đầu; không tự tuyên bố hệ thống có tính năng tự động gửi email lời mời (auto email invitation).
  - **Quy trình vận hành chuẩn 5 bước:**
    1. *Admin tạo tài khoản:* Admin gọi `POST /api/v1/staff`. Backend tạo user với `status = 'ACTIVE'`, `must_change_password = 1`, sinh mật khẩu ngẫu nhiên an toàn và lưu dạng BCrypt hash (bảo đảm không bao giờ trả về hoặc log mật khẩu plaintext).
    2. *Admin thông báo:* Admin thông báo cho nhân viên qua email đã đăng ký.
    3. *Nhân viên yêu cầu cấp OTP:* Nhân viên truy cập luồng Quên mật khẩu (`POST /api/v1/auth/forgot-password`) bằng email để nhận OTP qua SMTP.
    4. *Xác thực & Thiết lập mật khẩu:* Nhân viên xác thực OTP (`POST /api/v1/auth/verify-otp`) và đặt mật khẩu mới (`POST /api/v1/auth/reset-password`).
    5. *Đăng nhập lần đầu:* Nhân viên đăng nhập vào hệ thống (`POST /api/v1/auth/login`) và truy cập các chức năng được phân quyền.
  - **Ranh giới tích hợp:** Độc lập với luồng backend CRUD của DT2-10. Tính năng tự động phát hành email kích hoạt kèm token dùng 1 lần (Option A) sẽ được cân nhắc ở giai đoạn tích hợp E2E tiếp theo nếu PO yêu cầu.
- **Kế hoạch Rollback an toàn:**
  - Tuyệt đối **KHÔNG DROP TABLE** `staff_profiles` khi hệ thống đã có dữ liệu thực tế vì sẽ gây mất mát dữ liệu hồ sơ nhân sự không thể phục hồi.
  - Quy trình rollback an toàn khi phát sinh sự cố: (1) Revert code backend về commit trước đó (phiên bản ứng dụng đã kiểm chứng), (2) Khóa endpoint `/api/v1/staff/**` qua cấu hình Security / routing nếu cần, (3) Dữ liệu trong bảng `staff_profiles`, `users` và các liên kết lịch sử được bảo toàn nguyên vẹn; nếu cần khôi phục dữ liệu, phối hợp DB owner kiểm tra backup và lập kế hoạch đối soát dữ liệu phát sinh sau backup.

- **Tách ranh giới Transaction và SMTP I/O trong Password Recovery (`OtpService`):**
  - `initiatePasswordResetOtp()` dùng `TransactionTemplate` (propagation `REQUIRED`; `AuthController` không mở outer transaction) để thực hiện rate-limit, locking read `users` theo PK, kiểm tra `ACTIVE`/email và lưu challenge. Sau commit, chỉ gửi SMTP nếu user, email và challenge của lượt gửi đầu tiên còn khớp.
  - `resendOtp()` tách thành 3 pha trên schema `auth_otp_challenges` hiện hành (không thay đổi DDL):
    1. **Phase 1 — Reserve (short TX via `TransactionTemplate` `REQUIRED`):** Khóa dòng challenge (`FOR UPDATE`), plain read `users` (không khóa `users` để tránh chu trình khóa với `StaffService.updateStaff`), kiểm tra `used_at`, expiry, cooldown, `sends < 3` và SMTP readiness; tăng `sends`, cập nhật `next_send_at`, đặt `delivery_status = 'PENDING'` (cho challenge thực) và `last_error_code = NULL`, sau đó commit để giải phóng row lock và Hikari connection.
    2. **Phase 2 — Dispatch (ngoài TX/lock):** Đọc lại user, email, `used_at`, hạn OTP và `sends`. Chỉ gửi SMTP nếu `sends` còn bằng giá trị lượt gửi vừa giữ chỗ. Bỏ qua lượt đã thu hồi hoặc đã được lượt mới thay thế. Dummy challenge giữ nguyên hành vi trung tính và không gửi mail.
    3. **Phase 3 — Finalize (short CAS TX via `TransactionTemplate` `REQUIRED`):** Kiểm tra plain read user binding và gọi `recordOtpDelivery` (`WHERE id = ? AND used_at IS NULL AND sends = ? AND expires_at > ?`). Nếu SMTP thất bại, giữ nguyên `code_hash` cũ và ghi `delivery_status = 'FAILED'`, `last_error_code = 'SMTP_DELIVERY_FAILED'`. Nếu challenge bị `Staff PUT` thu hồi, `verifyOtp` tiêu thụ, hết hạn hoặc superseded trong lúc gửi mail (`updated == 0` kiểm tra lại qua locking read `findOtpChallengeByIdForUpdate`), thực hiện no-op an toàn để không hồi sinh credential.
  - **Giới hạn và trạng thái bất định:** Timeout SMTP hiện cấu hình riêng lẻ 5000 ms cho connection/read/write (không phải tổng timeout cho cả quá trình gửi). Nếu SMTP đã gửi nhưng DB Finalize lỗi hoặc process crash giữa Phase 1 và Phase 3, hệ thống **không tự rollback quota `sends` đã commit**, **không tự retry mù**, **không đánh dấu `SENT`**, giữ nguyên `code_hash` cũ và để challenge tiếp tục có thể verify bằng mã cũ (nếu còn hạn) hoặc resend lại sau cooldown 60s (nếu `sends < 3`), hoặc tạo yêu cầu `/forgot-password` mới. Quy ước hậu kiểm trong `OtpRetryLifecycleIntegrationTest.failedDatabaseTransitionRollsBackResend` (nhánh `deliveryTransition=true`) được cập nhật theo semantic bảo toàn quota này để Auth owner xem xét xác nhận.

### Nghiệm thu hợp đồng OTP khi finalize lỗi

**Chờ Auth owner xác nhận.** [Hợp đồng DT2-2 đã merge](../handover/dt2-2-otp-retry-lifecycle.md) yêu cầu rollback quota và cooldown nếu cập nhật kết quả gửi lỗi. PR #11 trả HTTP 500 và giữ reservation đã commit. Khi finalize thất bại trước commit, hash cũ còn nguyên. Khi kết quả commit chưa xác định, cần đối soát trạng thái trước khi kết luận mã nào có hiệu lực.

- Giữ reservation như PR đang làm giúp nhả khóa trước SMTP và hạn chế gửi lại khi thư có thể đã ra ngoài. Một lần gửi vẫn tính vào quota dù người dùng có thể chưa nhận được mã mới dùng được.
- Nếu giữ yêu cầu hoàn quota của hợp đồng cũ, cần chốt cách xác định khi nào bù reservation an toàn, nhất là khi SMTP hoặc commit có kết quả chưa rõ. Cách này cần thiết kế và kiểm thử riêng để tránh gửi trùng hoặc hoàn quota của lượt mới.

Nguồn đối chiếu là [thread review OTP](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/11#discussion_r4691836900).

### Định danh trong JSON

Response danh sách, chi tiết và upload trả ID nhân viên và ID ảnh dưới dạng chuỗi thập phân. Giữ nguyên chuỗi khi đưa vào URL hoặc gửi `avatarMediaId`. Request nhận chuỗi chữ số hoặc số nguyên JSON dương trong phạm vi `BIGINT` có dấu, và nhận `null` khi không có ảnh. Giá trị như `501.9`, `5.01e2`, chuỗi rỗng hoặc ID vượt phạm vi trả HTTP 400.

`birthday` là ngày theo định dạng `YYYY-MM-DD` hoặc `null`. Ngày ngoài phạm vi `DATE` được MySQL hỗ trợ (`1000-01-01` đến `9999-12-31`) trả HTTP 400 khi tạo hoặc cập nhật. API và JDBC giữ nguyên ngày khi ghi và đọc. [MySQL 8.0 Reference Manual](https://dev.mysql.com/doc/refman/8.0/en/datetime.html)
