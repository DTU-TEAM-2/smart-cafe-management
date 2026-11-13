# PR Review Fix Plan: PR #11 (DT2-10 Backend Quản lý nhân viên - Đợt 3)

## Context
- **PR**: [#11: feat(staff): CF-10 implement DT2-10 staff management backend](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/11)
- **Repository / branch**: `DTU-TEAM-2/smart-cafe-management` / `feature/dt2-10-staff-management`
- **Target base branch**: `develop`
- **Head commit**: `25c6ff3636c76e60afa7b4d7fd8dcede6b745ea6`
- **Reviewers**: `tranhoainam298`, `NV-DuyManh` (Review mới nhất từ `tranhoainam298` ngày 13/11/2026 trên commit `25c6ff3`)

---

## Review comments

| Thread ID / Vị trí | Người review | Yêu cầu cốt lõi | Phân loại / Ưu tiên | Phương án xử lý | Trạng thái |
|---|---|---|---|---|---|
| `PRRT_kwDOVkpoZs628VON`<br>`StaffService.java:324-334` | `tranhoainam298` | **[P1 mới] Hai ADMIN có thể xóa chéo khiến hệ thống mất toàn bộ ADMIN.** `deleteStaff` chỉ chặn tự xóa chính mình. Với 2 Admin A và B gửi `DELETE /staff/{B}` và `DELETE /staff/{A}` đồng thời, cả hai có thể UPDATE thành `INACTIVE`, vi phạm bất biến luôn có ít nhất 1 ADMIN ACTIVE (SRS Q&A #7). | Phân quyền & Concurrency<br>**P1** | **Phương án đề xuất:**<br>1. Trong `deleteStaff`, nếu `target.role()` là `ADMIN`:<br>- **Lựa chọn A (Khuyến nghị theo SRS Q&A #7):** Cấm hoàn toàn việc xóa tài khoản có role `ADMIN` qua API Staff (`DELETE /api/v1/staff/{id}`), ném `400 Bad Request` ("Không được phép xóa tài khoản Quản trị viên (ADMIN) theo quy định hệ thống."). Tương tự như quy định cấm hạ quyền ADMIN đã được duyệt.<br>- **Lựa chọn B (DB Locking kiểm tra ≥ 1 ADMIN):** Thực hiện `SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND status = 'ACTIVE' FOR UPDATE`. Nếu `adminCount <= 1`, ném `400 Bad Request` ("Hệ thống phải duy trì ít nhất 1 Quản trị viên (ADMIN) hoạt động."). Khi A và B xóa chéo song song, lock DB sẽ tuần tự hóa hai transaction, một request thành công và một request bị chặn.<br>2. Bổ sung testcase tự động (TC-28) kiểm tra concurrency hai Admin xóa chéo hoặc kiểm tra chặn xóa Admin, bảo đảm bất biến hệ thống luôn còn ít nhất 1 ADMIN ACTIVE. | Chờ Dev duyệt |
| `PRRT_kwDOVkpoZs62HO42`<br>`StaffService.java:215-230` | `tranhoainam298` | **[P1 mở] Onboarding nhân viên / mật khẩu ngẫu nhiên.** Đã dùng `SecureRandom` + BCrypt và `must_change_password=1`, nhưng nhân viên không có cách nào biết mật khẩu để đăng nhập lần đầu. Reviewer yêu cầu tích hợp luồng invite/verify/reset token từ module Auth hoặc gửi email an toàn, test E2E. | Bảo mật & Onboarding<br>**P1** | **Phương án đề xuất:**<br>1. Làm rõ ranh giới kiến trúc: Repository hiện tại chưa có module Auth / Mail sender (chưa có dependency SMTP và cấu hình gửi mail; module Auth thuộc subtask riêng).<br>2. Để đáp ứng tối đa hợp đồng cơ sở dữ liệu (`auth_otp_challenges`), khi `createStaff` thành công, ta có thể ghi một bản ghi challenge onboarding vào bảng `auth_otp_challenges` (nếu Dev muốn liên kết trước cho worker email tương lai), HOẶC phản hồi giải trình rõ ranh giới kỹ thuật với reviewer: DT2-10 chỉ quản lý hồ sơ nhân viên và tạo trạng thái kích hoạt an toàn (`must_change_password=1`, password ngẫu nhiên chống brute-force); việc phát hành email chứa token một lần thuộc trách nhiệm của module Auth/Email Service ở sprint kế tiếp.<br>3. Không bao giờ log hay trả plaintext password ra response API. | Chờ Dev quyết định |
| Review Summary Body<br>`PR #11 description` | `tranhoainam298` | Đồng bộ mô tả PR #11 từ số liệu cũ (19/19) sang kết quả thực tế hiện tại (27/27 scenarios, 13 MockMvc); lưu ý workflow Postman. | Tài liệu & PR hygiene<br>**P2** | Cập nhật PR description trên GitHub phản ánh chính xác 27/27 scenarios HTTP test và kết quả CI mới nhất. | Đã lên kế hoạch |
| Review Summary Body<br>`StaffImageService.java` | `tranhoainam298` | `@Transactional` hiện bao cả HTTP request Cloudinary. Nên tách external call ra ngoài hoặc dọn dẹp asset orphan nếu DB insert thất bại. | Hiệu năng & Kiến trúc<br>**P2** | Rà soát `StaffImageService` để tách biệt thao tác gọi HTTP Cloudinary với transaction DB lưu `media_assets`. | Đã lên kế hoạch |
| `PRRT_kwDOVkpoZs62HO5D` (Backfill columns)<br>`PRRT_kwDOVkpoZs62HO5I` (Cấm hạ quyền & limit Admin)<br>`PRRT_kwDOVkpoZs62HO5R` (Lọc role CUSTOMER)<br>`PRRT_kwDOVkpoZs62HO5W` (Validate email)<br>`PRRT_kwDOVkpoZs62HO5d` (Image magic bytes)<br>`PRRT_kwDOVkpoZs62HO5i` (TC-13 username rules)<br>`PRRT_kwDOVkpoZs62HO5m` (DDL media_assets)<br>`PRRT_kwDOVkpoZs62Mk7H` (Tràn số offset long)<br>`PRRT_kwDOVkpoZs627Hk3` (Validate salary 18 số) | `tranhoainam298`, `NV-DuyManh` | 9 vấn đề từ các đợt review trước. | P1 / P2 | Đã xử lý hoàn tất trong commit `df69708` và `0b6a74b`. Reviewer `tranhoainam298` tại HEAD `25c6ff3` đã xác nhận và ghi nhận các sửa đổi này đạt yêu cầu. | Đã hoàn thành (Confirmed by Reviewer) |

---

## Proposed changes

### 1. Files dự kiến thay đổi:
- `backend/src/main/java/vn/codegym/smartcafe/staff/StaffService.java`:
  - Trong `deleteStaff(Long id, String currentUsername)`:
    - Kiểm tra `target.role()`: nếu là `ADMIN`, áp dụng giải pháp bảo vệ (Cấm xóa Admin theo Lựa chọn A hoặc kiểm tra `COUNT(*) ... FOR UPDATE > 1` theo Lựa chọn B).
  - Tách gọi external Cloudinary ra ngoài transaction nếu xử lý tối ưu avatar.
- `backend/src/test/java/vn/codegym/smartcafe/staff/StaffHttpReportTest.java` & `StaffControllerTest.java`:
  - Bổ sung ca kiểm thử TC-28: kiểm tra bảo vệ Admin khi xóa (chặn xóa Admin hoặc test concurrency hai request xóa Admin song song đảm bảo luôn còn ít nhất 1 Admin ACTIVE).
- `docs/reports/dt2-10-backend.md`:
  - Cập nhật tài liệu kỹ thuật với kịch bản mới (28 ca test HTTP).
- Cập nhật mô tả PR #11 trên GitHub.

### 2. Các lệnh kiểm thử dự kiến:
```powershell
mvn test -Dtest=StaffControllerTest,StaffHttpReportTest
python scripts/render-staff-api-report.py
node scripts/skills.mjs check; node --test scripts/skills.test.mjs; git diff --check
```

---

## Open questions and risks

1. **Quyết định về xóa tài khoản ADMIN (`deleteStaff`):**
   - **Dev đã chọn Lựa chọn A:** Cấm hoàn toàn việc xóa tài khoản có role `ADMIN` thông qua API Staff (`DELETE /api/v1/staff/{id}`), trả về `400 Bad Request` ("Không được phép xóa tài khoản Quản trị viên (ADMIN) theo quy định hệ thống."). Bổ sung testcase TC-28 (cấm xóa Admin và kiểm tra concurrency hai Admin xóa chéo nhau đồng thời).

2. **Quyết định về Onboarding / Gửi mật khẩu qua Email (Thread #1):**
   - **Dev đồng ý phương án giải trình:** Làm rõ ranh giới trách nhiệm giữa subtask DT2-10 (Staff Backend CRUD, trạng thái bảo mật `must_change_password=1`, `SecureRandom` + BCrypt, không lộ plaintext) và module Auth / Email Service (phụ trách gửi mail kích hoạt/token một lần ở sprint kế tiếp).

---

## Progress and verification
- [x] Dev approved plan (Điểm kiểm soát 1)
- [x] Changes implemented:
  - `StaffService.java`: Chặn xóa tài khoản role ADMIN trong `deleteStaff` (trả về 400 Bad Request).
  - `StaffImageService.java`: Bỏ `@Transactional` ở phương thức `upload()` để không chiếm dụng DB connection trong lúc gọi HTTP external Cloudinary.
  - `StaffControllerTest.java`: Thêm test `adminCannotDeleteOtherAdmin()`.
  - `StaffHttpReportTest.java`: Thêm testcase TC-28 (chặn xóa Admin và kiểm thử Concurrency 2 Admin xóa chéo nhau song song).
  - `docs/reports/dt2-10-backend.md`: Cập nhật số lượng kiểm thử lên 28 scenarios và 14 MockMvc tests.
- [x] Checks run: command and result
  - `mvnw test '-Dtest=StaffControllerTest,StaffHttpReportTest'`: **15/15 PASS (14 MockMvc + 1 Report Runner với 28/28 scenarios PASS 100%)**.
  - `python scripts/render-staff-api-report.py`: Đã cập nhật `docs/reports/dt2-10-api/report.html` và `results.json`.
  - `node scripts/skills.mjs check; node --test scripts/skills.test.mjs; git diff --check`: **9/9 PASS**, 0 lỗi whitespace.
- [x] PR/MR replies: Đã đăng thành công lên Thread #11 (comment `PRRC_kwDOVkpoZs8AAAABD8RYsg`) và Thread #1 (comment `PRRC_kwDOVkpoZs8AAAABD8RZhA`).
- [x] Dev final confirmation (Điểm kiểm soát 2)
- [x] Đã push commit `df6fe96` lên GitHub `origin/feature/dt2-10-staff-management`.
