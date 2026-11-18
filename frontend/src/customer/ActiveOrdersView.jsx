import { useState, useEffect } from "react";
import { useCustomer, formatVnd } from "./CustomerContext.jsx";
import { updateOrderItem, cancelOrderItem, fetchCsrfToken } from "./customerApi.js";
import OrderResultModal from "./OrderResultModal.jsx";

export default function ActiveOrdersView({ onNavigateMenu }) {
  const { orders, session, updateBatchItem, markBatchItemCancelled } = useCustomer();
  const [now, setNow] = useState(Date.now());
  const [editingItem, setEditingItem] = useState(null); // { batchId, item, newQuantity }
  const [cancellingItem, setCancellingItem] = useState(null); // { batchId, item }
  const [actionError, setActionError] = useState(null);
  const [submittingAction, setSubmittingAction] = useState(false);

  // Live timer tick every 1000ms
  useEffect(() => {
    const timer = setInterval(() => {
      setNow(Date.now());
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  const hasOrders = orders.length > 0;

  function getRemainingSeconds(editableUntil, createdAt) {
    let targetTime = 0;
    if (editableUntil) {
      targetTime = new Date(editableUntil).getTime();
    } else if (createdAt) {
      targetTime = new Date(createdAt).getTime() + 180 * 1000;
    }
    if (!targetTime) return 0;
    const diff = Math.floor((targetTime - now) / 1000);
    return diff > 0 ? diff : 0;
  }

  function formatCountdown(sec) {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return `${m.toString().padStart(2, "0")}:${s.toString().padStart(2, "0")}`;
  }

  async function handleConfirmEdit() {
    if (!editingItem || submittingAction) return;
    setSubmittingAction(true);
    setActionError(null);

    try {
      const csrf = await fetchCsrfToken();
      const updated = await updateOrderItem({
        sessionId: session.sessionId,
        token: session.token,
        itemId: editingItem.item.id || editingItem.item.menuItemId,
        quantity: editingItem.newQuantity,
        expectedVersion: editingItem.item.version ?? 0,
        csrf,
      });

      updateBatchItem(
        editingItem.batchId,
        editingItem.item.id || editingItem.item.menuItemId,
        editingItem.newQuantity,
        updated.version
      );
      setEditingItem(null);
    } catch (err) {
      setActionError(err.message || "Không thể cập nhật số lượng món.");
    } finally {
      setSubmittingAction(false);
    }
  }

  async function handleConfirmCancel() {
    if (!cancellingItem || submittingAction) return;
    setSubmittingAction(true);
    setActionError(null);

    try {
      const csrf = await fetchCsrfToken();
      await cancelOrderItem({
        sessionId: session.sessionId,
        token: session.token,
        itemId: cancellingItem.item.id || cancellingItem.item.menuItemId,
        expectedVersion: cancellingItem.item.version ?? 0,
        csrf,
      });

      markBatchItemCancelled(
        cancellingItem.batchId,
        cancellingItem.item.id || cancellingItem.item.menuItemId
      );
      setCancellingItem(null);
    } catch (err) {
      setActionError(err.message || "Không thể hủy món.");
    } finally {
      setSubmittingAction(false);
    }
  }

  return (
    <div className="c-content">
      <h1 className="c-title">Đơn hàng của bạn</h1>
      <p className="c-subtitle">
        {hasOrders
          ? "Danh sách các món đã gửi tới quầy bar trong phiên phục vụ này"
          : "Chưa có đơn món nào được gửi"}
      </p>

      {/* Empty State */}
      {!hasOrders && (
        <div className="c-empty-state">
          <svg className="c-empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
            <polyline points="14 2 14 8 20 8" />
            <line x1="16" y1="13" x2="8" y2="13" />
            <line x1="16" y1="17" x2="8" y2="17" />
            <polyline points="10 9 9 9 8 9" />
          </svg>
          <h2 className="c-title" style={{ fontSize: "18px" }}>Chưa có đơn món</h2>
          <p className="c-subtitle">Các món bạn gửi từ giỏ hàng sẽ xuất hiện tại đây.</p>
          <button
            type="button"
            className="c-btn-primary"
            style={{ maxWidth: "200px", marginTop: "12px" }}
            onClick={onNavigateMenu}
          >
            CHỌN MÓN NGAY
          </button>
        </div>
      )}

      {/* Orders List */}
      {hasOrders && (
        <div>
          {orders.map((batch, index) => {
            const batchId = batch.batchId || batch.id || `Batch-${index + 1}`;
            const remaining = getRemainingSeconds(batch.editableUntil, batch.createdAt);
            const isEditable = remaining > 0;

            return (
              <div key={batchId} className="c-order-batch">
                <div className="c-batch-header">
                  <div>
                    <span className="c-batch-id">Đơn #{batchId}</span>
                    <div style={{ fontSize: "11px", color: "#8c7e70", marginTop: "2px" }}>
                      Tổng: <strong style={{ color: "#f1bf77" }}>{formatVnd(batch.totalVnd)}</strong>
                    </div>
                  </div>

                  {isEditable ? (
                    <div className="c-timer-badge">
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <circle cx="12" cy="12" r="10" />
                        <polyline points="12 6 12 12 16 14" />
                      </svg>
                      <span>Sửa/hủy: {formatCountdown(remaining)}</span>
                    </div>
                  ) : (
                    <div className="c-timer-badge expired">
                      <span>Đang chế biến</span>
                    </div>
                  )}
                </div>

                {/* Items in this batch */}
                <div>
                  {(batch.items || []).map((it) => {
                    const status = it.status || "SUBMITTED";
                    const isItemEditable = isEditable && status === "SUBMITTED";

                    return (
                      <div key={it.id || it.menuItemId} className="c-order-item-row">
                        <div>
                          <div style={{ fontSize: "14px", fontWeight: "600", color: "#f4ede2" }}>
                            {it.name || `Món #${it.menuItemId}`}
                          </div>
                          <div style={{ fontSize: "12px", color: "#b9aa98" }}>
                            Số lượng: <strong>{it.quantity}</strong> • {formatVnd(it.price)}
                          </div>
                        </div>

                        <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                          <span className={`c-item-status-badge c-status-${status}`}>
                            {status === "SUBMITTED"
                              ? "Đã gửi"
                              : status === "PREPARING"
                              ? "Đang làm"
                              : status === "CANCELLED"
                              ? "Đã hủy"
                              : status}
                          </span>

                          {isItemEditable && (
                            <>
                              <button
                                type="button"
                                className="c-btn-icon"
                                title="Sửa số lượng"
                                onClick={() =>
                                  setEditingItem({
                                    batchId,
                                    item: it,
                                    newQuantity: it.quantity,
                                  })
                                }
                                style={{ color: "#d5a953" }}
                              >
                                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                  <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                                  <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
                                </svg>
                              </button>

                              <button
                                type="button"
                                className="c-btn-icon"
                                title="Hủy món"
                                onClick={() =>
                                  setCancellingItem({
                                    batchId,
                                    item: it,
                                  })
                                }
                                style={{ color: "#ff6b6b" }}
                              >
                                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                  <circle cx="12" cy="12" r="10" />
                                  <line x1="15" y1="9" x2="9" y2="15" />
                                  <line x1="9" y1="9" x2="15" y2="15" />
                                </svg>
                              </button>
                            </>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Edit Quantity Modal */}
      {editingItem && (
        <div className="c-modal-overlay" role="dialog" aria-modal="true">
          <div className="c-modal-dialog" style={{ borderRadius: "16px" }}>
            <h2 className="c-title" style={{ fontSize: "18px" }}>Chỉnh sửa số lượng</h2>
            <p className="c-subtitle">
              Món: <strong>{editingItem.item.name}</strong>
            </p>

            <div className="c-stepper">
              <button
                type="button"
                className="c-stepper-btn"
                onClick={() =>
                  setEditingItem((prev) => ({
                    ...prev,
                    newQuantity: Math.max(1, prev.newQuantity - 1),
                  }))
                }
                disabled={editingItem.newQuantity <= 1}
              >
                -
              </button>
              <span className="c-stepper-value">{editingItem.newQuantity}</span>
              <button
                type="button"
                className="c-stepper-btn"
                onClick={() =>
                  setEditingItem((prev) => ({
                    ...prev,
                    newQuantity: prev.newQuantity + 1,
                  }))
                }
              >
                +
              </button>
            </div>

            {actionError && (
              <div style={{ color: "#ff8b8b", fontSize: "12px", marginBottom: "12px" }}>
                {actionError}
              </div>
            )}

            <div style={{ display: "flex", gap: "10px", marginTop: "16px" }}>
              <button
                type="button"
                className="c-btn-icon"
                style={{ flex: 1, height: "44px", border: "1px solid #3d3128" }}
                onClick={() => {
                  setEditingItem(null);
                  setActionError(null);
                }}
              >
                HỦY BỎ
              </button>
              <button
                type="button"
                className="c-btn-primary"
                style={{ flex: 2 }}
                onClick={handleConfirmEdit}
                disabled={submittingAction}
              >
                {submittingAction ? "ĐANG LƯU..." : "CẬP NHẬT"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Cancel Item Modal */}
      {cancellingItem && (
        <div className="c-modal-overlay" role="dialog" aria-modal="true">
          <div className="c-modal-dialog" style={{ borderRadius: "16px" }}>
            <h2 className="c-title" style={{ fontSize: "18px", color: "#ff6b6b" }}>Xác nhận hủy món</h2>
            <p className="c-subtitle">
              Bạn có chắc chắn muốn hủy <strong>{cancellingItem.item.name}</strong> (SL: {cancellingItem.item.quantity}) khỏi đơn đặt không?
            </p>

            {actionError && (
              <div style={{ color: "#ff8b8b", fontSize: "12px", marginBottom: "12px" }}>
                {actionError}
              </div>
            )}

            <div style={{ display: "flex", gap: "10px", marginTop: "16px" }}>
              <button
                type="button"
                className="c-btn-icon"
                style={{ flex: 1, height: "44px", border: "1px solid #3d3128" }}
                onClick={() => {
                  setCancellingItem(null);
                  setActionError(null);
                }}
              >
                GIỮ LẠI
              </button>
              <button
                type="button"
                className="c-btn-primary"
                style={{ flex: 2, background: "#e74c3c", color: "#fff" }}
                onClick={handleConfirmCancel}
                disabled={submittingAction}
              >
                {submittingAction ? "ĐANG HỦY..." : "XÁC NHẬN HỦY"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Action General Error Modal */}
      {actionError && !editingItem && !cancellingItem && (
        <OrderResultModal
          status="failure"
          title="Thao tác không thành công"
          detail={actionError}
          onClose={() => setActionError(null)}
        />
      )}
    </div>
  );
}
