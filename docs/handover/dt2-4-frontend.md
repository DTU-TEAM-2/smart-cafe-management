# DT2-4 — Bàn giao Frontend khách hàng

Cập nhật ngày 19/11/2026 cho PR #20, nhánh `feature/dt2-4_customer-frontend` nhắm `develop`.
Tài liệu này mô tả phần đã triển khai và kiểm thử, không thay approval và không xác nhận đã merge.

## Bảng tiến độ 8 chức năng Frontend DT2-4

| Chức năng | Ảnh đã đối chiếu trong ZIP | Tiến độ hiện tại | PR / phạm vi |
| --- | --- | --- | --- |
| 1. Xem Menu | Menu/danh mục, chi tiết món, đang tải thực đơn | Đã có danh mục, tìm kiếm, lọc món đang phục vụ, chi tiết và thêm giỏ; gọi API Menu thật theo contract. | PR #20 — Frontend khách hàng |
| 2. Bàn và phiên khách hàng | ZIP không có ảnh QR/tham gia phiên riêng | Chỉ hiển thị bàn từ phiên do module phiên cấp an toàn; không đọc bearer token từ URL, không tự mở/join phiên hoặc phát token. | PR #20 — consumer của module phiên; phần cấp phiên ngoài phạm vi |
| 3. Gọi món | Giỏ hàng, giỏ trống, đang tải, đặt món thành công/thất bại | Đã có giỏ và POST Order; retry không chắc chắn giữ nguyên Idempotency-Key; giá/tổng cuối cùng lấy từ response Backend. | PR #20 — Frontend Order |
| 4. Sửa/Remove món | Tham chiếu giỏ hàng và số lượng trong chi tiết món; ZIP không có màn sửa/hủy sau đặt riêng | Đã có đồng hồ 3 phút, PATCH số lượng và DELETE hủy; đồng bộ toàn bộ batch và version từ response Backend. | PR #20 — Frontend sửa/hủy |
| 5. Gọi nhân viên | Yêu cầu phục vụ, gửi thành công/thất bại | Đã có Call Waiter, giữ khóa retry khi chưa nhận kết quả chắc chắn. | PR #20 — Frontend WAITER |
| 6. Yêu cầu thanh toán | Yêu cầu phục vụ, yêu cầu thanh toán thành công/thất bại | Đã có Bill Request cho phiên có đơn tại client; Backend vẫn quyết định hợp lệ và không tự lập/thu bill trong frontend. | PR #20 — Frontend BILL |
| 7. Feedback | Đánh giá trải nghiệm: rating và nội dung | Đã có tên, email, rating và nội dung bắt buộc đúng contract Backend; có trạng thái thành công/thất bại. | PR #20 — Frontend Feedback |
| 8. Upload ảnh Feedback | Vùng đính kèm ảnh trong màn Feedback | Đã có chọn tối đa 3 JPEG/PNG, kiểm 5 MiB/file và multipart POST. Upload cloud thật còn phụ thuộc Firebase bucket/IAM của nhóm. | PR #20 — UI/multipart; Firebase thật chưa nghiệm thu |

## Bằng chứng kiểm thử

- `frontend/tests/customer.spec.js`: 8 kịch bản chạy trên desktop và mobile, tổng **16/16 PASS**. Bao gồm token không còn trong URL, cô lập A→B theo session/client, retry Order giữ nguyên Idempotency-Key, response shape thật, ba lần PATCH liên tiếp và validation Feedback.
- Toàn bộ Playwright repository: **118/118 PASS**.
- `npm run lint`: PASS.
- `npm run build`: PASS.
- Governance: registry **17 skills / 16 routes / 169 files**, routing tests **9/9**, Git Flow tests **8/8** PASS.
- Bảy ảnh tại `docs/handover/evidence/dt2-4/screenshots/` được tạo bởi `frontend/tests/capture-evidence.spec.js` với API mock theo contract. Đây là bằng chứng giao diện, không phải Postman hoặc tích hợp Backend/MySQL thật.

## Giới hạn còn lại

- Luồng QR → mở/join phiên → cấp bearer token chưa nằm trong PR #20. Module đó phải gọi `saveCustomerSession()` bằng credential do server cấp; không truyền token qua URL.
- Chưa chạy browser end-to-end với Backend/MySQL/Firebase thật trong lượt này. Backend PR #14 đã ở `develop`; Firebase cloud vẫn cần cấu hình nhóm và kiểm thử riêng.
- PR cần CI mới sau khi push, các discussion được reviewer xác nhận resolve và đủ hai approval độc lập trước khi maintainer xem xét merge.
