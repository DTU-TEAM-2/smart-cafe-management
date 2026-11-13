# Các quyết định cần chốt lại

Các quyết định liên quan DT2-6 được đối chiếu với [QA nhóm cung cấp](https://docs.google.com/spreadsheets/d/1m2vnTOdGWivwqokRiJYOpWmY2u3G_o-U/edit?gid=1741591823#gid=1741591823). Xem [bàn giao DT2-6](../handover/dt2-06-sales-income.md) để phân biệt phần đã trả lời và phần tích hợp còn thiếu.

| ID | Câu hỏi | Trạng thái |
| --- | --- | --- |
| QA-01 | Phạm vi, người dùng, nguồn SRS/project plan và tiêu chí bàn giao của vòng mới? | Chờ nhóm xác nhận |
| QA-02 | Ai kích hoạt/đóng phiên bàn, nhiều thiết bị tham gia thế nào? | QA No. 4: chỉ quản lý mở/reset. Thời điểm mở, nhiều thiết bị và reset chưa thu tiền còn cần chốt. |
| QA-03 | Chỉnh/hủy món có thời hạn nào và tính từ mốc nào? | Chủ yêu cầu xác nhận trong chat 13/11/2026: 3 phút từ lúc server nhận Order, chỉ khi món chưa chuẩn bị (SUBMITTED); hết hạn hoặc đã chuẩn bị thì từ chối. Lưu BEFORE_PREPARATION, dùng Clock server; xem bàn giao DT2-4 còn lại. |
| QA-04 | Phân biệt yêu cầu thanh toán, lập bill, nhận tiền, ghi doanh thu và reset? | QA No. 3/4/17: yêu cầu tính tiền riêng; quản lý xác nhận tiền trước khi in. Doanh thu đọc bill PAID theo view DB đã thống nhất; còn cần producer/giao kèo socket, chính sách void/hoàn tiền và xử lý món đến sau khi lập bill. |
| QA-05 | Hành vi khi mất kết nối và cơ chế phục hồi? | QA No. 12: báo lỗi mất kết nối. Chưa chốt chế độ bán offline; UI hiện tại có thử lại. |
| QA-06 | DT2-1: top gọi nhiều tính quantity hay số lần, trong khoảng thời gian nào; available=false có ẩn không? | Đang dùng view quantity loại CANCELLED toàn thời gian; chờ PO |
| QA-07 | DT2-1: route menu/footer và provider session production? | Header đã đọc profile/feed từ DB và kiểm chứng provider local; chờ nhập auth/route của DT2-2, DT2-3, DT2-4, DT2-9 để smoke production integration |

Mỗi câu trả lời cần người chốt, ngày, nguồn bằng chứng và task bị ảnh hưởng. Đề xuất kỹ thuật chưa được duyệt vẫn là đề xuất.

### DT2-4 — quyết định trong chat ngày 13/11/2026

Chủ yêu cầu giữ QR tạm dừng, cho xây dựng nghiệp vụ dùng session_clients hiện có để kiểm tra token khách; không làm lại bảng hoặc tự mở phiên. TTL/issuer của luồng QR vẫn chưa chốt; API chỉ dùng expires_at/revoked_at đã lưu. Chấp thuận quy tắc QA-03 nêu ở bảng trên. Firebase project/bucket/credential thuộc thành viên khác: chỉ chuẩn bị env/adapter, chưa chạy cloud thật. DT2-5 đã được yêu cầu tạm dừng, dù đã ghi nhận dự kiến quyền ADMIN và ngày tìm kiếm theo Asia/Ho_Chi_Minh để dùng khi được cho tiếp tục. Xem [bàn giao phần còn lại](../handover/dt2-4-remaining-backend.md).

### QA-02 — bổ sung yêu cầu DT2-4 ngày 11/11/2026

Người dùng yêu cầu khách quét QR tại bàn, vào thẳng Menu không đăng nhập và hệ thống tự mở phiên. Jira DT2-33 vẫn ghi QA-02 chưa chốt; QA nhóm trước đó ghi chỉ quản lý mở/reset. Cần PO/chủ DB xác nhận thay đổi trong phạm vi thẩm quyền và cách lưu người mở khi `table_sessions.opened_by_user_id` hiện NOT NULL FK tới `users`.

Các phương án cần quyết định: cho phép NULL để thể hiện khách vãng lai (cần duyệt schema), hoặc giữ schema và để người dùng nội bộ có thật mở phiên (không đáp ứng trải nghiệm tự mở hiện được yêu cầu). Không tạo tài khoản giả, gán ADMIN thay khách hoặc tắt FK. Thời hạn token, nhiều thiết bị và quét lại ở CHECKOUT chưa chốt. Xem [nguồn, phạm vi và blocker chức năng 2](../handover/dt2-4-table-session.md).


### DT2-9: DDL triển khai theo sơ đồ mới

Model đã theo sơ đồ mới do chủ repo cung cấp lại ngày 11/11. DB local legacy vẫn là bản cũ; cần chủ DB cung cấp/cập nhật DDL đúng mô hình trước smoke hoặc triển khai thực. PR không tự thay DB đó. Xem [đối chiếu](../architecture/database-contract.md#dt2-9--đối-chiếu-ngày-11112026-cho-pr-8).
