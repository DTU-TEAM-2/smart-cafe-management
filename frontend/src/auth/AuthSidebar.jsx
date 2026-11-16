export default function AuthSidebar({
  eyebrow = "LECKER COFFEE CAFE",
  title = "Một tách cà phê.\nMột ngày thật đẹp.",
  description = "Chào mừng bạn trở lại với LECKER.",
  imageSrc = "/images/cafe-interior.jpg",
}) {
  const lines = title.split("\n");

  return (
    <aside className="auth-sidebar login-story-side" aria-label="Thương hiệu LECKER Coffee Cafe">
      {/* 1. Desktop Story Side (Image 2) */}
      <div className="auth-sidebar-desktop-story login-story-bg" style={{ backgroundImage: `url('${imageSrc}')` }}>
        <div className="login-story-overlay" />
        <div className="login-story-content">
          <p className="login-story-eyebrow">— {eyebrow}</p>
          <h2 className="login-story-title">
            {lines.map((line, index) => (
              <span key={index} style={{ display: "block" }}>
                {line}
              </span>
            ))}
          </h2>
          <p className="login-story-desc">{description}</p>
        </div>
      </div>

      {/* 2. Mobile Brand Column (Image 5 / Figma) */}
      <div className="auth-sidebar-mobile-brand" aria-hidden="true">
        <div className="mobile-brand-top">
          <span className="mobile-brand-title">LECKER</span>
          <span className="mobile-brand-subtitle">COFFEE CAFE</span>
        </div>

        <div className="mobile-brand-crest">
          <svg
            className="mobile-crest-svg"
            viewBox="0 0 60 60"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
          >
            <circle cx="30" cy="30" r="27" stroke="#D5A953" strokeWidth="1.5" strokeOpacity="0.85" />
            <circle cx="30" cy="30" r="23" stroke="#604938" strokeWidth="0.8" strokeDasharray="2 2" />
            <circle cx="30" cy="30" r="19" fill="#E6D2B5" stroke="#C59A45" strokeWidth="1" />
            <path
              d="M30 19C26 22 24 26 25 30C26 34 30 36 30 36C30 36 34 34 35 30C36 26 34 22 30 19Z"
              fill="#3D281D"
              fillOpacity="0.35"
            />
            <path d="M30 20C29 24 31 28 30 34" stroke="#3D281D" strokeWidth="1" strokeLinecap="round" />
          </svg>
        </div>

        <div className="mobile-brand-bottom">
          <span className="mobile-tagline-smart">SMART</span>
          <span className="mobile-tagline-cafe">CAFE</span>
        </div>
      </div>
    </aside>
  );
}
