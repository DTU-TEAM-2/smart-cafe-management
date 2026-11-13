# LECKER — FE quản lý món và nhóm món

```powershell
cd frontend
npm ci
npm run dev
```

Mở `/preview/menu` trên Vite dev server (ví dụ http://127.0.0.1:5173/preview/menu) để xem module thực đơn minh họa, không yêu cầu backend hoặc đăng nhập. Route và code fixture chỉ bật trong dev; production build không chứa module preview, đường dẫn này không mở giao diện demo. Trang gốc giữ nguyên trang chủ và các chức năng từ develop. `npm run build`, `npm run lint`, `npm run test:ui` kiểm tra toàn bộ FE; Playwright tự khởi động cả dev server và production preview server để kiểm tra guard này.

Dữ liệu trong `src/menu/demoMenu.js` là mẫu theo Figma. Thêm/sửa/xóa chỉ thay đổi React state, tải lại trang khôi phục mẫu; ảnh chọn được đọc cục bộ, không upload ra dịch vụ. Không có tài khoản giả, API hoặc localStorage.

Phạm vi: danh sách món/nhóm món, tìm kiếm không dấu, lọc nhóm, phân trang 10 dòng, thêm/sửa, xem trước ảnh, xác nhận xóa và trạng thái trống. Các module khác trên sidebar chỉ là nhãn để giữ ngữ cảnh design.

Sửa local ngày 11/11/2026: tìm riêng mã/tên/giá/nhóm, thông báo khi tìm trống, nút hiển thị tất cả đặt lại bộ lọc. Sửa/xóa dòng đã chọn có thông báo khi chưa chọn. Xóa nhóm báo số món bị ảnh hưởng, xác nhận ẩn cả nhóm và món; hủy giữ nguyên. Mã đã ẩn không tái sử dụng trong cùng lượt xem thử. Giá VND nguyên >5.000, tối đa 18 chữ số, giữ bằng chuỗi để không mất chính xác; không đổi nhóm khi sửa món. Không chặn trùng tên vì backend không quy định tên duy nhất. Bỏ mô tả không có trong model backend. Ảnh JPG/PNG hợp lệ ≤5 MiB/20 triệu pixel chỉ xem trước local, chưa upload Cloudinary.

Kết quả và giới hạn: [rà soát frontend local](../docs/reports/dt2-9-frontend/local-fixes-2026-11-11.md), [bản tích hợp develop và ảnh PR ngày 13/11](../docs/reports/dt2-9-frontend/develop-integration-2026-11-13.md). Menu preview chưa nối API, auth, DB hoặc menu khách; kết quả UI fixture không thay nghiệm thu hệ thống đầy đủ.

Nguồn và giới hạn nghiệp vụ: [yêu cầu FE](../docs/handover/dt2-9-frontend-request.md). Figma `8:292`, `8:319` là nguồn màu và bố cục; nhóm món mở rộng cùng style. Tham khảo luồng native dialog và cấu trúc React/Vite từ repo legacy; không nhập backend, dữ liệu nhân viên/khách hay trạng thái hoàn thành cũ.
