# DT2-4 — Nghiệp vụ khách trên phiên đã có

Các API này không mở/tham gia phiên và không phát token. Cần một `session_clients` hợp lệ đã được phần quản lý phiên tạo. Không lấy tableId/browser state làm quyền. Tất cả response `Cache-Control: no-store`; POST/PATCH/DELETE yêu cầu CSRF qua cookie session và header được trả từ `GET /api/v1/customer/csrf`.

## Xác thực và đồng thời

`Authorization: Bearer <opaque-token>`: token base64url không dấu `=`, 43–256 ký tự; lưu/tra SHA-256 hex thường tại `access_token_hash`. Chỉ nhận token server phát với entropy đủ mạnh; không dùng UUID/số bàn thay token. Không log token hoặc đặt trong URL. TTL không cố định ở các API này: kiểm `expires_at > Clock.now`, `revoked_at IS NULL`, scope `session_id` đúng. Phiên CLOSED/CANCELLED và bàn ngừng phục vụ bị từ chối.

Các thao tác ghi khóa theo thứ tự bàn → phiên → client; cùng thứ tự với phần Sales trước khi khóa dòng/batch/request. Không cho đổi món khi phiên CHECKOUT hoặc đã có bill chưa VOID. Token bị thu hồi/hết hạn trả 401, không tiết lộ phiên khác. CSRF sai/thiếu trả 403.

## API

| Method / route | Payload / header | Kết quả |
| --- | --- | --- |
| GET `/api/v1/customer/sessions/{id}/orders/{batchId}` | Bearer của client đã gửi batch; không cần CSRF | 200 snapshot/version/tổng hiện tại, no-store; 401 token/scope/hạn không hợp lệ, 404 batch không thuộc client. Đọc được trong CHECKOUT để phục hồi kết quả; không mở quyền ghi. |
| POST `/api/v1/customer/sessions/{id}/orders` | `Idempotency-Key`, `{"items":[{"menuItemId":"12","quantity":2,"expectedUnitPrice":"45000"}]}` | 200, batch và các dòng snapshot, tổng VND dạng chuỗi, editableUntil; 400 rỗng/sai, 409 món ngừng phục vụ/giá khác giá đã xác nhận/mã gửi lại khác payload. |
| PATCH `/api/v1/customer/sessions/{id}/order-items/{itemId}` | `{"quantity":3,"expectedVersion":0}` | 200 batch hiện tại, tăng version; 404 không phải người gửi; 409 hết hạn, đã chuẩn bị, phiên bản cũ hoặc đã lập bill. |
| DELETE `/api/v1/customer/sessions/{id}/order-items/{itemId}` | `{"expectedVersion":1}` | 200, đánh dấu CANCELLED cùng mốc thời gian/lý do, giữ lịch sử; không DELETE vật lý. |
| POST `/api/v1/customer/sessions/{id}/waiter-requests` | `Idempotency-Key`, không cần body | 200, yêu cầu WAITER đúng phiên. |
| POST `/api/v1/customer/sessions/{id}/bill-requests` | `Idempotency-Key`, không cần body | 200, yêu cầu BILL; 400 nếu chưa có món, không tạo bill/payment. |

Chỉ gửi **các món mới chưa Order** trong payload batch. Gửi lại cùng thao tác phải giữ nguyên Idempotency-Key (1–64 ASCII không có khoảng trắng); cùng key/payload trả lại batch hiện tại, không tăng quantity. Đổi key biểu thị một lần Order mới có chủ ý, kể cả gọi thêm cùng món. Không gửi toàn bộ giỏ tích lũy hoặc tạo key mới khi retry mạng. Một request tối đa 100 món phân biệt; số lượng nguyên dương không vượt int MySQL, kiểm tổng theo DECIMAL(18,0).

Danh sách món được sắp theo ID trước khi băm payload và khóa; batch chỉ ghi khi tất cả món/danh mục active và available. Server không lấy giá/tên/tổng từ client. Snapshot không đổi khi menu đổi giá. Giới hạn sửa/hủy là 180 giây và trạng thái SUBMITTED theo xác nhận QA-03 ngày 13/11/2026; tại đúng editableUntil đã hết hạn. Chỉ client đã gửi batch được sửa/hủy, không mở quyền sửa đơn của thiết bị khác.

### Giá đã xác nhận và retry Order

FE gửi `expectedUnitPrice` cho mỗi món mới: chuỗi số nguyên VND dương, tối đa 18 chữ số (đúng constraint giá menu), không làm tròn qua JavaScript Number. Đây là điều kiện so sánh, không phải giá có thẩm quyền. Server vẫn lấy `menu_items.price` và tạo snapshot từ MySQL; so sánh dưới khóa `FOR SHARE` trong cùng transaction trước mọi INSERT. Khác giá trả 409 Problem Details tiếng Việt, không tạo batch/item và không lưu khóa lần gửi bị từ chối. FE tải lại giá, hiển thị tổng mới và yêu cầu khách xem lại giỏ rồi chủ động gửi lại; không tự đặt theo giá mới.

Giá kỳ vọng được ràng buộc trong request hash. Replay cùng khóa/payload của batch đã commit được đọc trước kiểm giá menu hiện tại, nên mất response rồi menu đổi giá vẫn trả batch cũ, không tạo đơn trùng. Retry chưa rõ kết quả giữ nguyên khóa và payload, kể cả giá kỳ vọng.

Trường này bổ sung tương thích: client cũ/attempt đã lưu không có `expectedUnitPrice` vẫn dùng giá server như hợp đồng trước; không tuyên bố bảo đảm xác nhận giá cho client bỏ trường. Không đổi schema hoặc giá lịch sử. FE khóa thêm/xóa/đổi số lượng ngay từ khi bắt đầu preflight đến khi POST kết thúc, kể cả đổi tab.

Service request dùng ràng buộc duy nhất hiện có `(session_id,idempotency_key)` và `active_request_key`. Gửi lại khóa đã lưu với cùng loại trả HTTP 200 cùng ID và trạng thái hiện tại, kể cả khi yêu cầu đã kết thúc. Dùng khóa đã lưu cho loại khác trả 409.

Nếu gửi khóa mới trong lúc có yêu cầu cùng loại đang PENDING hoặc ACKNOWLEDGED, API trả 409 với thông báo mã chưa được chấp nhận hoặc lưu. Sau khi yêu cầu cũ kết thúc, khách có thể dùng khóa chưa được lưu đó cho một thao tác mới có chủ ý. Khi retry vì mất kết nối, giữ nguyên khóa của thao tác ban đầu. Header đọc ORDER/WAITER từ DB và Sales đọc BILL.

Problem Details tiếng Việt: 400 dữ liệu sai, 401 token/scope/hạn, 403 CSRF/route không mở, 404 dòng không thuộc người gửi, 409 trạng thái/version/idempotency, 503 DB gián đoạn (retry giữ key). Không lưu đơn offline. Provider phát token và DDL triển khai vẫn cần bàn giao riêng, không tính phần QR đã hoàn tất.
