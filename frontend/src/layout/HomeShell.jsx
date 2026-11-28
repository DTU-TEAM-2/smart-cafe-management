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
const hashEntryKey = "__leckerHashEntry";
const accountTextFields = ["username", "email", "phone", "address", "birthday", "gender", "salary", "avatarUrl"];

function historyIndex() {
  return window.navigation?.currentEntry?.index ?? history.state?.[hashEntryKey];
}

function markHashEntry(index) {
  if (window.navigation?.currentEntry || (history.state != null
    && (typeof history.state !== "object" || Array.isArray(history.state)))) return;
  history.replaceState({ ...history.state, [hashEntryKey]: index }, "");
}

function isHomeId(id) {
  return typeof id === "string" && /^[1-9]\d{0,18}$/.test(id)
    && (id.length < 19 || id <= "9223372036854775807");
}

function isBirthday(value) {
  if (value == null) return true;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(value);
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

function isHomeAccount(account) {
  return isHomeId(account?.id)
    && typeof account.role === "string" && Object.hasOwn(roles, account.role)
    && typeof account.name === "string" && !!account.name.trim()
    && accountTextFields.every(field => account[field] == null || typeof account[field] === "string")
    && isBirthday(account.birthday);
}

function isNotice(item) {
  return typeof item?.id === "string" && !!item.id.trim()
    && typeof item.message === "string" && !!item.message.trim()
    && ["type", "tableId", "tableName", "createdAt", "href"].every(field => item[field] == null || typeof item[field] === "string")
    && (item.tableId == null || isHomeId(item.tableId))
    && (item.type == null || ["ORDER", "WAITER"].includes(item.type))
    && (item.createdAt == null || /^\d{4}-\d{2}-\d{2}T.*Z$/.test(item.createdAt)
      && isBirthday(item.createdAt.slice(0, 10)) && Number.isFinite(Date.parse(item.createdAt)));
}

export default function HomeShell({ authorization, credentials, loginHref = "#login" }) {
  const { sessionGeneration } = useAuth();
  return <ScopedHomeShell key={`${authorization ?? ""}:${credentials ?? ""}:${sessionGeneration}`}
    authorization={authorization} credentials={credentials} loginHref={loginHref} />;
}

function ScopedHomeShell({ authorization, credentials, loginHref }) {
  const auth = useAuth();
  const { captureSession, expireSession, isCurrentSession, verifiedUserId } = auth;
  const [hash, setHash] = useState(() => window.location.hash || "#top");
  const currentHash = useRef(hash);
  const currentHistoryIndex = useRef(historyIndex() ?? 0);
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
  const acceptHash = useCallback(next => {
    const index = historyIndex();
    if (Number.isSafeInteger(index) && index >= 0) currentHistoryIndex.current = index;
    else if (next !== currentHash.current) markHashEntry(++currentHistoryIndex.current);
    currentHash.current = next;
    setHash(next);
  }, []);
  const restoreHash = useCallback(() => {
    if ((location.hash || "#top") === currentHash.current) return;
    const arrived = historyIndex();
    const previous = currentHistoryIndex.current;
    if (previous >= 0 && arrived >= 0 && previous !== arrived) {
      history.go(previous - arrived);
    } else {
      history.replaceState(history.state, "", currentHash.current);
    }
  }, []);
  const [identity, setIdentity] = useState({ status: "loading", account: null });
  const [profileDisplay, setProfileDisplay] = useState(null);
  const verifiedProfileScope = useRef(null);
  const canPublishProfile = useCallback(profile => {
    const verified = verifiedProfileScope.current;
    return verified?.id === profile.id && verified.role === profile.role;
  }, []);
  const identityMismatch = Boolean(verifiedUserId && identity.account && verifiedUserId !== identity.account.id);
  const account = identityMismatch ? null : identity.account;
  const identityStatus = identityMismatch ? "error" : identity.status;
  const [accountAttempt, setAccountAttempt] = useState(0);
  const staffRecheckPending = useRef(false);
  const staffRecheckRetries = useRef(0);
  const feedPaused = useRef(false);
  const recheckIdentity = useCallback(() => {
    if (staffRecheckPending.current) return;
    staffRecheckPending.current = true;
    verifiedProfileScope.current = null;
    staffRecheckRetries.current = 0;
    setIdentity({ status: "loading", account: null });
    setAccountAttempt(value => value + 1);
  }, []);
  const news = useNewsSocket(authorization, account, recheckIdentity);
  const [feed, setFeed] = useState({ status: "idle", data: null });
  const [feedAttempt, setFeedAttempt] = useState(0);
  const onProfileLoaded = useCallback(profile => {
    setProfileDisplay({ id: profile.id, role: profile.role, name: profile.name, avatarUrl: profile.avatarUrl });
  }, []);
  // A profile response contributes display fields only after the account endpoint verifies its scope.
  const displayAccount = account && profileDisplay?.id === account.id && profileDisplay.role === account.role
    ? { ...account, name: profileDisplay.name, avatarUrl: profileDisplay.avatarUrl } : account;

  useEffect(() => {
    if (!Number.isSafeInteger(historyIndex())) markHashEntry(currentHistoryIndex.current);
    function onHash() {
      const next = window.location.hash || "#top";
      if (dirty.current && next !== currentHash.current) { setPendingNavigation(next); return; }
      const approved = approvedNewsHash.current === next;
      approvedNewsHash.current = null;
      if (!approved && next !== currentHash.current && newsGuard.current && !newsGuard.current()) {
        restoreHash();
        return;
      }
      acceptHash(next);
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
  }, [acceptHash, handleNavigate, restoreHash]);

  useEffect(() => {
    let active = true;
    let retryTimer;
    const controller = new AbortController();
    const session = captureSession();
    const isCurrent = () => active && isCurrentSession(session);
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
            verifiedProfileScope.current = null;
            setProfileDisplay(null);
            setIdentity({ status: "ready", account: null });
          }
          return;
        }
        if (!response.ok) throw new Error("Account unavailable");
        const account = await response.json();
        if (!isHomeAccount(account)) throw new Error("Invalid account response");
        if (isCurrent()) {
          staffRecheckPending.current = false;
          verifiedProfileScope.current = account;
          setIdentity({ status: "ready", account });
        }
      } catch {
        if (!isCurrent()) return;
        verifiedProfileScope.current = null;
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
  }, [authorization, credentials, accountAttempt, captureSession, isCurrentSession]);

  useEffect(() => {
    if (!account || (account.role !== "STAFF" && account.role !== "ADMIN")) {
      setFeed({ status: feedPaused.current ? "error" : "idle", data: null });
      return undefined;
    }
    if (feedPaused.current) return undefined;
    let timer;
    const controller = new AbortController();
    const session = captureSession();
    const isCurrent = () => !controller.signal.aborted && isCurrentSession(session);
    async function refresh() {
      let accessDenied = false;
      try {
        const headers = { Accept: "application/json" };
        if (authorization) headers.Authorization = authorization;
        const signal = AbortSignal.any([controller.signal, AbortSignal.timeout(headerRequestTimeoutMs)]);
        const response = await fetch("/api/v1/home/notifications", { headers, credentials, signal });
        accessDenied = response.status === 401 || response.status === 403;
        if (accessDenied && isCurrent()) {
          feedPaused.current = true;
          setFeed({ status: "error", data: null });
          if (response.status === 401 && session.token && authorization === `Bearer ${session.token}`) expireSession(session);
          else recheckIdentity();
          return;
        }
        if (!response.ok) throw new Error("Feed unavailable");
        const data = await response.json();
        if (data?.recipientId !== account.id || !Array.isArray(data.items)
          || data.items.length > 30 || !data.items.every(isNotice)) {
          throw new Error("Invalid recipient feed");
        }
        if (isCurrent()) setFeed({ status: "ready", data });
      } catch {
        if (isCurrent()) setFeed({ status: "error", data: null });
      } finally {
        if (isCurrent() && !accessDenied) timer = window.setTimeout(refresh, 5000);
      }
    }
    void refresh();
    return () => { controller.abort(); window.clearTimeout(timer); };
  }, [authorization, credentials, account, feedAttempt, captureSession, expireSession, isCurrentSession, recheckIdentity]);

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
  if (onMenuAdminPage) {
    if (identityStatus === "loading") return <main><p role="status">Đang xác minh tài khoản…</p></main>;
    if (identityStatus === "error") return <main><p role="alert">Chưa xác minh được tài khoản.</p>
      <button onClick={() => { setIdentity({ status: "loading", account: null }); setAccountAttempt(value => value + 1); }}>Thử lại</button></main>;
    if (account?.role !== "ADMIN" || !authorization) return <main><h1>Quản lý thực đơn</h1>
      <p role="alert">Chỉ quản trị viên đã đăng nhập mới có thể quản lý thực đơn.</p>
      <a href={account ? "#top" : loginHref}>{account ? "Về trang chủ" : "Đăng nhập"}</a></main>;
    return <Suspense fallback={<main><p role="status">Đang tải quản lý thực đơn…</p></main>}>
      <PasswordExpiryNotice status={auth.passwordStatus} />
      <MenuAdmin authorization={authorization} />
    </Suspense>;
  }
  if (onSalesPage) return <SalesWorkspace account={account} identityStatus={identityStatus}
    authorization={authorization} credentials={credentials} income={hash === "#sale-income"}
    retryIdentity={() => { setIdentity({ status: "loading", account: null }); setAccountAttempt(value => value + 1); }} />;
  function logout() {
    void auth.logout();
    if (!onNewsPage) window.location.hash = "#login";
  }
  const layout = {
    account: displayAccount, notifications: feed.data, links: { login: loginHref, news: "#news", menu: tableMenuHref }, notificationState: feed.status,
    onNavigate: handleNavigate,
    onRetryNotifications: () => { feedPaused.current = false; setFeed({ status: "loading", data: null }); setFeedAttempt(value => value + 1); },
    onLogout: () => {
      if (dirty.current) { setPendingNavigation(logoutNavigation); return; }
      if (newsGuard.current && !newsGuard.current()) return;
      logout();
    },
  };
  const isAdmin = account?.role === "ADMIN";
  const canUseStaffTools = account?.role === "STAFF" || isAdmin;
  const expiryNotice = auth.token && canUseStaffTools
    ? <PasswordExpiryNotice status={auth.passwordStatus} /> : null;
  if (onBillsPage && (isAdmin || (!onStatistics && account?.role === "STAFF"))) {
    return <BillsWorkspace layout={layout} authorization={authorization} credentials={credentials} statistics={onStatistics}
      isAdmin={isAdmin} expiryNotice={expiryNotice} />;
  }
  return <SiteLayout {...layout} homepage={!isSpecialPage || onNewsPage}
    currentPage={onAccountPage ? "account" : onLoginPage ? "login" : onNewsPage ? "news" : isSpecialPage ? null : hash === "#about" ? "about" : "home"} isAuthPage={isAuthPage}>
    {expiryNotice}
    {identityStatus === "error" && <div className="identity-error" role="alert">Chưa tải được thông tin tài khoản.
      <button type="button" onClick={() => setAccountAttempt(value => value + 1)}>Thử lại</button></div>}
    {onNewsPage ? (
      <Suspense fallback={<section className="account-page"><p role="status">Đang mở tin tức…</p></section>}>
        <NewsPage authorization={authorization} account={account} identityStatus={identityStatus}
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
      isAdmin ? <AdminDashboard onNavigate={handleNavigate} /> : <DashboardGate status={identityStatus} />
    ) : onStaffPage ? (
      canUseStaffTools ? <StaffDashboard onNavigate={handleNavigate} /> : <DashboardGate status={identityStatus} />
    ) : onAccountPage ? (
      account?.role === "CUSTOMER" && hash !== "#account-password"
        ? <CustomerAccountPage status={identityStatus} account={displayAccount} onNavigate={handleNavigate} />
        : <PersonnelAccountPage key={hash === "#account-password" ? "password" : "profile"} changingPassword={hash === "#account-password"}
          access={canUseStaffTools ? "verified" : identityStatus}
          canPublishProfile={canPublishProfile} onProfileLoaded={onProfileLoaded} onDirtyChange={onDirtyChange} />
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
      if ((window.location.hash || "#top") !== target) window.location.hash = target;
      // Commit the screen before the next browser traversal can return to the form.
      acceptHash(target);
    }} />}
  </SiteLayout>;
}

function DashboardGate({ status }) {
  if (status === "loading") return <p role="status">Đang xác minh tài khoản…</p>;
  if (status === "ready") return <p>Vui lòng dùng tài khoản có quyền truy cập khu vực này.</p>;
  return null;
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
