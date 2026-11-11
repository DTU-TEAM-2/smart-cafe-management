import { useEffect, useRef } from "react";
import { formatDateTime, vnd } from "./billingApi.js";

export default function BillDetailModal({ bill, onClose }) {
  const modalRef = useRef(null);

  useEffect(() => {
    function handleKeyDown(e) {
      if (e.key === "Escape") {
        onClose();
      }
      if (e.key === "Tab" && modalRef.current) {
        const focusables = modalRef.current.querySelectorAll(
          'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
        );
        if (focusables.length === 0) return;
        const first = focusables[0];
        const last = focusables[focusables.length - 1];
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    }

    document.addEventListener("keydown", handleKeyDown);
    modalRef.current?.focus();
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  if (!bill) return null;

  return (
    <div className="billing-modal-backdrop" onClick={onClose} role="presentation">
      <div
        ref={modalRef}
        className="billing-modal-content"
        role="dialog"
        aria-modal="true"
        aria-labelledby="bill-detail-title"
        tabIndex={-1}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="billing-modal-header">
          <div>
            <span className="eyebrow">Hóa đơn #{bill.billCode}</span>
            <h2 id="bill-detail-title">Chi tiết hóa đơn {bill.billCode ? `— ${bill.billCode}` : ""}</h2>
          </div>
          <button
            type="button"
            className="icon-button"
            onClick={onClose}
            aria-label="Đóng cửa sổ"
          >
            ✕
          </button>
        </div>

        <div className="billing-metadata-grid">
          <div className="billing-metadata-item">
            <span className="billing-metadata-label">Mã bàn & Tên bàn</span>
            <span className="billing-metadata-value">
              {bill.tableName || "—"}{bill.tableCode ? ` (${bill.tableCode})` : ""}
            </span>
          </div>
          <div className="billing-metadata-item">
            <span className="billing-metadata-label">Người lập</span>
            <span className="billing-metadata-value">{bill.creatorName}</span>
          </div>
          <div className="billing-metadata-item">
            <span className="billing-metadata-label">Mã phiên (Session)</span>
            <span className="billing-metadata-value">{bill.sessionCode}</span>
          </div>
          <div className="billing-metadata-item">
            <span className="billing-metadata-label">Trạng thái</span>
            <span className="billing-metadata-value">
              <span className={`billing-status-badge ${bill.status.toLowerCase()}`}>
                {bill.status}
              </span>
            </span>
          </div>
          <div className="billing-metadata-item">
            <span className="billing-metadata-label">Ngày tạo</span>
            <span className="billing-metadata-value">{formatDateTime(bill.createdAt)}</span>
          </div>
          <div className="billing-metadata-item">
            <span className="billing-metadata-label">Ngày thanh toán</span>
            <span className="billing-metadata-value">
              {bill.paidAt ? formatDateTime(bill.paidAt) : "Chưa thanh toán"}
            </span>
          </div>
        </div>

        <h3>Danh sách món đã gọi</h3>
        <table className="billing-items-table">
          <thead>
            <tr>
              <th scope="col">Tên món</th>
              <th scope="col">Số lượng</th>
              <th scope="col">Đơn giá</th>
              <th scope="col">Thành tiền</th>
            </tr>
          </thead>
          <tbody>
            {bill.items?.map((item, idx) => (
              <tr key={item.orderItemId || idx}>
                <th scope="row" style={{ fontWeight: 600 }}>{item.itemName}</th>
                <td>{item.quantity}</td>
                <td>{vnd(item.unitPrice)}</td>
                <td>{vnd(item.lineTotal)}</td>
              </tr>
            ))}
          </tbody>
        </table>

        <div className="billing-modal-footer">
          <div className="billing-modal-total">
            <span>Tổng cộng:</span>
            <strong>{vnd(bill.totalAmount)}</strong>
          </div>
          <div style={{ display: "flex", gap: "10px" }}>
            <button
              type="button"
              className="billing-button secondary"
              onClick={() => window.print()}
            >
              In hóa đơn
            </button>
            <button
              type="button"
              className="billing-button primary"
              onClick={onClose}
            >
              Đóng
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
