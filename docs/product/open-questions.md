# Các quyết định cần chốt lại

Chưa có quyết định nghiệp vụ mới được ghi nhận trong vòng triển khai này.

| ID | Câu hỏi | Trạng thái |
| --- | --- | --- |
| QA-01 | Phạm vi, người dùng, nguồn SRS/project plan và tiêu chí bàn giao của vòng mới? | Chờ nhóm xác nhận |
| QA-02 | Ai kích hoạt/đóng phiên bàn, nhiều thiết bị tham gia thế nào? | Chưa chốt |
| QA-03 | Chỉnh/hủy món có thời hạn nào và tính từ mốc nào? | Chưa chốt |
| QA-04 | Phân biệt yêu cầu thanh toán, lập bill, nhận tiền, ghi doanh thu và reset? | Chưa chốt |
| QA-05 | Hành vi khi mất kết nối và cơ chế phục hồi? | Chưa chốt |
| QA-06 | DT2-1: top gọi nhiều tính quantity hay số lần, trong khoảng thời gian nào; available=false có ẩn không? | Đang dùng view quantity loại CANCELLED toàn thời gian; chờ PO |
| QA-07 | DT2-1: route menu/footer và provider session production? | Header đã đọc profile/feed từ DB và kiểm chứng provider local; chờ nhập auth/route của DT2-2, DT2-3, DT2-4, DT2-9 để smoke production integration |

Mỗi câu trả lời cần người chốt, ngày, nguồn bằng chứng và task bị ảnh hưởng. Đề xuất kỹ thuật chưa được duyệt vẫn là đề xuất.


### DT2-9: DDL triển khai theo sơ đồ mới

Model đã theo sơ đồ mới do chủ repo cung cấp lại ngày 11/11. DB local legacy vẫn là bản cũ; cần chủ DB cung cấp/cập nhật DDL đúng mô hình trước smoke hoặc triển khai thực. PR không tự thay DB đó. Xem [đối chiếu](../architecture/database-contract.md#dt2-9--đối-chiếu-ngày-11112026-cho-pr-8).
