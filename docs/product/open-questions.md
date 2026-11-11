# Các quyết định cần chốt lại

Chưa có quyết định nghiệp vụ mới được ghi nhận trong vòng triển khai này.

| ID | Câu hỏi | Trạng thái |
| --- | --- | --- |
| QA-01 | Phạm vi, người dùng, nguồn SRS/project plan và tiêu chí bàn giao của vòng mới? | Chờ nhóm xác nhận |
| QA-02 | Ai kích hoạt/đóng phiên bàn, nhiều thiết bị tham gia thế nào? | Chưa chốt |
| QA-03 | Chỉnh/hủy món có thời hạn nào và tính từ mốc nào? | Chưa chốt |
| QA-04 | Phân biệt yêu cầu thanh toán, lập bill, nhận tiền, ghi doanh thu và reset? | Chưa chốt |
| QA-05 | Hành vi khi mất kết nối và cơ chế phục hồi? | Chưa chốt |
| QA-06 | Xác nhận nghiệm thu DT2-9 theo SRS tham chiếu: tên món ≥6 ký tự, giá VND nguyên >5.000, không đổi nhóm đã gán, xóa nhóm ẩn các món cùng transaction; FE demo hiện khác một số điểm. | Backend thực hiện theo nguồn legacy do chủ repo yêu cầu ngày 2026-11-11; cần đồng bộ/nghiệm thu FE riêng |
| QA-07 | Issuer/audience và mapping roles của auth chung, Cloudinary cloud/key thực cho môi trường tích hợp? | Chưa cấp; backend từ chối token khi thiếu issuer/audience, upload trả 503 khi thiếu cấu hình; không tạo fake account |

Mỗi câu trả lời cần người chốt, ngày, nguồn bằng chứng và task bị ảnh hưởng. Đề xuất kỹ thuật chưa được duyệt vẫn là đề xuất.
