# Nhật ký quyết định kiến trúc

Ngày mở vòng mới: 06/11/2026.

| ID | Trạng thái | Nội dung |
| --- | --- | --- |
| ADR-001 | Theo yêu cầu chủ repo | Khởi tạo repo mới với main rỗng; rule/skill được review qua PR trước khi vào main. Không mang code/migration/lịch sử PR cũ sang. |
| ADR-002 | Lịch sử; phần stack được thay bằng ADR-004 ngày 11/11/2026 | Ở PR nền tảng từng đề xuất đánh giá lại Spring/React/MySQL và chưa có application skeleton. Đây không còn là trạng thái stack chờ chốt hiện tại. |
| ADR-003 | Lịch sử; yêu cầu approval được thay bằng ADR-007 | Ngày 06/11/2026, NV-DuyManh hoặc mentor haitan2408 quyết định merge qua PR, approval từng là tùy chọn. Căn cứ lịch sử ở [PR #2](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/2); yêu cầu hiện hành ở ADR-007 và [thiết lập GitHub](../engineering/github-settings.md). |
| ADR-004 | Chủ repo yêu cầu áp dụng 11/11/2026 theo trao đổi mentor | **BE Spring Boot, FE ReactJS, database MySQL bắt buộc; còn lại tùy chọn.** Nguồn: [ảnh trao đổi Tân Hải do chủ repo cung cấp](../handover/evidence/technology-constraints-2026-11-11.png), chủ repo yêu cầu ghi rule và đồng bộ tài liệu. Áp dụng toàn dự án theo [rule công nghệ](../../.agents/rules/technology-constraints.md); không tự đổi stack, không biến công cụ tùy chọn hoặc version hiện tại thành yêu cầu mới của mentor. |
| ADR-005 | Chủ repo duyệt 11/11/2026 | Q&A 19 yêu cầu chọn đối tượng nhận tin; Q&A 20 giới hạn thông báo nhân viên. Sau khi xem đề xuất và giải thích hai cột, chủ repo xác nhận trong chat “vậy duyệt”: bổ sung `news_posts.audience_staff` và `audience_customer` dạng TINYINT(1), NOT NULL DEFAULT 0 và CHECK 0/1. Hai lựa chọn độc lập, dữ liệu cũ 0/0 chỉ ADMIN thấy tới khi gán nhóm. Chấp thuận triển khai/test local, chưa ALTER DB hệ thống, chưa commit/push. Script SQL versioned ở `backend/database/migrations/V20261111_01__news_audiences.sql`, không tự chạy khi startup. Nguồn nghiệp vụ: [phản hồi Q&A](../handover/dt2-7-qa-replies-2026-11-11.md). |
| ADR-006 | Chủ repo yêu cầu áp dụng DB hệ thống 11/11/2026 | Sau ADR-005, chủ repo chỉ thị rõ: cập nhật hai cột vào database hệ thống và kiểm tra API thực tế, chưa commit/push. Đã áp dụng nguyên script V20261111_01__news_audiences.sql lên MySQL local smart_cafe, xác minh hai cột/default/CHECK và các trường cũ không đổi. Không seed hoặc ghi row nghiệp vụ. BE hiện tại chạy kiểm tra ở loopback cổng 18080, giữ nguyên dịch vụ 8080. Chủ repo xác nhận chưa có phiên/token thật; chưa tuyên bố nghiệm thu HTTP có quyền thành công. Nhật ký migration, digest dữ liệu và kết quả API nằm ngoài repo trong hồ sơ review local. |

DT2-1 triển khai Spring Boot/JDBC + React/Vite + MySQL. Stack ba thành phần chính đã được chốt tại ADR-004; JDBC/Vite là công cụ đang dùng, code và lựa chọn cụ thể vẫn qua review. Home chỉ đọc, không tự DDL/seed; CI dùng database test riêng. Quyết định stack không tự phê duyệt nghiệp vụ ranking hoặc auth. Xem [handoff](../handover/dt2-1-home-layout.md).

## Cập nhật ngày 11/11/2026

- **ADR-007 — Chủ repo yêu cầu ít nhất hai người approve trước merge.** Thay phần approval tùy chọn của ADR-003; vẫn giữ hai người được quyền thực hiện merge. Ruleset main đã bật yêu cầu 2 approval, dismiss stale approval khi có commit mới và không có bypass actor. Hai reviewer phải khác tác giả và là hai người độc lập; kiểm thử/self-review của agent không thay thế approval thứ hai. Chi tiết ở [thiết lập GitHub](../engineering/github-settings.md).
- **ADR-008 — Chủ repo mở rộng full Task 7 và yêu cầu kiểm tra đăng nhập thật.** Form React được nối vào endpoint DT2-2 hiện có, xác minh qua consumer trước khi giữ token. Thay phạm vi chỉ chuẩn bị UI ngày 07/11; không tự thêm issuer, endpoint auth, account hệ thống hoặc cơ chế cấp role. [Handoff đăng nhập](../handover/dt2-1-login-ui.md) ghi ranh giới và bằng chứng.
- **ADR-009 — Chủ repo yêu cầu Firebase chỉ dùng gói miễn phí, không thanh toán.** Project `smart-cafe-management-47578` giữ Spark. Console yêu cầu Blaze cho Storage nên không liên kết billing, không tạo key hoặc tuyên bố có bucket cloud. Kiểm thử ảnh local dùng emulator chính thức trên loopback và MySQL `_test`, tách rõ khỏi adapter cloud. Project trước `smart-cafe-management-1cf45` đã xóa theo chỉ thị riêng của chủ repo; không tác động project khác.
- **ADR-010 — Chủ repo yêu cầu cấu hình Firebase dùng được sau clone, ngày 12/11/2026.** Bàn giao CLI/lockfile/config mẫu và launcher theo đường dẫn repo; `start-be.ps1 -EnvFile` đọc profile/endpoint, ảnh emulator export/import local qua các lần chạy. Không phụ thuộc Firebase login/key/ổ đĩa của tác giả, không đưa credential hoặc dữ liệu ảnh vào Git. Hướng dẫn phân biệt dữ liệu riêng từng máy với backend/bucket chung; cloud vẫn phụ thuộc bucket/IAM hợp lệ và giới hạn miễn phí tại ADR-009. Xem [cấu hình nhóm](../handover/dt2-7-firebase-setup.md).
- **Bổ sung ADR-009 ngày 12/11/2026:** chủ repo nhắc “cấm tuyệt đối bật thanh toán, chỉ được sử dụng free”. Không liên kết thẻ, nâng Blaze hoặc dùng trial đòi thanh toán, không hỏi mở billing lại. Live Storage bị tắt mặc định trước tra ADC/call cloud; chỉ kiểm thử và bàn giao emulator miễn phí trong phạm vi này. Hướng dẫn cloud cũ đã gỡ khỏi quickstart để không dẫn thành viên tới luồng trả phí.

Quyết định được chấp nhận phải có người chốt, ngày, căn cứ và hệ quả; không dùng bảng này để tự phê duyệt nghiệp vụ.

## ADR-011 — tích hợp menu với mô hình DB và auth chung (11/11/2026)

Đổi mã khi tích hợp main ngày 12/11 để tránh trùng ADR-005 về audience của Task 7. Nguồn main 8476ee3 gọi quyết định Menu này là ADR-005; giữ nguyên nội dung và ngày quyết định, chỉ phân biệt mã trong tài liệu hợp nhất.

Chủ repo cung cấp lại sơ đồ DB mới khi đối chiếu cho PR #8; mô hình đích và chênh lệch với DDL local được ghi tại [database contract](database-contract.md#dt2-9--đối-chiếu-ngày-11112026-cho-pr-8). Menu dùng category_id/image_media_id, giá decimal(18,0); URL là trường dẫn xuất, bỏ description không có trong mô hình. Không có migration runtime hay thay đổi DB local. Kiểm thử dùng fixture main nguyên trạng trong schema riêng.

Lựa chọn triển khai để review: tái sử dụng HeaderTokenFilter của main và AUTH_TOKEN_SECRET, kiểm tra quyền từ users; bỏ cơ chế JWT riêng của nhánh menu. Giữ CSRF cho mutation. Không phát hành token, seed tài khoản hoặc triển khai auth mới. Không thay quyền merge tại ADR-003.


## ADR-012 — Git Flow bắt buộc (12/11/2026)

Chủ repo yêu cầu áp dụng file git-flow.md vào dự án và luôn tuân thủ từ nay, thay quyết định GitHub Flow main-only trước đây. Nguồn nguyên bản được giữ tại docs/reference/git-flow-original-2026-11-12.md; quy tắc vận hành đã chỉnh cho dự án web tại git-flow.md.

Quyết định: main dành release, develop tích hợp task; feature/bugfix từ develop vào develop; release từ develop vào main; hotfix production từ main/tag vào main. Sau release/hotfix mở PR main → develop để đồng bộ, tránh mất bản sửa khi head tự xóa. Chỉ release khi scope ổn định, không tạo release cho mỗi feature. Thay ví dụ TestFlight/AppStore bằng staging/production web; giữ 2 approval độc lập đã được chủ repo chốt trước đó, không hạ xuống 1 của nguồn. Không rename nhánh cũ của thành viên; tên mới dùng ticket_mô-tả, vẫn nhận các nhánh legacy.

Cách thực thi: bootstrap develop từ main@8476ee3, retarget PR #7/#10/#11 vào develop, ruleset main/develop/release yêu cầu PR/2 approval/CI và merge bởi NV-DuyManh hoặc haitan2408. CI thêm Git Flow source/target check và regression tests. GitHub Actions kiểm quy trình/chất lượng, không giả báo đã cài bot AI code review. Tự review/AI review không thay hai người review. PR Git Flow chưa merge; các PR cũ cần nhận baseline workflow mới sau review. Không thay app/database, không tag/deploy hoặc nhập công việc local task tin tức còn dở.

Skills áp dụng cho thay đổi quy trình: cafe-workflow, cafe-review, cafe-quality tại .agents/skills; định tuyến workflow/verification/review, giữ attribution và ghi đúng giới hạn kiểm chứng. Cấu hình chi tiết và giới hạn thực thi nằm trong docs/engineering/github-settings.md.
