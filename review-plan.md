# PR Review Fix Plan: PR #11 (DT2-10 Backend Quản lý nhân viên - Đợt 2)

## Context
- **PR**: [#11: feat(staff): CF-10 implement DT2-10 staff management backend](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/11)
- **Repository / branch**: `DTU-TEAM-2/smart-cafe-management` / `feature/dt2-10-staff-management`
- **Target base branch**: `develop` (theo Git Flow chuẩn vừa merge PR #12)
- **Head commit hiện tại**: `0b6a74b`
- **Reviewer**: `tranhoainam298` (Review ID `5885015151`, ngày 13/11/2026)

---

## Review comments mới cần xử lý

| # | Thread ID / Vị trí | Người review | Yêu cầu cốt lõi | Phân loại / Ưu tiên | Phương án xử lý | Trạng thái |
|---|---|---|---|---|---|---|
| 1 | `4536372577` (reply `4558851326`)<br>`StaffService.java:212` | `tranhoainam298` | Mật khẩu tạo ngẫu nhiên nhưng người nhận không có cách biết để đăng nhập (chưa gửi email, token kích hoạt, chưa set `must_change_password=1` trong DB). | Bảo mật / Onboarding<br>**P1** | 1. Khi `createStaff`, set `must_change_password = 1` trong câu `INSERT INTO users`.<br>2. Xác định rõ ranh giới module Auth (phụ trách gửi email OTP/kích hoạt): tạo challenge trong bảng `auth_otp_challenges` (nếu có contract) hoặc trả về cờ yêu cầu đổi mật khẩu lần đầu an toàn; tuyệt đối không log hoặc trả plaintext mật khẩu trong API.<br>3. Bổ sung test kiểm tra `must_change_password = 1` sau khi tạo. | Đã giải quyết & Đã kiểm thử |
| 2 | `4536372608` (reply `4558851657`)<br>`StaffService.java:269-280` | `tranhoainam298` | 1. Chỉ chặn Admin tự hạ quyền, chưa chặn Admin A hạ quyền Admin B thành STAFF (trái SRS Q&A #7).<br>2. `SELECT COUNT(*)` không khóa đồng bộ gây race condition: 2 request cấp ADMIN song song có thể tạo thành 3 Admin. | Phân quyền & Concurrency<br>**P1** | 1. Trong `updateStaff`: chặn **mọi** thao tác chuyển `role` từ `ADMIN` sang `STAFF` (dù A sửa A hay A sửa B), trả về `400 Bad Request`.<br>2. Dùng MySQL Named Lock `SELECT GET_LOCK('smartcafe_admin_role_lock', 10)` và `RELEASE_LOCK(...)` trong transaction để serialize việc tạo/nâng cấp Admin, bảo đảm bất biến tối đa 2 Admin kể cả khi gọi đồng thời.<br>3. Bổ sung test: A hạ quyền B bị từ chối 400; test song song (concurrency) cấp Admin. | Đã giải quyết & Đã kiểm thử |
| 3 | `4558861940`<br>`StaffValidation.java:110` | `tranhoainam298` | Lương (`salary`) 19 chữ số lọt validation gây lỗi JDBC overflow vì cột DB là `DECIMAL(18,0)`. | Validation / Database<br>**P2** | 1. Thêm giới hạn `MAX_SALARY = 999999999999999999` (18 chữ số) vào `StaffValidation.java`.<br>2. Bổ sung test kiểm tra gửi lương 19 chữ số bị từ chối với `400 Bad Request`. | Đã giải quyết & Đã kiểm thử |
| 4 | `origin/develop` sync & CI | `tranhoainam298` | Đồng bộ nhánh với `origin/develop` mới (chứa PR #12: Git Flow, CI workflow cho `develop`, clock fix cho test Sales), cập nhật mô tả PR và tài liệu backend report. | Quy trình Git Flow & CI<br>**P2** | 1. Chạy `git merge origin/develop` để nhận cấu hình CI cho `develop` và các bản sửa lỗi hệ thống.<br>2. Cập nhật số lượng test trong `docs/reports/dt2-10-backend.md` và PR description (khớp 23+ ca test). | Đã giải quyết & Đã kiểm thử |

---

## Proposed changes

### 1. Files dự kiến thay đổi:
- `backend/src/main/java/vn/codegym/smartcafe/staff/StaffValidation.java`:
  - Thêm `MAX_SALARY = new BigDecimal("999999999999999999")` và kiểm tra `salary.compareTo(MAX_SALARY) <= 0`.
- `backend/src/main/java/vn/codegym/smartcafe/staff/StaffService.java`:
  - `createStaff`: set `must_change_password = 1` trong `INSERT INTO users`.
  - `createStaff` & `updateStaff`: bọc kiểm tra giới hạn 2 Admin trong MySQL lock `GET_LOCK('smartcafe_admin_role_lock', 10)` và `RELEASE_LOCK`.
  - `updateStaff`: từ chối mọi trường hợp đổi `role` từ `ADMIN` sang `STAFF` (không phân biệt self hay cross-admin).
- `backend/src/test/java/vn/codegym/smartcafe/staff/StaffControllerTest.java` & `StaffHttpReportTest.java`:
  - Bổ sung test: Lương 19 chữ số $\rightarrow$ 400 Bad Request.
  - Bổ sung test: Admin A hạ quyền Admin B $\rightarrow$ 400 Bad Request.
  - Bổ sung test: Concurrency gọi đồng thời tạo Admin thứ 2 và thứ 3 $\rightarrow$ bảo đảm chỉ đúng 2 Admin được tạo.
  - Kiểm tra `must_change_password = 1` của nhân viên mới.
- `docs/reports/dt2-10-backend.md`: Cập nhật số lượng testcase và bằng chứng mới.

### 2. Kế hoạch kiểm thử:
- `mvnw test -Dtest=StaffControllerTest,StaffHttpReportTest`
- `python scripts/render-staff-api-report.py`
- Kiểm tra Governance: `node scripts/skills.mjs check; node --test scripts/skills.test.mjs; git diff --check`

---

## Open questions and risks
- **Gửi email mật khẩu / Activation token**: Quyết định SRS Q&A #1 quy định gửi email mật khẩu ban đầu để xác minh và đổi mật khẩu. Tuy nhiên hạ tầng gửi mail (SMTP) hiện chưa được cấu hình ở backend của sprint này và thuộc trách nhiệm của module Auth. Việc đặt `must_change_password = 1` đảm bảo an toàn rằng ngay khi user đăng nhập, hệ thống sẽ bắt buộc đổi mật khẩu. Dev có đồng thuận với phương án này không?

---

## Progress and verification
- [x] Dev approved plan (Điểm kiểm soát 1)
- [x] Changes implemented
- [x] Checks run: command and result
- [x] PR/MR replies: posted or draft
- [ ] Dev final confirmation (Điểm kiểm soát 2)
