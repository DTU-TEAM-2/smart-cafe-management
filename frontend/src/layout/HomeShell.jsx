import { useCallback, useEffect, useRef, useState } from "react";
import HomePage from "../home/HomePage.jsx";
import SiteLayout from "./SiteLayout.jsx";
import LoginPage from "../auth/LoginPage.jsx";
import RegisterPage from "../auth/RegisterPage.jsx";
import ForgotPasswordPage from "../auth/ForgotPasswordPage.jsx";
import OtpPage from "../auth/OtpPage.jsx";
import ResetPasswordPage from "../auth/ResetPasswordPage.jsx";
import AdminDashboard from "../dashboard/AdminDashboard.jsx";
import StaffDashboard from "../dashboard/StaffDashboard.jsx";
import SalesWorkspace from "../sales/SalesWorkspace.jsx";
import CustomerApp from "../customer/CustomerApp.jsx";
import { useAuth } from "../auth/AuthContext.jsx";
import PersonnelAccountPage from "../account/AccountPage.jsx";
import PasswordExpiryNotice from "../account/PasswordExpiryNotice.jsx";
import LeaveProfileDialog from "../account/LeaveProfileDialog.jsx";

const roles = { ADMIN: "Quản trị viên", STAFF: "Nhân viên phục vụ", CUSTOMER: "Khách hàng thân thiết" };
const money = new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" });

export default function HomeShell({ authorization, credentials, loginHref = "#login" }) {
  const { sessionGeneration } = useAuth();
  return <ScopedHomeShell key={`${authorization ?? ""}:${credentials ?? ""}:${sessionGeneration}`}
    authorization={authorization} credentials={credentials} loginHref={loginHref} />;
}

