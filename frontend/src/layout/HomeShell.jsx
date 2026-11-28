import { lazy, Suspense, useCallback, useEffect, useRef, useState } from "react";
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
import BillsWorkspace from "../billing/BillsWorkspace.jsx";
import CustomerApp from "../customer/CustomerApp.jsx";
import { useAuth } from "../auth/AuthContext.jsx";
import PersonnelAccountPage from "../account/AccountPage.jsx";
import PasswordExpiryNotice from "../account/PasswordExpiryNotice.jsx";
import LeaveProfileDialog from "../account/LeaveProfileDialog.jsx";

import useNewsSocket from "../news/useNewsSocket.js";
import { readCustomerSession } from "../customer/customerSession.js";

const MenuAdmin = lazy(() => import("../menu/MenuAdmin.jsx"));
const NewsPage = lazy(() => import("../news/NewsPage.jsx"));

const roles = { ADMIN: "Quản trị viên", STAFF: "Nhân viên phục vụ", CUSTOMER: "Khách hàng thân thiết" };
const money = new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" });
const logoutNavigation = Symbol("logout");
const headerRequestTimeoutMs = 10000;
const staffRecheckRetryMs = 5000;
const maxStaffRecheckRetries = 3;
const accountTextFields = ["username", "email", "phone", "address", "birthday", "gender", "salary", "avatarUrl"];

function isHomeAccount(account) {
  return typeof account?.id === "string" && /^[1-9]\d{0,18}$/.test(account.id)
    && (account.id.length < 19 || account.id <= "9223372036854775807")
    && typeof account.role === "string" && Object.hasOwn(roles, account.role)
    && typeof account.name === "string" && !!account.name.trim()
    && accountTextFields.every(field => account[field] == null || typeof account[field] === "string");
}

function isNotice(item) {
  return typeof item?.id === "string" && !!item.id.trim()
    && typeof item.message === "string" && !!item.message.trim()
    && ["type", "tableId", "tableName", "createdAt", "href"].every(field => item[field] == null || typeof item[field] === "string")
    && (item.createdAt == null || Number.isFinite(Date.parse(item.createdAt)));
}

export default function HomeShell({ authorization, credentials, loginHref = "#login" }) {
  const { sessionGeneration } = useAuth();
  return <ScopedHomeShell key={`${authorization ?? ""}:${credentials ?? ""}:${sessionGeneration}`}
    authorization={authorization} credentials={credentials} loginHref={loginHref} />;
}

