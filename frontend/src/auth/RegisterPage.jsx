import AuthSidebar from "./AuthSidebar.jsx";
import "./auth.css";

export default function RegisterPage({ onNavigate }) {
  const handleNavigate = onNavigate || ((href) => { window.location.hash = href; });

  return (
    <section className="auth-page-container auth-container" aria-label="Đăng ký tài khoản">
      <div className="auth-card">
        <AuthSidebar />
        <div className="auth-content auth-flow-card">
          <a
            href="#top"
            className="auth-back-link"
            onClick={(event) => {
              event.preventDefault();
              handleNavigate("#top");
            }}
          >
            ← Về trang chủ
          </a>
          <div className="auth-brand-tag">
            LECKER<span>COFFEE CAFE</span>
          </div>
          <span className="auth-step-pill">Chưa khả dụng</span>
          <h1 className="auth-title">Đăng ký tài khoản</h1>
          <p className="auth-warning-banner" role="status">
            Đăng ký tài khoản trực tuyến hiện chưa khả dụng. Vui lòng đăng nhập bằng tài khoản hiện có hoặc liên hệ nhân viên quán để được hỗ trợ.
          </p>
          <div className="auth-actions-row">
            <button
              type="button"
              className="btn-primary"
              onClick={() => handleNavigate("#login")}
            >
              QUAY VỀ ĐĂNG NHẬP
            </button>
          </div>
        </div>
      </div>
    </section>
  );
}