function ScopedHomeShell({ authorization, credentials, loginHref }) {
  const auth = useAuth();
  const [hash, setHash] = useState(() => window.location.hash || "#top");
  const currentHash = useRef(hash);
  const dirty = useRef(false);
  const [pendingNavigation, setPendingNavigation] = useState(null);
  const onDirtyChange = useCallback(value => { dirty.current = value; }, []);
  const [identity, setIdentity] = useState({ status: "loading", account: null });
  const [accountAttempt, setAccountAttempt] = useState(0);
  const [feed, setFeed] = useState({ status: "idle", data: null });
  const [feedAttempt, setFeedAttempt] = useState(0);
  const [readIds, setReadIds] = useState(() => new Set());
  const profileAuthority = useRef(0);
  const onProfileLoaded = useCallback(profile => {
    profileAuthority.current += 1;
    setIdentity({ status: "ready", account: profile });
  }, []);

  useEffect(() => {
    function onHash() {
      const next = window.location.hash || "#top";
      if (dirty.current && next !== currentHash.current) { setPendingNavigation(next); return; }
      currentHash.current = next;
      setHash(next);
    }
    function onLink(event) {
      if (!dirty.current || event.defaultPrevented || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
      const link = event.target.closest?.("a[href]");
      if (!link || link.target === "_blank" || link.hasAttribute("download")) return;
      const url = new URL(link.href, window.location.href);
      if (url.origin !== location.origin || url.pathname !== location.pathname || url.search !== location.search
        || !url.hash || url.hash === "#main" || url.hash === currentHash.current || link.closest(".account-feedback")) return;
      event.preventDefault();
      setPendingNavigation(url.hash);
    }
    function onUnload(event) { if (dirty.current) { event.preventDefault(); event.returnValue = ""; } }
    window.addEventListener("hashchange", onHash);
    document.addEventListener("click", onLink, true);
    window.addEventListener("beforeunload", onUnload);
    return () => { window.removeEventListener("hashchange", onHash); document.removeEventListener("click", onLink, true); window.removeEventListener("beforeunload", onUnload); };
  }, []);

  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    const expectedAuthority = profileAuthority.current;
    async function loadAccount() {
      setIdentity(current => (current.status === "ready" ? current : { status: "loading", account: null }));
      try {
        const headers = { Accept: "application/json" };
        if (authorization) headers.Authorization = authorization;
        const response = await fetch("/api/v1/home/account", { headers, credentials, signal: controller.signal });
        if (response.status === 401 || response.status === 403) {
          if (active && expectedAuthority === profileAuthority.current) setIdentity({ status: "ready", account: null });
          return;
        }
        if (!response.ok) throw new Error("Account unavailable");
        const account = await response.json();
        if (active && expectedAuthority === profileAuthority.current) setIdentity({ status: "ready", account });
      } catch {
        if (active && expectedAuthority === profileAuthority.current) setIdentity({ status: "error", account: null });
      }
    }
    void loadAccount();
    return () => { active = false; controller.abort(); };
  }, [authorization, credentials, accountAttempt]);

  useEffect(() => {
    const account = identity.account;
    if (!account || (account.role !== "STAFF" && account.role !== "ADMIN")) {
      setFeed({ status: "idle", data: null });
      return undefined;
    }
    let timer;
    const controller = new AbortController();
    async function refresh() {
      try {
        const headers = { Accept: "application/json" };
        if (authorization) headers.Authorization = authorization;
        const response = await fetch("/api/v1/home/notifications", { headers, credentials, signal: controller.signal });
        if (!response.ok) throw new Error("Feed unavailable");
        const data = await response.json();
        if (data.recipientId !== account.id || !Array.isArray(data.items)
          || data.items.length > 30 || !data.items.every(item => typeof item.id === "string" && typeof item.message === "string")) {
          throw new Error("Invalid recipient feed");
        }
        if (!controller.signal.aborted) setFeed({ status: "ready", data });
      } catch {
        if (!controller.signal.aborted) setFeed({ status: "error", data: null });
      } finally {
        if (!controller.signal.aborted) timer = window.setTimeout(refresh, 5000);
      }
    }
    void refresh();
    return () => { controller.abort(); window.clearTimeout(timer); };
  }, [authorization, credentials, identity.account, feedAttempt]);

  const onAccountPage = hash.startsWith("#account");
  const onLoginPage = hash.startsWith("#login");
  const onRegisterPage = hash.startsWith("#register");
  const onForgotPasswordPage = hash.startsWith("#forgot-password");
  const onOtpPage = hash.startsWith("#verify-otp");
  const onResetPasswordPage = hash.startsWith("#reset-password");
  const onAdminPage = hash.startsWith("#admin");
  const onStaffPage = hash.startsWith("#staff");
  const onSalesPage = hash === "#sale" || hash === "#sale-income";
  const onCustomerPage = hash.startsWith("#customer");

  const isAuthPage = onLoginPage || onRegisterPage || onForgotPasswordPage || onOtpPage || onResetPasswordPage;
  const isSpecialPage = onAccountPage || isAuthPage || onAdminPage || onStaffPage || onSalesPage || onCustomerPage;

  useEffect(() => {
    if (!isSpecialPage && hash) {
      const frame = requestAnimationFrame(() => document.getElementById(hash.slice(1))?.scrollIntoView());
      return () => cancelAnimationFrame(frame);
    }
    if (isSpecialPage) {
      window.scrollTo(0, 0);
      document.getElementById(onSalesPage ? "sale-main" : "main")?.focus({ preventScroll: true });
    }
  }, [hash, isSpecialPage, onSalesPage]);

  function handleNavigate(newHash) {
    window.location.hash = newHash;
  }

  if (onCustomerPage) return <CustomerApp onNavigateHome={() => handleNavigate("#top")} />;
  const account = identity.account ? { ...identity.account, profileHref: "#account" } : null;
  if (onSalesPage) return <SalesWorkspace account={account} identityStatus={identity.status}
    authorization={authorization} credentials={credentials} income={hash === "#sale-income"}
    retryIdentity={() => { setIdentity({ status: "loading", account: null }); setAccountAttempt(value => value + 1); }} />;
  const notifications = feed.data && { ...feed.data, items: feed.data.items.map(item => ({ ...item, read: readIds.has(item.id) })) };
  return <SiteLayout account={account} notifications={notifications} links={{ login: loginHref }}
    currentPage={onAccountPage ? "account" : onLoginPage ? "login" : "home"} isAuthPage={isAuthPage} notificationState={feed.status}
    onRetryNotifications={() => { setFeed({ status: "loading", data: null }); setFeedAttempt(value => value + 1); }}
    onNotificationsOpen={() => setReadIds(previous => new Set([...previous, ...(feed.data?.items.map(item => item.id) ?? [])]))}
    onLogout={() => { void auth.logout(); window.location.hash = "#login"; }}>
    {auth.token && ["ADMIN", "STAFF"].includes(auth.user?.role) && <PasswordExpiryNotice status={auth.passwordStatus} />}
    {identity.status === "error" && <div className="identity-error" role="alert">Chưa tải được thông tin tài khoản.
      <button type="button" onClick={() => setAccountAttempt(value => value + 1)}>Thử lại</button></div>}
    {onLoginPage ? (
      account ? (
        <section className="account-page"><h1>Bạn đã đăng nhập</h1><a href="#account">Xem thông tin tài khoản</a></section>
      ) : (
        <LoginPage onNavigate={handleNavigate} />
      )
    ) : onRegisterPage ? (
      <RegisterPage onNavigate={handleNavigate} />
    ) : onForgotPasswordPage ? (
      <ForgotPasswordPage onNavigate={handleNavigate} />
    ) : onOtpPage ? (
      <OtpPage onNavigate={handleNavigate} />
    ) : onResetPasswordPage ? (
      <ResetPasswordPage onNavigate={handleNavigate} />
    ) : onAdminPage ? (
      <AdminDashboard onNavigate={handleNavigate} />
    ) : onStaffPage ? (
      <StaffDashboard onNavigate={handleNavigate} />
    ) : onAccountPage ? (
      identity.account?.role === "CUSTOMER" && hash !== "#account-password"
        ? <CustomerAccountPage status={identity.status} account={identity.account} />
        : <PersonnelAccountPage key={hash === "#account-password" ? "password" : "profile"} changingPassword={hash === "#account-password"} onProfileLoaded={onProfileLoaded} onDirtyChange={onDirtyChange} />
    ) : (
      <HomePage />
    )}
    {pendingNavigation && <LeaveProfileDialog onCancel={() => {
      history.replaceState(history.state, "", `${location.pathname}${location.search}${currentHash.current}`);
      setPendingNavigation(null);
    }} onLeave={() => {
      const target = pendingNavigation;
      dirty.current = false;
      setPendingNavigation(null);
      currentHash.current = target;
      setHash(target);
      if (window.location.hash !== target) window.location.hash = target;
    }} />}
  </SiteLayout>;
}

