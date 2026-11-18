import { useId, useState } from "react";
import { useAuth } from "./useAuth.js";
import AuthSidebar from "./AuthSidebar.jsx";
import "./auth.css";

export default function LoginPage({ onNavigate }) {
  const id = useId();
  const { login } = useAuth();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleNavigate = onNavigate || ((href) => { window.location.hash = href; });

  async function handleSubmit(e) {
    e.preventDefault();
    setErrorMessage("");

    const trimmed = username.trim();
    if (!trimmed || !password) {
      setErrorMessage("Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.");
      return;
    }

    setIsSubmitting(true);
    try {
      const user = await login(trimmed, password);
      if (user.role === "ADMIN") {
        handleNavigate("#admin");
      } else if (user.role === "STAFF") {
        handleNavigate("#staff");
      } else {
        handleNavigate("#top");
      }
    } catch (err) {
      setErrorMessage(err.message || "Tài khoản hoặc mật khẩu không chính xác.");
    } finally {
      setIsSubmitting(false);
    }
  }

  function handleCancel() {
    setUsername("");
    setPassword("");
    setErrorMessage("");
    handleNavigate("#top");
  }

  return (
    <section className="auth-page-container login-page-container" aria-labelledby={`${id}-title`}>
      <div className="auth-card login-split-card">
        {/* Left Column: Atmospheric Cafe Story Side (Desktop) + Figma Brand Column (Mobile) */}
        <AuthSidebar
          eyebrow="LECKER COFFEE CAFE"
          title={"Một tách cà phê.\nMột ngày thật đẹp."}
          description="Chào mừng bạn trở lại với LECKER."
        />

        {/* Right Column: Login form panel matching Image 2 & Image 5 */}
        <div className="auth-content login-content-card">
          <a
            href="#top"
            className="auth-back-link login-back"
            onClick={(e) => {
              e.preventDefault();
              handleNavigate("#top");
            }}
          >
            ← Về trang chủ
          </a>

          {/* Gold brand tag matching Image 2 / Image 5 */}
          <div className="auth-brand-tag">
            LECKER<span className="auth-brand-tag-sub">COFFEE CAFE</span>
          </div>

          <h1 id={`${id}-title`} className="auth-title">Đăng nhập</h1>
          <p className="auth-subtitle login-intro">Hệ thống Quản lý Quán Cà phê Thông minh</p>
          <p className="auth-welcome-mobile">Chào mừng quay lại</p>

          {errorMessage && (
            <div id={`${id}-error`} className="auth-error-banner" role="alert">
              <span aria-hidden="true">⚠️</span>
              <span>{errorMessage}</span>
            </div>
          )}

          <form className="auth-form login-form" onSubmit={handleSubmit} noValidate>
            <div className="form-group form-group-username">
              <label className="form-label" htmlFor={`${id}-username`}>
                Tên đăng nhập
                <span className="required-asterisk" aria-hidden="true" title="Bắt buộc">*</span>
              </label>
              <input
                id={`${id}-username`}
                name="username"
                type="text"
                aria-label="Tên đăng nhập"
                className="form-input"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                disabled={isSubmitting}
                autoComplete="username"
                placeholder="staff"
                required
                aria-required="true"
                autoFocus
                aria-describedby={errorMessage ? `${id}-error` : undefined}
                aria-invalid={Boolean(errorMessage)}
              />
            </div>

            <div className="form-group form-group-password">
              <label className="form-label" htmlFor={`${id}-password`}>
                Mật khẩu
                <span className="required-asterisk" aria-hidden="true" title="Bắt buộc">*</span>
              </label>
              <div className="password-field password-input-wrapper">
                <input
                  id={`${id}-password`}
                  name="password"
                  type={showPassword ? "text" : "password"}
                  aria-label="Mật khẩu"
                  className="form-input"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  disabled={isSubmitting}
                  autoComplete="current-password"
                  placeholder="••••••••"
                  required
                  aria-required="true"
                  aria-describedby={errorMessage ? `${id}-error` : undefined}
                  aria-invalid={Boolean(errorMessage)}
                />
                <button
                  type="button"
                  className="password-toggle-btn"
                  onClick={() => setShowPassword((prev) => !prev)}
                  aria-label={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                  aria-pressed={showPassword}
                  disabled={isSubmitting}
                >
                  {showPassword ? "Ẩn" : "Hiện"}
                </button>
              </div>
            </div>

            {/* Mobile Forgot Password Pill (Image 5 placement) */}
            <div className="auth-forgot-row">
              <a
                href="#forgot-password"
                className="auth-link auth-forgot-pill"
                onClick={(e) => {
                  e.preventDefault();
                  handleNavigate("#forgot-password");
                }}
              >
                Quên mật khẩu?
              </a>
            </div>

            <div className="auth-actions-row">
              <button
                type="submit"
                className="btn-primary auth-submit-btn"
                aria-label="Đăng nhập"
                disabled={isSubmitting}
              >
                <span className="btn-text-desktop">
                  {isSubmitting ? "Đang xử lý..." : "Đăng nhập"}
                </span>
                <span className="btn-text-mobile" aria-hidden="true">
                  {isSubmitting ? "Đang xử lý..." : "ĐĂNG NHẬP"} <span className="btn-arrow" aria-hidden="true">→</span>
                </span>
              </button>
              <button
                type="button"
                className="btn-secondary btn-cancel"
                onClick={handleCancel}
                disabled={isSubmitting}
              >
                Hủy
              </button>
            </div>

            <div className="auth-links-row">
              <a
                href="#forgot-password"
                className="auth-link auth-forgot-desktop"
                onClick={(e) => {
                  e.preventDefault();
                  handleNavigate("#forgot-password");
                }}
              >
                Quên mật khẩu?
              </a>
              <a
                href="#register"
                className="auth-link auth-register-composite"
                aria-label="Đăng ký (Chưa khả dụng)"
                onClick={(e) => {
                  e.preventDefault();
                  handleNavigate("#register");
                }}
              >
                <span className="auth-register-label">Chưa khả dụng</span>
                <span className="auth-register-pill">Đăng ký</span>
              </a>
            </div>
          </form>
        </div>
      </div>
    </section>
  );
}
