# DT2-9 — đối chiếu Figma và contract sau re-review

Ngày 13/11/2026, chủ repo yêu cầu đọc lại comment PR #13 và Figma rồi xử lý các điểm reviewer nêu. Đã đọc trực tiếp design context kèm screenshot của file `7iFkUz20p6rE2fPC9Xn8rp`, các frame [danh sách 8:292](https://www.figma.com/design/7iFkUz20p6rE2fPC9Xn8rp/LECKER?node-id=8-292), [form 8:319](https://www.figma.com/design/7iFkUz20p6rE2fPC9Xn8rp/LECKER?node-id=8-319), [xác nhận xóa 57:306](https://www.figma.com/design/7iFkUz20p6rE2fPC9Xn8rp/LECKER?node-id=57-306). Không chỉnh sửa file Figma hoặc schema.

## Đối chiếu nguồn

| Thành phần | Figma hiện tại | FE / API / DB hiện tại |
| --- | --- | --- |
| Form món | Mã, tên, danh mục, đơn giá, Mô tả, ảnh; form hai cột | FE giữ các trường theo contract, không có Mô tả vì menu_items/API không có description |
| Định dạng ảnh | Nhãn JPG / PNG / WebP, tối đa 5 MB | FE và backend chỉ PNG/JPEG; preflight pixel trước decode; không hỗ trợ WebP |
| Phong cách | Nền #100e0c, sidebar #1d1916 rộng 253 px, panel #302923, gold #d5a953, Inter/Playfair Display | Đã áp dụng trong menu.css, có scoped styles/mobile; active text sáng hơn để đạt contrast |
| Dialog | 630×352, nền #3d3128, viền #6a5543, tiêu đề #f1bf77, nút Hủy/Xác nhận | Cùng token; nội dung xóa logic/cascade theo contract đã ghi nhận, không tự đưa luật từ chối xóa vì đơn hàng cũ trong prototype thành luật backend |

Design context ba frame có markup/text, không cung cấp static image/SVG riêng cần tải để dùng làm asset. Ảnh món vẫn là dữ liệu động của preview; không lấy screenshot Figma làm asset triển khai.

## Quyết định của chủ repo

Comment P3 [4559372697](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/13#discussion_r4559372697) xác định đây là xung đột Figma–domain contract, không khẳng định FE sai và không yêu cầu tự thêm cột DB. Sau khi được hỏi các lựa chọn, chủ repo trả lời ngày 13/11/2026: **“Giữ contract hiện tại; ghi rõ chênh lệch và chờ PO chốt Figma”.**

- Giữ FE/API/DB hiện tại, không thêm description, không bật WebP và không đổi schema/constraint hoặc tự mở rộng nhiệm vụ backend.
- Chênh lệch Mô tả/WebP còn tồn tại trong prototype, được ghi nhận rõ để PO/UX/BE thống nhất cập nhật Figma. Chưa coi giao diện khớp tuyệt đối Figma và không ghi “đã sửa Figma”.
- Không thay đổi hành vi CRUD/quyền/giá/soft delete để khớp caption prototype chưa thống nhất. Phạm vi dev preview và follow-up tích hợp thật giữ nguyên.
- Thread P3 giữ mở cho quyết định thiết kế của PO, không tự resolve bằng cách thêm field/format ngoài contract.

Các finding cũ về ảnh trước decode và Git Flow đã được chính reviewer tranhoainam298 xác nhận có thể resolve trong các reply 4559360215/4559360558; đã resolve theo sự đồng thuận đó. Hai finding contract tìm kiếm và production guard đã được reviewer deptraivodich xác nhận và resolve. Không reopen hoặc sửa các hành vi đã được xác nhận chỉ để giống prototype khi chúng ảnh hưởng contract dữ liệu.

## Kiểm chứng của lượt đối chiếu

Lượt này chỉ bổ sung nguồn/ghi nhận quyết định và phản hồi review; không đổi code UI/backend hoặc Figma. Kiểm authored whitespace và link/path tài liệu; không chạy lại application tests hoặc lấy số test cũ làm kết quả chạy mới. Skills: cafe-requirements (.agents/skills/cafe-requirements/SKILL.md) để xử lý nguồn xung đột và figma-design-to-code (skill Figma đã đọc) để đọc design context/screenshot thực tế.
