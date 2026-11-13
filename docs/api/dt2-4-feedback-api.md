# DT2-4 — Gửi Feedback và chuẩn bị Firebase Storage

Nguồn: prompt chủ repo, Jira DT2-4/DT2-33 và [ảnh phản hồi](../handover/evidence/dt2-4/reference/05-feedback.png). Ảnh chỉ có rating/nội dung; yêu cầu tên/email theo Jira và các cột author_name/author_email hiện có, không bịa trường còn thiếu từ thiết kế.

## Gửi phản hồi văn bản

1. `GET /api/v1/customer/feedback/csrf` lấy `headerName`, `token` và giữ cookie session. Không cache hoặc log token.
2. `POST /api/v1/customer/feedback`, application/json, header CSRF tương ứng và cùng cookie:

```json
{"name":"Khách kiểm thử","email":"customer@example.invalid","content":"Đồ uống ngon!","rating":5}
```

201 trả `{"id":"...","message":"Cảm ơn bạn đã gửi phản hồi!"}`. Không trả email, token hoặc thông tin định danh. Tên ≤160, email hợp lệ ≤255, nội dung bắt buộc và ≤65.535 byte UTF-8 theo TEXT MySQL, rating nguyên 1–5. Tên/nội dung được strip. Không nhận session/user/media ID để gán quyền; phản hồi vãng lai lưu các FK nullable là NULL và status mặc định UNREAD.

400: thiếu trường/sai định dạng/rating hoặc JSON; fieldErrors chỉ gồm tên trường và thông báo Việt hóa, không echo giá trị email/nội dung. 503 DB lỗi, không lộ SQL/host/stack. POST thiếu CSRF bị 403. Không mở API GET danh sách/chi tiết hoặc PUT/DELETE; DT2-5 đang tạm dừng. Không có ràng buộc idempotency cho feedback trong schema: mỗi lần gửi hợp lệ là một phản hồi mới, không tự retry POST không rõ kết quả.

## Multipart với ảnh

Cùng POST, `multipart/form-data`, part `feedback` có Content-Type application/json như trên, một đến ba part `images`. Chỉ JPEG/PNG đọc được từ bytes thật, mỗi ảnh ≤5 MiB, mỗi cạnh 1–4096 pixel và tối đa 4.000.000 điểm ảnh. Backend kiểm tra kích thước trước khi giải mã raster. Request tối đa 16 MiB. Ảnh được gửi cùng phản hồi mới.

Kiểm tra tất cả file trước upload. Cloud calls ngoài DB transaction; transaction lưu feedback + media_assets(provider FIREBASE) + feedback_images/sort_order. Không lưu binary ở MySQL. Nếu upload/persistence thất bại, rollback DB và cố gắng xóa object đã upload; lỗi cleanup ghi thông báo không chứa tên/email/credential. Process crash hoặc provider không phản hồi có thể để object mồ côi: cần cơ chế đối soát/lifecycle của thành viên vận hành trước production, không tuyên bố có distributed transaction.

## Biến môi trường và phần đang chờ

Mặc định mỗi tiến trình nhận tối đa 20 lượt POST Feedback/phút cho mỗi địa chỉ nguồn và 120 lượt/phút tổng cộng, tính theo phút UTC. Bộ đếm chạy trước khi đọc body và tính cả lượt sai CSRF hoặc validation. Quá ngưỡng trả `429` kèm `Retry-After` theo giây. Giới hạn dùng địa chỉ do servlet container cung cấp. Khi triển khai sau reverse proxy, chỉ cấu hình nhận địa chỉ chuyển tiếp từ proxy tin cậy. Nhiều replica cần quota chung ở tầng proxy/gateway.

Mỗi tiến trình mặc định nhận tối đa 2 request multipart đồng thời, tính từ trước khi parse đến khi xử lý xong upload và ghi DB. Request vượt giới hạn trả `503` kèm `Retry-After: 1`. Với mặc định này, tối đa 2 raster đang giải mã (mỗi raster ≤4 triệu điểm ảnh) và 30 MiB dữ liệu ảnh nén giữ trong các lượt upload, chưa tính chi phí bộ nhớ của decoder, SDK và servlet. Đo heap/CPU ở môi trường triển khai trước khi nâng giới hạn hoặc bật Firebase.

GET cấp CSRF có hạn mức riêng mặc định 12 lượt/phút/nguồn và 60 lượt/phút/tiến trình, tính cả lượt có cookie. Quá hạn mức trả `429` kèm `Retry-After` và `Cache-Control: no-store` trước khi tạo session. Client dùng lại token và cookie để tránh gọi GET nhiều lần. Khi triển khai, cấu hình giới hạn và thời gian hết hạn của session store theo tài nguyên thực tế, đồng thời áp dụng quota GET chung tại gateway nếu có nhiều replica.

```dotenv
FEEDBACK_FIREBASE_ENABLED=false
FIREBASE_STORAGE_BUCKET=
GOOGLE_APPLICATION_CREDENTIALS=
FEEDBACK_PER_SOURCE_PER_MINUTE=20
FEEDBACK_TOTAL_PER_MINUTE=120
FEEDBACK_CONCURRENT_MULTIPART=2
FEEDBACK_CSRF_PER_SOURCE_PER_MINUTE=12
FEEDBACK_CSRF_TOTAL_PER_MINUTE=60
```

Admin SDK 9.11.0 dùng Application Default Credentials; GOOGLE_APPLICATION_CREDENTIALS là đường dẫn file ngoài Git, không phải JSON/key dán vào .env.example. Không hardcode project/bucket/credential, không tự tạo project/billing/IAM. Chỉ bật enabled khi thành viên phụ trách cung cấp bucket và quyền cần thiết. Chưa cấu hình thì multipart ảnh hợp lệ trả 503, không ghi feedback/media; Feedback JSON vẫn hoạt động.

Bucket/object không được tự mở public. secure_url là URI HTTPS ổn định của object riêng tư; provider_asset_id là key `feedback/<UUID>.<ext>`. Khi DT2-5 được làm tiếp cần bàn giao cách đọc ảnh qua server/URL có thời hạn phù hợp; URL metadata không được xem là đã kiểm chứng hiển thị ảnh thật.

Test adapter mock + MySQL kiểm chứng liên kết, transaction rollback và cleanup; **không phải Firebase cloud PASS**. Cần upload/download thật bằng Postman và xác minh metadata trên môi trường được giao quyền sau khi có cấu hình. [Tài liệu Admin SDK](https://firebase.google.com/docs/admin/setup) · [Storage Admin](https://firebase.google.com/docs/storage/admin/start).
