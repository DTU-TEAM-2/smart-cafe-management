# Các quyết định cần chốt lại

Các quyết định liên quan DT2-6 được đối chiếu với [QA nhóm cung cấp](https://docs.google.com/spreadsheets/d/1m2vnTOdGWivwqokRiJYOpWmY2u3G_o-U/edit?gid=1741591823#gid=1741591823). Xem [bàn giao DT2-6](../handover/dt2-06-sales-income.md) để phân biệt phần đã trả lời và phần tích hợp còn thiếu.

| ID | Câu hỏi | Trạng thái |
| --- | --- | --- |
| QA-01 | Phạm vi, người dùng, nguồn SRS/project plan và tiêu chí bàn giao của vòng mới? | Chờ nhóm xác nhận |
| QA-02 | Ai kích hoạt/đóng phiên bàn, nhiều thiết bị tham gia thế nào? | QA No. 4: chỉ quản lý mở/reset. Thời điểm mở, nhiều thiết bị và reset chưa thu tiền còn cần chốt. |
| QA-03 | Chỉnh/hủy món có thời hạn nào và tính từ mốc nào? | Chưa chốt |
| QA-04 | Phân biệt yêu cầu thanh toán, lập bill, nhận tiền, ghi doanh thu và reset? | QA No. 3/4/17: yêu cầu tính tiền riêng; quản lý xác nhận tiền trước khi in. Doanh thu đọc bill PAID theo view DB đã thống nhất; còn cần producer/giao kèo socket, chính sách void/hoàn tiền và xử lý món đến sau khi lập bill. |
| QA-05 | Hành vi khi mất kết nối và cơ chế phục hồi? | QA No. 12: báo lỗi mất kết nối. Chưa chốt chế độ bán offline; UI hiện tại có thử lại. |
| QA-06 | DT2-1: top gọi nhiều tính quantity hay số lần, trong khoảng thời gian nào; available=false có ẩn không? | Đang dùng view quantity loại CANCELLED toàn thời gian; chờ PO |
| QA-07 | DT2-1: route menu/footer và provider session production? | Header đã đọc profile/feed từ DB và kiểm chứng provider local; chờ nhập auth/route của DT2-2, DT2-3, DT2-4, DT2-9 để smoke production integration |

Mỗi câu trả lời cần người chốt, ngày, nguồn bằng chứng và task bị ảnh hưởng. Đề xuất kỹ thuật chưa được duyệt vẫn là đề xuất.

### QA-02 — bổ sung yêu cầu DT2-4 ngày 11/11/2026

Người dùng yêu cầu khách quét QR tại bàn, vào thẳng Menu không đăng nhập và hệ thống tự mở phiên. Jira DT2-33 vẫn ghi QA-02 chưa chốt; QA nhóm trước đó ghi chỉ quản lý mở/reset. Cần PO/chủ DB xác nhận thay đổi trong phạm vi thẩm quyền và cách lưu người mở khi `table_sessions.opened_by_user_id` hiện NOT NULL FK tới `users`.

Các phương án cần quyết định: cho phép NULL để thể hiện khách vãng lai (cần duyệt schema), hoặc giữ schema và để người dùng nội bộ có thật mở phiên (không đáp ứng trải nghiệm tự mở hiện được yêu cầu). Không tạo tài khoản giả, gán ADMIN thay khách hoặc tắt FK. Thời hạn token, nhiều thiết bị và quét lại ở CHECKOUT chưa chốt. Xem [nguồn, phạm vi và blocker chức năng 2](../handover/dt2-4-table-session.md).


### DT2-9: DDL triển khai theo sơ đồ mới

Model đã theo sơ đồ mới do chủ repo cung cấp lại ngày 11/11. DB local legacy vẫn là bản cũ; cần chủ DB cung cấp/cập nhật DDL đúng mô hình trước smoke hoặc triển khai thực. PR không tự thay DB đó. Xem [đối chiếu](../architecture/database-contract.md#dt2-9--đối-chiếu-ngày-11112026-cho-pr-8).

### DT2-9: form Figma có Mô tả / WebP ngoài contract hiện tại

Frame Figma 8:319 có Mô tả và nhãn WebP, nhưng API/ERD menu chưa có description và backend chỉ nhận PNG/JPEG. Reviewer nêu P3 cần PO/UX/BE chốt. **Chủ repo chọn ngày 13/11: giữ contract hiện tại, ghi rõ chênh lệch và chờ PO chốt Figma.** Không tự thêm schema/field/format; phần cập nhật thiết kế vẫn chờ PO. Xem [đối chiếu và quyết định](../handover/dt2-9-figma-contract-alignment.md).

### QA-08 — DT2-10: Onboarding nhân viên và luồng gửi Email kích hoạt (13/11/2026)

- **Mã issue / Dependency:** `DT2-2` (Module Authentication & Account Onboarding / Email Service).
- **Owner:** Nhóm phụ trách module Auth (`DT2-2`).
- **Blocker tích hợp:** Hạ tầng SMTP / JavaMailSender chưa tích hợp trong backend Spring Boot ở sprint này.
- **Tiêu chí E2E Acceptance:** Khi module Auth hoàn thiện: Admin tạo nhân viên (`POST /api/v1/staff`) -> Backend lưu `must_change_password=1` và mật khẩu ngẫu nhiên an toàn -> Auth Module phát hành email kèm activation/reset token một lần -> Nhân viên truy cập link xác thực, tự đặt mật khẩu mới -> Đăng nhập thành công lần đầu.
- **Trạng thái:** Backend User & Security State của Staff hoàn tất; đang chờ module Auth (DT2-2) kết nối luồng gửi email và trang đặt mật khẩu.
