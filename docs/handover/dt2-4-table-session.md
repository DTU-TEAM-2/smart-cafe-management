# DT2-4 — Chức năng 2: QR, bàn và phiên khách hàng

[Bảng theo dõi đủ 8 chức năng DT2-4](dt2-4-progress.md).

## Nguồn đã kiểm tra ngày 11/11/2026

- [DT2-4](https://duymanhdnvn20092005.atlassian.net/browse/DT2-4): menu, gọi món, phản hồi và phục vụ phải gắn đúng bàn đang hoạt động.
- [DT2-33](https://duymanhdnvn20092005.atlassian.net/browse/DT2-33): Spring kiểm tra phiên và quyền; QA-02 định danh/mở phiên chưa được coi là chốt, chỉ triển khai phần phụ thuộc khi có quyết định.
- [DT2-11](https://duymanhdnvn20092005.atlassian.net/browse/DT2-11): quản lý bàn cung cấp dữ liệu cho gọi món và bán hàng, không quy định chi tiết cấp token khách vãng lai.
- Chủ yêu cầu trong chat ngày 11/11/2026 xác nhận: khách quét QR tại bàn, vào thẳng Menu và sử dụng, không có bước đăng nhập; muốn hệ thống tự mở phiên khi khách quét QR.
- SRS gốc đã xác định metadata là `Quan ly quan cafe thong minh_SRS_v4.1.docx`, Drive ID `1Mhaq4ttf8d-uiPbv2gSnfHK0AhCu4qpc`; lượt đọc nội dung timeout sau 300 giây. Không tuyên bố đã đọc SRS gốc trong lượt này.
- ZIP 15 ảnh do người dùng cung cấp không có ảnh QR/mở phiên riêng. Không dùng ảnh Menu để tuyên bố đã triển khai màn hình QR.

## Phần độc lập đã triển khai

PR #9 đã merge vào main tại `8476ee3`. Nhánh PR #10 đã đồng bộ commit này qua merge `9790cbb`, không xung đột và tree mã nguồn trùng revision đã kiểm thử `a342090`. Main và develop cùng ở `8476ee3` khi đối chiếu ngày 13/11/2026. Theo ảnh quy trình chủ repo cung cấp trong chat cùng ngày, PR #10 nhắm develop và review độc lập phần tra QR; cần CI xanh và hai người khác tác giả approve trước khi maintainer quyết định merge. Đây là hướng dẫn đích PR mới thay phần mô tả main trước đó của task này; chưa tự sửa quy trình chung của repository. Không chờ hoàn tất nghiệp vụ phiên để review endpoint chỉ đọc này.

`GET /api/v1/customer/tables/qr/{qrPublicId}` cho khách chưa đăng nhập tra bàn từ QR. Kết quả chỉ có `tableId`, `tableCode`, `tableName`, dùng làm ngữ cảnh cho Menu. QR phải là mã UUID đủ 36 ký tự theo cột `cafe_tables.qr_public_id`; đối chiếu mã chính xác theo collation ASCII binary của fixture.

- Bàn ACTIVE: 200 với đúng bàn.
- QR sai định dạng: 400; QR không tồn tại hoặc bàn ARCHIVED: 404.
- Bàn OUT_OF_SERVICE: 409; DB không khả dụng: 503, thông báo tiếng Việt, không lộ JDBC.
- GET không ghi DB, không tạo `table_sessions`/`session_clients` và không trả token/quyền order.
- POST trên route tra QR và route tra bàn bằng số ID không được mở public.
- Không xây dựng frontend hoặc tự đặt URL redirect cho frontend chưa tích hợp.

## Phần chưa thể hoàn tất: tự mở phiên

Yêu cầu trải nghiệm đã rõ, nhưng chưa có quyết định schema/actor phù hợp khách vãng lai:

1. `table_sessions.opened_by_user_id` hiện NOT NULL và có FK tới `users(id)`. Khách chưa đăng nhập không có user ID để ghi. Không được tạo tài khoản giả, lấy ID ADMIN làm người mở thay khách hoặc tắt FK.
2. Đã nêu phương án cho phép NULL cho khách, giữ ID người thật khi nhân viên mở; chưa nhận được xác nhận cụ thể cho thay đổi cột/schema từ người có thẩm quyền. Chưa thêm/chạy migration, chưa đổi fixture để che lỗi.
3. Yêu cầu tự mở cần đối chiếu lại QA-02 đang ghi chỉ quản lý mở/reset. Ghi nhận mong muốn của người dùng không tự sửa quyết định của chủ DB/PO trước đó.
4. Cơ chế tham gia nhiều thiết bị, token hết hạn và xử lý CHECKOUT khi có khách quét lại vẫn chưa chốt. Không phát hành token tạm để tuyên bố hoàn thành.

Vì vậy chức năng 2 **chưa hoàn tất**. Phần tra QR được tách để có thể review độc lập; không coi nó là xác thực phiên hoặc chống gọi món trái bàn đã hoàn thành. Không đánh dấu Done hoặc triển khai order dựa vào số bàn phía trình duyệt.

## Bảng/cột và kiểm thử

Phần tra QR chỉ đọc `cafe_tables(id,code,name,status,qr_public_id)` theo fixture schema hiện có và database-contract.md. Không thay schema, không ghi DB hệ thống. Kiểm thử persistence chỉ ở MySQL `_test` với dữ liệu synthetic.

Test controller: truy cập public không token, không tiết lộ token/phiên, lỗi Việt hóa và DB, không mở quyền ghi. Test MySQL: hai QR trả đúng hai bàn, bàn ngừng/lưu trữ, QR sai/không tồn tại, gọi lặp không tạo phiên.

Ngày 11/11/2026 đã chạy trên Docker local: Maven verify 65/65 test PASS, 0 skip; test MySQL QR có assertion không tạo session/client chạy bổ sung 1/1 PASS; collection QR 9/9 request và 16/16 assertion PASS bằng Newman. [Ảnh và dữ liệu thực tế](../reports/dt2-4-qr/README.md). Đây không phải kết quả của luồng tự mở/xác thực phiên còn bị chặn.

Skills: requirements `cafe-requirements`; implementation `cafe-spring-api`, `cafe-database`; verification/review `cafe-quality`, `cafe-review` tại `.agents/skills/<identifier>/SKILL.md`. Drive metadata/read dùng `google-drive`; nội dung SRS không đọc được phải giữ là giới hạn.
