import { useState } from "react";
import { useAuth } from "./useAuth.js";
import "./auth.css";

export default function LoginPage({ onNavigate }) {
  const { login } = useAuth();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

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
        onNavigate("#admin");
      } else if (user.role === "STAFF") {
        onNavigate("#staff");
      } else {
        onNavigate("#top");
      }
    } catch (err) {
      setErrorMessage(err.message || "Tên đăng nhập hoặc mật khẩu không chính xác.");
    } finally {
      setIsSubmitting(false);
    }
  }

  function handleCancel() {
    onNavigate("#top");
  }

  return (
    <section className="auth-container" aria-label="Khu vực đăng nhập">
      <div className="login-split-layout">
        <div
          className="login-story-side"
          style={{ backgroundImage: "url('/images/cafe-interior.jpg')" }}
        >
          <div className="login-story-overlay" />
          <div className="login-story-content">
            <p className="login-story-eyebrow">LECKER Coffee Cafe</p>
            <h2 className="login-story-title">
              Một tách cà phê.<br />Một ngày thật đẹp.
            </h2>
            <p className="login-story-desc">
              Chào mừng bạn trở lại với không gian và hương vị cà phê thượng hạng.
            </p>
          </div>
        </div>

        <div className="login-form-side">
          <div className="auth-card-header" style={{ textAlign: "left" }}>
            <a
              href="#top"
              className="auth-back-link"
              onClick={(e) => {
                e.preventDefault();
                onNavigate("#top");
              }}
            >
              ← Về trang chủ
            </a>
            <div className="auth-brand-tag" style={{ textAlign: "left" }}>
              LECKER<span>COFFEE CAFE</span>
            </div>
            <h1>Đăng nhập</h1>
            <p>Hệ thống Quản lý Quán Cà phê Thông minh</p>
          </div>

          {errorMessage && (
            <div className="auth-error-banner" role="alert">
              <span aria-hidden="true">⚠️</span>
              <span>{errorMessage}</span>
            </div>
          )}

          <form className="auth-form" onSubmit={handleSubmit} noValidate>
            <div className="form-group">
              <label className="form-label" htmlFor="login-username">
                Tên đăng nhập
              </label>
              <input
                id="login-username"
                type="text"
                className="form-input"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                disabled={isSubmitting}
                autoComplete="username"
                required
                autoFocus
                aria-invalid={Boolean(errorMessage)}
              />
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="login-password">
                Mật khẩu
              </label>
              <div className="password-input-wrapper">
                <input
                  id="login-password"
                  type={showPassword ? "text" : "password"}
                  className="form-input"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  disabled={isSubmitting}
                  autoComplete="current-password"
                  required
                  aria-invalid={Boolean(errorMessage)}
                />
                <button
                  type="button"
                  className="password-toggle-btn"
                  onClick={() => setShowPassword((prev) => !prev)}
                  aria-label={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
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
          </form>

          <div className="auth-links-row">
            <a
              href="#forgot-password"
              className="auth-text-link"
              onClick={(e) => {
                e.preventDefault();
                onNavigate("#forgot-password");
              }}
            >
              Quên mật khẩu?
            </a>
            <a
              href="#top"
              className="auth-text-link"
              onClick={(e) => {
                e.preventDefault();
                onNavigate("#top");
              }}
            >
              Trang chủ
            </a>
          </div>
        </div>
      </div>
    </section>
  );
}
