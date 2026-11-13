import { useEffect, useId, useRef, useState } from "react";
import { loginWithExistingModule } from "./loginApi.js";
export default function LoginPage() {
  const id = useId();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [busy, setBusy] = useState(false), [error, setError] = useState("");
  const controller = useRef(null), errorRef = useRef(null);
  useEffect(() => {
    const abort = new AbortController(); controller.current = abort;
    return () => abort.abort();
  }, []);
  async function submit(event) {
    event.preventDefault();
    if (busy) return;
    setBusy(true); setError("");
    try {
      const token = await loginWithExistingModule(username, password, controller.current.signal);
      if (controller.current.signal.aborted) return;
      sessionStorage.setItem("smart_cafe_token", token);
      setPassword("");
      location.hash = "#news";
      window.dispatchEvent(new Event("smart-cafe-auth-change"));
    } catch (failure) {
      if (!controller.current.signal.aborted) {
        setError(failure instanceof TypeError || failure.name === "TimeoutError" ? "Chưa kết nối được máy chủ đăng nhập. Vui lòng thử lại." : failure.message);
        requestAnimationFrame(() => errorRef.current?.focus());
      }
    } finally { if (!controller.current.signal.aborted) setBusy(false); }
  }

  return <section className="login-page" aria-labelledby={`${id}-title`}>
    <div className="login-story">
      <img src="/images/cafe-interior.jpg" alt="" />
      <div><p className="eyebrow">LECKER Coffee Cafe</p><h2>Một tách cà phê.<br />Một ngày thật đẹp.</h2>
        <p>Chào mừng bạn trở lại với LECKER.</p></div>
    </div>
    <div className="login-card">
      <a href="#top" className="login-back">← Về trang chủ</a>
      <p className="eyebrow">Tài khoản LECKER</p>
      <h1 id={`${id}-title`}>Đăng nhập</h1>
      <p className="login-intro">Rất vui được gặp lại bạn.</p>
      {error && <p className="login-error" role="alert" tabIndex={-1} ref={errorRef}>{error}</p>}
      <form onSubmit={submit} aria-describedby={`${id}-help`} aria-busy={busy}>
        <label htmlFor={`${id}-username`}>Tên đăng nhập</label>
        <input id={`${id}-username`} name="username" autoComplete="username" required maxLength={255} disabled={busy}
          value={username} onChange={event => setUsername(event.target.value)} />
        <label htmlFor={`${id}-password`}>Mật khẩu</label>
        <div className="password-field">
          <input id={`${id}-password`} name="password" type={showPassword ? "text" : "password"}
            autoComplete="current-password" required maxLength={128} disabled={busy} value={password}
            onChange={event => setPassword(event.target.value)} />
          <button type="button" aria-label={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
            aria-pressed={showPassword} onClick={() => setShowPassword(value => !value)}>{showPassword ? "Ẩn" : "Hiện"}</button>
        </div>
        <button className="hero-cta login-submit" type="submit" disabled={busy} aria-describedby={`${id}-help`}>
          {busy ? "Đang đăng nhập…" : "Đăng nhập"}<span aria-hidden="true">→</span>
        </button>
        <p id={`${id}-help`} className="login-help">Dùng tên đăng nhập hoặc email tài khoản của bạn.</p>
      </form>
    </div>
  </section>;
}
