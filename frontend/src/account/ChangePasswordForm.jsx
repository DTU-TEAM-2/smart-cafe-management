import { useEffect, useRef, useState } from "react";
import { AccountIssue, ErrorSummary } from "./AccountFeedback.jsx";

const empty = { currentPassword: "", newPassword: "", confirmNewPassword: "" };
const labels = { currentPassword: "Mật khẩu hiện tại", newPassword: "Mật khẩu mới", confirmNewPassword: "Xác nhận mật khẩu mới" };

export default function ChangePasswordForm({ account, onCancel, onSuccess, onDirtyChange }) {
  const [passwords, setPasswords] = useState(empty);
  const [visible, setVisible] = useState({});
  const first = useRef(null);
  const pending = useRef(false);
  useEffect(() => { first.current?.focus(); }, []);
  const dirty = Object.values(passwords).some(Boolean);
  useEffect(() => { onDirtyChange(dirty || account.saving); return () => onDirtyChange(false); }, [dirty, account.saving, onDirtyChange]);

  async function submit(event) {
    event.preventDefault();
    if (pending.current) return;
    pending.current = true;
    try {
      const result = await account.changeOwnPassword(passwords);
      if (result.ok || result.clearPasswords) setPasswords(empty);
      if (result.ok) onSuccess();
    } finally { pending.current = false; }
  }
  return <form className="personnel-password-form" noValidate onSubmit={submit} aria-busy={account.saving}>
    <AccountIssue issue={account.issue} />
    <ErrorSummary errors={account.errors} prefix="password" />
    <div className="personnel-password-hint" id="password-policy"><strong>Bảo vệ tài khoản của bạn</strong>
      <p>Dùng mật khẩu khác mật khẩu hiện tại, từ 8–64 ký tự; có ít nhất một chữ hoa, một chữ thường và một chữ số. Mật khẩu có dấu hoặc emoji có thể chạm giới hạn độ dài sớm hơn.</p></div>
    {Object.entries(labels).map(([field, label], index) => <div className="personnel-password-field" key={field}>
      <div className="personnel-password-label"><label htmlFor={`password-${field}`}>{label}</label><span aria-hidden="true">*</span></div>
      <div className="personnel-password-input">
        <input id={`password-${field}`} ref={index === 0 ? first : undefined} type={visible[field] ? "text" : "password"}
          name={field} required maxLength={256} autoComplete={field === "currentPassword" ? "current-password" : "new-password"}
          value={passwords[field]} readOnly={account.saving || account.reauthenticate}
          aria-invalid={Boolean(account.errors[field])}
          aria-describedby={`${field === "newPassword" ? "password-policy " : ""}${account.errors[field] ? `password-${field}-error` : ""}`.trim() || undefined}
          onChange={event => setPasswords(previous => ({ ...previous, [field]: event.target.value }))} />
        <button type="button" aria-label={`${visible[field] ? "Ẩn" : "Hiện"} ${label.toLocaleLowerCase("vi")}`}
          aria-pressed={Boolean(visible[field])} onClick={() => setVisible(previous => ({ ...previous, [field]: !previous[field] }))}>{visible[field] ? "Ẩn" : "Hiện"}</button>
      </div>
      {account.errors[field] && <span className="account-field-error" id={`password-${field}-error`}>{account.errors[field]}</span>}
    </div>)}
    <div className="personnel-form-actions">
      <button className="account-secondary" type="button" disabled={account.saving} onClick={() => { setPasswords(empty); onCancel(); }}>Hủy</button>
      {account.reauthenticate ? <button className="hero-cta" type="button" onClick={account.requireAuthentication}>Xác thực lại</button>
        : <button className="hero-cta" disabled={account.saving}>{account.saving ? "Đang cập nhật…" : "Cập nhật mật khẩu"}</button>}
    </div>
    <p className="personnel-security-note">Sau khi đổi mật khẩu, bạn tiếp tục sử dụng LECKER với phiên đăng nhập mới.</p>
  </form>;
}
