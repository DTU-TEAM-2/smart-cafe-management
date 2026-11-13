# Các quyết định cần chốt lại

Các mục nội bộ lịch sử bên dưới cần đối chiếu với Q&A hiện hành trước khi áp dụng. Phần DT2-7 đã được đối chiếu online ngày 11/11/2026 ở cuối tài liệu.
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

## DT2-7 — đối chiếu Q&A thật ngày 11/11/2026

Nguồn online: [Q&A dự án](https://docs.google.com/spreadsheets/d/1m2vnTOdGWivwqokRiJYOpWmY2u3G_o-U/edit), No.4/5/10; [Project Plan](https://docs.google.com/spreadsheets/d/1nT1hxrsgVdBxTbORlTwohwzmbvujldYR/edit), Tasks_Sprint_1!B9/E9. Các số No. này thuộc file Q&A khách hàng, không đồng nhất với mã QA-01…QA-07 nội bộ phía trên.

- **Đã trả lời, No.4:** chỉ quyền quản lý làm các tác vụ được hỏi, bao gồm đăng/sửa/xóa tin tức. ADMIN quản lý là cách mapping hiện tại; không hỏi lại STAFF có được thao tác hay không.
- **Đã trả lời, No.5:** tất cả trường hợp xóa dùng xóa mềm/logic. Không hỏi lại xóa cứng hay mềm. Dùng ARCHIVED sẵn có là lựa chọn hiện thực; không tự suy ra toàn bộ chính sách khôi phục/retention từ đáp án này.
- **Đã trả lời, No.10:** nhóm tự suy nghĩ thiết kế tin tức, chốt ở phiên prototype. Không xin bổ sung lại toàn bộ layout/trường dữ liệu; đưa prototype cụ thể để xác nhận.

Danh sách QA-08–QA-12 từng soạn local được thay bằng đối chiếu này vì có câu hỏi trùng đáp án. Giới hạn ảnh, kiểu editor và trạng thái cụ thể là đề xuất prototype, không phải điều khoản đã được nguồn yêu cầu xác nhận. Bucket/ADC là dependency cấu hình kỹ thuật riêng, không hỏi secret trong Q&A.

## DT2-7 — Q&A 19–20 đã được trả lời

[Ảnh phản hồi và cách triển khai](../handover/dt2-7-qa-replies-2026-11-11.md):

| No. Q&A | Trạng thái | Trả lời | Kết quả triển khai BE |
| --- | --- | --- | --- |
| 19 | Answered | Thêm lựa chọn gửi tin đến Nhân viên, Khách hàng | audiences STAFF/CUSTOMER, lưu hai cột theo ADR-005 được chủ repo duyệt; người đọc chỉ thấy PUBLISHED đúng nhóm. |
| 20 | Answered | Chỉ thông báo nhân viên | Socket chỉ STAFF; generic NEWS_CHANGED sau commit khi danh sách STAFF bị ảnh hưởng; CUSTOMER/ADMIN không nhận. |

Không hỏi lại hai câu đã trả lời. Hai checkbox độc lập, mặc định bài cũ 0/0 và API bắt buộc chọn ít nhất một nhóm khi tạo là cách triển khai đã trình chủ repo trong phương án schema. Chủ repo đã duyệt “vậy duyệt” sau khi nghe giải thích. Theo chỉ thị riêng tiếp theo tại ADR-006, đã áp dụng schema vào smart_cafe local; không seed hoặc ghi dữ liệu thử, chưa commit/push.

Phần chưa được phản hồi chốt: “khách hàng” có gồm người chưa đăng nhập/phiên QR không, chi tiết UI toast hay tự reload. Tiếp tục giới hạn đọc ở tài khoản CUSTOMER thật và để FE thiết kế prototype theo No.10; không tự giả auth hoặc mở public mọi tin. Không chặn việc triển khai hai câu đã được duyệt vì các chi tiết UI chưa thuộc BE.

Hợp đồng: [DT2-7 backend](../handover/dt2-7-backend-contract.md). Firebase cloud không sử dụng do ràng buộc miễn phí; local dùng Storage Emulator, không đổi trạng thái Q&A 19–20 về chưa trả lời.

### QA-02 — bổ sung yêu cầu DT2-4 ngày 11/11/2026

Người dùng yêu cầu khách quét QR tại bàn, vào thẳng Menu không đăng nhập và hệ thống tự mở phiên. Jira DT2-33 vẫn ghi QA-02 chưa chốt; QA nhóm trước đó ghi chỉ quản lý mở/reset. Cần PO/chủ DB xác nhận thay đổi trong phạm vi thẩm quyền và cách lưu người mở khi `table_sessions.opened_by_user_id` hiện NOT NULL FK tới `users`.

Các phương án cần quyết định: cho phép NULL để thể hiện khách vãng lai (cần duyệt schema), hoặc giữ schema và để người dùng nội bộ có thật mở phiên (không đáp ứng trải nghiệm tự mở hiện được yêu cầu). Không tạo tài khoản giả, gán ADMIN thay khách hoặc tắt FK. Thời hạn token, nhiều thiết bị và quét lại ở CHECKOUT chưa chốt. Xem [nguồn, phạm vi và blocker chức năng 2](../handover/dt2-4-table-session.md).


### DT2-9: DDL triển khai theo sơ đồ mới

Model đã theo sơ đồ mới do chủ repo cung cấp lại ngày 11/11. DB local legacy vẫn là bản cũ; cần chủ DB cung cấp/cập nhật DDL đúng mô hình trước smoke hoặc triển khai thực. PR không tự thay DB đó. Xem [đối chiếu](../architecture/database-contract.md#dt2-9--đối-chiếu-ngày-11112026-cho-pr-8).

### DT2-9: form Figma có Mô tả / WebP ngoài contract hiện tại

Frame Figma 8:319 có Mô tả và nhãn WebP, nhưng API/ERD menu chưa có description và backend chỉ nhận PNG/JPEG. Reviewer nêu P3 cần PO/UX/BE chốt. **Chủ repo chọn ngày 13/11: giữ contract hiện tại, ghi rõ chênh lệch và chờ PO chốt Figma.** Không tự thêm schema/field/format; phần cập nhật thiết kế vẫn chờ PO. Xem [đối chiếu và quyết định](../handover/dt2-9-figma-contract-alignment.md).
