import { useState } from "react";
import { useCustomer } from "./CustomerContext.jsx";
import { sendWaiterRequest, sendBillRequest, fetchCsrfToken } from "./customerApi.js";
import OrderResultModal from "./OrderResultModal.jsx";

export default function ServiceRequestsView({ onNavigateFeedback }) {
  const { session, orders } = useCustomer();
  const [loadingType, setLoadingType] = useState(null); // 'waiter' | 'bill' | null
  const [resultModal, setResultModal] = useState(null); // { status, title, detail }

  async function handleCallWaiter() {
    if (loadingType) return;
    if (!session || !session.sessionId || !session.token) {
      setResultModal({
        status: "failure",
        title: "Chưa xác thực bàn",
        detail: "Vui lòng quét mã QR tại bàn để xác nhận phiên phục vụ trước khi gọi nhân viên.",
      });
      return;
    }

    setLoadingType("waiter");
    try {
      const csrf = await fetchCsrfToken();
      await sendWaiterRequest({
        sessionId: session.sessionId,
        token: session.token,
        csrf,
      });

      // 12-waiter-success.png
      setResultModal({
        status: "success",
        title: "Đã gọi nhân viên!",
        detail: "Yêu cầu hỗ trợ của bạn đã được chuyển đến nhân viên phục vụ bàn. Chúng tôi sẽ có mặt ngay.",
      });
    } catch (err) {
      // 13-waiter-failure.png
      setResultModal({
        status: "failure",
        title: "Gửi yêu cầu thất bại",
        detail: err.message || "Hiện chưa thể gửi yêu cầu gọi nhân viên. Vui lòng gọi trực tiếp.",
      });
    } finally {
      setLoadingType(null);
    }
  }

  async function handleRequestBill() {
    if (loadingType) return;
    if (!session || !session.sessionId || !session.token) {
      setResultModal({
        status: "failure",
        title: "Chưa xác thực bàn",
        detail: "Vui lòng quét mã QR tại bàn để xác nhận phiên phục vụ trước khi yêu cầu thanh toán.",
      });
      return;
    }

    if (!orders || orders.length === 0) {
      setResultModal({
        status: "failure",
        title: "Chưa có đơn món",
        detail: "Bàn chưa có đơn món nào được gửi. Vui lòng đặt món trước khi yêu cầu thanh toán.",
      });
      return;
    }

    setLoadingType("bill");
    try {
      const csrf = await fetchCsrfToken();
      await sendBillRequest({
        sessionId: session.sessionId,
        token: session.token,
        csrf,
      });

      // 14-bill-request-success.png
      setResultModal({
        status: "success",
        title: "Đã yêu cầu thanh toán!",
        detail: "Nhân viên thu ngân đang chuẩn bị hóa đơn và sẽ mang tới bàn của bạn trong ít phút.",
      });
    } catch (err) {
      // 15-bill-request-failure.png
      setResultModal({
        status: "failure",
        title: "Yêu cầu thanh toán thất bại",
        detail: err.message || "Không thể gửi yêu cầu thanh toán lúc này. Vui lòng liên hệ nhân viên.",
      });
    } finally {
      setLoadingType(null);
    }
  }

  return (
    <div className="c-content">
      <h1 className="c-title">Dịch vụ tại bàn</h1>
      <p className="c-subtitle">Bạn cần hỗ trợ gì từ nhân viên Smart Cafe LECKER?</p>

      {/* Waiter Request Card (04-service-requests.png) */}
      <div className="c-service-card">
        <div className="c-service-card-header">
          <div className="c-service-icon-box">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M18 8a6 6 0 0 0-12 0c0 6-3 6-3 8h18c0-2-3-2-3-8" />
              <path d="M10 21h4" />
            </svg>
          </div>
          <div>
            <h2 className="c-service-card-title">Gọi nhân viên phục vụ</h2>
            <p className="c-service-card-desc">Thêm khăn giấy, nước lọc hoặc hỗ trợ món ăn trực tiếp tại bàn</p>
          </div>
        </div>

        <button
          type="button"
          className="c-btn-primary"
          onClick={handleCallWaiter}
          disabled={loadingType === "waiter"}
        >
          {loadingType === "waiter" ? "ĐANG GỬI YÊU CẦU..." : "GỌI PHỤC VỤ NGAY"}
        </button>
      </div>

      {/* Bill Request Card */}
      <div className="c-service-card">
        <div className="c-service-card-header">
          <div className="c-service-icon-box">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <rect x="2" y="4" width="20" height="16" rx="2" />
              <line x1="2" y1="10" x2="22" y2="10" />
            </svg>
          </div>
          <div>
            <h2 className="c-service-card-title">Yêu cầu thanh toán</h2>
            <p className="c-service-card-desc">In hóa đơn và chuẩn bị thanh toán tiền mặt hoặc chuyển khoản tại bàn</p>
          </div>
        </div>

        <button
          type="button"
          className="c-btn-primary"
          style={{ background: "#f1bf77" }}
          onClick={handleRequestBill}
          disabled={loadingType === "bill"}
        >
          {loadingType === "bill" ? "ĐANG GỬI YÊU CẦU..." : "YÊU CẦU HÓA ĐƠN"}
        </button>
      </div>

      {/* Feedback Card */}
      <div className="c-service-card">
        <div className="c-service-card-header">
          <div className="c-service-icon-box">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
            </svg>
          </div>
          <div>
            <h2 className="c-service-card-title">Đánh giá trải nghiệm</h2>
            <p className="c-service-card-desc">Góp ý về hương vị đồ uống, không gian và thái độ phục vụ</p>
          </div>
        </div>

        <button
          type="button"
          className="c-btn-primary"
          style={{ background: "transparent", border: "1px solid #d5a953", color: "#d5a953" }}
          onClick={onNavigateFeedback}
        >
          GỬI Ý KIẾN ĐÓNG GÓP
        </button>
      </div>

      {/* Result Modal */}
      {resultModal && (
        <OrderResultModal
          status={resultModal.status}
          title={resultModal.title}
          detail={resultModal.detail}
          onClose={() => setResultModal(null)}
        />
      )}
    </div>
  );
}
