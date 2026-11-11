# Quy tắc làm việc nhóm

Áp dụng cho nhóm 6 người từ khi khởi tạo repo ngày 04/11/2026. Đây là baseline kỹ thuật của repo; PO/mentor xác nhận nghiệp vụ, năng lực và phân công thực tế. Sửa quy tắc qua PR, ghi lý do và tác động.

## Trách nhiệm

- PO/mentor chốt ưu tiên, phạm vi và câu trả lời QA; nhóm không thay đề xuất bằng quyết định đã duyệt.
- Leader giữ luồng tích hợp, môi trường, phụ thuộc và blocker. Leader không phải người duyệt duy nhất cho mọi PR.
- Mỗi task có một owner chịu trách nhiệm cập nhật và một reviewer có năng lực phù hợp. Không tự tính self-review là review chéo.
- Người thay API/schema chủ động thông báo tác động cho người dùng hợp đồng đó. Giữ ghi nhận tác giả, nguồn tích hợp và bằng chứng của cộng tác viên.

## Nhận và luân chuyển việc

Mọi thành viên và AI phải tuân thủ [rule database nguồn chuẩn](../../.agents/rules/database-source-of-truth.md) cho tất cả task liên quan dữ liệu: bám sơ đồ chủ repo ngày 11/11/2026, đối chiếu DDL, không tự chế bảng/cột/quan hệ. Thiếu hoặc lệch schema phải ghi rõ và chờ người có trách nhiệm chốt trước khi triển khai phần phụ thuộc.

Board: Thông tin/QA → Product Backlog → Sprint To do → Doing → Review → Test → Done. `BLOCKED` là nhãn trên card tại bước hiện tại; ghi vướng gì, ai có thể giải quyết và bước tiếp theo.

Mỗi người tập trung một task triển khai chính; có thể hỗ trợ review và gỡ blocker. Tách task lớn thành kết quả kiểm chứng được, thường khoảng 4–8 giờ khả dụng. Ước lượng gồm code, kiểm thử, review và tích hợp; không cộng cả story và task con hai lần.

Daily ngắn: kết quả vừa hoàn thành, mục tiêu tiếp theo, blocker cần hỗ trợ. Cập nhật card ngay khi phát hiện blocker, không chờ Daily. Lịch họp, ngày công ty và giờ ở nhà theo kickoff đã thống nhất; lịch trong tài liệu bàn giao hiện là dự kiến.

Reviewer phản hồi trong ngày làm việc kế tiếp nếu có thể. Nếu không đủ khả dụng, đổi reviewer rõ trên card. Trao đổi quyết định trên issue/PR/decision log để người vắng mặt đọc lại được.

## Definition of Ready

- Có mục tiêu, vai trò sử dụng, nguồn yêu cầu và tiêu chí nghiệm thu quan sát được.
- Quy tắc nghiệp vụ ảnh hưởng trực tiếp đã rõ hoặc có phạm vi độc lập có thể làm trước.
- Có phụ thuộc, owner, reviewer và ước lượng nhóm thống nhất.
- Với thay đổi liên tầng: đã thống nhất payload, lỗi, quyền và tác động dữ liệu ở mức đủ triển khai.

## Definition of Done

- Đạt tiêu chí card; xử lý validation, quyền và trạng thái lỗi có liên quan.
- Build/lint/test thích hợp đạt; có bằng chứng đã chạy thật và ghi rõ phần chưa kiểm tra.
- Review chéo hoàn tất, blocker được sửa, thread của reviewer được xử lý theo quy ước PR.
- API/schema/tài liệu cấu hình/hướng dẫn được cập nhật khi thay đổi.
- Đã tích hợp đúng nhánh và kiểm tra tác động với phần liên quan.
- Không có secret, dữ liệu cá nhân, debug log hoặc mock bị trình bày như dữ liệu thật.

CI xanh là một điều kiện; không tự thay thế nghiệm thu, review hoặc kiểm thử trình duyệt. DoD cũ nhắc IE, Bootstrap, I18n và `dev` chỉ là tài liệu tham khảo; baseline này dùng PR từ nhánh task vào `main`, trình duyệt hiện hành và tiêu chí phù hợp task. Một task đang review tiếp tục dùng cùng nhánh; merge xong dọn nhánh.

## Bàn giao và bất đồng

Dùng [mẫu handoff](../templates/task-handoff.md) cho việc qua người/phase; việc nhỏ ghi ngay trong PR. Ghi nguồn quyết định, revision, lệnh kiểm tra và bước tiếp theo. Khi bất đồng nghiệp vụ, chuyển PO; khi bất đồng kỹ thuật, trình bày lựa chọn, chi phí và bằng chứng, ghi ADR nếu ảnh hưởng dài hạn. Không tự đóng câu hỏi chưa được trả lời.
