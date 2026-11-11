export default function DeleteStaffModal({ staff, onClose, onConfirm, isDeleting }) {
  if (!staff) return null;

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="delete-staff-title">
      <div className="modal-dialog" style={{ maxWidth: "450px" }}>
        <div className="modal-header">
          <h2 id="delete-staff-title" style={{ color: "#fc8181" }}>Xác nhận xóa nhân viên</h2>
          <button type="button" className="modal-close-btn" onClick={onClose} aria-label="Đóng">&times;</button>
        </div>
        <div style={{ margin: "16px 0", color: "#f4ede2", lineHeight: 1.6 }}>
          <p>
            Bạn có chắc chắn muốn xóa nhân viên <strong>{staff.fullName}</strong> (Username: <code>{staff.username}</code>)?
          </p>
          <p style={{ fontSize: "13px", color: "var(--muted)" }}>
            * Lưu ý: Tài khoản sẽ được chuyển sang trạng thái <strong>Ngừng hoạt động (INACTIVE)</strong> để bảo toàn toàn vẹn lịch sử hóa đơn và phiên làm việc.
          </p>
        </div>
        <div className="modal-footer">
          <button type="button" className="btn-secondary" onClick={onClose} disabled={isDeleting}>
            Hủy
          </button>
          <button
            type="button"
            className="btn-danger"
            style={{ padding: "10px 18px", fontSize: "14px", fontWeight: "600" }}
            onClick={() => onConfirm(staff.id)}
            disabled={isDeleting}
          >
            {isDeleting ? "Đang xử lý…" : "Xác nhận xóa"}
          </button>
        </div>
      </div>
    </div>
  );
}
