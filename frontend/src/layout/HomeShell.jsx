import { useEffect, useState, useSyncExternalStore } from "react";
import HomePage from "../home/HomePage.jsx";
import SiteLayout from "./SiteLayout.jsx";
import LoginPage from "../auth/LoginPage.jsx";
import ForgotPasswordPage from "../auth/ForgotPasswordPage.jsx";
import OtpPage from "../auth/OtpPage.jsx";
import ResetPasswordPage from "../auth/ResetPasswordPage.jsx";
import AdminDashboard from "../dashboard/AdminDashboard.jsx";
import StaffDashboard from "../dashboard/StaffDashboard.jsx";

function subscribeHash(callback) {
  window.addEventListener("hashchange", callback);
  return () => window.removeEventListener("hashchange", callback);
}

const money = new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" });
const roles = { ADMIN: "Quản lý", STAFF: "Nhân viên", CUSTOMER: "Khách hàng" };

export default function HomeShell(props) {
  // Remount account-scoped state on credential changes, including logout and account switching.
  return <ScopedHomeShell key={props.authorization ?? ""} {...props} />;
}

function ScopedHomeShell({ authorization = "", loginHref = "#login", credentials = "same-origin" }) {
  const hash = useSyncExternalStore(subscribeHash, () => window.location.hash);
  const [identity, setIdentity] = useState({ status: "loading", account: null });
  const [feed, setFeed] = useState({ status: "loading", data: null });
  const [readIds, setReadIds] = useState(() => new Set());
  const [accountAttempt, setAccountAttempt] = useState(0);
  const [feedAttempt, setFeedAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    async function loadAccount() {
      try {
        const response = await fetch("/api/v1/home/account", {
          headers: authorization ? { Authorization: authorization } : {},
          signal: AbortSignal.any([controller.signal, AbortSignal.timeout(10000)]),
          cache: "no-store",
          credentials,
        });
        if (response.status === 401 || response.status === 403) {
          if (!controller.signal.aborted) setIdentity({ status: "guest", account: null });
          return;
        }
        if (!response.ok) throw new Error("Account unavailable");
        const account = await response.json();
        if (typeof account.id !== "string" || typeof account.name !== "string" || !roles[account.role]) throw new Error("Invalid account");
        if (!controller.signal.aborted) setIdentity({ status: "ready", account });
      } catch {
        if (!controller.signal.aborted) setIdentity({ status: "error", account: null });
      }
    }
    void loadAccount();
    return () => controller.abort();
  }, [authorization, credentials, accountAttempt]);

  useEffect(() => {
    const account = identity.account;
    if (!account || !["STAFF", "ADMIN"].includes(account.role)) return;
    const controller = new AbortController();
    let timer;
    async function refresh() {
      try {
        const response = await fetch("/api/v1/home/notifications", {
          headers: authorization ? { Authorization: authorization } : {},
          signal: AbortSignal.any([controller.signal, AbortSignal.timeout(10000)]), cache: "no-store", credentials,
        });
        if (response.status === 401 || response.status === 403) {
          if (!controller.signal.aborted) {
            setIdentity({ status: "guest", account: null });
            setFeed({ status: "ready", data: null });
          }
          return;
        }
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

  const onAccountPage = hash === "#account";
  const onLoginPage = hash === "#login";
  const onForgotPasswordPage = hash === "#forgot-password";
  const onOtpPage = hash === "#verify-otp";
  const onResetPasswordPage = hash === "#reset-password";
  const onAdminPage = hash === "#admin";
  const onStaffPage = hash === "#staff";

  useEffect(() => {
    if (!onAccountPage && !onLoginPage && !onForgotPasswordPage && !onOtpPage && !onResetPasswordPage && !onAdminPage && !onStaffPage && hash) {
      const frame = requestAnimationFrame(() => document.getElementById(hash.slice(1))?.scrollIntoView());
      return () => cancelAnimationFrame(frame);
    }
    if (onAccountPage || onLoginPage || onForgotPasswordPage || onOtpPage || onResetPasswordPage || onAdminPage || onStaffPage) {
      window.scrollTo(0, 0);
      document.getElementById("main")?.focus({ preventScroll: true });
    }
  }, [hash, onAccountPage, onLoginPage, onForgotPasswordPage, onOtpPage, onResetPasswordPage, onAdminPage, onStaffPage]);

  function handleNavigate(newHash) {
    window.location.hash = newHash;
  }

  const account = identity.account ? { ...identity.account, profileHref: "#account" } : null;
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
