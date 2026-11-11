# Bắt buộc bám sát database hệ thống

Áp dụng cho **mọi thành viên và AI agent**, mọi task FE/BE/API, báo cáo, script, migration và kiểm thử có liên quan dữ liệu; không chỉ DT2-1.

## Nguồn chuẩn

- Chủ repo yêu cầu ngày **11/11/2026**: toàn bộ dự án phải dựa theo [sơ đồ database này](../../docs/handover/evidence/system-database-reference-2026-11-11.jpg), không tự chế thêm schema. Ảnh ngày 06/11 chỉ là tham chiếu lịch sử, không thay thế bản mới.
- [Database contract](../../docs/architecture/database-contract.md) ghi cách đối chiếu và chênh lệch. Trước khi viết/sửa persistence, đọc DDL thực tế được cung cấp hoặc kiểm tra read-only `SHOW CREATE TABLE`, `SHOW CREATE VIEW`, index/constraint tại database đúng môi trường. Không truy xuất/đưa dữ liệu cá nhân hoặc credential vào tài liệu.
- Ảnh xác định mô hình đã được chủ repo chỉ định; DDL xác định cấu trúc thực sự đang chạy. Nếu hai nguồn khác nhau hoặc chữ/quan hệ trong ảnh không rõ, **không tự chọn một bên để sửa code/schema**: ghi chênh lệch, dừng phần phụ thuộc và hỏi chủ repo/người phụ trách DB được giao quyền. Các phần độc lập vẫn tiếp tục.

## Không được tự ý làm

1. Thêm, đổi tên, xóa bảng/cột; thay kiểu, precision, nullable, default, PK/FK, index, unique/check hoặc quan hệ để khớp code.
2. Tạo bảng thay thế/trùng chức năng như `notifications`, `accounts`, `products` hoặc `orders` khi chưa có trong mô hình và chưa được duyệt. Không tự thêm view, trigger hay stored procedure; view đã tồn tại phải có DDL/contract đối chiếu.
3. Tự đặt giá trị role/status, enum nghiệp vụ, cách liên kết dữ liệu hoặc giả định cột không có trong DB. `varchar` trong ảnh không chứng minh toàn bộ giá trị hợp lệ; phải đối chiếu constraint và yêu cầu nghiệp vụ đã duyệt.
4. Bật `ddl-auto=update/create/create-drop`, SQL init hoặc script tự sửa schema/seed vào DB hệ thống khi ứng dụng khởi động. Không tắt FK/check để vượt lỗi dữ liệu.
5. Đổi schema kiểm thử để che lỗi mapping hoặc coi fixture/H2/schema tự dựng là chuẩn thay cho DB hệ thống. Không nạp account/order/payment giả vào DB để lấp UI nếu chưa có yêu cầu rõ ràng cho dữ liệu và môi trường đó.

DTO, alias truy vấn và giá trị tính toán trong code được phép khi truy xuất được về trường/quan hệ hiện có và đúng yêu cầu; chúng không cho phép tự tạo thêm cột lưu trữ hoặc quy tắc nghiệp vụ.

## Cách thực hiện mỗi task

1. Liệt kê bảng/cột/quan hệ cần dùng; đối chiếu ảnh, DDL và contract trước khi viết query/entity/repository. FE nhận dữ liệu qua API contract, không tự suy ra schema.
2. Giữ đúng precision tiền, FK, nullable, version, snapshot lịch sử, idempotency và các constraint thực tế có liên quan. Không sửa giá/tên lịch sử theo menu hiện tại, không cascade xóa lịch sử tài chính.
3. Nếu thiếu dữ liệu cấu trúc: ghi vào database contract và câu hỏi mở, kèm task, trường cần dùng, lý do, tác động; **chưa tạo migration hay cột/bảng để chạy tạm**.
4. Chỉ khi chủ repo/người phụ trách DB được giao quyền chốt thay đổi bằng quyết định truy xuất được, mới triển khai đúng phạm vi đã duyệt. Cập nhật sơ đồ/DDL/contract và migration versioned trong PR; không sửa migration đã chạy ở môi trường dùng chung. Chấp thuận feature không tự đồng nghĩa chấp thuận mở rộng schema.
5. Test trên MySQL riêng có hậu tố `_test`, dùng cấu trúc từ DDL đã đối chiếu; fixture dữ liệu không đưa vào DB hệ thống. Ghi rõ phần chưa kiểm chứng thay vì bịa kết quả hoặc lấy mock làm bằng chứng vận hành thật.

## Review trước merge

PR có thay đổi dữ liệu phải nêu bảng/cột bị tác động, nguồn schema/DDL đối chiếu và kết quả test. Nếu đổi schema, cần link quyết định chấp thuận và kế hoạch migration/khôi phục phù hợp. Reviewer yêu cầu sửa các phần tự chế; CI xanh không thay thế việc đối chiếu DB.

Đây là quy tắc làm việc/review được neo tại `AGENTS.md` và checklist PR; không phải cơ chế tự động phát hiện mọi SQL/entity sai schema. Không tuyên bố CI đã khóa toàn bộ thay đổi schema nếu chưa có kiểm tra thực thi tương ứng.
