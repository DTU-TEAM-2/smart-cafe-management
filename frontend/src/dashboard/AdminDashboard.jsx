import { useEffect } from "react";
import { useAuth } from "../auth/useAuth.js";

export default function AdminDashboard({ onNavigate }) {
  const { user, token, logout } = useAuth();

  useEffect(() => {
    if (!user && !token) {
      onNavigate("#login");
    } else if (user && user.role !== "ADMIN") {
      onNavigate("#top");
    }
  }, [user, token, onNavigate]);

  if (!user || user.role !== "ADMIN") {
    return null;
  }

  return (
    <div style={{ maxWidth: "1200px", margin: "0 auto", padding: "40px 24px", minHeight: "80vh" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "32px", borderBottom: "1px solid var(--border)", paddingBottom: "20px" }}>
        <div>
          <span style={{ color: "var(--gold)", fontSize: "12px", letterSpacing: "2px", textTransform: "uppercase" }}>Trung tâm Quản trị</span>
          <h1 style={{ fontFamily: "var(--font-display)", fontSize: "28px", margin: "4px 0" }}>Bảng Điều Khiển Quản Lý</h1>
          <p style={{ color: "var(--muted)", margin: 0 }}>Xin chào, <strong>{user.fullName}</strong> ({user.username})</p>
        </div>
        <button
          type="button"
          className="btn-secondary"
          onClick={async () => {
            await logout();
            onNavigate("#login");
          }}
        >
          Đăng xuất
        </button>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))", gap: "20px" }}>
        <div style={{ background: "var(--surface)", border: "1px solid var(--border)", borderRadius: "8px", padding: "24px" }}>
          <h3 style={{ color: "var(--gold)", margin: "0 0 8px" }}>👥 Quản lý Nhân viên</h3>
          <p style={{ color: "var(--muted)", fontSize: "13px", lineHeight: "1.5" }}>Danh sách nhân viên, thêm mới, phân quyền và điều chỉnh thông tin nhân sự quán.</p>
        </div>
        <div style={{ background: "var(--surface)", border: "1px solid var(--border)", borderRadius: "8px", padding: "24px" }}>
          <h3 style={{ color: "var(--gold)", margin: "0 0 8px" }}>☕ Quản lý Món & Menu</h3>
          <p style={{ color: "var(--muted)", fontSize: "13px", lineHeight: "1.5" }}>Cập nhật danh mục đồ uống, giá cả, hình ảnh món và trạng thái phục vụ.</p>
          <a href="#admin-menu">Mở quản lý thực đơn</a>
        </div>
        <div style={{ background: "var(--surface)", border: "1px solid var(--border)", borderRadius: "8px", padding: "24px" }}>
          <h3 style={{ color: "var(--gold)", margin: "0 0 8px" }}>🪑 Quản lý Bàn phục vụ</h3>
          <p style={{ color: "var(--muted)", fontSize: "13px", lineHeight: "1.5" }}>Sơ đồ bàn, trạng thái bàn trống, kích hoạt bàn và phiên phục vụ.</p>
        </div>
        <div style={{ background: "var(--surface)", border: "1px solid var(--border)", borderRadius: "8px", padding: "24px" }}>
          <h3 style={{ color: "var(--gold)", margin: "0 0 8px" }}>📊 Thống kê Doanh thu</h3>
          <p style={{ color: "var(--muted)", fontSize: "13px", lineHeight: "1.5" }}>Báo cáo doanh thu theo ngày, biểu đồ hóa đơn và thống kê món bán chạy.</p>
        </div>
      </div>
    </div>
  );
}
