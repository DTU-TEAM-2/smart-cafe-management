import { useState } from "react";
import { useAuth } from "./useAuth.js";
import { forgotPasswordApi } from "./authApi.js";
import "./auth.css";

export default function ForgotPasswordPage({ onNavigate }) {
  const { beginOtp } = useAuth();
  const [email, setEmail] = useState("");
  const [errorMessage, setErrorMessage] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setErrorMessage("");

    const trimmed = email.trim();
    if (!trimmed) {
      setErrorMessage("Vui lòng nhập địa chỉ email của bạn.");
      return;
    }

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed)) {
      setErrorMessage("Địa chỉ email không đúng định dạng.");
      return;
    }

    setIsSubmitting(true);
    try {
      const normalized = trimmed.toLowerCase();
      const result = await forgotPasswordApi(normalized);
      beginOtp({ ...result, email: normalized, purpose: "RESET" });
      onNavigate("#verify-otp");
    } catch (err) {
      setErrorMessage(err.message || "Không thể gửi yêu cầu xác minh. Vui lòng thử lại sau.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <section className="auth-container" aria-label="Quên mật khẩu">
      <div className="auth-flow-card">
        <div className="auth-card-header">
          <a
            href="#login"
            className="auth-back-link"
            onClick={(e) => {
              e.preventDefault();
              onNavigate("#login");
            }}
          >
            ← Quay lại Đăng nhập
          </a>
          <div className="auth-brand-tag">
            LECKER<span>COFFEE CAFE</span>
          </div>
          <span className="auth-step-pill">Bước 01 / 03 · Khôi phục tài khoản</span>
          <h1>Quên mật khẩu</h1>
          <p>Nhập email đã đăng ký để nhận mã OTP xác minh</p>
        </div>

        {errorMessage && (
          <div className="auth-error-banner" role="alert">
            <span aria-hidden="true">⚠️</span>
            <span>{errorMessage}</span>
          </div>
        )}

        <form className="auth-form" onSubmit={handleSubmit} noValidate>
          <div className="form-group">
            <label className="form-label" htmlFor="recovery-email">
              Địa chỉ Email
            </label>
            <input
              id="recovery-email"
              type="email"
              className="form-input"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={isSubmitting}
              autoComplete="email"
              required
              autoFocus
              placeholder="example@smartcafe.vn"
              aria-invalid={Boolean(errorMessage)}
            />
            <p className="form-hint">
              Nếu email đủ điều kiện, bạn sẽ nhận được mã OTP 6 số có hiệu lực trong 10 phút.
            </p>
          </div>

          <div className="auth-actions-row">
            <button
              type="submit"
              className="btn-primary"
              disabled={isSubmitting}
            >
              {isSubmitting ? "Đang gửi mã..." : "Gửi mã OTP"}
            </button>
          </div>
        </form>

        <div className="auth-links-row" style={{ justifyContent: "center" }}>
          <a
            href="#login"
            className="auth-text-link"
            onClick={(e) => {
              e.preventDefault();
              onNavigate("#login");
            }}
          >
            Đã nhớ mật khẩu? Đăng nhập ngay
          </a>
        </div>
      </div>
    </section>
  );
}
