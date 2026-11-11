import { useId, useState } from "react";
import { useAuth } from "./useAuth.js";
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
    <section className="login-page" aria-labelledby={`${id}-title`}>
      {/* Cột trái: Story hình ảnh LECKER Coffee Cafe theo code mới (Ảnh 1) */}
      <div className="login-story">
        <img src="/images/cafe-interior.jpg" alt="" />
        <div>
          <p className="eyebrow">LECKER Coffee Cafe</p>
          <h2>
            Một tách cà phê.<br />Một ngày thật đẹp.
          </h2>
          <p>Chào mừng bạn trở lại với LECKER.</p>
        </div>
      </div>

      {/* Cột phải: Khung đăng nhập giữ nguyên bố cục theo code hiện tại (Ảnh 2) với màu sắc mới */}
      <div className="login-card">
        <a
          href="#top"
          className="login-back"
          onClick={(e) => {
            e.preventDefault();
            handleNavigate("#top");
          }}
        >
          ← Về trang chủ
        </a>

        <div className="auth-brand-tag" style={{ marginBottom: "16px" }}>
          LECKER<span>COFFEE CAFE</span>
        </div>

        <h1 id={`${id}-title`}>Đăng nhập</h1>
        <p className="login-intro">Hệ thống Quản lý Quán Cà phê Thông minh</p>

        {errorMessage && (
          <div className="auth-error-banner" role="alert">
            <span aria-hidden="true">⚠️</span>
            <span>{errorMessage}</span>
          </div>
        )}

        <form className="auth-form" onSubmit={handleSubmit} noValidate>
          <div className="form-group">
            <label className="form-label" htmlFor={`${id}-username`}>
              Tên đăng nhập
              <span className="required-asterisk" aria-hidden="true" title="Bắt buộc">*</span>
            </label>
            <input
              id={`${id}-username`}
              name="username"
              type="text"
              className="form-input"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              disabled={isSubmitting}
              autoComplete="username"
              required
              aria-required="true"
              autoFocus
              aria-invalid={Boolean(errorMessage)}
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor={`${id}-password`}>
              Mật khẩu
              <span className="required-asterisk" aria-hidden="true" title="Bắt buộc">*</span>
            </label>
            <div className="password-field">
              <input
                id={`${id}-password`}
                name="password"
                type={showPassword ? "text" : "password"}
                className="form-input"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={isSubmitting}
                autoComplete="current-password"
                required
                aria-required="true"
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

          <div className="auth-actions-row">
            <button
              type="submit"
              className="btn-primary"
              disabled={isSubmitting}
            >
              {isSubmitting ? "Đang xử lý..." : "Đăng nhập"}
            </button>
            <button
              type="button"
              className="btn-secondary"
              onClick={handleCancel}
              disabled={isSubmitting}
            >
              Hủy
            </button>
          </div>

          <div className="auth-links-row">
            <a
              href="#forgot-password"
              className="auth-link"
              onClick={(e) => {
                e.preventDefault();
                handleNavigate("#forgot-password");
              }}
            >
              Quên mật khẩu?
            </a>
            <a
              href="#top"
              className="auth-link"
              onClick={(e) => {
                e.preventDefault();
                handleNavigate("#top");
              }}
            >
              Trang chủ
            </a>
          </div>
        </form>
      </div>
    </section>
  );
}
