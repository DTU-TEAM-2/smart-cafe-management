# Nhật ký quyết định kiến trúc

Ngày mở vòng mới: 06/11/2026.

| ID | Trạng thái | Nội dung |
| --- | --- | --- |
| ADR-001 | Theo yêu cầu chủ repo | Khởi tạo repo mới với main rỗng; rule/skill được review qua PR trước khi vào main. Không mang code/migration/lịch sử PR cũ sang. |
| ADR-002 | Lịch sử; phần stack được thay bằng ADR-004 ngày 11/11/2026 | Ở PR nền tảng từng đề xuất đánh giá lại Spring/React/MySQL và chưa có application skeleton. Đây không còn là trạng thái stack chờ chốt hiện tại. |
| ADR-003 | Lịch sử 06/11; approval/phạm vi cập nhật tại ADR-012 | NV-DuyManh hoặc haitan2408 quyết định merge qua PR. Approval từng tùy chọn; từ quyết định chủ repo 11/11 yêu cầu 2 người approve, giữ nguyên khi chuyển Git Flow 12/11. Cả nhóm có thể review. Căn cứ cũ [PR #2](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/2); [cấu hình hiện hành](../engineering/github-settings.md). |
| ADR-004 | Chủ repo yêu cầu áp dụng 11/11/2026 theo trao đổi mentor | **BE Spring Boot, FE ReactJS, database MySQL bắt buộc; còn lại tùy chọn.** Nguồn: [ảnh trao đổi Tân Hải do chủ repo cung cấp](../handover/evidence/technology-constraints-2026-11-11.png), chủ repo yêu cầu ghi rule và đồng bộ tài liệu. Áp dụng toàn dự án theo [rule công nghệ](../../.agents/rules/technology-constraints.md); không tự đổi stack, không biến công cụ tùy chọn hoặc version hiện tại thành yêu cầu mới của mentor. |

DT2-1 triển khai Spring Boot/JDBC + React/Vite + MySQL. Stack ba thành phần chính đã được chốt tại ADR-004; JDBC/Vite là công cụ đang dùng, code và lựa chọn cụ thể vẫn qua review. Home chỉ đọc, không tự DDL/seed; CI dùng database test riêng. Quyết định stack không tự phê duyệt nghiệp vụ ranking hoặc auth. Xem [handoff](../handover/dt2-1-home-layout.md).

Quyết định được chấp nhận phải có người chốt, ngày, căn cứ và hệ quả; không dùng bảng này để tự phê duyệt nghiệp vụ.

## ADR-005 — tích hợp menu với mô hình DB và auth chung (11/11/2026)

Chủ repo cung cấp lại sơ đồ DB mới khi đối chiếu cho PR #8; mô hình đích và chênh lệch với DDL local được ghi tại [database contract](database-contract.md#dt2-9--đối-chiếu-ngày-11112026-cho-pr-8). Menu dùng category_id/image_media_id, giá decimal(18,0); URL là trường dẫn xuất, bỏ description không có trong mô hình. Không có migration runtime hay thay đổi DB local. Kiểm thử dùng fixture main nguyên trạng trong schema riêng.

Lựa chọn triển khai để review: tái sử dụng HeaderTokenFilter của main và AUTH_TOKEN_SECRET, kiểm tra quyền từ users; bỏ cơ chế JWT riêng của nhánh menu. Giữ CSRF cho mutation. Không phát hành token, seed tài khoản hoặc triển khai auth mới. Không thay quyền merge tại ADR-003.

## ADR-006 — vòng đời OTP resend trong DT2-2 (12/11/2026)

Nguồn: yêu cầu trực tiếp của chủ repo ngày 12/11/2026 để sửa [P1 PR #7](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/7#discussion_r4545684172); chính sách ưu tiên và phạm vi được ghi tại [bàn giao](../handover/dt2-2-otp-retry-lifecycle.md). Không có yêu cầu gia hạn TTL sau resend trong handoff DT2-2 đang có; lựa chọn sau thực hiện phương án chủ repo yêu cầu ưu tiên.

- `sends` đếm lượt yêu cầu được tiếp nhận: 1 lúc tạo, tối đa 2 resend, bất kể kết quả SMTP. Readiness/configuration failure bị từ chối trước khi tiêu thụ budget cho cả real/dummy.
- `expires_at` cố định từ lúc tạo challenge; resend thành công/thất bại/dummy đều không gia hạn. `expiresInSeconds` của resend là số giây còn lại tại lúc tiếp nhận (làm tròn lên); `now >= expires_at` từ chối resend/verify.
- Giữ transaction và row lock trước SMTP. Mỗi resend hợp lệ reserve budget/cooldown một lần. SMTP thành công mới thay hash/ghi `SENT` và `sent_at`; SMTP lỗi ghi `FAILED`, giữ hash/attempts/used_at/sent_at/hạn cũ. Dummy không gửi email và không giả lập `SENT`.
- Không thêm bảng/cột, migration, outbox hoặc background retry. Lỗi DB/row count bất thường rollback, trả lỗi máy chủ; không bắt như lỗi SMTP. SMTP và DB commit chưa atomic: thư có thể đã được nhận trước khi transaction thất bại.

## ADR-012 — Git Flow bắt buộc (12/11/2026)

Chủ repo yêu cầu áp dụng file git-flow.md vào dự án và luôn tuân thủ từ nay, thay quyết định GitHub Flow main-only trước đây. Nguồn nguyên bản được giữ tại docs/reference/git-flow-original-2026-11-12.md; quy tắc vận hành đã chỉnh cho dự án web tại git-flow.md.

Quyết định: main dành release, develop tích hợp task; feature/bugfix từ develop vào develop; release từ develop vào main; hotfix production từ main/tag vào main. Sau release/hotfix mở PR main → develop để đồng bộ, tránh mất bản sửa khi head tự xóa. Chỉ release khi scope ổn định, không tạo release cho mỗi feature. Thay ví dụ TestFlight/AppStore bằng staging/production web; giữ 2 approval độc lập đã được chủ repo chốt trước đó, không hạ xuống 1 của nguồn. Không rename nhánh cũ của thành viên; tên mới dùng ticket_mô-tả, vẫn nhận các nhánh legacy.

Cách thực thi: bootstrap develop từ main@8476ee3, retarget PR #7/#10/#11 vào develop, ruleset main/develop/release yêu cầu PR/2 approval/CI và merge bởi NV-DuyManh hoặc haitan2408. CI thêm Git Flow source/target check và regression tests. GitHub Actions kiểm quy trình/chất lượng, không giả báo đã cài bot AI code review. Tự review/AI review không thay hai người review. PR Git Flow chưa merge; các PR cũ cần nhận baseline workflow mới sau review. Không thay app/database, không tag/deploy hoặc nhập công việc local task tin tức còn dở.

Skills áp dụng cho thay đổi quy trình: cafe-workflow, cafe-review, cafe-quality tại .agents/skills; định tuyến workflow/verification/review, giữ attribution và ghi đúng giới hạn kiểm chứng. Cấu hình chi tiết và giới hạn thực thi nằm trong docs/engineering/github-settings.md.

## ADR-013 — hồ sơ cá nhân và vòng đời mật khẩu DT2-3 (15/11/2026)

Trạng thái: **owner chấp thuận trực tiếp trong chat DT2-3 ngày 15/11/2026**. Nguồn, khác biệt SRS/Jira và AC được lưu tại [yêu cầu DT2-3](../handover/dt2-3-requirements.md).

- Áp dụng QA #8 cho STAFF tự sửa đúng tên/địa chỉ/SĐT. ADMIN dùng cùng allowlist cá nhân; username/role/salary/status/auth/email/birthday/gender/avatar nằm ngoài endpoint này. CUSTOMER không có quyền hồ sơ nhân sự. Không thay module quản trị nhân viên.
- `passwordExpiresAt = passwordChangedAt + Duration.ofDays(30)`; `now >= expiresAt` hoặc cờ DB bằng 1 làm `mustChangePassword=true`. Login/Profile trả thông tin này, **chưa cưỡng chế khóa API nghiệp vụ**. Dùng Clock UTC và JDBC session UTC, không thêm cột expiry.
- PasswordPolicy/BCrypt/TokenService/AuthRepository được tái sử dụng. Từ chối password trùng hiện tại. Hash/timestamp/flag/auth_version/version đổi nguyên tử. Cấp bearer thay thế sau transaction commit, Frontend phải thay bearer cũ trước khi quay về hồ sơ. Reset grant gắn auth_version cũ bị thu hồi theo Auth hiện hữu.
- Dùng một account projection cho Home/Profile; PATCH giữ omitted fields, cấm explicit null và field ngoài allowlist. `users.version` được kiểm và tăng; writers password reset/change cùng tăng version. Row lock trên users bảo vệ các cập nhật của task. Writer Staff PR #11 chưa tham gia hợp đồng version: chưa bảo đảm optimistic concurrency xuyên mọi module.
- Chỉ hai mutation Profile chính xác dùng bearer-header stateless được miễn CSRF; không cookie login, không thêm CSRF bootstrap/session, không tắt CSRF chung. Menu/Sales giữ bảo vệ hiện hữu. Cơ chế này phù hợp API bearer hiện hữu; chuyển sang cookie auth phải đánh giá lại CSRF.
- Không thêm rate limiter/cột/bảng/purpose OTP. Chưa có bảo vệ brute force current-password/login được phê duyệt để tái sử dụng; cần owner chốt gateway/quota trước production. Không tuyên bố có rate limiting chỉ vì OTP có quota.

Không cấp quyền migrate database chung, deploy, push, tạo PR hoặc sửa ruleset. Thiếu CF vẫn chặn commit/title PR cuối cùng theo quyết định owner.

### ADR-013 — verification bổ sung, không mở rộng quyết định (15/11/2026)

Rehearsal với Staff#11 HEAD4c7b969 đã **tái hiện** lost update/stale version qua transaction MySQL đồng thời thật; đây không còn chỉ là rủi ro suy từ source. Hợp đồng lock users→staff_profiles, CAS expected-version/tăng version cho mọi writer và auth_version security transition được đóng gói thành [patch đề xuất cho owner Staff](../qa/rehearsals/dt2-3/README.md), chưa áp trên nhánh DT2-3 và chưa là quyết định API mới được owner module chấp thuận. Candidate sau patch9/9 không thay trạng thái full integrationFAIL.

Shared filter/matcher proposal sửa Profile401 và STAFF cross-account personnel200 đã tái hiện ở candidate Auth/Staff/News; không thay authentication architecture hoặc tự nhập PR khác. Bounded Login/current-password probe không quan sát429; policy shared admission/body cap vẫn **NEEDS OWNER DECISION**, không được gọi đã brute-force protected. [Báo cáo hiện hành](../reports/dt2-3-hardening.md) phân biệt baseline failure, patch-only success, NOT RUN Sonar và DBA deployment blockers.

### ADR-013 — CF/publication update18/11/2026

Owner xác nhận **CF-04** cho DT2-3/DT2-27 và cho phép commit/push current feature branch, tạo **Draft PR vào develop**, title/commit theo [requirements](../handover/dt2-3-requirements.md). Ghi nhận không-publication/thiếu-CF15/11 được thay cho phạm vi publication này; không cấp merge/approve/JiraDone hoặc deployment. Backend/DDL/policy nghiệp vụ không đổi. Develop331c1e3 được fast-forward và restore local implementation không conflict; [checks và blockers mới](../reports/dt2-3-publication.md) tách khỏi rehearsal lịch sử.
