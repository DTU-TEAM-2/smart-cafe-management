import { useCustomer, formatVnd } from "./CustomerContext.jsx";

export default function CustomerCartView({ onNavigateMenu }) {
  const {
    cart,
    updateCartQuantity,
    removeFromCart,
    cartTotal,
    isSubmittingOrder,
    hasPendingOrder,
    submitOrder,
  } = useCustomer();

  const isEmpty = cart.length === 0;

  return (
    <div className="c-content">
      <h1 className="c-title">Giỏ hàng của bạn</h1>
      <p className="c-subtitle">
        {isEmpty ? "Chưa có món nào được chọn" : `Bạn đang chọn ${cart.length} món`}
      </p>

      {/* Empty Cart (08-cart-empty.png) */}
      {isEmpty && (
        <div className="c-empty-state">
          <svg className="c-empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
            <circle cx="9" cy="21" r="1" />
            <circle cx="20" cy="21" r="1" />
            <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6" />
          </svg>
          <h2 className="c-title" style={{ fontSize: "18px" }}>Giỏ hàng trống</h2>
          <p className="c-subtitle">Hãy khám phá thực đơn thơm ngon và chọn món bạn yêu thích nhé.</p>
          <button
            type="button"
            className="c-btn-primary"
            style={{ maxWidth: "220px", marginTop: "12px" }}
            onClick={onNavigateMenu}
          >
            XEM THỰC ĐƠN NGAY
          </button>
        </div>
      )}

      {/* Cart Items List (03-cart.png) */}
      {!isEmpty && (
        <>
          <div style={{ flex: 1 }}>
            {cart.map(({ item, quantity }) => (
              <div key={item.id} className="c-cart-item">
                {item.imageUrl ? (
                  <img src={item.imageUrl} alt={item.name} className="c-cart-thumb" />
                ) : (
                  <div className="c-cart-thumb c-item-thumb-placeholder">
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                      <path d="M18 8h1a4 4 0 0 1 0 8h-1M2 8h16v9a4 4 0 0 1-4 4H6a4 4 0 0 1-4-4V8z" />
                    </svg>
                  </div>
                )}

                <div className="c-cart-details">
                  <h2 className="c-cart-name">{item.name}</h2>
                  <div className="c-cart-price">{formatVnd(item.price)}</div>
                </div>

                <div className="c-cart-actions">
                  <div className="c-stepper" style={{ margin: 0, gap: "8px" }}>
                    <button
                      type="button"
                      className="c-stepper-btn"
                      style={{ width: "30px", height: "30px", fontSize: "14px" }}
                      onClick={() => updateCartQuantity(item.id, quantity - 1)}
                      aria-label="Giảm số lượng"
                      disabled={isSubmittingOrder}
                    >
                      -
                    </button>
                    <span className="c-stepper-value" style={{ minWidth: "20px", fontSize: "14px" }}>
                      {quantity}
                    </span>
                    <button
                      type="button"
                      className="c-stepper-btn"
                      style={{ width: "30px", height: "30px", fontSize: "14px" }}
                      onClick={() => updateCartQuantity(item.id, quantity + 1)}
                      aria-label="Tăng số lượng"
                      disabled={isSubmittingOrder}
                    >
                      +
                    </button>
                  </div>

                  <button
                    type="button"
                    className="c-btn-icon"
                    onClick={() => removeFromCart(item.id)}
                    aria-label={`Xóa ${item.name} khỏi giỏ`}
                    style={{ color: "#ff6b6b", marginLeft: "4px" }}
                    disabled={isSubmittingOrder}
                  >
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <polyline points="3 6 5 6 21 6" />
                      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                    </svg>
                  </button>
                </div>
              </div>
            ))}
          </div>

          {/* Cart Summary */}
          <div className="c-cart-summary">
            <div className="c-summary-row">
              <span>Tạm tính</span>
              <span>{formatVnd(cartTotal)}</span>
            </div>
            <div className="c-summary-row">
              <span>Phí dịch vụ</span>
              <span>Miễn phí</span>
            </div>
            <div className="c-summary-row total">
              <span>Tổng cộng (tạm tính)</span>
              <span className="c-summary-total-price">{formatVnd(cartTotal)}</span>
            </div>
          </div>

        </>
      )}
      {(!isEmpty || hasPendingOrder) && (
        <div style={{ marginTop: "16px" }}>
          {hasPendingOrder && <p className="c-subtitle" id="pending-order-note">
            Đơn trước chưa có kết quả. Gửi lại sẽ dùng đúng các món của lần gửi trước.
          </p>}
          <button type="button" className="c-btn-primary" onClick={submitOrder}
            disabled={isSubmittingOrder} aria-describedby={hasPendingOrder ? "pending-order-note" : undefined}>
            {isSubmittingOrder ? "ĐANG GỬI ĐƠN ĐẶT MÓN..."
              : hasPendingOrder ? "GỬI LẠI ĐƠN CHỜ" : `GỬI ĐƠN ĐẶT MÓN • ${formatVnd(cartTotal)}`}
          </button>
        </div>
      )}
    </div>
  );
}