function ScopedHomeShell({ authorization, credentials, loginHref }) {
  const auth = useAuth();
  const [hash, setHash] = useState(() => window.location.hash || "#top");
  const currentHash = useRef(hash);
  const currentHistoryIndex = useRef(window.navigation?.currentEntry?.index);
  const dirty = useRef(false);
  const newsGuard = useRef(null);
  const approvedNewsHash = useRef(null);
  const setNavigationGuard = useCallback(guard => { newsGuard.current = guard; }, []);
  const [pendingNavigation, setPendingNavigation] = useState(null);
  const onDirtyChange = useCallback(value => { dirty.current = value; }, []);
  const handleNavigate = useCallback(next => {
    if (next === currentHash.current) return;
    if (dirty.current) {
      setPendingNavigation(next);
      return;
    }
    if (newsGuard.current && !newsGuard.current()) return;
    approvedNewsHash.current = next;
    window.location.hash = next;
  }, []);
  const restoreHash = useCallback(() => {
    if ((location.hash || "#top") === currentHash.current) return;
    const arrived = window.navigation?.currentEntry?.index;
    const previous = currentHistoryIndex.current;
    if (previous >= 0 && arrived >= 0 && previous !== arrived) {
      history.go(previous - arrived);
    } else {
      history.replaceState(history.state, "", currentHash.current);
    }
  }, []);
  const [identity, setIdentity] = useState({ status: "loading", account: null });
  const [accountAttempt, setAccountAttempt] = useState(0);
  const staffRecheckPending = useRef(false);
  const staffRecheckRetries = useRef(0);
  const recheckIdentity = useCallback(() => {
    staffRecheckPending.current = true;
    staffRecheckRetries.current = 0;
    setIdentity({ status: "loading", account: null });
    setAccountAttempt(value => value + 1);
  }, []);
  const news = useNewsSocket(authorization, identity.account, recheckIdentity);
  const [feed, setFeed] = useState({ status: "idle", data: null });
  const [feedAttempt, setFeedAttempt] = useState(0);
  const [readIds, setReadIds] = useState(() => new Set());
  const onProfileLoaded = useCallback(profile => {
    if (staffRecheckPending.current) return;
    setIdentity(current => {
      const account = current.account;
      if (!account || account.id !== profile.id || account.role !== profile.role
        || (account.name === profile.name && account.avatarUrl === profile.avatarUrl)) return current;
      return { ...current, account: { ...account, name: profile.name, avatarUrl: profile.avatarUrl } };
    });
  }, []);

  useEffect(() => {
    function onHash() {
      const next = window.location.hash || "#top";
      if (dirty.current && next !== currentHash.current) { setPendingNavigation(next); return; }
      const approved = approvedNewsHash.current === next;
      approvedNewsHash.current = null;
      if (!approved && next !== currentHash.current && newsGuard.current && !newsGuard.current()) {
        restoreHash();
        return;
      }
      currentHash.current = next;
      currentHistoryIndex.current = window.navigation?.currentEntry?.index;
      setHash(next);
    }
    function onLink(event) {
      if ((!dirty.current && !newsGuard.current) || event.defaultPrevented || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
      const link = event.target.closest?.("a[href]");
      if (!link || link.target === "_blank" || link.hasAttribute("download")) return;
      const url = new URL(link.href, window.location.href);
      if (newsGuard.current && url.origin === location.origin && url.hash === "#main") {
        event.preventDefault();
        document.getElementById("main")?.focus();
        return;
      }
      if (url.origin !== location.origin || url.pathname !== location.pathname || url.search !== location.search
        || !url.hash || url.hash === "#main" || url.hash === currentHash.current || link.closest(".account-feedback")) return;
      event.preventDefault();
      handleNavigate(url.hash);
    }
    function onUnload(event) { if (dirty.current) { event.preventDefault(); event.returnValue = ""; } }
    window.addEventListener("hashchange", onHash);
    document.addEventListener("click", onLink);
    window.addEventListener("beforeunload", onUnload);
    return () => { window.removeEventListener("hashchange", onHash); document.removeEventListener("click", onLink); window.removeEventListener("beforeunload", onUnload); };
  }, [handleNavigate, restoreHash]);

  useEffect(() => {
    let active = true;
    let retryTimer;
    const controller = new AbortController();
    const isCurrent = () => active;
    async function loadAccount() {
      setIdentity(current => (current.status === "ready" ? current : { status: "loading", account: null }));
      try {
        const headers = { Accept: "application/json" };
        if (authorization) headers.Authorization = authorization;
        const signal = AbortSignal.any([controller.signal, AbortSignal.timeout(headerRequestTimeoutMs)]);
        const response = await fetch("/api/v1/home/account", { headers, credentials, signal });
        if (response.status === 401 || response.status === 403) {
          if (isCurrent()) {
            staffRecheckPending.current = false;
            setIdentity({ status: "ready", account: null });
          }
          return;
        }
        if (!response.ok) throw new Error("Account unavailable");
        const account = await response.json();
        if (!isHomeAccount(account)) throw new Error("Invalid account response");
        if (isCurrent()) {
          staffRecheckPending.current = false;
          setIdentity({ status: "ready", account });
        }
      } catch {
        if (!isCurrent()) return;
        setIdentity({ status: "error", account: null });
        if (staffRecheckPending.current && staffRecheckRetries.current < maxStaffRecheckRetries) {
          const delay = staffRecheckRetryMs * 2 ** staffRecheckRetries.current++;
          retryTimer = window.setTimeout(() => {
            if (isCurrent()) setAccountAttempt(value => value + 1);
          }, delay);
        }
      }
    }
    void loadAccount();
    return () => { active = false; controller.abort(); window.clearTimeout(retryTimer); };
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
      let accessDenied = false;
      try {
        const headers = { Accept: "application/json" };
        if (authorization) headers.Authorization = authorization;
        const signal = AbortSignal.any([controller.signal, AbortSignal.timeout(headerRequestTimeoutMs)]);
        const response = await fetch("/api/v1/home/notifications", { headers, credentials, signal });
        accessDenied = response.status === 401 || response.status === 403;
        if (!response.ok) throw new Error("Feed unavailable");
        const data = await response.json();
        if (data?.recipientId !== account.id || !Array.isArray(data.items)
          || data.items.length > 30 || !data.items.every(isNotice)) {
          throw new Error("Invalid recipient feed");
        }
        if (!controller.signal.aborted) setFeed({ status: "ready", data });
      } catch {
        if (!controller.signal.aborted) setFeed({ status: "error", data: null });
      } finally {
        if (!controller.signal.aborted && !accessDenied) timer = window.setTimeout(refresh, 5000);
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
  const onMenuAdminPage = hash === "#admin-menu";
  const onStaffPage = hash.startsWith("#staff");
  const onNewsPage = /^#news(?:\/|$)/.test(hash);
  const onSalesPage = hash === "#sale" || hash === "#sale-income";
  const onCustomerPage = hash.startsWith("#customer");
  const onStatistics = hash === "#statistics";
  const onBillsPage = onStatistics || ["#bills", "#manage/bills", "#manage-bills"].includes(hash);

  const isAuthPage = onLoginPage || onRegisterPage || onForgotPasswordPage || onOtpPage || onResetPasswordPage;
  const isSpecialPage = onAccountPage || isAuthPage || onAdminPage || onStaffPage || onSalesPage || onCustomerPage || onNewsPage || onBillsPage;

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

  if (onCustomerPage) return <CustomerApp onNavigateHome={() => handleNavigate("#top")} />;
  const tableMenuHref = readCustomerSession() ? "#customer" : undefined;
  const account = identity.account;
  if (onMenuAdminPage) {
    if (identity.status === "loading") return <main><p role="status">Đang xác minh tài khoản…</p></main>;
    if (identity.status === "error") return <main><p role="alert">Chưa xác minh được tài khoản.</p>
      <button onClick={() => { setIdentity({ status: "loading", account: null }); setAccountAttempt(value => value + 1); }}>Thử lại</button></main>;
    if (account?.role !== "ADMIN" || !authorization) return <main><h1>Quản lý thực đơn</h1>
      <p role="alert">Chỉ quản trị viên đã đăng nhập mới có thể quản lý thực đơn.</p>
      <a href={account ? "#top" : loginHref}>{account ? "Về trang chủ" : "Đăng nhập"}</a></main>;
    return <Suspense fallback={<main><p role="status">Đang tải quản lý thực đơn…</p></main>}>
      <PasswordExpiryNotice status={auth.passwordStatus} />
      <MenuAdmin authorization={authorization} />
    </Suspense>;
  }
  if (onSalesPage) return <SalesWorkspace account={account} identityStatus={identity.status}
    authorization={authorization} credentials={credentials} income={hash === "#sale-income"}
    retryIdentity={() => { setIdentity({ status: "loading", account: null }); setAccountAttempt(value => value + 1); }} />;
  const notifications = feed.data && { ...feed.data, items: feed.data.items.map(item => ({ ...item, read: readIds.has(item.id) })) };
  function logout() {
    void auth.logout();
    if (!onNewsPage) window.location.hash = "#login";
  }
  const layout = {
    account, notifications, links: { login: loginHref, news: "#news", menu: tableMenuHref }, notificationState: feed.status,
    onNavigate: handleNavigate,
    onRetryNotifications: () => { setFeed({ status: "loading", data: null }); setFeedAttempt(value => value + 1); },
    onNotificationsOpen: () => setReadIds(previous => new Set([...previous, ...(feed.data?.items.map(item => item.id) ?? [])])),
    onLogout: () => {
      if (dirty.current) { setPendingNavigation(logoutNavigation); return; }
      if (newsGuard.current && !newsGuard.current()) return;
      logout();
    },
  };
  const isAdmin = account?.role === "ADMIN";
  const expiryNotice = auth.token && ["ADMIN", "STAFF"].includes(auth.user?.role)
    ? <PasswordExpiryNotice status={auth.passwordStatus} /> : null;
  if (onBillsPage && (isAdmin || (!onStatistics && account?.role === "STAFF"))) {
    return <BillsWorkspace layout={layout} authorization={authorization} credentials={credentials} statistics={onStatistics}
      isAdmin={isAdmin} expiryNotice={expiryNotice} />;
  }
  return <SiteLayout {...layout} homepage={!isSpecialPage || onNewsPage}
    currentPage={onAccountPage ? "account" : onLoginPage ? "login" : onNewsPage ? "news" : isSpecialPage ? null : hash === "#about" ? "about" : "home"} isAuthPage={isAuthPage}>
    {expiryNotice}
    {identity.status === "error" && <div className="identity-error" role="alert">Chưa tải được thông tin tài khoản.
      <button type="button" onClick={() => setAccountAttempt(value => value + 1)}>Thử lại</button></div>}
    {onNewsPage ? (
      <Suspense fallback={<section className="account-page"><p role="status">Đang mở tin tức…</p></section>}>
        <NewsPage authorization={authorization} account={identity.account} identityStatus={identity.status}
          route={hash} revision={news.revision} connection={news.connection} setNavigationGuard={setNavigationGuard} />
      </Suspense>
    ) : onLoginPage ? (
      account ? (
        <section className="account-page"><h1>Bạn đã đăng nhập</h1><a href="#account">Xem thông tin tài khoản</a></section>
      ) : (
        <LoginPage onNavigate={handleNavigate} returnHref={tableMenuHref} />
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
        ? <CustomerAccountPage status={identity.status} account={identity.account} onNavigate={handleNavigate} />
        : <PersonnelAccountPage key={hash === "#account-password" ? "password" : "profile"} changingPassword={hash === "#account-password"}
          access={account?.role === "STAFF" || account?.role === "ADMIN" ? "verified" : identity.status}
          onProfileLoaded={onProfileLoaded} onDirtyChange={onDirtyChange} />
    ) : onBillsPage ? (
      <section className="account-page">
        <h1>{onStatistics ? "Thống kê doanh thu" : "Khu vực quản lý hóa đơn"}</h1>
        {identity.status === "loading" ? <p role="status">Đang tải thông tin tài khoản…</p>
          : identity.status === "ready" && <p>{onStatistics ? "Chỉ Quản trị viên (ADMIN) được xem thống kê." : "Khu vực dành cho nhân viên và Quản trị viên (ADMIN)."}</p>}
        <a href={account ? "#top" : loginHref}>{account ? "Về trang chủ" : "Đăng nhập"}</a>
      </section>
    ) : (
      <HomePage tableMenuHref={tableMenuHref} />
    )}
    {pendingNavigation && <LeaveProfileDialog onCancel={() => {
      restoreHash();
      setPendingNavigation(null);
    }} onLeave={() => {
      const target = pendingNavigation;
      dirty.current = false;
      setPendingNavigation(null);
      if (target === logoutNavigation) { logout(); return; }
      currentHash.current = target;
      setHash(target);
      if (window.location.hash !== target) window.location.hash = target;
      else currentHistoryIndex.current = window.navigation?.currentEntry?.index;
    }} />}
  </SiteLayout>;
}

function CustomerAccountPage({ status, account, onNavigate }) {
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
  return <section className="account-page customer-account-page" aria-labelledby="account-title">
    <header className="customer-account-summary">
      <p className="eyebrow">Tài khoản LECKER</p>
      <h1 id="account-title">Thông tin cá nhân</h1>
      <p className="account-greeting">Xin chào, {account.name}.</p>
    </header>
    <dl className="account-details">{fields.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value || "Chưa cập nhật"}</dd></div>)}</dl>
    <button className="hero-cta" type="button" onClick={() => onNavigate("#top")}>Về trang chủ <span aria-hidden="true">→</span></button>
  </section>;
}
