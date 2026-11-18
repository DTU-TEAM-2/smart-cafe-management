# DT2-3 / DT2-27 — bàn giao Backend cho DT2-28

Ngày 15/11/2026; branch `feature/dt2-3_personal-account-backend`, HEAD/base `ffa4d9527e7924dd0bc9815fefb3034463d03156`, implementation còn local/uncommitted. Xem [yêu cầu và nguồn](dt2-3-requirements.md), [ADR-013](../architecture/decisions.md#adr-013--hồ-sơ-cá-nhân-và-vòng-đời-mật-khẩu-dt2-3-15112026), [OpenAPI](../api/dt2-3-profile.yaml), [ví dụ HTTP thật đã che credentials](../api/dt2-3-http-examples.json), [47 response lỗi HTTP thật](../api/dt2-3-http-errors.json), và [kết quả hardening hiện hành](../reports/dt2-3-hardening.md).

## Contract hiện thực

| API | Thành công | Hành vi |
| --- | --- | --- |
| `GET /api/v1/profiles/me` | 200, Profile | Identity từ bearer/principal, kiểm ACTIVE, role hiện tại và auth_version; chỉ ADMIN/STAFF |
| `PATCH /api/v1/profiles/me` | 200, Profile mới | `version` chuỗi thập phân bắt buộc, ít nhất một trong `name`, `address`, `phone`; cập nhật nguyên tử |
| `POST /api/v1/profiles/me/password` | 200, token và password status | currentPassword/newPassword/confirmNewPassword; token mới chỉ phát sau commit |
| `POST /api/v1/auth/login` | 200, contract Auth mở rộng | `token`, `user`, **passwordChangedAt, passwordExpiresAt, mustChangePassword ở top-level**, không nằm trong user |

QA #8 và quyết định trực tiếp của owner cho phép STAFF tự sửa đúng ba trường tên/địa chỉ/SĐT, giải thích khác biệt với SRS v4.1/Jira cũ. ADMIN dùng cùng allowlist cá nhân. CUSTOMER bị403. Payload chứa `userId`, `username`, `role`, `salary`, `status`, `email`, `birthday`, `gender`, avatar hoặc trường auth bị400 **toàn bộ**, không bỏ qua rồi cập nhật một phần. Quản trị nhân sự/quyền/lương thuộc DT2-10.

PATCH giữ nguyên trường bị omitted; explicit null, chuỗi chỉ khoảng trắng, sai kiểu JSON hoặc version âm/leading-zero/vượt signed BIGINT bị400. Giới hạn Unicode code point: name160, address255, phone20; không tự áp phone regex. `version` trong khoảng `0`–`9223372036854775807`. Home/Profile dùng chung AccountReader; không nhận ID client để đổi đối tượng.

Profile.id, salary và version là **string** để giữ nguyên BIGINT/DECIMAL(18,0). Login.user.id vẫn là JSON number theo Auth hiện hữu; không dùng số đó làm identity cho request hồ sơ. `phone`, `address`, `birthday`, `gender`, `salary`, `avatarUrl` có thể null. Không tạo profile giả hay lương mặc định khi thiếu staff_profiles. GET vẫn200; PATCH có address trả409 PROFILE_INCOMPLETE và rollback toàn bộ nếu thiếu quan hệ. name/phone riêng có thể cập nhật mà không tạo staff_profiles.

PasswordPolicy hiện có: 8–64 Java UTF-16 code units, tối đa72 byte UTF-8, có chữ hoa/thường/số; không trim password. Current/confirmation chỉ nhận chuỗi không rỗng tối đa256 Java UTF-16 code units. `PasswordEncoder.matches` kiểm mật khẩu hiện tại và từ chối reuse. Đổi thành công cập nhật hash/password_changed_at, reset must_change_password, tăng auth_version/version trong transaction; bearer/reset grant cũ mất hiệu lực.

Hạn là đúng30×24 giờ UTC; tại `now >= passwordChangedAt + 30 days` hoặc DB flag=1 thì mustChangePassword=true. Login/Profile cùng tính theo server; **chưa khóa các API nghiệp vụ toàn hệ thống**. Trạng thái cảnh báo không phải giao kèo cưỡng chế đã triển khai.

## FE phải thực hiện

1. Nạp Profile với bearer hiện tại; không gửi userId/role để chọn hồ sơ. Chỉ render input name/address/phone cho ADMIN/STAFF, các trường khác chỉ đọc.
2. Khi lưu, gửi version nhận từ GET và đúng các field cần sửa; lấy response mới để thay local Profile/version, refresh Home/Header từ cùng nguồn. Không dùng salary/ID bằng Number.
3. Sau POST password thành công, **thay token trong AuthContext trước** mọi Profile/Header request; hủy/loại bỏ response từ request dùng token cũ. Tiếp tục trang cá nhân bằng bearer mới. Không dùng bearer cũ và không ghi token/password ra log.
4. Không tự retry POST password khi mất response: kết quả commit có thể đã xảy ra. Yêu cầu xác thực lại bằng mật khẩu mới, sau đó đọc trạng thái server. Nếu chưa commit và mật khẩu mới không đăng nhập được, người dùng có thể đăng nhập bằng mật khẩu cũ/luồng recovery hiện có.
5. `409 PROFILE_CONFLICT`: reload, cho người dùng xem dữ liệu mới rồi quyết định lại; không tự gửi lại payload stale. `PROFILE_INCOMPLETE`: liên hệ quản trị nhân sự, không tự tạo lương/profile. `PASSWORD_CONFLICT`: xác thực lại. `401`: bỏ phiên đã invalid và xác thực lại; `403`: không có quyền. `503`: hiển thị lỗi tạm thời, không suy ra đã ghi thành công.
6. Hiển thị cảnh báo từ mustChangePassword do server trả. Không tự mở khóa hoặc chặn API nghiệp vụ ngoài contract. Không tự tính30 ngày theo lịch/múi giờ browser.

Profile errors dùng application/problem+json:400 INVALID_PROFILE_REQUEST/CURRENT_PASSWORD_INCORRECT/PASSWORD_MISMATCH/INVALID_PASSWORD/PASSWORD_REUSED;401 UNAUTHORIZED;403 FORBIDDEN;409 PROFILE_CONFLICT/PROFILE_INCOMPLETE/PASSWORD_CONFLICT;503 PROFILE_UNAVAILABLE. SQL/concurrency exception được advice Profile ưu tiên cao xử lý, không lộ SQL. Shared filter có thể trả503 sanitized không có code/type; FE phải xử lý theo HTTP status. Login giữ lỗi Auth, ví dụ400 VALIDATION_FAILED và401 UNAUTHORIZED.

Hai mutation Profile dùng bearer header stateless, được miễn CSRF đúng path/method; không thêm cookie authentication. Profile response và lỗi advice, Login thành công có Cache-Control no-store. Không suy ra tất cả lỗi shared security đều cùng cache header.

## Môi trường và ranh giới

DDL20261113.1 không đổi; `verified-db` bật native Flyway/preflight trên schema `_test` mới. `smart_cafe` cấu hình local vẫn legacy13 bảng/0view, thiếu staff_profiles/media_assets; không phải target đã duyệt. DBA phải xác định instance đích, lineage, grants/DEFINER, backup/restore và rollout trước deployment. Không dùng startup seed, không tắt safety để chạy.

Tổ hợp PR #11/#14/#15 chưa đạt integration full-suite; patch nằm [riêng để owner xem xét](../qa/rehearsals/dt2-3/README.md). Không nhập module chưa được duyệt vào nhánh này. Shared Login/current-password admission limit và transport JSON cap chưa được phê duyệt/triển khai; chưa được mô tả là brute-force protected. Chưa lập trình FE, commit/push/PR/Jira Done hay deployment.
