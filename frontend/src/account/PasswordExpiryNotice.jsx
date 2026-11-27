export default function PasswordExpiryNotice({ status }) {
  if (!status?.mustChangePassword) return null;
  return <aside className="personnel-expiry" aria-label="Cảnh báo mật khẩu">
    <span className="personnel-expiry-symbol" aria-hidden="true">!</span>
    <div><strong>Đã đến lúc cập nhật mật khẩu</strong><p>Hệ thống yêu cầu bạn đổi mật khẩu để bảo vệ tài khoản.</p></div>
    <a href="#account-password">Đổi mật khẩu <span aria-hidden="true">→</span></a>
  </aside>;
}
