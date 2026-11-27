# CF-04 / DT2-3 / DT2-28 — Frontend

Ngày triển khai: 18/11/2026. Nhánh `feature/dt2-3_personal-account-frontend`, tách từ `origin/develop@e3ce5b1df9475f3bde709828ad0ccdead2fa19ef`. [Backend PR #19](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/19) được xác minh `merged=true` qua GitHub connector và có trong ancestry của nhánh. Không tiếp tục phát triển trên nhánh Backend đã merge.

## Nguồn và quyết định thiết kế

- [DT2-3](https://duymanhdnvn20092005.atlassian.net/browse/DT2-3), [DT2-25](https://duymanhdnvn20092005.atlassian.net/browse/DT2-25), [DT2-28](https://duymanhdnvn20092005.atlassian.net/browse/DT2-28): đã đọc lại description, status, attachment và các link được connector trả về. Jira còn mô tả Admin-only cũ; QA #8, quyết định owner và contract hiện hành cho phép ADMIN/STAFF tự sửa đúng ba trường.
- [SRS v4.1](https://docs.google.com/document/d/1Mhaq4ttf8d-uiPbv2gSnfHK0AhCu4qpc/edit): đọc lại nội dung 6.1.2–6.1.3 và Header qua Drive connector. [Project Plan](https://docs.google.com/spreadsheets/d/1nT1hxrsgVdBxTbORlTwohwzmbvujldYR/edit) bổ sung inline update và 30 ngày. [QA #8](https://docs.google.com/spreadsheets/d/1m2vnTOdGWivwqokRiJYOpWmY2u3G_o-U/edit): tên/địa chỉ/SĐT của STAFF, quyền/lương thuộc quản trị nhân viên.
- [Yêu cầu Backend đã xác nhận](dt2-3-requirements.md), [bàn giao Backend](dt2-3-backend.md), [OpenAPI](../api/dt2-3-profile.yaml), [HTTP examples](../api/dt2-3-http-examples.json), [HTTP errors](../api/dt2-3-http-errors.json), [review matrix](dt2-3-review-matrix.md), [follow-up](../reports/dt2-3-review-follow-up.md).
- [Figma chính thức](https://www.figma.com/design/7iFkUz20p6rE2fPC9Xn8rp): file key `7iFkUz20p6rE2fPC9Xn8rp`. Đã thử metadata và design context của page `0:1`; MCP trả hết quota Starter, `whoami` xác nhận Starter/View. Page Authentication `5:2` là địa chỉ trong yêu cầu, chưa đọc được context. Computer Use dừng vì không xác minh được URL Chrome trên Windows; không tiếp tục vượt qua hạn chế đó.
- **Quyết định bổ sung của owner trong chat 18/11/2026:** owner chưa tìm thấy prototype riêng Profile/Update Profile/Change Password, cho phép thiết kế bổ sung theo LECKER hiện hành. Dùng CSS/component desktop trong repo làm chuẩn, không dùng bố cục login hai cột/ảnh quán cho Profile. Không chỉnh Figma gốc. **Ngày 19/11/2026 owner đã chấp nhận giao diện hiện tại và cho phép commit, push feature branch, tạo PR vào develop.** Đây không phải approval code độc lập hoặc quyền merge.
- Hai ảnh đính kèm của owner có cùng SHA-256 `91c6e2ea366f18e27a7926c197f9459939abb3d7593a29d19bde8af6b2a24aec`, cùng thể hiện bảng Authentication. Lưu một bản [reference-auth.png](evidence/dt2-28/reference-auth.png). Không có ảnh desktop thứ hai khác biệt để đo đối chiếu.

**Frame Profile đã xác minh: không có.** Đây là thiết kế bổ sung DT2-3 dựa trên design system LECKER hiện hành, không phải bản chuyển đổi pixel-perfect từ frame Figma. Không đoán frame/node ID hoặc tỷ lệ giống thiết kế. Viewport thiết kế bổ sung chính là 1440×900.

## Thành phần và luồng

Entry point thực tế là `frontend/src/main.jsx` → `AuthProvider` → `HomeShell`. `App.jsx` cũ không được dùng để tạo một router/layout thứ hai.

| Thành phần | Trách nhiệm |
| --- | --- |
| `account/AccountPage.jsx` | Trang hồ sơ, trạng thái tải/lỗi, thông báo thành công, phối hợp form |
| `account/ProfileView.jsx` | Thẻ nhận diện, tám trường SRS, read-only hoặc inline edit |
| `account/ChangePasswordForm.jsx` | Ba password fields, show/hide độc lập, validation, xóa state khi rời form |
| `account/PasswordExpiryNotice.jsx` | Warning từ cờ server, liên kết đến form đổi mật khẩu |
| `account/AccountFeedback.jsx` | Lỗi trường, summary có liên kết/focus và thông báo an toàn |
| `account/LeaveProfileDialog.jsx` | Xác nhận rời bản nháp, Escape và phục hồi focus |
| `account/useAccountProfile.js` | GET/PATCH/password lifecycle, snapshot/version, bỏ response cũ, chống gửi lặp |
| `account/accountApi.js`, `profileValidation.js` | API cố định `/me`, kiểm contract, mapping lỗi và validation |
| `layout/AccountAvatar.jsx` | Avatar từ API hoặc fallback chữ cái hiện hành; lỗi ảnh không dùng ảnh tài khoản khác |
| `auth/AuthContext.jsx`, `authApi.js` | Một phiên xác thực, thay bearer, lưu password status và bảo vệ generation |

Tái sử dụng SiteLayout/Header/Footer, logo chữ LECKER, font local, palette `--surface/--gold/--muted/--serif`, button chính và hash navigation. CSS mới được scope trong `personnel-*`/`account-*`; `styles.css`, font/assets cũ, Sales/Menu và Backend không bị sửa. Layout chung hiện tại của Home/Profile không có navigation sidebar; thẻ nhận diện bên trái là nội dung hồ sơ, không tạo thêm một shell quản trị. Customer giữ trang tài khoản cơ bản hiện có, không dùng form/API nhân sự; `#account-password` bị chặn theo role/server.

Routes: `#account` và `#account-password`. Header mở đúng hồ sơ hiện tại, không nhận user ID/username/role từ URL để chọn tài khoản. `GET /api/v1/profiles/me` luôn là nguồn dữ liệu hồ sơ. Home account là nguồn Header ban đầu; Profile response mới đồng bộ Header và chặn Home response cũ ghi đè. Feed thông báo vẫn polling theo recipient; thay token remount scope và hủy request/timer cũ. News/WebSocket chưa merge không được nhập vào nhánh này; không tạo socket riêng.

PR Customer FE #20 hiện cùng sửa `HomeShell.jsx`; đã kiểm danh sách diff remote để ghi nhận điểm tích hợp. Không nhập App/Customer modules từ nhánh đó. Khi hai PR được review để tích hợp, owner cần giữ cả personnel routes/token lifecycle và guest session/CSRF của Customer. Skip-link của SiteLayout chỉ focus main, không đổi hash hoặc làm mất draft.

## Cập nhật hồ sơ

- Vào trang ở chế độ xem. Chỉ `name/address/phone` chuyển thành input khi chọn Cập nhật; capability `canUpdateProfile` cũng phải được server cho phép.
- Capture nguyên Profile/version tại thời điểm bắt đầu edit. Draft chỉ chứa trường thực sự được nhập; nullable field chưa chạm được omitted.
- PATCH chỉ gửi trường thay đổi và version chuỗi của snapshot. Không gửi trường bảo vệ, không chuyển ID/salary/version sang Number, không gửi PATCH nếu không đổi. Lương hiển thị bằng `Intl.NumberFormat` với BigInt; birthday chỉ tách chuỗi date-only.
- Validate whitespace theo Java `isWhitespace OR isSpaceChar`, giới hạn code points 160/255/20, không thêm phone regex, không trim dữ liệu hợp lệ.
- Cancel bỏ draft, không gọi API. Mọi draft khác snapshot, kể cả draft chưa hợp lệ, đều được bảo vệ khi rời trang; beforeunload dùng cơ chế trình duyệt. Dialog cũng giải thích request đã gửi có thể đã được server xử lý.
- `PROFILE_CONFLICT`: bỏ snapshot/draft cũ, tải lại GET, yêu cầu bắt đầu edit mới, không replay. `PROFILE_INCOMPLETE`: giữ draft, hướng dẫn liên hệ quản trị nhân sự, không tự tạo personnel/lương.
- Ref lock chặn submit lặp ngay trong cùng event tick; request GET cũ bị hủy trước mutation. Chỉ response còn thuộc scope/session hiện hành được cập nhật UI.

## Đổi mật khẩu và phiên

POST `/api/v1/profiles/me/password` chỉ gửi `currentPassword/newPassword/confirmNewPassword`. Không dùng Forgot/Reset endpoint và không gửi identity. Mật khẩu được che mặc định; autocomplete là `current-password/new-password`; không ghi vào URL/storage/log/evidence.

Validation giữ nguyên password, current/confirm tối đa 256 UTF-16 units, new 8–64 units và tối đa 72 UTF-8 bytes, có Unicode uppercase/lowercase/digit tương ứng PasswordPolicy Java, khác current và confirm khớp chính xác. Backend vẫn xác định kết quả cuối.

Thành công: cập nhật bearer/state/storage đồng bộ, tăng session generation trước khi phát auth-change, lưu password status, hủy/bỏ qua scope cũ, rồi Profile/Header tải bằng bearer mới. Quay về `#account`, xóa password fields và hiển thị success. Old 401 hoặc response đăng nhập/logout trễ không thể làm mất/khôi phục phiên mới. Login capture session ngay khi bắt đầu và kiểm lại snapshot trước commit; regression ngày 19/11 tái hiện rồi sửa response login cũ ghi đè phiên đã được đồng bộ qua auth event. `/auth/me` không chứa expiry nên không được xóa status top-level từ Login/Profile/password response. User cache hỏng không làm mất bearer hợp lệ; Header dùng AuthContext trực tiếp, không có bộ đọc token độc lập bên ngoài Provider. Nếu browser từ chối storage, phiên trong bộ nhớ vẫn dùng token mới; persistence qua reload không thể bảo đảm trong trường hợp đó.

Lỗi được map bằng code/status, không render raw detail/SQL. Wrong current/mismatch/reuse/policy đặt đúng field. `PASSWORD_CONFLICT` yêu cầu xác thực lại. Mất mạng/timeout hoặc response thành công không đủ replacement contract được coi là chưa rõ kết quả: xóa password inputs, chặn gửi lại trên form, hướng dẫn đăng nhập bằng mật khẩu mới/cũ hoặc recovery. Không tự retry POST.

Warning 30 ngày chỉ dùng `mustChangePassword/passwordChangedAt/passwordExpiresAt` server trả; không tính deadline bằng đồng hồ browser và không khóa business routes. Warning mất khi server trả false sau đổi password.

## Ảnh và kiểm chứng

Tất cả ảnh actual dùng synthetic data. [Kết quả kiểm thử](../reports/dt2-28-frontend-test-results.md) tách HTTP mock, real browser E2E, API/Newman, MySQL và giới hạn.

| Trạng thái | Ảnh actual |
| --- | --- |
| ADMIN / STAFF | [ADMIN](evidence/dt2-28/profile-admin.png), [STAFF](evidence/dt2-28/profile-staff.png) |
| Inline edit / update success | [Edit](evidence/dt2-28/profile-edit.png), [Success](evidence/dt2-28/profile-success.png) |
| Password / password success | [Form](evidence/dt2-28/change-password.png), [Success](evidence/dt2-28/password-success.png) |
| Expiry / validation / conflict | [Warning](evidence/dt2-28/profile-expiry.png), [Validation](evidence/dt2-28/profile-validation.png), [409](evidence/dt2-28/profile-conflict.png) |
| Nullable / loading / error | [Empty](evidence/dt2-28/profile-empty.png), [Loading](evidence/dt2-28/profile-loading.png), [503](evidence/dt2-28/profile-error.png) |
| Responsive | [1920×1080](evidence/dt2-28/profile-1920x1080.png), [1440×900](evidence/dt2-28/profile-1440x900.png), [1366×768](evidence/dt2-28/profile-1366x768.png), [1024×768](evidence/dt2-28/profile-1024x768.png), [768×1024](evidence/dt2-28/profile-768x1024.png), [390×844](evidence/dt2-28/profile-390x844.png) |
| Password mobile | [390px](evidence/dt2-28/password-mobile.png) |
| Backend/MySQL thật | [ADMIN](evidence/dt2-28/live-admin-profile.png), [STAFF](evidence/dt2-28/live-staff-profile.png), [Password](evidence/dt2-28/live-password-success.png), [Expiry](evidence/dt2-28/live-expiry.png), [409](evidence/dt2-28/live-conflict.png) |

Đã đối chiếu ảnh browser với palette/font/component đang chạy trong repo và ảnh Authentication được cung cấp. Đã sửa nút mobile xuống dòng, tăng chữ field và badge; không dùng screenshot làm UI/background. Không có ảnh Profile Figma chính thức để làm phép so sánh pixel; không cập nhật baseline mù quáng.

Ảnh update success được chụp lại từ đầu trang với reduced-motion sau khi focus/scroll đã ổn định, tránh artifact của sticky Header trong full-page capture khi trang đang cuộn. Không chỉnh pixel của screenshot hoặc thay đổi runtime để tạo ảnh.

## Handoff và publication

Không sửa DDL/API Backend, không ghi shared `smart_cafe`, không nhập code từ PR #11/#15/#18/#20 chưa merge. Canonical schema trên develop vẫn `20261113.1`, SHA-256 `84cb9f2e99073ea73b117e828dc7f144928052a8f8345cde6f903b82280e4b5e`. Real browser runner chỉ provision/seed/drop catalog do chính nó tạo, đúng hậu tố `_test`, bằng native Flyway và fixture synthetic hiện có. DBA rollout của database dùng chung vẫn thuộc owner tương ứng.

Skills thực sự áp dụng:

| Phase | Identifier và SKILL.md |
| --- | --- |
| Requirements/workflow | `cafe-workflow`, `cafe-requirements`: `.agents/skills/<identifier>/SKILL.md` |
| React/a11y/design | `vercel-react-best-practices`: `.agents/skills/react-best-practices/SKILL.md`; `fixing-accessibility`, `ui-ux-pro-max`: `.agents/skills/<identifier>/SKILL.md`. Search UX focusable error summary được dùng cho lỗi form; giữ design system hiện hữu. |
| Verification/review | `cafe-quality`, `cafe-review`: `.agents/skills/<identifier>/SKILL.md` |
| Decision record | `domain-modeling`: `.agents/skills/domain-modeling/SKILL.md`; bổ sung quyết định owner vào ADR-013 hiện hữu, không tạo thêm glossary/ADR cho lựa chọn UI có thể đảo ngược. |
| External skills | `ponytail` tại `C:/Users/khatr/.codex/skills/ponytail/SKILL.md`; `figma-design-to-code` trong plugin Figma 20.0.0; `api-testing` trong Postman plugin 0.9.0. Dùng runner Newman hiện có; không thay bằng mock. Graphify không có graph sẵn, dùng đọc/search trực tiếp theo fast lookup guidance. |

Owner đã duyệt UI và cho phép publication ngày 19/11/2026. Lượt fetch/prune và merge `origin/develop` cuối trước kiểm thử trả `Already up to date` tại base `e3ce5b1df9475f3bde709828ad0ccdead2fa19ef`, không có conflict hoặc remote feature phải tích hợp. Không nhập PR chưa merge. [Bản mô tả PR](dt2-28-pr-description.md) và [machine evidence](../reports/dt2-28-frontend-evidence.json) ghi phạm vi đã kiểm; trạng thái CI sau publication được xác minh trên HEAD PR thực tế. Năm tệp untracked từ Backend có sẵn được giữ nguyên ngoài commit. Không sửa Jira/Figma, self-approve, resolve discussion, merge hoặc deploy. Hai reviewer độc lập vẫn là điều kiện bắt buộc.
