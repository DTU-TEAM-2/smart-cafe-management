import { useState } from "react";
import { useAuth } from "../auth/useAuth.js";

export default function SiteLayout({ children, currentRoute, onNavigate }) {
  const { user, logout } = useAuth();
  const [isMenuOpen, setIsMenuOpen] = useState(false);

  return (
    <div style={{ display: "flex", flexDirection: "column", minHeight: "100vh" }}>
      <header className="site-header">
        <div className="header-inner">
          <a
            href="#top"
            className="brand"
            onClick={(e) => {
              e.preventDefault();
              onNavigate("#top");
            }}
          >
            <strong>LECKER</strong>
            <span>Coffee Cafe</span>
          </a>

          <nav className="header-nav" aria-label="Điều hướng chính">
            <a
              href="#top"
              aria-current={currentRoute === "#top" ? "page" : undefined}
              onClick={(e) => {
                e.preventDefault();
                onNavigate("#top");
              }}
            >
              Trang chủ
            </a>
            {user?.role === "ADMIN" && (
              <a
                href="#admin"
                aria-current={currentRoute === "#admin" ? "page" : undefined}
                onClick={(e) => {
                  e.preventDefault();
                  onNavigate("#admin");
                }}
              >
                Quản trị
              </a>
            )}
            {(user?.role === "ADMIN" || user?.role === "STAFF") && (
              <a
                href="#staff"
                aria-current={currentRoute === "#staff" ? "page" : undefined}
                onClick={(e) => {
                  e.preventDefault();
                  onNavigate("#staff");
                }}
              >
                Vận hành bàn
              </a>
            )}
          </nav>

          <div className="header-actions">
            {user ? (
              <div style={{ position: "relative" }}>
                <button
                  type="button"
                  className="icon-button"
                  onClick={() => setIsMenuOpen((prev) => !prev)}
                  aria-expanded={isMenuOpen}
                  aria-label="Tài khoản cá nhân"
                >
                  <span className="avatar-badge">
                    {user.fullName ? user.fullName.trim()[0].toUpperCase() : "U"}
                  </span>
                </button>

                {isMenuOpen && (
                  <div className="user-menu-dropdown">
                    <p>
                      Đã đăng nhập:
                      <strong>{user.fullName}</strong>
                      <span style={{ fontSize: "11px", color: "var(--gold)" }}>[{user.role}]</span>
                    </p>
                    <hr />
                    {user.role === "ADMIN" && (
                      <button
                        type="button"
                        onClick={() => {
                          setIsMenuOpen(false);
                          onNavigate("#admin");
                        }}
                      >
                        Bảng quản trị
                      </button>
                    )}
                    {(user.role === "ADMIN" || user.role === "STAFF") && (
                      <button
                        type="button"
                        onClick={() => {
                          setIsMenuOpen(false);
                          onNavigate("#staff");
                        }}
                      >
                        Bảng nhân viên
                      </button>
                    )}
                    <button
                      type="button"
                      style={{ color: "var(--danger)" }}
                      onClick={async () => {
                        setIsMenuOpen(false);
                        await logout();
                        onNavigate("#login");
                      }}
                    >
                      Đăng xuất
                    </button>
                  </div>
                )}
              </div>
            ) : (
              <a
                href="#login"
                className="header-login-btn"
                onClick={(e) => {
                  e.preventDefault();
                  onNavigate("#login");
                }}
              >
                Đăng nhập
              </a>
            )}
          </div>
        </div>
      </header>

      <main id="main" style={{ flex: 1 }}>
        {children}
      </main>

      <footer className="site-footer">
        <div className="footer-inner">
          <div className="brand footer-brand">
            <strong>LECKER</strong>
            <span>COFFEE CAFE</span>
          </div>
          <div className="footer-nav">
            <span>Liên hệ: 0901 234 567</span>
            <span>•</span>
            <span>Địa chỉ: 123 Đường Cà Phê, TP. Đà Nẵng</span>
          </div>
          <small>© 2026 LECKER Coffee Cafe — Hệ thống Quản lý Quán Cà phê Thông minh</small>
        </div>
      </footer>
    </div>
  );
}
