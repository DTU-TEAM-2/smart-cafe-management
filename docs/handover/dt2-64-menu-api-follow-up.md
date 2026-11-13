# DT2-64 — bàn giao tích hợp menu FE với API

Nguồn: review PR #13 ngày 13/11/2026, [DT2-64 do reviewer dẫn](https://duymanhdnvn20092005.atlassian.net/browse/DT2-64). Đây là checklist follow-up, không phải kết quả tích hợp đã hoàn tất. Đầu mối FE/handoff là tác giả PR #13 `tranvanhuy13`; phân công owner sprint và phối hợp auth/backend cần maintainer/nhóm xác nhận. Không tự gán task cho reviewer hoặc nhận thông tin Jira chưa đọc thành quyết định được phê duyệt.

PR #13 chỉ bàn giao preview chạy trong dev, dữ liệu synthetic trong bộ nhớ; không tick Done DT2-9/DT2-64 hoặc gọi đây là frontend production đã tích hợp. Các tiêu chí cần hoàn tất riêng:

- ADMIN Bearer từ auth thật; CSRF cookie/header; xử lý 401/403, logout/revocation, không demo account.
- Danh sách, lọc/tìm kiếm/phân trang do server quyết định; loading/empty/error/retry, hủy response cũ.
- CRUD với version, 409/stale dữ liệu, 404, xác nhận/hủy xóa nhóm có món; backend quyết định giá/trạng thái/transaction.
- Contract tên/nhóm theo collation hệ thống: không phân biệt case/accent, wildcard `%`/`_`/`!` literal. Không dùng normalize của fixture làm nguồn quyền cho dữ liệu thật; kiểm chứng bằng MySQL/API thật.
- Serialize/parse giá decimal(18,0) mà không đi qua Number làm mất chính xác; thống nhất API–FE trước tích hợp.
- Upload thật trả mediaId, lưu imageMediaId, đọc URL từ server; ảnh Cloudinary xuất hiện sau tạo/sửa trên menu khách, kiểm tra lỗi/retry/provider và cleanup theo contract.
- Hồi quy menu khách/đặt món, bảo toàn lịch sử giá; dữ liệu test chỉ trong schema test được guard, không seed DB nghiệp vụ.
- Evidence FE→API thật/MySQL/Cloudinary và review độc lập; không dùng PASS fixture UI thay bằng chứng nghiệm thu hệ thống.

Phụ thuộc Git Flow: PR #12 ban đầu chưa merge khi đọc review; sau đó đã được maintainer merge vào develop ở 5cc4132. Nhánh PR #13 đã tích hợp baseline được duyệt đó, giữ workflow Git Flow/validator/CODEOWNERS và fixture bán hàng từ develop; chỉ giữ thêm sửa readiness MySQL qua TCP. Chạy đủ required checks, bao gồm Git Flow, trên revision mới và lấy approval hợp lệ trước merge. Không bypass protection/approval hoặc gọi kết quả bốn check cũ là 5/5. Người đặt discussion xác nhận/resolve sau review lại; không tự tính reply là approval.