function CustomerAccountPage({ status, account }) {
  if (status === "loading") return <section className="account-page"><p role="status">Đang tải thông tin tài khoản…</p></section>;
  if (!account) return <section className="account-page"><h1>Thông tin tài khoản</h1><p>Bạn chưa đăng nhập hoặc phiên đã hết hạn.</p><a href="#top">Về trang chủ</a></section>;
  const birthday = account.birthday ? account.birthday.split("-").reverse().join("/") : null;
  const gender = { MALE: "Nam", FEMALE: "Nữ" }[account.gender];
  const salary = typeof account.salary === "string" && /^\d+$/.test(account.salary) ? money.format(BigInt(account.salary)) : null;
  const fields = [
    ["Tên đăng nhập", account.username], ["Họ và tên", account.name], ["Email", account.email],
    ["Địa chỉ", account.address], ["Điện thoại", account.phone], ["Vai trò", roles[account.role]],
    ["Ngày sinh", birthday], ["Giới tính", gender], ...(account.role !== "CUSTOMER" ? [["Lương", salary]] : []),
  ];
  return <section className="account-page" aria-labelledby="account-title">
    <p className="eyebrow">LECKER • Tài khoản của bạn</p>
    <h1 id="account-title">Thông tin cá nhân</h1>
    <p className="account-greeting">Xin chào, {account.name}.</p>
    <dl className="account-details">{fields.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value || "Chưa cập nhật"}</dd></div>)}</dl>
    <a className="hero-cta" href="#top">Về trang chủ <span aria-hidden="true">→</span></a>
  </section>;
}
