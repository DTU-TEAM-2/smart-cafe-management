# DT2-9 — tích hợp frontend vào develop

Ngày 13/11/2026. Chủ repo yêu cầu xử lý conflict, tạo PR vào **develop**, đính kèm ảnh giao diện ngay trong mô tả PR. Chỉ tạo PR để review, không merge hoặc deploy. Báo cáo ngày 11/11 là lịch sử bản FE độc lập; bản này cập nhật đường dẫn và bằng chứng sau tích hợp.

## Phạm vi tích hợp

Nhánh feature/dt2-9-manage-menu-items-and-categories-frontend tích hợp origin/develop 8476ee30536fdd01b521cb53e5bb22d38b3b5833 bằng merge commit, giữ attribution/lịch sử. Giữ bootstrap, Vite, root stylesheet, backend và các màn home/login/sales/income từ develop. Menu xem thử có đường dẫn `/preview/menu`, lazy load và CSS giới hạn dưới `.menu-preview`; không thay trang gốc hoặc tự thêm menu quản trị cho người chưa xác thực.

Giữ sửa lỗi local đã xác minh ngày 11/11: CRUD trong bộ nhớ, 4 tiêu chí tìm kiếm, thông báo khi chưa chọn dòng/tìm trống, xác nhận/hủy xóa nhóm có món, validation theo contract backend, giá 18 chữ số chính xác, kiểm tra ảnh và chống kết quả đọc ảnh cũ. Không nhập code nghiệp vụ/dữ liệu thật từ legacy.

Chỉ thêm Axe vào devDependencies cho kiểm thử accessibility; không đổi version thư viện runtime. CI thêm develop vào pull_request branches để PR chạy checks theo yêu cầu đích của chủ repo; không sửa chính sách merge/toàn bộ nhánh dài hạn.

## Kiểm tra và bằng chứng

Lint và production build đạt; toàn bộ Playwright **50/50 đạt** trên desktop Chromium 1440×1300/mobile Chromium mô phỏng iPhone 13, gồm 24 ca menu và 26 ca hồi quy home/header/login/sales/income. Kiểm thử chờ menu lazy-load hiển thị trước khi chụp ảnh/Axe; fallback loading có main landmark và h1. Lượt trước phát hiện kiểm tra Axe chạy trước khi màn lazy-load hiển thị; đã sửa trình tự chờ và cấu trúc loading.

Registry check: 17 skills/16 routes, checksum 169 inherited files; routing tests 9/9. Whitespace check loại snapshots inherited. Backend/DDL/fixtures không thay đổi so với develop; không chạy DB local hoặc khai báo kiểm thử API mới từ các lượt UI này. CI GitHub xác minh riêng sau khi mở PR.

CI lượt đầu của PR #13 (run 43346500969) lỗi trước khi chạy Maven: health check socket chấp nhận server khởi tạo MySQL tạm (port 0); trong lúc chuyển sang server chính, import schema gặp socket chưa tồn tại. Đổi health check và import schema sang TCP 127.0.0.1 để chỉ chạy sau khi server chính mở cổng; không sửa schema, dữ liệu hay test backend để che lỗi.

Ảnh giao diện hiện tại ở `develop-integration-2026-11-13/`: danh sách món, danh sách nhóm, thêm món, sửa món, form nhóm món, xác nhận xóa món và xác nhận xóa nhóm có món, cho cả desktop/mobile. Chụp trong Playwright bằng dữ liệu minh họa; PR nhúng ảnh dùng URL ghim commit để reviewer xem đúng bản.

## Giới hạn

Menu preview vẫn dùng dữ liệu trong bộ nhớ và ảnh cục bộ, tải lại khôi phục mẫu; chưa nối API/auth/DB/Cloudinary/menu khách. Không tick toàn bộ tiêu chí nghiệm thu hệ thống bằng kết quả fixture UI. Không có công cụ Aikido MCP khả dụng để chạy SAST; self-review không thay review độc lập.

Skills: cafe-workflow (.agents/skills/cafe-workflow/SKILL.md) cho tích hợp/handoff; vercel-react-best-practices (.agents/skills/react-best-practices/SKILL.md) cho lazy load/state; fixing-accessibility (.agents/skills/fixing-accessibility/SKILL.md) cho loading/focus/landmarks; cafe-quality và cafe-review (SKILL.md trong thư mục tương ứng) cho kiểm chứng và self-review.
