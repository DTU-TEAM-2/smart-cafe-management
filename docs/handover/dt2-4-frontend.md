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

- Mốc kiểm thử ban đầu: Customer 16/16 và toàn bộ Playwright 118/118. Đây là kết quả lịch sử, không dùng để chứng minh HEAD hiện tại; xem bản cập nhật review phía dưới.
- `npm run lint`: PASS.
- `npm run build`: PASS.
- Governance: registry **17 skills / 16 routes / 169 files**, routing tests **9/9**, Git Flow tests **8/8** PASS.
- Bảy ảnh tại `docs/handover/evidence/dt2-4/screenshots/` được tạo bởi `frontend/tests/capture-evidence.spec.js` với API mock theo contract. Đây là bằng chứng giao diện, không phải Postman hoặc tích hợp Backend/MySQL thật.

## Giới hạn còn lại

- Luồng QR → mở/join phiên → cấp bearer token chưa nằm trong PR #20. Module đó phải gọi `saveCustomerSession()` bằng credential do server cấp; không truyền token qua URL.
- Chưa chạy browser end-to-end với Backend/MySQL/Firebase thật trong lượt này. Backend PR #14 đã ở `develop`; Firebase cloud vẫn cần cấu hình nhóm và kiểm thử riêng.
- PR cần CI mới sau khi push, các discussion được reviewer xác nhận resolve và đủ hai approval độc lập trước khi maintainer xem xét merge.

## Cập nhật review ngày 19/11/2026

- Retry Order/WAITER/BILL giữ attempt trong bộ nhớ khi sessionStorage lỗi. Bộ nhớ được ưu tiên để dữ liệu storage cũ không ghi đè attempt mới; clear có tombstone nếu removeItem lỗi. Bảo đảm này có hiệu lực trong vòng đời trang hiện tại; không cam kết sống qua reload khi trình duyệt từ chối lưu trữ.
- Trước một Order mới, tải giá chi tiết hiện tại cho từng món. Giá đổi thì cập nhật tổng và yêu cầu khách xác nhận bằng lần bấm gửi tiếp theo; GET lỗi thì chặn POST. Retry chưa rõ kết quả vẫn giữ nguyên key/payload. Backend tiếp tục chốt giá và tổng tại thời điểm ghi.
- GET `/api/v1/customer/sessions/{id}/orders/{batchId}` xác thực token, phiên và chủ sở hữu batch, trả snapshot/version mới nhất với no-store. FE đọc lại khi mở tab đơn, reload hoặc mất phản hồi PATCH/DELETE; khóa sửa/hủy khi chưa xác minh được kết quả và có nút tải lại.
- Giữ menuItemId dạng chuỗi, không đổi ID BIGINT qua Number; bộ lọc mới xóa món của bộ lọc cũ khi page 0 lỗi.
- Lint/build PASS; unit 5/5; Playwright toàn repository **222/222 PASS**, Customer **44/44 PASS** (22 kịch bản × desktop/mobile), dùng API mock. Bảy ảnh trong `docs/handover/evidence/dt2-4/screenshots/` được tạo lại từ lượt này; đã kiểm trực tiếp ảnh giỏ và đơn món.
- Skills: React best practices cho fetch/state; cafe-spring-api cho GET kiểm quyền; cafe-quality/cafe-review cho test lỗi mạng, giá, storage và tự kiểm diff. Self-review không thay review chéo.

### Xác nhận giá trước Order — review bổ sung

Thông báo “Giá món đã thay đổi” dùng action `reviewPrices`: nút **XEM LẠI GIỎ** chỉ đóng modal và chuyển về giỏ, không gọi POST. Khách kiểm tra tổng mới rồi bấm nút gửi đơn trong giỏ để xác nhận. Modal lỗi mạng vẫn có **THỬ LẠI**, giữ nguyên attempt/key/payload chưa rõ kết quả. Test Customer #22 bấm trực tiếp CTA đổi giá và kiểm 0 POST, sau đó xác nhận gửi và retry mạng cùng key/payload. Ảnh mobile mới: `docs/handover/evidence/dt2-4/screenshots/08-fe-price-consent.png` (API mock).

### Deep re-review — giá, ảnh và retry trang0

