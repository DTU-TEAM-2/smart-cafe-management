# DT2-4 — Nghiệp vụ khách trên phiên đã có

Các API này không mở/tham gia phiên và không phát token. Cần một `session_clients` hợp lệ đã được phần quản lý phiên tạo. Không lấy tableId/browser state làm quyền. Tất cả response `Cache-Control: no-store`; POST/PATCH/DELETE yêu cầu CSRF qua cookie session và header được trả từ `GET /api/v1/customer/csrf`.

## Xác thực và đồng thời

`Authorization: Bearer <opaque-token>`: token base64url không dấu `=`, 43–256 ký tự; lưu/tra SHA-256 hex thường tại `access_token_hash`. Chỉ nhận token server phát với entropy đủ mạnh; không dùng UUID/số bàn thay token. Không log token hoặc đặt trong URL. TTL không cố định ở các API này: kiểm `expires_at > Clock.now`, `revoked_at IS NULL`, scope `session_id` đúng. Phiên CLOSED/CANCELLED và bàn ngừng phục vụ bị từ chối.

Các thao tác ghi khóa theo thứ tự bàn → phiên → client; cùng thứ tự với phần Sales trước khi khóa dòng/batch/request. Không cho đổi món khi phiên CHECKOUT hoặc đã có bill chưa VOID. Token bị thu hồi/hết hạn trả 401, không tiết lộ phiên khác. CSRF sai/thiếu trả 403.

## API

| Method / route | Payload / header | Kết quả |
| --- | --- | --- |
| POST `/api/v1/customer/sessions/{id}/orders` | `Idempotency-Key`, `{"items":[{"menuItemId":12,"quantity":2}]}` | 200, batch và các dòng snapshot, tổng VND dạng chuỗi, editableUntil; 400 rỗng/sai, 409 món ngừng phục vụ/mã gửi lại khác payload. |
| PATCH `/api/v1/customer/sessions/{id}/order-items/{itemId}` | `{"quantity":3,"expectedVersion":0}` | 200 batch hiện tại, tăng version; 404 không phải người gửi; 409 hết hạn, đã chuẩn bị, phiên bản cũ hoặc đã lập bill. |
| DELETE `/api/v1/customer/sessions/{id}/order-items/{itemId}` | `{"expectedVersion":1}` | 200, đánh dấu CANCELLED cùng mốc thời gian/lý do, giữ lịch sử; không DELETE vật lý. |
| POST `/api/v1/customer/sessions/{id}/waiter-requests` | `Idempotency-Key`, không cần body | 200, yêu cầu WAITER đúng phiên. |
| POST `/api/v1/customer/sessions/{id}/bill-requests` | `Idempotency-Key`, không cần body | 200, yêu cầu BILL; 400 nếu chưa có món, không tạo bill/payment. |

Chỉ gửi **các món mới chưa Order** trong payload batch. Gửi lại cùng thao tác phải giữ nguyên Idempotency-Key (1–64 ASCII không có khoảng trắng); cùng key/payload trả lại batch hiện tại, không tăng quantity. Đổi key biểu thị một lần Order mới có chủ ý, kể cả gọi thêm cùng món. Không gửi toàn bộ giỏ tích lũy hoặc tạo key mới khi retry mạng. Một request tối đa 100 món phân biệt; số lượng nguyên dương không vượt int MySQL, kiểm tổng theo DECIMAL(18,0).

Danh sách món được sắp theo ID trước khi băm payload và khóa; batch chỉ ghi khi tất cả món/danh mục active và available. Server không lấy giá/tên/tổng từ client. Snapshot không đổi khi menu đổi giá. Giới hạn sửa/hủy là 180 giây và trạng thái SUBMITTED theo xác nhận QA-03 ngày 13/11/2026; tại đúng editableUntil đã hết hạn. Chỉ client đã gửi batch được sửa/hủy, không mở quyền sửa đơn của thiết bị khác.

Service request dùng uniqueness hiện có `(session_id,idempotency_key)` và active_request_key. Cùng key khác loại bị 409; một yêu cầu cùng loại đang PENDING/ACKNOWLEDGED được gộp về request đó, kể cả thiết bị khác cùng phiên. Key khác bị gộp không tạo thêm receipt riêng: sau khi request đã kết thúc, thao tác mới dùng key mới. Header hiện có đọc ORDER/WAITER từ DB; Sales đọc BILL, không tạo hệ thống thông báo riêng hoặc giả WebSocket đã tích hợp.

Problem Details tiếng Việt: 400 dữ liệu sai, 401 token/scope/hạn, 403 CSRF/route không mở, 404 dòng không thuộc người gửi, 409 trạng thái/version/idempotency, 503 DB gián đoạn (retry giữ key). Không lưu đơn offline. Provider phát token và DDL triển khai vẫn cần bàn giao riêng, không tính phần QR đã hoàn tất.
