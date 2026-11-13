# Nguồn skill và phạm vi kế thừa

Ngày snapshot: 04/11/2026. Tham chiếu dự án MathVisionKid theo yêu cầu người dùng. Repository HEAD tham chiếu khi lấy snapshot: `f1c4bb20498922a99f0346605490f37060fd0f57`. Đây là snapshot working tree, không khẳng định mọi file skill đã nằm trong commit đó.

Kế thừa nguyên bộ file cần thiết của năm skill: `react-best-practices`, `fixing-accessibility`, `web-design-guidelines`, `ui-ux-pro-max`, `ponytail-review`. Giữ frontmatter, tài liệu hỗ trợ, script và dữ liệu tra cứu. [Manifest](inherited-skills.json) ghi hash từng file, không gồm Python cache.

`react-best-practices` có identifier `vercel-react-best-practices`, metadata tác giả Vercel và khai báo MIT trong nguồn. Giữ khai báo/attribution của các nguồn. Các skill còn lại không được suy diễn giấy phép chỉ vì nằm trong cùng dự án; repo này không cấp lại giấy phép cho nội dung bên thứ ba.

Bảy skill `cafe-*` được viết cho repo này. Bộ định tuyến lấy cấu trúc canonical path, đọc chọn lọc trước triển khai, báo skill đã dùng và truy xuất nguồn từ MathVisionKid; bổ sung chuyển phase requirements → implementation → quality → review → handoff/release cho Spring/React cafe.

Không chuyển các quy tắc Expo, OCR/training, Student Mobile hoặc các luật commit/push riêng của MathVisionKid sang repo cafe. Không thay đổi dự án nguồn. Skill chỉ cung cấp cách làm; không tự cấp quyền dịch vụ, gửi tin hoặc triển khai.


## DT2-1 additions — 04/11/2026

- `mattpocock/skills` pinned at `4ae2317ddb22b046e26455e4f9c9b3aaaf62212d`: tdd, diagnosing-bugs, domain-modeling, codebase-design with their supporting files. Original MIT license retained as `tdd/LICENSE.upstream`, applicable to this bundle. TDD adapted locally: existing task criteria authorize routine verification; only unresolved behavior prompts a question. Review routing points to `cafe-review` instead of an uninstalled upstream skill.
- `nexu-io/open-design` pinned at `5a0c47777975ddb91d09c1c16b423a3ef74bf572`: web-prototype template/resources. Name/description normalized to `open-design-web-prototype`, scope restricted to standalone prototypes. Original Apache 2.0 license retained as `LICENSE.upstream`. This is a targeted resource bundle, not an installed/running OpenDesign daemon/MCP or the full catalog.
- SRS governs this React implementation. OpenDesign prototype output is not required for DT2-1 and was not generated. No unrelated slide/media/marketing skills were installed.

## Phạm vi manifest — 06/11/2026

Manifest kiểm tra 169 file Git theo dõi trong 10 thư mục skill kế thừa. Bổ sung checksum cho 22 file Matt Pocock và OpenDesign từ nội dung đã lưu trong Git, giữ nguyên 147 checksum trước đó cùng nội dung và giấy phép của các skill.

CI đối chiếu danh sách file trong Git với manifest và báo các đường dẫn thêm hoặc xóa so với nhánh đích. Khi thay đổi danh sách có chủ đích, tác giả ghi lý do, nguồn và thay đổi giấy phép (nếu có) vào tài liệu này trong cùng PR để nhóm xem xét. Các file trong danh sách mới vẫn phải qua kiểm tra checksum.
