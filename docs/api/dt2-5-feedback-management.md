# DT2-5 — đọc Feedback quản trị

Chỉ ADMIN thông qua signed bearer của Auth và role/status/auth_version thực tế trong MySQL. Không phát token, không cho STAFF/CUSTOMER đọc email/nội dung. Cả hai endpoint trả `Cache-Control: no-store`. Route nằm dưới matcher `/api/v1/admin/**` hiện có; không sửa chính sách CSRF hoặc mở public.

| Method / route | Parameters | Response |
| --- | --- | --- |
| GET `/api/v1/admin/feedbacks` | `date=YYYY-MM-DD` tùy chọn; page mặc định0, size mặc định20/max100; sort newest/oldest | content[], totalElements, page, size |
| GET `/api/v1/admin/feedbacks/{id}` | ID BIGINT | id, creator, email, createdAt, date, content, rating, images[] |

Summary chứa id dạng chuỗi, creator từ author_name, email từ author_email, date là ngày quán, createdAt là instant UTC; content preview tối đa200 ký tự MySQL; rating 1–5. Chi tiết trả nguyên TEXT. ID giữ chuỗi cả BE/FE, không chuyển qua JS Number.

Tìm ngày dùng `[00:00 Asia/Ho_Chi_Minh, 00:00 ngày tiếp theo)`, đổi sang UTC để query indexed created_at. Sort cố định created_at,id theo cùng chiều, phân trang offset an toàn long. Không dùng DATE(created_at) làm mất khả năng dùng range index. Count/list và detail/images nằm trong transaction read-only; một trang không query ảnh từng dòng. Chi tiết đọc ảnh bằng một JOIN theo sort_order,media id.

Ảnh chỉ đọc metadata chưa deleted; mỗi phần tử có id, mimeType, url. URL không phải HTTPS hoặc chứa userinfo trả null. Bucket private có thể trả403 khi trình duyệt tải; FE báo chưa khả dụng. Không tự ký URL, không mở bucket hay bật Firebase. Metadata HTTPS không đồng nghĩa ảnh public hoặc đã có quyền xem.

Lỗi: 400 query sai/ngày ngoài1000–9998; 401 thiếu/sai/hết hạn token; 403 sai role; 404 ID không tồn tại; 503 DB không khả dụng. Problem Details không trả SQL/stack trace. GET không đánh dấu READ, không có API sửa/xóa hoặc thay đổi schema.

Nguồn: prompt owner đã lưu nội dung yêu cầu tại [kiểm kê](../handover/dt2-5-requirements.md); DDL bàn giao `V20261113_01__smart_cafe_schema.sql`: feedback, feedback_images, media_assets. Không coi fixture test là DDL production đã nghiệm thu. POST khách DT2-4 được giữ nguyên.
