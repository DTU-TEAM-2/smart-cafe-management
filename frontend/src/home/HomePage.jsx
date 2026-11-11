export default function HomePage({ onNavigate }) {
  return (
    <div style={{ maxWidth: "1280px", margin: "0 auto", padding: "60px 24px" }}>
      <div style={{ textAlign: "center", maxWidth: "800px", margin: "0 auto 60px" }}>
        <p style={{ color: "var(--gold)", fontSize: "13px", letterSpacing: "3px", textTransform: "uppercase", fontWeight: 600 }}>
          Hương vị cà phê nguyên bản
        </p>
        <h1 style={{ fontFamily: "var(--font-display)", fontSize: "44px", lineHeight: "1.2", margin: "16px 0 20px" }}>
          Khám phá không gian ẩm thực & cà phê LECKER
        </h1>
        <p style={{ color: "var(--fg-subtle)", fontSize: "16px", lineHeight: "1.7", margin: "0 0 32px" }}>
          Thưởng thức những tách Espresso đậm đà, Latte béo ngậy được tuyển chọn kỹ lưỡng từ những hạt cà phê thượng hạng cùng hệ thống phục vụ thông minh.
        </p>
        <div style={{ display: "flex", justifyContent: "center", gap: "16px", flexWrap: "wrap" }}>
          <button
            type="button"
            className="btn-primary"
            style={{ width: "auto", minWidth: "180px" }}
            onClick={() => onNavigate("#login")}
          >
            Đăng nhập hệ thống
          </button>
        </div>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: "24px" }}>
        <div style={{ background: "var(--surface)", border: "1px solid var(--border)", borderRadius: "8px", overflow: "hidden" }}>
          <img src="/images/espresso.jpg" alt="Espresso" style={{ width: "100%", height: "200px", objectFit: "cover" }} />
          <div style={{ padding: "20px" }}>
            <h3 style={{ margin: "0 0 8px", color: "var(--gold)" }}>Espresso Đậm Đà</h3>
            <p style={{ color: "var(--muted)", fontSize: "13px", margin: 0 }}>Hương thơm nồng nàn từ 100% hạt Arabica rang mộc nguyên chất.</p>
          </div>
        </div>
        <div style={{ background: "var(--surface)", border: "1px solid var(--border)", borderRadius: "8px", overflow: "hidden" }}>
          <img src="/images/latte.jpg" alt="Latte" style={{ width: "100%", height: "200px", objectFit: "cover" }} />
          <div style={{ padding: "20px" }}>
            <h3 style={{ margin: "0 0 8px", color: "var(--gold)" }}>Caffè Latte</h3>
            <p style={{ color: "var(--muted)", fontSize: "13px", margin: 0 }}>Sự hòa quyện êm dịu giữa sữa tươi thanh trùng và lớp espresso mịn màng.</p>
          </div>
        </div>
        <div style={{ background: "var(--surface)", border: "1px solid var(--border)", borderRadius: "8px", overflow: "hidden" }}>
          <img src="/images/iced-coffee.jpg" alt="Iced Coffee" style={{ width: "100%", height: "200px", objectFit: "cover" }} />
          <div style={{ padding: "20px" }}>
            <h3 style={{ margin: "0 0 8px", color: "var(--gold)" }}>Cà Phê Sữa Đá</h3>
            <p style={{ color: "var(--muted)", fontSize: "13px", margin: 0 }}>Vị ngọt ngào truyền thống cùng lớp đá mát lạnh sảng khoái.</p>
          </div>
        </div>
      </div>
    </div>
  );
}
