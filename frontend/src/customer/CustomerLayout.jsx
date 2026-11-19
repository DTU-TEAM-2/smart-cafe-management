import { useCustomer } from "./CustomerContext.jsx";
import CustomerDock from "./CustomerDock.jsx";
import CustomerMenuView from "./CustomerMenuView.jsx";
import CustomerCartView from "./CustomerCartView.jsx";
import ActiveOrdersView from "./ActiveOrdersView.jsx";
import ServiceRequestsView from "./ServiceRequestsView.jsx";
import FeedbackView from "./FeedbackView.jsx";
import OrderResultModal from "./OrderResultModal.jsx";
import "./customer.css";

export default function CustomerLayout({ onNavigateHome }) {
  const {
    currentTab,
    setCurrentTab,
    session,
    orderModalResult,
    setOrderModalResult,
    submitOrder,
  } = useCustomer();

  return (
    <div className="c-container">
      {/* Header */}
      <header className="c-header">
        <div className="c-brand">
          <a href="#customer" className="c-brand-logo" onClick={() => setCurrentTab("menu")}>
            LECKER
          </a>
          <span className="c-table-badge">
            {session?.tableNumber ? `BÀN ${session.tableNumber}` : "KHÁCH VÃNG LAI"}
          </span>
        </div>

        <div className="c-header-actions">
          {onNavigateHome && (
            <button
              type="button"
              className="c-btn-icon"
              title="Về trang chủ"
              onClick={onNavigateHome}
              aria-label="Về trang chủ"
            >
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
                <polyline points="9 22 9 12 15 12 15 22" />
              </svg>
            </button>
          )}
        </div>
      </header>

      {/* Notice if unauthenticated */}
      {!session?.token && (
        <div
          style={{
            background: "rgba(213, 169, 83, 0.12)",
            borderBottom: "1px solid rgba(213, 169, 83, 0.25)",
            padding: "8px 16px",
            fontSize: "12px",
            color: "#f1bf77",
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
          }}
        >
          <span>Quét mã QR tại bàn để xác thực phiên đặt món và gọi phục vụ.</span>
        </div>
      )}

      {/* Main Tab View */}
      <main style={{ flex: 1, display: "flex", flexDirection: "column" }}>
        {currentTab === "menu" && <CustomerMenuView />}
        {currentTab === "cart" && (
          <CustomerCartView
            onNavigateMenu={() => setCurrentTab("menu")}
            onNavigateOrders={() => setCurrentTab("orders")}
          />
        )}
        {currentTab === "orders" && (
          <ActiveOrdersView onNavigateMenu={() => setCurrentTab("menu")} />
        )}
        {currentTab === "service" && (
          <ServiceRequestsView onNavigateFeedback={() => setCurrentTab("feedback")} />
        )}
        {currentTab === "feedback" && (
          <FeedbackView onNavigateMenu={() => setCurrentTab("menu")} />
        )}
      </main>

      {/* Bottom Dock */}
      <CustomerDock />

      {/* Global Order Result Modal */}
      {orderModalResult && (
        <OrderResultModal
          status={orderModalResult.status}
          title={orderModalResult.title}
          detail={orderModalResult.detail}
          primaryText={orderModalResult.status === "success" ? "XEM ĐƠN MÓN" : "THỬ LẠI"}
          secondaryText={orderModalResult.status === "success" ? "TIẾP TỤC CHỌN MÓN" : "ĐÓNG"}
          onPrimaryAction={
            orderModalResult.status === "success"
              ? () => {
                  setOrderModalResult(null);
                  setCurrentTab("orders");
                }
              : () => {
                  setOrderModalResult(null);
                  submitOrder();
                }
          }
          onClose={() => setOrderModalResult(null)}
        />
      )}
    </div>
  );
}
