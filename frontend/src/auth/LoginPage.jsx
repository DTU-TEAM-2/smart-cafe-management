import { useId, useState } from "react";
export default function LoginPage() {
  const id = useId();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);

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
      <form onSubmit={event => event.preventDefault()} aria-describedby={`${id}-help`}>
        <label htmlFor={`${id}-username`}>Tên đăng nhập</label>
        <input id={`${id}-username`} name="username" autoComplete="username" required maxLength={64}
          value={username} onChange={event => setUsername(event.target.value)} />
        <label htmlFor={`${id}-password`}>Mật khẩu</label>
        <div className="password-field">
          <input id={`${id}-password`} name="password" type={showPassword ? "text" : "password"}
            autoComplete="current-password" required maxLength={128} value={password}
            onChange={event => setPassword(event.target.value)} />
          <button type="button" aria-label={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
            aria-pressed={showPassword} onClick={() => setShowPassword(value => !value)}>{showPassword ? "Ẩn" : "Hiện"}</button>
        </div>
        <button className="hero-cta login-submit" type="submit" disabled aria-describedby={`${id}-help`}>
          Đăng nhập<span aria-hidden="true">→</span>
        </button>
        <p id={`${id}-help`} className="login-help">Đăng nhập chưa khả dụng.</p>
      </form>
    </div>
  </section>;
}
