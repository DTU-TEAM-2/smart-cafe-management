# DT2-9 — kết quả rà soát và sửa FE local

Nhánh feature/dt2-9-manage-menu-items-and-categories-frontend, baseline a2aa41a, ngày 11/11/2026. [Yêu cầu và nguồn đối chiếu](../../handover/dt2-9-frontend-local-review-2026-11-11.md). Thay đổi nằm trong working tree local, chưa commit/push, không tạo PR.

## Lỗi đã xử lý

| Lỗi/hạn chế trước sửa | Kết quả sau sửa |
| --- | --- |
| Tìm gộp mã/tên/nhóm, thiếu giá, không có tìm trống/Show List Service | Chọn mã/tên/giá/nhóm; mã và giá chính xác, tên/nhóm không dấu; thông báo trống/không kết quả và nút hiển thị tất cả xóa bộ lọc/reset trang |
| Chưa có thao tác với dòng được chọn và thông báo khi chưa chọn | Radio chọn dòng, sửa/xóa đã chọn, báo khi chưa chọn; xóa lựa chọn khi đổi bộ lọc/trang/danh sách |
| Chặn xóa nhóm đang có món | Dialog báo số món bị ảnh hưởng; hủy giữ nhóm/món; xác nhận ẩn cả nhóm và món; mã đã ẩn không tái sử dụng trong lượt xem thử |
| Validation tạm khác backend: giá >0, giới hạn 9 số, đổi nhóm, thiếu min/max tên/mã, chặn trùng tên, description | Theo contract PR #8; giá lưu chuỗi/format BigInt để giữ chính xác 18 chữ số, nhóm không đổi khi sửa, bỏ mô tả/trùng tên ngoài contract |
| File chỉ kiểm tra MIME/size, chấp nhận WebP và ảnh hỏng; đọc tệp có thể hoàn thành sai thứ tự | PNG/JPEG kiểm tra signature/giải mã/size/pixel, báo lỗi và chặn lưu, chỉ lần chọn mới nhất cập nhật preview; bỏ ảnh hủy lượt đọc đang chờ |
| Lỗi form chung, khó xác định trường sai | Lỗi cạnh trường, aria-describedby/aria-invalid, focus trường lỗi đầu tiên; giữ dialog focus/keyboard/mobile |

## Kiểm chứng

- Baseline trước sửa: 12/12 UI tests đạt; các thiếu sót trên chưa nằm trong coverage cũ.
- npm ci --offline: cài đúng lockfile, không đổi dependency; npm run lint và npm run build đạt.
- npm run test:ui -- --workers=2: **24/24 đạt**, 12 hành vi × desktop Chromium 1440×1000/mobile Chromium mô phỏng iPhone 13. Có regression giá 18 số, mã đã xóa, xóa liên quan/hủy, lựa chọn bị ẩn, 4 tiêu chí, ảnh hỏng/sai MIME/WebP/vượt 5 MiB/20 triệu pixel/đọc ngược thứ tự và giữ preview sau sửa.
- Axe trên danh sách món/nhóm, form, dialog: không có violation được phát hiện; kiểm tra focus bàn phím và trang không tràn ngang. Không tuyên bố audit WCAG đầy đủ.
- Ảnh tại local-review-2026-11-11/: desktop/mobile list, categories, editor, delete; chụp từ Playwright, chỉ có fixture hiển thị.
- git diff --check loại inherited skill snapshots đạt. Không sửa backend, dependency, CI, auth hoặc DB; không chạy Maven/kiểm thử DB vì thay đổi chỉ ở FE.

## Giới hạn và handoff

Chưa nối API hoặc tích hợp main mới, chưa upload Cloudinary, chưa kiểm tra menu khách hay dữ liệu thật. Không tick toàn bộ tiêu chí nghiệm thu hệ thống bằng các kết quả UI fixture này. Không có công cụ Aikido MCP khả dụng nên chưa chạy SAST. Self-review không thay review độc lập.

Skills đã đọc: cafe-requirements (.agents/skills/cafe-requirements/SKILL.md) để đối chiếu phạm vi; vercel-react-best-practices (.agents/skills/react-best-practices/SKILL.md) để giữ state bất biến và tránh kết quả bất đồng bộ cũ; fixing-accessibility (.agents/skills/fixing-accessibility/SKILL.md) để sửa lỗi/focus/nhãn; cafe-quality và cafe-review (cùng thư mục tương ứng) cho verification/self-review; ui-ux-pro-max (.agents/skills/ui-ux-pro-max/SKILL.md), query “error summary validation” domain ux, đối chiếu kết quả Forms/Accessibility để giữ lỗi tại trường và điều hướng bàn phím. Không thay hệ thiết kế hoặc tải lại Figma trong lượt này.
