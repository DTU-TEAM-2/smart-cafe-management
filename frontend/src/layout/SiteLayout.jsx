import { useId, useRef, useState } from "react";

const noticeTime = new Intl.DateTimeFormat("vi-VN", { hour: "2-digit", minute: "2-digit", day: "2-digit", month: "2-digit", timeZone: "Asia/Ho_Chi_Minh" });
function formattedTime(value) {
  const date = new Date(value);
  return Number.isFinite(date.getTime()) ? noticeTime.format(date) : null;
}

const navigation = [
  { label: "Trang chủ", href: "#top" },
  { label: "Menu", href: "#newest" },
  { label: "Tin tức", key: "news" },
  { label: "Về chúng tôi", href: "#about" },
];

const footer = [
  { label: "Liên hệ", key: "contact" },
  { label: "Giờ mở cửa", key: "hours" },
  { label: "Hỗ trợ", key: "support" },
];

function localLink(href) {
  return typeof href === "string" && /^(?:#[\w-]+$|\/(?!\/))/.test(href) && !/[\\\r\n]/.test(href);
}

function SiteLink({ href, children, ...props }) {
  return localLink(href)
    ? <a href={href} {...props}>{children}</a>
    : <span aria-disabled="true" title="Nội dung chưa được kết nối">{children}</span>;
}

function Icon({ name }) {
  return <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24"
    fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
    {name === "search" && <><circle cx="10.8" cy="10.8" r="5.6" /><path d="m15 15 4 4" /></>}
    {name === "heart" && <path d="M12 19 5.4 12.7a4 4 0 1 1 5.7-5.6l.9.9.9-.9a4 4 0 1 1 5.7 5.6Z" />}
    {name === "bell" && <path d="M18 8a6 6 0 0 0-12 0c0 6-3 6-3 8h18c0-2-3-2-3-8m-8 11a2 2 0 0 0 4 0" />}
    {name === "menu" && <path d="M4 6h16M4 12h16M4 18h16" />}
  </svg>;
}

export default function SiteLayout({ children, account = null, notifications = null, links = {},
  currentPage = "home", notificationState = "ready", onRetryNotifications, onNotificationsOpen }) {
  const [panel, setPanel] = useState(null);
  const regionId = useId();
  const menuButton = useRef(null);
  const accountButton = useRef(null);
  const notificationButton = useRef(null);
  const isStaff = account?.role === "STAFF" || account?.role === "ADMIN";
  const notices = isStaff && account.id && notifications?.recipientId === account.id
    ? [...new Map(notifications.items.map((notice) => [notice.id, notice])).values()]
    : null;
  const unread = notices?.filter((notice) => !notice.read).length ?? 0;

  function toggle(name) { setPanel((current) => current === name ? null : name); }
  function handleHeaderKey(event) {
    if (event.key !== "Escape" || !panel) return;
    event.preventDefault();
    ({ menu: menuButton, account: accountButton, notifications: notificationButton })[panel].current?.focus();
    setPanel(null);
  }

  return <>
    <div id="top" />
    <a href="#main" className="skip-link">Bỏ qua điều hướng</a>
    <header className="site-header" onKeyDown={handleHeaderKey} onBlur={(event) => {
      if (!event.currentTarget.contains(event.relatedTarget)) setPanel(null);
    }}>
      <div className="header-inner">
        <a className="brand" href="#top" aria-label="LECKER Trang chủ">
          <strong>LECKER</strong><span>Coffee Cafe</span>
        </a>
        <button ref={menuButton} type="button" className="icon-button mobile-toggle" aria-label="Điều hướng"
          aria-expanded={panel === "menu"} aria-controls={`${regionId}-nav`} onClick={() => toggle("menu")}>
          <Icon name="menu" />
        </button>
        <nav id={`${regionId}-nav`} className={`header-nav ${panel === "menu" ? "is-open" : ""}`} aria-label="Điều hướng chính">
          {navigation.map(({ label, href, key }, index) => (
            <SiteLink key={label} href={href ?? links[key]} aria-current={index === 0 && currentPage === "home" ? "page" : undefined}
              onClick={() => { if (panel === "menu") menuButton.current?.focus(); setPanel(null); }}>{label}</SiteLink>
          ))}
        </nav>
        <div className="header-actions">
          <a className="icon-button" href="#newest" aria-label="Khám phá danh sách món"><Icon name="search" /></a>
          <a className="icon-button" href="#most-ordered" aria-label="Món được gọi nhiều"><Icon name="heart" /></a>
          {isStaff && <div className="header-control">
            <button type="button" ref={notificationButton} className="icon-button"
              aria-label={unread ? `Thông báo, ${unread} chưa đọc` : "Thông báo"}
              aria-expanded={panel === "notifications"} aria-controls={`${regionId}-notifications`}
              onClick={() => { toggle("notifications"); onNotificationsOpen?.(); }}>
              <Icon name="bell" />{unread > 0 && <span className="notification-dot" />}
            </button>
            <section className="header-panel" id={`${regionId}-notifications`} hidden={panel !== "notifications"} aria-label="Thông báo phục vụ">
              <h2>Thông báo</h2>
              {notificationState === "loading" ? <p role="status">Đang tải thông báo…</p>
                : notificationState === "error" ? <div role="alert"><p>Chưa tải được thông báo.</p><button type="button" onClick={onRetryNotifications}>Thử lại</button></div>
                : notices === null ? <p>Chưa kết nối thông báo phục vụ.</p>
                : notices.length === 0 ? <p>Chưa có thông báo.</p>
                  : <ul className="notice-list">{notices.map(({ id, message, href, type, createdAt, read }) => <li key={id}>
                    <span className={`notice-symbol ${read ? "is-read" : ""}`} aria-hidden="true"><Icon name="bell" /></span>
                    <div><span className="notice-kind">{type === "ORDER" ? "Gọi món" : type === "WAITER" ? "Gọi phục vụ" : "Thông báo"}</span>
                      <p>{localLink(href) ? <a href={href}>{message}</a> : message}</p>
                      {formattedTime(createdAt) && <time dateTime={createdAt}>{formattedTime(createdAt)}</time>}
                    </div>
                  </li>)}</ul>}
            </section>
          </div>}
          {!account && localLink(links.login) ? <a href={links.login} className="header-login" aria-current={currentPage === "login" ? "page" : undefined}>Đăng nhập</a> : <div className="header-control">
            <button type="button" ref={accountButton} className="icon-button" aria-label={account ? `Tài khoản ${account.name}` : "Tài khoản"}
              aria-expanded={panel === "account"} aria-controls={`${regionId}-account`} onClick={() => toggle("account")}>
              <span className="avatar" aria-hidden="true">{account?.name?.trim().slice(0, 1).toLocaleUpperCase("vi")}</span>
            </button>
            <section className="header-panel" id={`${regionId}-account`} hidden={panel !== "account"} aria-label="Thông tin tài khoản">
              <h2>{account?.name ?? "Tài khoản"}</h2>
              {isStaff && <p><a href="#sale" onClick={() => setPanel(null)}>Bán hàng theo bàn</a></p>}
              {account && localLink(account.profileHref) ? <a href={account.profileHref} onClick={() => setPanel(null)}>Thông tin tài khoản</a>
                : !account && localLink(links.login) ? <a href={links.login}>Đăng nhập</a>
                  : <p>{account ? "Chưa kết nối trang hồ sơ." : "Bạn chưa đăng nhập."}</p>}
            </section>
          </div>}
        </div>
      </div>
    </header>
    <main id="main" tabIndex={-1}>{children}</main>
    <footer className="site-footer">
      <div className="footer-inner">
        <a className="brand footer-brand" href="#top" aria-label="LECKER Về đầu trang">
          <strong>LECKER</strong><span>COFFEE CAFE</span>
        </a>
        <nav aria-label="Liên kết cuối trang">
          {footer.map(({ label, key }) => <SiteLink key={key} href={links[key]}>{label}</SiteLink>)}
        </nav>
        <small>© 2026 LECKER Coffee Cafe</small>
      </div>
    </footer>
  </>;
}
