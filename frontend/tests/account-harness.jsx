// Test-only integration surface. This is not the product UI or a Figma implementation.
import { StrictMode, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import { AuthProvider, useAuth } from "../src/auth/AuthContext.jsx";
import LoginPage from "../src/auth/LoginPage.jsx";
import useAccountProfile from "../src/account/useAccountProfile.js";

const emptyPasswords = { currentPassword: "", newPassword: "", confirmNewPassword: "" };
function AccountHarness({ captured }) {
  const auth = useAuth();
  const account = useAccountProfile();
  const [passwords, setPasswords] = useState(emptyPasswords);
  const [changing, setChanging] = useState(false);
  const [notice, setNotice] = useState("");
  const profile = account.profile;
  return <main>
    <h1>DT2-28 API lifecycle harness</h1>
    <p>Test surface only; no Figma fidelity claim.</p>
    <p data-testid="identity">{auth.user?.fullName}</p>
    <p data-testid="expiry">{String(auth.passwordStatus?.mustChangePassword ?? "unknown")}</p>
    <p data-testid="status">{account.status}</p>
    <button onClick={() => void auth.logout()}>Đăng xuất</button>
    <button onClick={() => { captured.current = auth.captureSession(); }}>Capture session</button>
    <button onClick={() => auth.expireSession(captured.current)}>Expire captured session</button>
    {account.issue && <p role="alert">{account.issue.message}</p>}
    {notice && <p role="status">{notice}</p>}
    <button onClick={account.retry}>Retry GET</button>
    {account.reauthenticate && <button onClick={account.requireAuthentication}>Xác thực lại</button>}
    {profile && <>
      <dl>{["id", "username", "name", "address", "phone", "role", "birthday", "gender", "salary", "version"]
        .map(field => <div key={field}><dt>{field}</dt><dd data-testid={field}>{profile[field] ?? "Chưa cập nhật"}</dd></div>)}</dl>
      <button onClick={account.startEdit}>Edit profile</button>
      <button onClick={() => { account.clearErrors(); setChanging(true); }}>Change password</button>
    </>}
    {account.snapshot && <form noValidate onSubmit={async event => {
      event.preventDefault();
      const result = await account.save();
      if (result.ok) setNotice(result.unchanged ? "Không có thay đổi" : "Đã lưu");
    }}>
      {["name", "address", "phone"].map(field => <div key={field}>
        <label htmlFor={field}>{field}</label>
        <input id={field} value={account.draft[field] ?? account.snapshot[field] ?? ""}
          onChange={event => account.updateField(field, event.target.value)} />
        {account.errors[field] && <p role="alert">{account.errors[field]}</p>}
      </div>)}
      <button disabled={account.saving}>Save profile</button>
      <button type="button" onClick={account.cancelEdit}>Cancel edit</button>
    </form>}
    {changing && <form noValidate onSubmit={async event => {
      event.preventDefault();
      const result = await account.changeOwnPassword(passwords);
      if (result.ok || result.clearPasswords) setPasswords(emptyPasswords);
      if (result.ok) { setChanging(false); setNotice("Đã đổi mật khẩu"); }
    }}>
      {Object.keys(emptyPasswords).map(field => <div key={field}>
        <label htmlFor={field}>{field}</label>
        <input id={field} type="password" autoComplete={field === "currentPassword" ? "current-password" : "new-password"}
          value={passwords[field]} onChange={event => setPasswords(previous => ({ ...previous, [field]: event.target.value }))} />
        {account.errors[field] && <p role="alert">{account.errors[field]}</p>}
      </div>)}
      <button disabled={account.saving || account.reauthenticate}>Save password</button>
      <button type="button" onClick={() => { setChanging(false); setPasswords(emptyPasswords); account.clearErrors(); }}>Cancel password</button>
    </form>}
  </main>;
}

function Harness() {
  const { token, sessionGeneration, logout } = useAuth();
  const captured = useRef(null);
  return token ? <AccountHarness key={sessionGeneration} captured={captured} /> : <><button onClick={() => void logout()}>Cancel session</button><LoginPage onNavigate={() => {}} /></>;
}
createRoot(document.getElementById("root")).render(<StrictMode><AuthProvider><Harness /></AuthProvider></StrictMode>);
