import { useId, useRef, useState } from "react";
import AccountAvatar from "./AccountAvatar.jsx";

const noticeTime = new Intl.DateTimeFormat("vi-VN", { hour: "2-digit", minute: "2-digit", day: "2-digit", month: "2-digit", timeZone: "Asia/Ho_Chi_Minh" });
function formattedTime(value) {
  const date = new Date(value);
  return Number.isFinite(date.getTime()) ? noticeTime.format(date) : null;
}

const customerMenuHref = "#customer";
const navigation = [
  { label: "Trang chủ", href: "#top" },
  { label: "Thực đơn", key: "menu", href: customerMenuHref },
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
    {name === "account" && <><circle cx="12" cy="8" r="3" /><path d="M5 20a7 7 0 0 1 14 0" /></>}
    {name === "pin" && <><path d="M19 10c0 5-7 10-7 10S5 15 5 10a7 7 0 1 1 14 0Z" /><circle cx="12" cy="10" r="2.5" /></>}
    {name === "phone" && <path d="M21 16.5v2.8a2 2 0 0 1-2.2 2A18.7 18.7 0 0 1 2.7 5.2 2 2 0 0 1 4.7 3h2.8a2 2 0 0 1 2 1.7l.6 3a2 2 0 0 1-.6 2L8.2 11a15 15 0 0 0 4.8 4.8l1.3-1.3a2 2 0 0 1 2-.6l3 .6a2 2 0 0 1 1.7 2Z" />}
    {name === "mail" && <><rect x="3" y="5" width="18" height="14" rx="2" /><path d="m4 7 8 6 8-6" /></>}
    {name === "logout" && <path d="M10 4H5v16h5m4-12 4 4-4 4m-5-4h9" />}
    {name === "sales" && <path d="M5 11h14M7 11v8m10-8v8M6 6h12l2 5H4l2-5Z" />}
  </svg>;
}

function HomeFooter({ links, onBackToTop }) {
  const phone = "0934678320";
  const callHref = `tel:${phone}`;
  const email = "lecker@gmail.com";
  const topLabel = <>Về đầu trang <span aria-hidden="true">↑</span></>;

  return <div className="home-footer-inner">
    <div className="home-footer-grid">
      <div className="home-footer-brand">
        <a className="brand footer-brand" href="#top" aria-label="LECKER Coffee Cafe Về đầu trang">
          <strong>LECKER</strong><span>COFFEE CAFE</span>
        </a>
        <p className="home-footer-tagline">Cà phê ngon, chuyện trò lâu hơn.</p>
        <p className="home-footer-note">Dành một khoảng thảnh thơi cho chính mình, mỗi ngày.</p>
      </div>
      <section className="home-footer-visit" aria-labelledby="home-footer-visit">
        <h2 id="home-footer-visit">Ghé LECKER</h2>
        <address>
          <dl className="home-footer-contact">
            <div>
              <dt><span className="home-footer-contact-icon"><Icon name="pin" /></span>Địa chỉ</dt>
              <dd>295 Nguyễn Tất Thành</dd>
            </div>
            <div>
              <dt><span className="home-footer-contact-icon"><Icon name="phone" /></span>Điện thoại</dt>
              <dd><a href={callHref}>{phone}</a></dd>
            </div>
            <div>
              <dt><span className="home-footer-contact-icon"><Icon name="mail" /></span>Email</dt>
              <dd><a href={`mailto:${email}`}>{email}</a></dd>
            </div>
          </dl>
        </address>
        <a className="home-footer-call" href={callHref}><Icon name="phone" /> Gọi cho LECKER</a>
      </section>
      <section className="home-footer-hours" aria-labelledby="home-footer-hours">
        <h2 id="home-footer-hours">Giờ mở cửa</h2>
        <p className="home-footer-opening"><time dateTime="08:00">08:00</time> – <time dateTime="23:00">23:00</time></p>
        <p>Hẹn bạn một ly cà phê.</p>
      </section>
      <nav className="home-footer-explore" aria-labelledby="home-footer-explore">
        <h2 id="home-footer-explore">Khám phá</h2>
        <a href={links.menu ?? customerMenuHref}>Thực đơn</a>
        <SiteLink href={links.news}>Tin tức</SiteLink>
        <a href="#about">Về chúng tôi</a>
      </nav>
      <section className="home-footer-gallery" aria-labelledby="home-footer-gallery">
        <h2 id="home-footer-gallery">Chút cảm hứng</h2>
        <div className="home-footer-photos">
          <figure>
            <img src="/images/footer-latte.jpg" width="640" height="427" loading="lazy" alt="" />
            <figcaption>Khoảnh khắc cà phê</figcaption>
          </figure>
          <figure>
            <img src="/images/footer-cafe.jpg" width="640" height="960" loading="lazy" alt="" />
            <figcaption>Một góc thân quen</figcaption>
          </figure>
        </div>
      </section>
    </div>
    <div className="home-footer-bottom">
      <small>© 2026 LECKER Coffee Cafe</small>
      {onBackToTop
        ? <button type="button" className="home-footer-top" onClick={onBackToTop}>{topLabel}</button>
        : <a href="#top" className="home-footer-top">{topLabel}</a>}
    </div>
  </div>;
}

