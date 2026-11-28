import { useEffect, useRef, useState } from "react";
import { useAuth } from "../auth/AuthContext.jsx";
import useAccountProfile from "./useAccountProfile.js";
import ProfileView, { ProfileIdentity } from "./ProfileView.jsx";
import ChangePasswordForm from "./ChangePasswordForm.jsx";
import { AccountIssue, ErrorSummary } from "./AccountFeedback.jsx";
import "./account.css";

export default function AccountPage({ changingPassword, access, scopeKey, getProfileScope, nextDisplaySequence, onProfileLoaded, onDirtyChange }) {
  const auth = useAuth();
  const account = useAccountProfile({ scopeKey, verified: access === "verified", getProfileScope, nextDisplaySequence, onProfileLoaded });
  const [success, setSuccess] = useState("");
  const editButton = useRef(null);
  const changeButton = useRef(null);
  const wasEditing = useRef(false);
  const profile = account.profile;
  useEffect(() => {
    if (account.snapshot && !wasEditing.current) document.getElementById("profile-name")?.focus();
    if (!account.snapshot && wasEditing.current) editButton.current?.focus();
    wasEditing.current = Boolean(account.snapshot);
  }, [account.snapshot]);
  useEffect(() => {
    if (changingPassword) return undefined;
    onDirtyChange(account.unsaved || account.saving);
    return () => onDirtyChange(false);
  }, [account.unsaved, account.saving, changingPassword, onDirtyChange]);

  function returnToProfile() {
    onDirtyChange(false);
    window.location.hash = "#account";
    requestAnimationFrame(() => changeButton.current?.focus());
  }

  async function save(event) {
    event.preventDefault();
    const session = auth.captureSession();
    const result = await account.save();
    const currentScope = getProfileScope();
    if (result.ok && auth.isCurrentSession(session) && currentScope && `${currentScope.id}:${currentScope.role}` === scopeKey) {
      setSuccess(result.unchanged ? "Thông tin của bạn không có thay đổi." : "Thông tin cá nhân đã được cập nhật thành công.");
    }
  }

  if (access !== "verified") {
    return <section className="personnel-page">
      <h1>{changingPassword ? "Đổi mật khẩu" : "Thông tin cá nhân"}</h1>
      {access === "loading" && <p role="status">Đang xác minh tài khoản…</p>}
      {access === "ready" && <p>Vui lòng đăng nhập bằng tài khoản nhân viên hoặc quản trị viên.</p>}
    </section>;
  }

  return <section className="personnel-page" aria-labelledby="personnel-title">
    <div className="personnel-breadcrumb"><a href="#top">Trang chủ</a><span aria-hidden="true">/</span><span>Tài khoản của tôi</span></div>
    <div className="personnel-heading"><div><p className="eyebrow">LECKER • Không gian của bạn</p>
      <h1 id="personnel-title">{changingPassword ? "Đổi mật khẩu" : "Thông tin cá nhân"}</h1>
      <p>Cập nhật thông tin và giữ tài khoản của bạn luôn an toàn.</p></div>
      <a className="personnel-back" href={changingPassword ? "#account" : "#top"}>← {changingPassword ? "Về hồ sơ" : "Về trang chủ"}</a></div>
    {(success || auth.accountNotice) && <div className="account-feedback account-feedback-success" role="status">
      <span className="personnel-check" aria-hidden="true">✓</span><span>{success || auth.accountNotice}</span>
      <button aria-label="Đóng thông báo thành công" type="button" onClick={() => { setSuccess(""); auth.clearAccountNotice(); }}>×</button></div>}
    {!profile ? <div className="personnel-state" aria-busy={account.status === "loading"}>
      {account.status === "loading" || account.status === "idle" ? <><div className="personnel-skeleton" aria-hidden="true" /><p role="status">Đang tải hồ sơ cá nhân…</p></>
        : account.status === "anonymous" ? <><h2>Vui lòng đăng nhập</h2><p>Phiên đăng nhập chưa có hoặc đã hết hiệu lực.</p><a className="hero-cta" href="#login">Đăng nhập</a></>
          : account.status === "forbidden" ? <><h2>Không có quyền truy cập</h2><p>Hồ sơ nhân sự dành cho quản trị viên và nhân viên.</p><a className="hero-cta" href="#top">Về trang chủ</a></>
            : <><h2>Chưa tải được hồ sơ</h2><AccountIssue issue={account.issue} /><button className="hero-cta" onClick={account.retry}>Thử lại</button></>}
    </div> : <div className="personnel-grid">
      <ProfileIdentity profile={profile} />
      <section className="personnel-card" aria-labelledby="profile-section-title">
        <div className="personnel-card-heading"><div><p className="personnel-kicker">{changingPassword ? "Bảo mật tài khoản" : "Hồ sơ nhân sự"}</p>
          <h2 id="profile-section-title">{changingPassword ? "Thiết lập mật khẩu mới" : account.snapshot ? "Cập nhật thông tin" : "Thông tin tài khoản"}</h2>
          <p>{changingPassword ? "Nhập mật khẩu hiện tại để xác nhận thay đổi." : account.snapshot ? "Bạn có thể chỉnh sửa họ tên, địa chỉ và điện thoại." : "Thông tin được đồng bộ từ hồ sơ của bạn tại LECKER."}</p></div>
          {!changingPassword && <span className="personnel-mode">{account.snapshot ? "Đang chỉnh sửa" : "Chế độ xem"}</span>}</div>
        {changingPassword ? <ChangePasswordForm account={account} onCancel={() => { account.clearErrors(); returnToProfile(); }} onSuccess={returnToProfile} onDirtyChange={onDirtyChange} />
          : <form noValidate onSubmit={save} aria-busy={account.saving}>
            <AccountIssue issue={account.issue} /><ErrorSummary errors={account.errors} prefix="profile" />
            <ProfileView profile={profile} snapshot={account.snapshot} draft={account.draft} errors={account.errors}
              saving={account.saving || account.writeUncertain} onChange={account.updateField} />
            <div className="personnel-form-actions">
              {account.writeUncertain ? <button className="hero-cta" type="button" onClick={account.retry}>Tải lại hồ sơ</button>
                : account.snapshot ? <><button className="account-secondary" type="button" disabled={account.saving} onClick={account.cancelEdit}>Hủy thay đổi</button>
                  <button key="save-profile" className="hero-cta" type="submit" disabled={account.saving}>{account.saving ? "Đang lưu…" : "Lưu thay đổi"}</button></>
                : <><a ref={changeButton} href="#account-password" className="account-secondary">Đổi mật khẩu</a>
                  {profile.canUpdateProfile && <button key="start-profile-edit" ref={editButton} className="hero-cta" type="button" onClick={event => { event.preventDefault(); setSuccess(""); auth.clearAccountNotice(); account.startEdit(); }}>Cập nhật thông tin <span aria-hidden="true">↗</span></button>}</>}
            </div>
          </form>}
      </section>
    </div>}
  </section>;
}
