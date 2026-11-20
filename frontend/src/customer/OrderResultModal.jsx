export default function OrderResultModal({ status = "success", title, detail, onClose, onPrimaryAction, primaryText, secondaryText }) {
  const isSuccess = status === "success";

  return (
    <div className="c-modal-overlay" role="dialog" aria-modal="true" onClick={onClose}>
      <div className="c-modal-dialog" style={{ borderRadius: "20px" }} onClick={(e) => e.stopPropagation()}>
        <div className="c-result-box">
          <div className={`c-result-icon ${isSuccess ? "success" : "failure"}`}>
            {isSuccess ? (
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <polyline points="20 6 9 17 4 12" />
              </svg>
            ) : (
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="12" cy="12" r="10" />
                <line x1="12" y1="8" x2="12" y2="12" />
                <line x1="12" y1="16" x2="12.01" y2="16" />
              </svg>
            )}
          </div>

          <h3 className="c-result-title">{title}</h3>
          <p className="c-result-detail">{detail}</p>

          <div style={{ display: "flex", flexDirection: "column", gap: "10px", width: "100%" }}>
            {onPrimaryAction && (
              <button
                type="button"
                className="c-btn-primary"
                onClick={() => {
                  onPrimaryAction();
                  onClose();
                }}
              >
                {primaryText || "XÁC NHẬN"}
              </button>
            )}
            <button
              type="button"
              className="c-btn-icon"
              style={{ width: "100%", height: "44px", color: "#b9aa98", fontSize: "13px" }}
              onClick={onClose}
            >
              {secondaryText || "ĐÓNG"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
