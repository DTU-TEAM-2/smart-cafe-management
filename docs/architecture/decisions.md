# Nhật ký quyết định kiến trúc

Ngày mở vòng mới: 06/11/2026.

## DT2-4 — quyết định của chủ yêu cầu ngày 13/11/2026

Giữ QR dừng tại `77ddb61`; các chức năng còn lại dùng chung nhánh riêng `feature/dt2-4_customer-workflows`, không tự merge. Cho xây dựng xác thực nghiệp vụ trên session_clients sẵn có, không thay bảng hoặc tạo actor giả. Xác nhận sửa/hủy trong 180 giây từ khi server nhận Order, chỉ trước chuẩn bị (SUBMITTED/BEFORE_PREPARATION), kiểm bằng server. Cho chuẩn bị Firebase qua env nhưng project/bucket/credential do thành viên khác cung cấp; không tạo project hay tuyên bố cloud PASS. DT2-5 tạm dừng theo yêu cầu mới nhất. Nguồn: các trả lời trực tiếp trong chat ngày 13/11; chi tiết/AC tại [bàn giao](../handover/dt2-4-remaining-backend.md). Các lựa chọn token format/upload limit trong API là thiết kế triển khai theo ủy quyền, không đổi schema hoặc thay quyền của chủ DB.

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

## ADR-012 — Git Flow bắt buộc (12/11/2026)

Chủ repo yêu cầu áp dụng file git-flow.md vào dự án và luôn tuân thủ từ nay, thay quyết định GitHub Flow main-only trước đây. Nguồn nguyên bản được giữ tại docs/reference/git-flow-original-2026-11-12.md; quy tắc vận hành đã chỉnh cho dự án web tại git-flow.md.

Quyết định: main dành release, develop tích hợp task; feature/bugfix từ develop vào develop; release từ develop vào main; hotfix production từ main/tag vào main. Sau release/hotfix mở PR main → develop để đồng bộ, tránh mất bản sửa khi head tự xóa. Chỉ release khi scope ổn định, không tạo release cho mỗi feature. Thay ví dụ TestFlight/AppStore bằng staging/production web; giữ 2 approval độc lập đã được chủ repo chốt trước đó, không hạ xuống 1 của nguồn. Không rename nhánh cũ của thành viên; tên mới dùng ticket_mô-tả, vẫn nhận các nhánh legacy.

Cách thực thi: bootstrap develop từ main@8476ee3, retarget PR #7/#10/#11 vào develop, ruleset main/develop/release yêu cầu PR/2 approval/CI và merge bởi NV-DuyManh hoặc haitan2408. CI thêm Git Flow source/target check và regression tests. GitHub Actions kiểm quy trình/chất lượng, không giả báo đã cài bot AI code review. Tự review/AI review không thay hai người review. PR Git Flow chưa merge; các PR cũ cần nhận baseline workflow mới sau review. Không thay app/database, không tag/deploy hoặc nhập công việc local task tin tức còn dở.

Skills áp dụng cho thay đổi quy trình: cafe-workflow, cafe-review, cafe-quality tại .agents/skills; định tuyến workflow/verification/review, giữ attribution và ghi đúng giới hạn kiểm chứng. Cấu hình chi tiết và giới hạn thực thi nằm trong docs/engineering/github-settings.md.
