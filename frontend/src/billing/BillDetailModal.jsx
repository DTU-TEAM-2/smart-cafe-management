import { useEffect, useRef } from "react";
import { createPortal } from "react-dom";
import { formatDateTime, vnd, billStatusLabels } from "./billingApi.js";

export default function BillDetailModal({ bill, onClose, canPrint }) {
  const modalRef = useRef(null);

  useEffect(() => {
    const dialog = modalRef.current;
    const trigger = document.activeElement;
    dialog.showModal();
    return () => {
      dialog.close();
      if (trigger instanceof HTMLElement && trigger.isConnected) trigger.focus();
    };
  }, []);

  return createPortal(
    <dialog ref={modalRef} className="billing-modal-content" aria-labelledby="bill-detail-title"
      onCancel={(event) => { event.preventDefault(); onClose(); }}>
        <div className="billing-modal-header">
          <div>
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
                {billStatusLabels[bill.status] ?? bill.status}
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
        <div className="billing-items-scroll" role="region" aria-label="Món trong hóa đơn" tabIndex={0}>
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
        </div>

        <div className="billing-modal-footer">
          <div className="billing-modal-total">
            <span>Tổng cộng:</span>
            <strong>{vnd(bill.totalAmount)}</strong>
          </div>
          <div style={{ display: "flex", gap: "10px" }}>
            {canPrint && bill.status === "PAID" && (
              <button type="button" className="billing-button secondary" onClick={() => window.print()}>
                In hóa đơn
              </button>
            )}
            <button
              type="button"
              className="billing-button primary"
              onClick={onClose}
            >
              Đóng
            </button>
          </div>
        </div>
    </dialog>,
    document.body,
  );
}
