# LECKER — FE quản lý món và nhóm món

```powershell
cd frontend
npm ci
npm run dev
```

Mở địa chỉ Vite hiển thị (mặc định http://127.0.0.1:5173). Trang gốc là module thực đơn, không yêu cầu backend hoặc đăng nhập. `npm run build`, `npm run lint`, `npm run test:ui` kiểm tra riêng FE.

Dữ liệu trong `src/menu/demoMenu.js` là mẫu theo Figma. Thêm/sửa/xóa chỉ thay đổi React state, tải lại trang khôi phục mẫu; ảnh chọn được đọc cục bộ, không upload ra dịch vụ. Không có tài khoản giả, API hoặc localStorage.

Phạm vi: danh sách món/nhóm món, tìm kiếm không dấu, lọc nhóm, phân trang 10 dòng, thêm/sửa, xem trước ảnh, xác nhận xóa và trạng thái trống. Các module khác trên sidebar chỉ là nhãn để giữ ngữ cảnh design.

Nguồn và giới hạn nghiệp vụ: [yêu cầu FE](../docs/handover/dt2-9-frontend-request.md). Figma `8:292`, `8:319` là nguồn màu và bố cục; nhóm món mở rộng cùng style. Tham khảo luồng native dialog và cấu trúc React/Vite từ repo legacy; không nhập backend, dữ liệu nhân viên/khách hay trạng thái hoàn thành cũ.
