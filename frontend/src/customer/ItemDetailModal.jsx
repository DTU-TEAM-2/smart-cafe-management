import { useState } from "react";
import { useCustomer, formatVnd } from "./CustomerContext.jsx";

export default function ItemDetailModal({ item, onClose }) {
  const { addToCart } = useCustomer();
  const [quantity, setQuantity] = useState(1);
  const [added, setAdded] = useState(false);

  if (!item) return null;

  const unitPrice = parseInt(String(item.price).replace(/\D/g, ""), 10) || 0;
  const totalPrice = unitPrice * quantity;

  function handleAdd() {
    addToCart(item, quantity);
    setAdded(true);
    setTimeout(() => {
      onClose();
    }, 600);
  }

  return (
    <div className="c-modal-overlay" role="dialog" aria-modal="true" aria-labelledby="modal-item-title" onClick={onClose}>
      <div className="c-modal-dialog" onClick={(e) => e.stopPropagation()}>
        {item.imageUrl ? (
          <img src={item.imageUrl} alt={item.name} className="c-modal-image" />
        ) : (
          <div className="c-modal-image c-item-thumb-placeholder">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
              <path d="M18 8h1a4 4 0 0 1 0 8h-1M2 8h16v9a4 4 0 0 1-4 4H6a4 4 0 0 1-4-4V8z" />
              <line x1="6" y1="1" x2="6" y2="4" />
              <line x1="10" y1="1" x2="10" y2="4" />
              <line x1="14" y1="1" x2="14" y2="4" />
            </svg>
          </div>
        )}

        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
          <div>
            <span className="c-category-pill" style={{ padding: "4px 10px", fontSize: "11px", marginBottom: "8px", display: "inline-block" }}>
              {item.categoryName || "Thực đơn"}
            </span>
            <h2 id="modal-item-title" className="c-title" style={{ fontSize: "18px", margin: "4px 0" }}>
              {item.name}
            </h2>
            <span style={{ fontSize: "12px", color: "#8c7e70" }}>Mã món: {item.code || item.id}</span>
          </div>
          <button className="c-btn-icon" onClick={onClose} aria-label="Đóng cửa sổ">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>

        <div style={{ margin: "16px 0", padding: "12px 0", borderTop: "1px solid rgba(213, 169, 83, 0.15)", borderBottom: "1px solid rgba(213, 169, 83, 0.15)" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span className="c-label">Đơn giá</span>
            <span className="c-item-price" style={{ fontSize: "16px" }}>{formatVnd(unitPrice)}</span>
          </div>
        </div>

        <div className="c-stepper">
          <button
            type="button"
            className="c-stepper-btn"
            onClick={() => setQuantity((q) => Math.max(1, q - 1))}
            aria-label="Giảm số lượng"
            disabled={quantity <= 1}
          >
            -
          </button>
          <span className="c-stepper-value">{quantity}</span>
          <button
            type="button"
            className="c-stepper-btn"
            onClick={() => setQuantity((q) => q + 1)}
            aria-label="Tăng số lượng"
          >
            +
          </button>
        </div>

        <div style={{ marginTop: "16px" }}>
          <button
            type="button"
            className="c-btn-primary"
            onClick={handleAdd}
            disabled={added}
          >
            {added ? "✓ ĐÃ THÊM VÀO GIỎ" : `THÊM VÀO GIỎ • ${formatVnd(totalPrice)}`}
          </button>
        </div>
      </div>
    </div>
  );
}
