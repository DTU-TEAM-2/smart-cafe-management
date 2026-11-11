import { useEffect, useState } from "react";
import { useAuth } from "./useAuth.js";
import { resetPasswordApi, verifyResetTokenApi } from "./authApi.js";
import "./auth.css";

export default function ResetPasswordPage({ onNavigate }) {
  const { recoveryToken, clearOtp } = useAuth();
  const [token] = useState(() => {
    const hashQuery = window.location.hash.includes("?")
      ? window.location.hash.split("?")[1]
      : "";
    const hashToken = hashQuery ? new URLSearchParams(hashQuery).get("token") : null;
    return recoveryToken || hashToken || new URLSearchParams(window.location.search).get("token") || "";
  });

  const [isVerifying, setIsVerifying] = useState(true);
  const [isTokenValid, setIsTokenValid] = useState(false);
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [successMessage, setSuccessMessage] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!token) {
      setIsVerifying(false);
      setIsTokenValid(false);
      setErrorMessage("Vui lòng hoàn thành bước xác minh OTP trước khi đặt mật khẩu mới.");
      return;
    }

    let ignore = false;
    async function checkToken() {
      try {
        const valid = await verifyResetTokenApi(token);
        if (!ignore) {
          setIsTokenValid(valid);
          if (!valid) {
            setErrorMessage("Liên kết hoặc mã đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.");
          }
        }
      } catch {
        if (!ignore) {
          setIsTokenValid(false);
          setErrorMessage("Không thể xác minh liên kết đặt lại mật khẩu.");
        }
      } finally {
        if (!ignore) {
          setIsVerifying(false);
        }
      }
    }

    checkToken();
    return () => {
      ignore = true;
    };
  }, [token]);

  async function handleSubmit(e) {
    e.preventDefault();
    setErrorMessage("");

    if (!newPassword || !confirmPassword) {
      setErrorMessage("Vui lòng nhập đầy đủ mật khẩu mới và xác nhận mật khẩu.");
      return;
    }

    if (newPassword !== confirmPassword) {
      setErrorMessage("Xác nhận mật khẩu không khớp.");
      return;
    }

    if (newPassword.length < 8 || newPassword.length > 64) {
      setErrorMessage("Mật khẩu phải có độ dài từ 8 đến 64 ký tự.");
      return;
    }

    const hasUpper = /[A-Z]/.test(newPassword);
    const hasLower = /[a-z]/.test(newPassword);
    const hasDigit = /[0-9]/.test(newPassword);

    if (!hasUpper || !hasLower || !hasDigit) {
      setErrorMessage("Mật khẩu phải chứa ít nhất 1 chữ hoa, 1 chữ thường và 1 chữ số.");
      return;
    }

    setIsSubmitting(true);
    try {
      const res = await resetPasswordApi(token, newPassword, confirmPassword);
      setSuccessMessage(res.message || "Mật khẩu đã được cập nhật thành công! Bạn có thể đăng nhập bằng mật khẩu mới.");
      clearOtp();
    } catch (err) {
      setErrorMessage(err.message || "Không thể đặt lại mật khẩu. Vui lòng thử lại.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <section className="auth-container" aria-label="Đặt lại mật khẩu">
      <div className="auth-flow-card">
        <div className="auth-card-header">
          <div className="auth-brand-tag">
            LECKER<span>COFFEE CAFE</span>
          </div>
          <span className="auth-step-pill">Bước 03 / 03 · Hoàn tất khôi phục</span>
          <h1>Đặt lại mật khẩu</h1>
          <p>Thiết lập mật khẩu mới cho tài khoản của bạn</p>
        </div>

        {isVerifying ? (
          <div style={{ textAlign: "center", padding: "24px 0", color: "var(--muted)" }}>
            Đang xác minh liên kết đặt lại mật khẩu...
          </div>
        ) : successMessage ? (
          <div>
            <div className="auth-success-banner" role="status">
              <span>{successMessage}</span>
            </div>
            <div className="auth-actions-row">
              <button
                type="button"
                className="btn-primary"
                onClick={() => onNavigate("#login")}
              >
                Đăng nhập ngay
              </button>
            </div>
          </div>
        ) : !isTokenValid ? (
          <div>
            <div className="auth-error-banner" role="alert">
              <span aria-hidden="true">⚠️</span>
              <span>{errorMessage || "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn."}</span>
            </div>
            <div className="auth-actions-row">
              <button
                type="button"
                className="btn-primary"
                onClick={() => onNavigate("#forgot-password")}
              >
                Yêu cầu mã OTP mới
              </button>
            </div>
          </div>
        ) : (
          <>
            {errorMessage && (
              <div className="auth-error-banner" role="alert">
                <span aria-hidden="true">⚠️</span>
                <span>{errorMessage}</span>
              </div>
            )}

            <form className="auth-form" onSubmit={handleSubmit} noValidate>
              <div className="form-group">
                <label className="form-label" htmlFor="new-password">
                  Mật khẩu mới
                </label>
                <div className="password-input-wrapper">
                  <input
                    id="new-password"
                    type={showPassword ? "text" : "password"}
                    className="form-input"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    disabled={isSubmitting}
                    autoComplete="new-password"
                    required
                    autoFocus
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
                <p className="form-hint">
                  Tối thiểu 8 ký tự, gồm ít nhất 1 chữ hoa, 1 chữ thường và 1 chữ số.
                </p>
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="confirm-password">
                  Xác nhận mật khẩu mới
                </label>
                <input
                  id="confirm-password"
                  type={showPassword ? "text" : "password"}
                  className="form-input"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  disabled={isSubmitting}
                  autoComplete="new-password"
                  required
                  aria-invalid={Boolean(errorMessage)}
                />
              </div>

              <div className="auth-actions-row">
                <button
                  type="submit"
                  className="btn-primary"
                  disabled={isSubmitting}
                >
                  {isSubmitting ? "Đang cập nhật..." : "Cập nhật mật khẩu"}
                </button>
              </div>
            </form>
          </>
        )}

        <div className="auth-links-row" style={{ justifyContent: "center" }}>
          <a
            href="#login"
            className="auth-text-link"
            onClick={(e) => {
              e.preventDefault();
              onNavigate("#login");
            }}
          >
            ← Quay lại trang Đăng nhập
          </a>
        </div>
      </div>
    </section>
  );
}