- Bỏ GET/price-write lúc mount CustomerCartView. Chỉ preflight của submitOrder cập nhật giá trước lần gửi mới; không còn request nền có thể về muộn ghi đè giá mới. Retry outcome chưa rõ vẫn giữ key/payload, không đổi cơ chế idempotency.
- FE kiểm metadata JPEG/PNG trước decode/preview: dữ liệu thật khớp MIME, >0 và ≤5MiB, width/height1–4096 và width×height≤4,000,000, sau đó kiểm ảnh đọc được. Chặn gửi trong lúc kiểm tra; revoke object URL khi đổi ảnh/unmount. Không tự resize/crop hoặc nới giới hạn backend.
- Trang0 menu lỗi giữ error state, có THỬ LẠI THỰC ĐƠN cho cùng filter/page0; empty chỉ xuất hiện khi response thành công nhưng content rỗng. Load-more giữ retry riêng như cũ.
- Regression #18 kiểm 0 GET từ mount, preflight cập nhật giá mới và không tạo stale writer; #23 kiểm503→retry cùngquery→200; #24 kiểmJPEG4032×3024 dưới5MiB, PNG2001×2000/4097×1 bị từ chối và PNG2000×2000 được gửi multipart. Ảnh mobile #09/#10 dùng mock API.
- Lượt cuối: lint/build PASS, unit5/5 và toàn bộ Playwright **226/226 PASS**, Customer **48/48 PASS** (24 kịch bản × desktop/mobile). Không sửa Backend/schema/Firebase trong lượt này; CI Backend kiểm riêng sau push. Kết quả222/222 phía trên là mốc lịch sử trước hai regression mới.

### Price preflight và giá thay đổi trước commit — review bổ sung

- Khóa giỏ bằng guard đồng bộ tại Provider ngay trước GET preflight đến khi POST kết thúc: không đổi số lượng/xóa/thêm từ quick-add hoặc modal, kể cả chuyển tab. Nút disabled có trạng thái thị giác; sau lỗi có thể chỉnh lại giỏ. Retry chưa rõ kết quả vẫn dùng attempt bất biến trước đó; món thêm giữa hai lần thử không bị trộn vào attempt cũ.
- Order mới của FE gửi `expectedUnitPrice` chuỗi VND. Backend so sánh với `menu_items.price` dưới khóa `FOR SHARE` trong transaction trước mọi INSERT; đổi giá trả 409 tiếng Việt, không ghi đơn hoặc giữ key bị từ chối. Khách tải lại giá, xem tổng mới và chủ động gửi lại. Replay đã commit chạy trước kiểm giá hiện hành, hash ràng buộc cả giá kỳ vọng; không tự chuyển giá của attempt đang chưa rõ kết quả.
- Thay đổi API bổ sung tương thích: client/attempt cũ không gửi trường giá vẫn dùng hợp đồng server-price cũ. Không tuyên bố bảo đảm consent cho client bỏ trường. Không sửa schema, QR, Firebase hoặc DT2-5. Chi tiết tại [contract Order](../api/dt2-4-customer-actions.md#giá-đã-xác-nhận-và-retry-order).
- Playwright **230/230 PASS**, gồm Customer **52/52** (26 kịch bản × desktop/mobile, mock API). Regression #25 giữ GET/POST chậm và kiểm cart/quick-add bị khóa, payload không đổi; #26 mô phỏng409→preflight giá mới→XEM LẠI GIỎ (0 POST)→gửi mới với key/giá mới. Unit **5/5**, lint/build PASS. Các số226/226 phía trên là lịch sử.
- MySQL regression Customer Actions **18/18 PASS** trên MySQL8.0.46/schema riêng `smart_cafe_pr20_test`: giá thay đổi sau GET, 409 không ghi batch/item, xác nhận lại thành công, replay sau đổi menu, hash khác giá bị409 và giá sai/biên1VND. Postman collection thêm mismatch409 và gửi/replay cùng giá kỳ vọng. Toàn bộ Maven verify/HTTP mới xem Checks của HEAD PR #20; không lấy kết quả của commit cũ làm bằng chứng mới.
- Hai ảnh mobile mới `11-fe-cart-preflight-lock.png`, `12-fe-price-race-consent.png` tại thư mục screenshots, đã kiểm trực tiếp; dùng API mock, không phải bằng chứng browser→MySQL/Firebase thật.
- Skills áp dụng: `cafe-spring-api` (`.agents/skills/cafe-spring-api/SKILL.md`), `vercel-react-best-practices` (`.agents/skills/react-best-practices/SKILL.md`); kiểm thử/tự review dùng `cafe-quality` và `cafe-review` ở các đường dẫn `.agents/skills/*/SKILL.md` tương ứng. Self-review không thay review độc lập. Không trả lời/Resolve threads hoặc merge.
