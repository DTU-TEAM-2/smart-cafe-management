import { useEffect, useState } from "react";
import { useAuth } from "./useAuth.js";
import { resendOtpApi, verifyOtpApi } from "./authApi.js";
import "./auth.css";

export default function OtpPage({ onNavigate }) {
  const { otpChallenge, beginOtp, completeOtp } = useAuth();
  const [otp, setOtp] = useState("");
  const [errorMessage, setErrorMessage] = useState("");
  const [statusMessage, setStatusMessage] = useState(
    otpChallenge ? "Mã xác minh OTP đã được tiếp nhận. Vui lòng kiểm tra email và thư rác." : ""
  );
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  const remainingSeconds = Math.max(0, Math.ceil(((otpChallenge?.expiresAt || 0) - now) / 1000));
  const cooldownSeconds = Math.max(0, Math.ceil(((otpChallenge?.resendAt || 0) - now) / 1000));

  async function handleSubmit(e) {
    e.preventDefault();
    setErrorMessage("");

    if (!/^[0-9]{6}$/.test(otp)) {
      setErrorMessage("Vui lòng nhập đầy đủ 6 chữ số OTP.");
      return;
    }

    if (!otpChallenge?.challengeId) {
      setErrorMessage("Phiên xác minh không hợp lệ. Vui lòng bắt đầu lại.");
      return;
    }

    setIsSubmitting(true);
    try {
      const result = await verifyOtpApi(otpChallenge.challengeId, otp);
      completeOtp(result.resetToken || result.token);
      onNavigate("#reset-password");
    } catch (err) {
      setErrorMessage(err.message || "Mã OTP không chính xác hoặc đã hết hạn.");
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleResend() {
    if (cooldownSeconds > 0 || isSubmitting) return;
    setErrorMessage("");
    setStatusMessage("");
    setIsSubmitting(true);

    try {
      const result = await resendOtpApi(otpChallenge.challengeId);
      beginOtp({ ...otpChallenge, ...result });
      setStatusMessage(result.message || "Mã OTP mới đã được gửi tới email của bạn.");
      setOtp("");
    } catch (err) {
      setErrorMessage(err.message || "Không thể gửi lại mã OTP. Vui lòng thử lại sau.");
      if (err.retryAfterSeconds) {
        beginOtp({ ...otpChallenge, retryAfterSeconds: err.retryAfterSeconds });
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  if (!otpChallenge) {
    return (
      <section className="auth-container" aria-label="Xác minh OTP">
        <div className="auth-flow-card" style={{ textAlign: "center" }}>
          <div className="auth-brand-tag">
            LECKER<span>COFFEE CAFE</span>
          </div>
          <h1>Phiên xác minh đã kết thúc</h1>
          <p style={{ margin: "16px 0 24px", color: "var(--muted)" }}>
            Chưa có yêu cầu xác minh nào đang chờ hoặc phiên làm việc đã hết hạn.
          </p>
          <button
            type="button"
            className="btn-primary"
            onClick={() => onNavigate("#forgot-password")}
          >
            Bắt đầu lại
          </button>
        </div>
      </section>
    );
  }

  return (
    <section className="auth-container" aria-label="Xác minh mã OTP">
      <div className="auth-flow-card">
        <div className="auth-card-header">
          <a
            href="#forgot-password"
            className="auth-back-link"
            onClick={(e) => {
              e.preventDefault();
              onNavigate("#forgot-password");
            }}
          >
            ← Nhập lại email
          </a>
          <div className="auth-brand-tag">
            LECKER<span>COFFEE CAFE</span>
          </div>
          <span className="auth-step-pill">Bước 02 / 03 · Xác minh email</span>
          <h1>Nhập mã OTP</h1>
          <p>Mã gồm 6 chữ số, chỉ sử dụng một lần trong 10 phút</p>
        </div>

        <div className="otp-target-email">
          Đang xác minh email: <span>{otpChallenge.email}</span>
        </div>

        {statusMessage && (
          <div className="auth-info-banner" role="status">
            <span>{statusMessage}</span>
          </div>
        )}

        {errorMessage && (
          <div className="auth-error-banner" role="alert">
            <span aria-hidden="true">⚠️</span>
            <span>{errorMessage}</span>
          </div>
        )}

        <form className="auth-form" onSubmit={handleSubmit} noValidate>
          <div className="form-group">
            <label className="form-label" htmlFor="otp-input">
              Mã xác minh 6 số
            </label>
            <input
              id="otp-input"
              type="text"
              inputMode="numeric"
              pattern="[0-9]{6}"
              maxLength={6}
              autoComplete="one-time-code"
              className="form-input otp-input"
              value={otp}
              onChange={(e) => setOtp(e.target.value.replace(/[^0-9]/g, ""))}
              disabled={isSubmitting || remainingSeconds === 0}
              autoFocus
              placeholder="000000"
              required
              aria-invalid={Boolean(errorMessage)}
            />
            <div className="otp-meta">
              <span>
                {remainingSeconds > 0
                  ? `Thời hạn: ${Math.floor(remainingSeconds / 60)}:${String(remainingSeconds % 60).padStart(2, "0")}`
                  : "Mã OTP đã hết hạn"}
              </span>
              <span>Tối đa 5 lần thử</span>
            </div>
          </div>

          <div className="auth-actions-row">
            <button
              type="submit"
              className="btn-primary"
              disabled={isSubmitting || remainingSeconds === 0 || otp.length !== 6}
            >
              {isSubmitting ? "Đang xác minh..." : "Xác minh OTP"}
            </button>
            <button
              type="button"
              className="btn-secondary"
              onClick={handleResend}
              disabled={isSubmitting || cooldownSeconds > 0}
            >
              {cooldownSeconds > 0 ? `Gửi lại sau ${cooldownSeconds}s` : "Gửi lại mã"}
            </button>
          </div>
        </form>

        <div className="auth-links-row" style={{ justifyContent: "center" }}>
          <p className="form-hint" style={{ textAlign: "center" }}>
            Tối đa 2 lần gửi lại. Tuyệt đối không chia sẻ mã với bất kỳ ai.
          </p>
        </div>
      </div>
    </section>
  );
}