export default function SiteLayout({ children, account = null, notifications = null, links = {}, onNavigate,
  currentPage = "home", isAuthPage = false, notificationState = "ready", onRetryNotifications, onNotificationsOpen, onLogout, management = false, homepage = false }) {
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
  function selectAccountAction(action) {
    accountButton.current?.focus({ preventScroll: true });
    setPanel(null);
    action();
  }
  function handleHeaderKey(event) {
    if (event.key !== "Escape" || !panel) return;
    event.preventDefault();
    ({ menu: menuButton, account: accountButton, notifications: notificationButton })[panel].current?.focus();
    setPanel(null);
  }

  return <>
    <div id="top" />
    <a href="#main" className="skip-link" onClick={event => {
      event.preventDefault();
      document.getElementById("main")?.focus();
    }}>Bỏ qua điều hướng</a>
    <header className={`site-header${management ? " billing-header" : ""}${isAuthPage ? " site-header-auth" : ""}${homepage ? " site-header-home" : ""}`} onKeyDown={handleHeaderKey} onBlur={(event) => {
      if (!event.currentTarget.contains(event.relatedTarget)) setPanel(null);
    }}>
      <div className="header-inner">
        {management ? <strong className="billing-header-title">{account?.role === "ADMIN" ? "KHU VỰC QUẢN TRỊ" : "KHÔNG GIAN NHÂN VIÊN"}</strong> : <>
        <a className="brand" href="#top" aria-label="LECKER Coffee Cafe Trang chủ">
          <strong>LECKER</strong>{" "}<span>Coffee Cafe</span>
        </a>
        <button ref={menuButton} type="button" className="icon-button mobile-toggle" aria-label="Điều hướng"
          aria-expanded={panel === "menu"} aria-controls={`${regionId}-nav`} onClick={() => toggle("menu")}>
          <Icon name="menu" />
        </button>
        <nav id={`${regionId}-nav`} className={`header-nav ${panel === "menu" ? "is-open" : ""}`} aria-label="Điều hướng chính">
          {[
            ...navigation,
            ...(isStaff ? [{ label: "Quản lý hóa đơn", href: "#bills" }] : []),
          ].map(({ label, href, key }, index) => (
            <SiteLink key={label} href={links[key] ?? href} aria-current={(index === 0 && currentPage === "home") || key === currentPage ? "page" : undefined}
              onClick={() => { if (panel === "menu") menuButton.current?.focus(); setPanel(null); }}>{label}</SiteLink>
          ))}
        </nav>
        </>}
        <div className="header-actions">
          {!management && <>
          <a className="icon-button" href={links.menu ?? customerMenuHref} aria-label="Tìm món trong thực đơn"><Icon name="search" /></a>
          <a className="icon-button" href="#most-ordered" aria-label="Món được gọi nhiều"><Icon name="heart" /></a>
          </>}
          {isStaff && <div className="header-control notification-control">
            <button type="button" ref={notificationButton} className="icon-button"
              aria-label={unread ? `Thông báo, ${unread} chưa đọc` : "Thông báo"}
              aria-expanded={panel === "notifications"} aria-controls={`${regionId}-notifications`}
              onClick={() => { toggle("notifications"); onNotificationsOpen?.(); }}>
              <Icon name="bell" />{unread > 0 && <span className="notification-dot" />}
              {management && <span className="billing-header-action-label">Thông báo</span>}
            </button>
            <section className="header-panel notification-panel" id={`${regionId}-notifications`} hidden={panel !== "notifications"} aria-label="Thông báo phục vụ">
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
          {!account && localLink(links.login) ? (homepage
            ? <a href={links.login} className="icon-button home-login" aria-label="Đăng nhập"><Icon name="account" /></a>
            : <a href={links.login} className="header-login" aria-current={currentPage === "login" ? "page" : undefined}>Đăng nhập</a>
          ) : <div className="header-control">
            <button type="button" ref={accountButton} className="icon-button" aria-label={account ? `Tài khoản ${account.name}` : "Tài khoản"}
              aria-expanded={panel === "account"} aria-controls={`${regionId}-account`} onClick={() => toggle("account")}>
              <AccountAvatar name={account?.name} url={account?.avatarUrl} />
              {management && <span className="billing-header-action-label">Tài khoản</span>}
            </button>
            <section className="header-panel account-panel" id={`${regionId}-account`} hidden={panel !== "account"} aria-label="Thông tin tài khoản">
              <div className="account-panel-identity">
                <AccountAvatar name={account?.name} url={account?.avatarUrl} />
                <div><h2>{account?.name || "Tài khoản"}</h2>
                  {account?.role && <p>{{ ADMIN: "Quản trị viên", STAFF: "Nhân viên", CUSTOMER: "Khách hàng" }[account.role]}</p>}</div>
              </div>
              {account && onNavigate && <button type="button" className="account-panel-action" onClick={() => selectAccountAction(() => onNavigate("#account"))}>
                <Icon name="account" /> <span>Thông tin tài khoản</span>
              </button>}
              {isStaff && onNavigate && <button type="button" className="account-panel-action" onClick={() => selectAccountAction(() => onNavigate("#sale"))}>
                <Icon name="sales" /> <span>Bán hàng theo bàn</span>
              </button>}
              {!account && <p className="account-panel-empty">Bạn chưa đăng nhập.</p>}
              {account && onLogout && <div className="account-panel-footer"><button type="button" className="account-panel-action" onClick={() => selectAccountAction(onLogout)}>
                <Icon name="logout" /> <span>Đăng xuất</span>
              </button></div>}
            </section>
          </div>}
        </div>
      </div>
    </header>
    <main id="main" className={homepage ? "site-main-home" : undefined} tabIndex={-1}>{children}</main>
    {!management && <footer className={`site-footer${isAuthPage ? " site-footer-auth" : ""}${homepage ? " site-footer-home" : ""}`}>
      {homepage ? <HomeFooter links={links} onBackToTop={currentPage === "news" ? () => {
        document.getElementById("main")?.focus({ preventScroll: true });
        window.scrollTo(0, 0);
      } : undefined} /> : <div className="footer-inner">
        <a className="brand footer-brand" href="#top" aria-label="LECKER Coffee Cafe Về đầu trang">
          <strong>LECKER</strong>{" "}<span>COFFEE CAFE</span>
        </a>
        <nav aria-label="Liên kết cuối trang">
          {footer.map(({ label, key }) => <SiteLink key={key} href={links[key]}>{label}</SiteLink>)}
        </nav>
        <small>© 2026 LECKER Coffee Cafe</small>
      </div>}
    </footer>}
  </>;
}
