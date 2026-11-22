import { useCustomer } from "./CustomerContext.jsx";

export default function CustomerDock() {
  const { currentTab, setCurrentTab, cartCount, orders } = useCustomer();

  const hasActiveOrders = orders.length > 0;

  return (
    <nav className="c-bottom-dock" aria-label="Điều hướng chính cho khách">
      {/* Menu Tab */}
      <button
        type="button"
        className={`c-dock-item ${currentTab === "menu" ? "active" : ""}`}
        onClick={() => setCurrentTab("menu")}
        aria-label="Thực đơn"
      >
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M18 8h1a4 4 0 0 1 0 8h-1M2 8h16v9a4 4 0 0 1-4 4H6a4 4 0 0 1-4-4V8z" />
          <line x1="6" y1="1" x2="6" y2="4" />
          <line x1="10" y1="1" x2="10" y2="4" />
          <line x1="14" y1="1" x2="14" y2="4" />
        </svg>
        <span>Thực đơn</span>
      </button>

      {/* Cart Tab */}
      <button
        type="button"
        className={`c-dock-item ${currentTab === "cart" ? "active" : ""}`}
        onClick={() => setCurrentTab("cart")}
        aria-label={`Giỏ hàng, ${cartCount} món`}
      >
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <circle cx="9" cy="21" r="1" />
          <circle cx="20" cy="21" r="1" />
          <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6" />
        </svg>
        {cartCount > 0 && <span className="c-dock-badge">{cartCount}</span>}
        <span>Giỏ hàng</span>
      </button>

      {/* Orders Tab */}
      <button
        type="button"
        className={`c-dock-item ${currentTab === "orders" ? "active" : ""}`}
        onClick={() => setCurrentTab("orders")}
        aria-label="Đơn món đã đặt"
      >
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
          <polyline points="14 2 14 8 20 8" />
          <line x1="16" y1="13" x2="8" y2="13" />
          <line x1="16" y1="17" x2="8" y2="17" />
        </svg>
        {hasActiveOrders && (
          <span className="c-dock-badge" style={{ background: "#f1bf77", minWidth: "8px", height: "8px", padding: 0 }} />
        )}
        <span>Đơn món</span>
      </button>

      {/* Service Tab */}
      <button
        type="button"
        className={`c-dock-item ${currentTab === "service" ? "active" : ""}`}
        onClick={() => setCurrentTab("service")}
        aria-label="Dịch vụ và gọi phục vụ"
      >
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M18 8a6 6 0 0 0-12 0c0 6-3 6-3 8h18c0-2-3-2-3-8" />
          <path d="M10 21h4" />
        </svg>
        <span>Phục vụ</span>
      </button>

      {/* Feedback Tab */}
      <button
        type="button"
        className={`c-dock-item ${currentTab === "feedback" ? "active" : ""}`}
        onClick={() => setCurrentTab("feedback")}
        aria-label="Đánh giá"
      >
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
        </svg>
        <span>Đánh giá</span>
      </button>
    </nav>
  );
}
