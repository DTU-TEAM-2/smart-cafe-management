import { useEffect, useState, useSyncExternalStore } from "react";
import HomePage from "../home/HomePage.jsx";
import SiteLayout from "./SiteLayout.jsx";
import LoginPage from "../auth/LoginPage.jsx";
import ForgotPasswordPage from "../auth/ForgotPasswordPage.jsx";
import OtpPage from "../auth/OtpPage.jsx";
import ResetPasswordPage from "../auth/ResetPasswordPage.jsx";
import AdminDashboard from "../dashboard/AdminDashboard.jsx";
import StaffDashboard from "../dashboard/StaffDashboard.jsx";
import SalesWorkspace from "../sales/SalesWorkspace.jsx";

function subscribeHash(callback) {
  window.addEventListener("hashchange", callback);
  return () => window.removeEventListener("hashchange", callback);
}

const roles = { ADMIN: "Quản trị viên", STAFF: "Nhân viên phục vụ", CUSTOMER: "Khách hàng thân thiết" };
const money = new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" });

export default function HomeShell({ authorization, credentials, loginHref = "#login" }) {
  return <ScopedHomeShell key={`${authorization ?? ""}:${credentials ?? ""}`}
    authorization={authorization} credentials={credentials} loginHref={loginHref} />;
}

function ScopedHomeShell({ authorization, credentials, loginHref }) {
  const hash = useSyncExternalStore(subscribeHash, () => window.location.hash || "#top");
  const [identity, setIdentity] = useState({ status: "loading", account: null });
  const [accountAttempt, setAccountAttempt] = useState(0);
  const [feed, setFeed] = useState({ status: "idle", data: null });
  const [feedAttempt, setFeedAttempt] = useState(0);
  const [readIds, setReadIds] = useState(() => new Set());

  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    async function loadAccount() {
      setIdentity(current => (current.status === "ready" ? current : { status: "loading", account: null }));
      try {
        const headers = { Accept: "application/json" };
        if (authorization) headers.Authorization = authorization;
        const response = await fetch("/api/v1/home/account", { headers, credentials, signal: controller.signal });
        if (response.status === 401 || response.status === 403) {
          if (active) setIdentity({ status: "ready", account: null });
          return;
        }
        if (!response.ok) throw new Error("Account unavailable");
        const account = await response.json();
        if (active) setIdentity({ status: "ready", account });
      } catch {
        if (active) setIdentity({ status: "error", account: null });
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
  const onForgotPasswordPage = hash.startsWith("#forgot-password");
  const onOtpPage = hash.startsWith("#verify-otp");
  const onResetPasswordPage = hash.startsWith("#reset-password");
  const onAdminPage = hash.startsWith("#admin");
  const onStaffPage = hash.startsWith("#staff");
  const onSalesPage = hash === "#sale" || hash === "#sale-income";

  const isSpecialPage = onAccountPage || onLoginPage || onForgotPasswordPage || onOtpPage || onResetPasswordPage || onAdminPage || onStaffPage || onSalesPage;

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

  const account = identity.account ? { ...identity.account, profileHref: "#account" } : null;
  if (onSalesPage) return <SalesWorkspace account={account} identityStatus={identity.status}
    authorization={authorization} credentials={credentials} income={hash === "#sale-income"}
    retryIdentity={() => { setIdentity({ status: "loading", account: null }); setAccountAttempt(value => value + 1); }} />;
  const notifications = feed.data && { ...feed.data, items: feed.data.items.map(item => ({ ...item, read: readIds.has(item.id) })) };
  return <SiteLayout account={account} notifications={notifications} links={{ login: loginHref }}
    currentPage={onAccountPage ? "account" : onLoginPage ? "login" : "home"} notificationState={feed.status}
    onRetryNotifications={() => { setFeed({ status: "loading", data: null }); setFeedAttempt(value => value + 1); }}
    onNotificationsOpen={() => setReadIds(previous => new Set([...previous, ...(feed.data?.items.map(item => item.id) ?? [])]))}>
    {identity.status === "error" && <div className="identity-error" role="alert">Chưa tải được thông tin tài khoản.
      <button type="button" onClick={() => setAccountAttempt(value => value + 1)}>Thử lại</button></div>}
    {onLoginPage ? (
      account ? (
        <section className="account-page"><h1>Bạn đã đăng nhập</h1><a href="#account">Xem thông tin tài khoản</a></section>
      ) : (
        <LoginPage onNavigate={handleNavigate} />
      )
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
      <AccountPage status={identity.status} account={identity.account} />
    ) : (
      <HomePage />
    )}
  </SiteLayout>;
}

function AccountPage({ status, account }) {
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
