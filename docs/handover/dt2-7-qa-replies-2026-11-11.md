# DT2-7 — phản hồi Q&A 19–20

Nguồn: [ảnh phản hồi do chủ repo gửi ngày 11/11/2026](evidence/dt2-7-qa-replies-2026-11-11.png). Chủ repo yêu cầu triển khai sau khi gửi phản hồi. Ban đầu phạm vi chỉ backend local; sau đó chủ repo yêu cầu “làm ui luôn… full task 7”. Phạm vi gồm BE và React UI. Ban đầu chỉ làm local; ngày 13/11 chủ repo cho phép commit/push và mở PR vào develop. Hành vi UI tại [handoff frontend](dt2-7-frontend-contract.md).

## Phản hồi gốc

- Q&A 19, Answered: “Bổ sung tin tức gửi đến ai (thêm 1 option để tick vào). Nhân viên, khách hàng.”
- Q&A 20, Answered: “Chỉ thông báo cho nhân viên thôi nhé”.

Quyền đăng/sửa/xóa vẫn theo Q&A 4 (quản lý); xóa theo Q&A 5 (mềm/logic). Phản hồi 19 là chọn đối tượng nhận bài, không cấp quyền quản trị cho STAFF/CUSTOMER. Phản hồi 20 quy định người nhận socket; không đồng nghĩa khách hàng được nhận socket nếu được xem bài.

## Hành vi backend cần thực hiện

- ADMIN chọn nhóm nhận bài STAFF hoặc CUSTOMER. Hai checkbox độc lập theo phương án chủ repo duyệt để có thể chọn một hoặc cả hai nhóm; bắt buộc có ít nhất một nhóm khi tạo bài.
- ADMIN quản lý mọi bài. Người dùng STAFF/CUSTOMER chỉ đọc bài PUBLISHED thuộc nhóm mình, không đọc tin DRAFT/ARCHIVED hay bài của nhóm khác. Lấy role từ xác thực server/DB, không lấy query/body làm quyền.
- Chưa tự mở quyền đọc cho khách chưa đăng nhập: phản hồi nêu “khách hàng”, không xác nhận truy cập ẩn danh hoặc guest/table-session auth.
- Socket chỉ nhận kết nối STAFF đang ACTIVE, token còn hạn và auth_version hợp lệ. CUSTOMER/ADMIN không nhận thông báo ở kênh nhân viên này; ADMIN tiếp tục quản trị bằng REST.
- Chỉ thông báo thay đổi ảnh hưởng danh sách STAFF được phép xem. Không phát ID/thông tin tin nháp hoặc bài chỉ gửi khách hàng tới STAFF. Trường hợp bài bị gỡ nhóm STAFF, hủy xuất bản hoặc lưu trữ phải thông báo để nhân viên bỏ tin cũ khỏi danh sách.
- Ảnh đi kèm phải kiểm tra quyền đọc theo bài, không để việc biết media ID vượt qua quyền của bài.

Các lựa chọn hiện thực về bắt buộc nhóm khi tạo, hai checkbox và xử lý bài cũ sẽ được ghi trong contract; không gọi đó là câu chữ đã được khách hàng quy định riêng.

## Dependency schema

SHOW CREATE TABLE đọc lại ngày 11/11 cho thấy news_posts chưa có trường người nhận. Rule database yêu cầu quyết định riêng cho mở rộng schema. Sau khi được giải thích tác dụng và xem phương án cụ thể, chủ repo xác nhận “vậy duyệt”. ADR-005 ghi nhận hai cột audience_staff/audience_customer và CHECK 0/1; script SQL versioned và fixture test đã cập nhật theo phần mở rộng được duyệt. Ban đầu chỉ chạy DDL trên MySQL _test riêng. Sau đó chủ repo chỉ thị áp dụng DB hệ thống: ADR-006 ghi nhận đã áp dụng đúng script lên smart_cafe local, không seed/DML, chưa commit/push.
