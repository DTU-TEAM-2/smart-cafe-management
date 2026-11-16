import { useEffect } from "react";
import { useAuth } from "../auth/useAuth.js";

export default function StaffDashboard({ onNavigate }) {
  const { user, logout } = useAuth();

  useEffect(() => {
    if (!user) {
      onNavigate("#login");
    } else if (!["ADMIN", "STAFF"].includes(user.role)) {
      onNavigate("#top");
    }
  }, [user, onNavigate]);

  if (!user || !["ADMIN", "STAFF"].includes(user.role)) {
    return null;
  }

  return (
    <div style={{ maxWidth: "1200px", margin: "0 auto", padding: "40px 24px", minHeight: "80vh" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "32px", borderBottom: "1px solid var(--border)", paddingBottom: "20px" }}>
        <div>
          <span style={{ color: "var(--gold)", fontSize: "12px", letterSpacing: "2px", textTransform: "uppercase" }}>Không gian Vận hành</span>
          <h1 style={{ fontFamily: "var(--font-display)", fontSize: "28px", margin: "4px 0" }}>Bảng Phục Vụ Nhân Viên</h1>
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
          <h3 style={{ color: "var(--gold)", margin: "0 0 8px" }}>📝 Tiếp nhận Gọi món (POS)</h3>
          <p style={{ color: "var(--muted)", fontSize: "13px", lineHeight: "1.5" }}>Ghi nhận đồ uống theo số bàn, ghi chú độ ngọt, đá và gửi danh sách chế biến.</p>
        </div>
        <div style={{ background: "var(--surface)", border: "1px solid var(--border)", borderRadius: "8px", padding: "24px" }}>
          <h3 style={{ color: "var(--gold)", margin: "0 0 8px" }}>🛎️ Yêu cầu Phục vụ</h3>
          <p style={{ color: "var(--muted)", fontSize: "13px", lineHeight: "1.5" }}>Theo dõi chuông gọi từ khách tại các bàn và xử lý yêu cầu trực tiếp.</p>
        </div>
        <div style={{ background: "var(--surface)", border: "1px solid var(--border)", borderRadius: "8px", padding: "24px" }}>
          <h3 style={{ color: "var(--gold)", margin: "0 0 8px" }}>🧾 Lập Hóa đơn & Thanh toán</h3>
          <p style={{ color: "var(--muted)", fontSize: "13px", lineHeight: "1.5" }}>Chốt danh sách món, lập hóa đơn, nhận thanh toán và hoàn tất phiên phục vụ.</p>
        </div>
      </div>
    </div>
  );
}
