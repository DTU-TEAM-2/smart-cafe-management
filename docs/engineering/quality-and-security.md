# Chất lượng, bảo mật và vận hành

## Checks theo thay đổi

| Loại thay đổi | Bằng chứng cần có |
| --- | --- |
| Tài liệu / skill / quy trình | Liên kết/path đúng, registry/routing kiểm tra đạt, đọc lại nội dung |
| Java / API | Maven verify, test hành vi và lỗi/quyền liên quan |
| React / UI | Lint, production build, kiểm tra trạng thái/keyboard/layout liên quan |
| Order / bill / phiên | Thêm case gửi lặp, đồng thời, sai phiên, hết quyền, giá và ranh giới thời gian có liên quan |
| Migration | Chạy trên engine thật, nâng từ schema trước, tác động lịch sử và recovery |
| Release | Candidate revision + CI, smoke trên môi trường đích, rollback |

Kiểm thử theo rủi ro; không đặt coverage phần trăm tùy ý hoặc viết test chỉ lặp lại implementation. Baseline hiện tại có integration test health và cấu hình deny-by-default; chưa chứng minh RBAC/order/bill chưa xây dựng. Không ghi browser/device test nếu chưa quan sát thật.

## Mức độ finding

- **P0:** đang gây gián đoạn nghiêm trọng, lộ dữ liệu/credential hoặc sai tiền diện rộng; dừng rollout và báo đầu mối ngay.
- **P1:** sai quyền, sai dữ liệu/tiền hoặc hỏng luồng chính có thể tái hiện; chặn merge/release phần bị ảnh hưởng.
- **P2:** lỗi hành vi có ảnh hưởng nhưng có cách tránh; ưu tiên sửa trong kế hoạch phù hợp.
- **P3:** cải thiện nhỏ/nit; không tự biến thành blocker nếu không có tác động được giải thích.

Ghi trigger, tác động, file/line và cách xác minh; tránh tranh luận chỉ dựa vào sở thích.

## Bảo mật trong phát triển

API kiểm tra quyền và resource ownership ở backend; UI ẩn nút chỉ là trải nghiệm. Health public không hiển thị chi tiết môi trường. Endpoint mới phải có allowlist/authorization rõ và test; repo chỉ mở GET health và GET home highlights, từ chối các endpoint khác cho đến khi có authorization phù hợp.

Khi auth được triển khai, hash mật khẩu bằng thuật toán phù hợp qua Spring Security, không plaintext; chọn session/token, CSRF/CORS và logout/expiry theo thiết kế có ghi nhận. Không tự tắt CSRF/CORS để chữa lỗi frontend. Kiểm soát reset token một lần/có hạn, brute-force và thay đổi quyền theo phạm vi thực tế.

Không commit `.env`, key, token, database dump, dữ liệu cá nhân nhân viên hoặc tài khoản production. `.env.example` chỉ có tên biến và giá trị local không nhạy cảm. Không log password/reset token/auth header. Tránh đưa dữ liệu thật vào ảnh chụp test và mẫu seed.

Lockfile được commit. Dependency thay đổi có lý do, kiểm tra tương thích và thông báo bảo mật phù hợp. Không dùng `npm audit fix --force` hay nâng major hàng loạt để làm xanh một báo cáo mà chưa kiểm tra tác động.

## Cấu hình và vận hành

Tách dev/staging/production. Build reproducible từ lockfile và Maven wrapper. Secrets cấp theo môi trường, quyền tối thiểu. CI pull request chỉ đọc repo và không có credential triển khai.

Trước khi vận hành thật, bổ sung readiness/dependency monitoring, log/trace có request ID, retention, backup và thử khôi phục theo hạ tầng đã chốt. Đây là đầu việc release, chưa phải cam kết đã triển khai. Với sự cố: lưu bằng chứng được lọc, giảm tác động, rollback hoặc hotfix, kiểm tra hồi phục, rồi ghi nguyên nhân và hành động phòng ngừa.
