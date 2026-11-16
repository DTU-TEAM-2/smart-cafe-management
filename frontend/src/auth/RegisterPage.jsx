import { useId, useState } from "react";
import AuthSidebar from "./AuthSidebar.jsx";
import "./auth.css";

export default function RegisterPage({ onNavigate, initialStep = "form", initialChallenge = null }) {
  const id = useId();
  const [step, setStep] = useState(initialStep); // "form" | "verify"
  const [fullName, setFullName] = useState("");
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [otp, setOtp] = useState("");
  const [errorMessage, setErrorMessage] = useState("");
  const [blockedNotice, setBlockedNotice] = useState(null);

  // Legitimate registration challenge from backend
  const [registrationChallenge] = useState(initialChallenge);

  const handleNavigate = onNavigate || ((href) => { window.location.hash = href; });

  function handleSubmitForm(e) {
    e.preventDefault();
    setErrorMessage("");
    setBlockedNotice(null);

    const trimmedName = fullName.trim();
    const trimmedUsername = username.trim();
    const trimmedEmail = email.trim();

    if (!trimmedName || !trimmedUsername || !trimmedEmail || !password || !confirmPassword) {
      setErrorMessage("Vui lòng điền đầy đủ tất cả các trường bắt buộc.");
      return;
    }

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmedEmail)) {
      setErrorMessage("Địa chỉ email không đúng định dạng.");
      return;
    }

    if (password.length < 8) {
      setErrorMessage("Mật khẩu phải có ít nhất 8 ký tự.");
      return;
    }

    const hasUpper = /[A-Z]/.test(password);
    const hasLower = /[a-z]/.test(password);
    const hasDigit = /[0-9]/.test(password);
    if (!hasUpper || !hasLower || !hasDigit) {
      setErrorMessage("Mật khẩu phải chứa ít nhất 1 chữ hoa, 1 chữ thường và 1 chữ số.");
      return;
    }

    if (password !== confirmPassword) {
      setErrorMessage("Xác nhận mật khẩu không khớp.");
      return;
    }

    // Backend DT2-2 scope: Check actual backend API contract
    // The current Backend DT2-2 does NOT have a customer registration endpoint (/api/v1/auth/register).
    // Per instructions: DO NOT fabricate mock endpoints, mock challengeIds, or fake success states.
    // Explicitly report that registration is BLOCKED (OUT OF SCOPE) on this environment.
    setBlockedNotice(
      "Đăng ký tài khoản trực tuyến hiện chưa khả dụng. Vui lòng đăng nhập hoặc liên hệ nhân viên quán để được hỗ trợ."
    );
  }

  function handleVerifyOtp(e) {
    e.preventDefault();
    setErrorMessage("");

    if (!registrationChallenge?.challengeId) {
      setErrorMessage("Chưa có phiên đăng ký hợp lệ từ máy chủ. Vui lòng quay lại biểu mẫu đăng ký.");
      return;
    }

    if (otp.length !== 6) {
      setErrorMessage("Vui lòng nhập đầy đủ 6 chữ số OTP.");
      return;
    }
  }

  return (
    <section className="auth-page-container auth-container" aria-label="Đăng ký tài khoản">
      <div className="auth-card">
        {/* Left Column: Atmospheric Story Side */}
        <AuthSidebar
          eyebrow="LECKER COFFEE CAFE"
          title={"Đăng ký thành viên.\nƯu đãi đặc quyền."}
          description="Tạo tài khoản để trải nghiệm dịch vụ tại LECKER."
        />

        {/* Right Column: Register Panel */}
        <div className="auth-content auth-flow-card">
          <a
            href="#login"
            className="auth-back-link"
            onClick={(e) => {
              e.preventDefault();
              handleNavigate("#login");
            }}
          >
            ← Về đăng nhập
          </a>

          <div className="auth-brand-tag">
            LECKER<span>COFFEE CAFE</span>
          </div>

          {step === "form" ? (
            <>
              <div className="auth-header-row">
                <span className="auth-step-pill">Đăng ký thành viên</span>
              </div>

              <h1 className="auth-title">Đăng ký tài khoản</h1>
              <p className="auth-subtitle">
                Tạo tài khoản để trải nghiệm dịch vụ và ưu đãi độc quyền tại LECKER Coffee Cafe.
              </p>

              {errorMessage && (
                <div id={`${id}-error`} className="auth-error-banner" role="alert">
                  <span aria-hidden="true">⚠️</span>
                  <span>{errorMessage}</span>
                </div>
              )}

              {blockedNotice && (
                <div className="auth-warning-banner" role="alert">
                  <span aria-hidden="true">ℹ️</span>
                  <div>
                    <strong>Chức năng chưa khả dụng:</strong>
                    <p style={{ margin: "4px 0 0" }}>{blockedNotice}</p>
                  </div>
                </div>
              )}

              <form className="auth-form" onSubmit={handleSubmitForm} noValidate>
                <div className="form-row">
                  <div className="form-group">
                    <label className="form-label" htmlFor={`${id}-fullname`}>
                      Họ và tên
                      <span className="required-asterisk" aria-hidden="true" title="Bắt buộc">*</span>
                    </label>
                    <input
                      id={`${id}-fullname`}
                      type="text"
                      aria-label="Họ và tên"
                      className="form-input"
                      aria-invalid={Boolean(errorMessage)}
                      aria-describedby={errorMessage ? `${id}-error` : undefined}
                      value={fullName}
                      onChange={(e) => setFullName(e.target.value)}
                      placeholder="Nguyễn Văn A"
                      required
                    />
                  </div>

                  <div className="form-group">
                    <label className="form-label" htmlFor={`${id}-username`}>
                      Tên đăng nhập
                      <span className="required-asterisk" aria-hidden="true" title="Bắt buộc">*</span>
                    </label>
                    <input
                      id={`${id}-username`}
                      type="text"
                      aria-label="Tên đăng nhập"
                      className="form-input"
                      aria-invalid={Boolean(errorMessage)}
                      aria-describedby={errorMessage ? `${id}-error` : undefined}
                      value={username}
                      onChange={(e) => setUsername(e.target.value)}
                      placeholder="username123"
                      required
                    />
                  </div>
                </div>

                <div className="form-row">
                  <div className="form-group">
                    <label className="form-label" htmlFor={`${id}-email`}>
                      Địa chỉ Email
                      <span className="required-asterisk" aria-hidden="true" title="Bắt buộc">*</span>
                    </label>
                    <input
                      id={`${id}-email`}
                      type="email"
                      aria-label="Địa chỉ Email"
                      className="form-input"
                      aria-invalid={Boolean(errorMessage)}
                      aria-describedby={errorMessage ? `${id}-error` : undefined}
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      placeholder="you@example.com"
                      required
                    />
                  </div>

                  <div className="form-group">
                    <label className="form-label" htmlFor={`${id}-phone`}>
                      Số điện thoại
                    </label>
                    <input
                      id={`${id}-phone`}
                      type="tel"
                      aria-label="Số điện thoại"
                      className="form-input"
                      aria-invalid={Boolean(errorMessage)}
                      aria-describedby={errorMessage ? `${id}-error` : undefined}
                      value={phone}
                      onChange={(e) => setPhone(e.target.value)}
                      placeholder="0912345678"
                    />
                  </div>
                </div>

                <div className="form-row">
                  <div className="form-group">
                    <label className="form-label" htmlFor={`${id}-password`}>
                      Mật khẩu
                      <span className="required-asterisk" aria-hidden="true" title="Bắt buộc">*</span>
                    </label>
                    <div className="password-input-wrapper">
                      <input
                        id={`${id}-password`}
                        type={showPassword ? "text" : "password"}
                        aria-label="Mật khẩu"
                        className="form-input"
                      aria-invalid={Boolean(errorMessage)}
                      aria-describedby={errorMessage ? `${id}-error` : undefined}
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        required
                      />
                      <button
                        type="button"
                        className="password-toggle-btn"
                        onClick={() => setShowPassword((prev) => !prev)}
                        aria-label={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                      >
                        {showPassword ? "Ẩn" : "Hiện"}
                      </button>
                    </div>
                  </div>

                  <div className="form-group">
                    <label className="form-label" htmlFor={`${id}-confirm`}>
                      Xác nhận mật khẩu
                      <span className="required-asterisk" aria-hidden="true" title="Bắt buộc">*</span>
                    </label>
                    <input
                      id={`${id}-confirm`}
                      type={showPassword ? "text" : "password"}
                      aria-label="Xác nhận mật khẩu"
                      className="form-input"
                      aria-invalid={Boolean(errorMessage)}
                      aria-describedby={errorMessage ? `${id}-error` : undefined}
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                      required
                    />
                  </div>
                </div>

                <div className="auth-actions-row">
                  <button type="submit" className="btn-primary">
                    <span>ĐĂNG KÝ & NHẬN OTP</span>
                    <span aria-hidden="true">→</span>
                  </button>
                </div>
              </form>

              <div className="auth-links-row" style={{ justifyContent: "center" }}>
                <a
                  href="#login"
                  className="auth-link"
                  onClick={(e) => {
                    e.preventDefault();
                    handleNavigate("#login");
                  }}
                >
                  Đã có tài khoản? Đăng nhập ngay
                </a>
              </div>
            </>
          ) : !registrationChallenge ? (
            /* Direct access or missing registration challenge state */
            <div className="auth-invalid-state" style={{ textAlign: "center", padding: "16px 0" }}>
              <span className="auth-step-pill">Chưa có phiên làm việc</span>
              <h1 className="auth-title">Chưa có phiên đăng ký</h1>
              <p className="auth-subtitle">
                Không tìm thấy thông tin đăng ký hợp lệ hoặc phiên xác minh đã hết hạn. Vui lòng quay lại biểu mẫu đăng ký.
              </p>
              <div className="auth-actions-row" style={{ justifyContent: "center", marginTop: "20px" }}>
                <button
                  type="button"
                  className="btn-primary"
                  onClick={() => setStep("form")}
                >
                  ← Về biểu mẫu đăng ký
                </button>
              </div>
            </div>
          ) : (
            /* Legitimate Verify View with valid challenge from backend */
            <>
              <div className="auth-header-row">
                <span className="auth-step-pill">Xác nhận đăng ký</span>
              </div>

              <h1 className="auth-title">Xác minh tài khoản</h1>
              <p className="auth-subtitle">
                Nhập mã kích hoạt 6 chữ số gửi tới email của bạn để kích hoạt tài khoản.
              </p>

              <div className="otp-target-email">
                Đang xác minh email: <span>{registrationChallenge.email}</span>
              </div>

              {errorMessage && (
                <div id={`${id}-error`} className="auth-error-banner" role="alert">
                  <span aria-hidden="true">⚠️</span>
                  <span>{errorMessage}</span>
                </div>
              )}

              <form className="auth-form" onSubmit={handleVerifyOtp} noValidate>
                <div className="form-group">
                  <label className="form-label" htmlFor={`${id}-reg-otp`}>
                    Mã kích hoạt 6 số
                    <span className="required-asterisk" aria-hidden="true" title="Bắt buộc">*</span>
                  </label>

                  <div className="otp-wrapper">
                    <div className="otp-boxes-container">
                      {[0, 1, 2, 3, 4, 5].map((index) => {
                        const digit = otp[index] || "";
                        return (
                          <div
                            key={index}
                            className={`otp-box ${digit ? "is-filled" : ""}`}
                          >
                            {digit}
                          </div>
                        );
                      })}
                      <input
                        id={`${id}-reg-otp`}
                        type="text"
                        inputMode="numeric"
                        maxLength={6}
                        className="otp-accessible-input"
                        value={otp}
                        onChange={(e) => setOtp(e.target.value.replace(/[^0-9]/g, "").slice(0, 6))}
                        autoFocus
                      />
                    </div>
                  </div>
                </div>

                <div className="auth-actions-row">
                  <button type="submit" className="btn-primary" disabled={otp.length !== 6}>
                    <span>KÍCH HOẠT TÀI KHOẢN</span>
                    <span aria-hidden="true">→</span>
                  </button>
                </div>
              </form>

              <div className="auth-links-row" style={{ justifyContent: "center" }}>
                <a
                  href="#login"
                  className="auth-link"
                  onClick={(e) => {
                    e.preventDefault();
                    handleNavigate("#login");
                  }}
                >
                  ← Về đăng nhập
                </a>
              </div>
            </>
          )}
        </div>
      </div>
    </section>
  );
}
