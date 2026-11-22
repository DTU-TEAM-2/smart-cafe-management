import { lazy, StrictMode, Suspense } from "react";
import { createRoot } from "react-dom/client";
import HomeShell from "./layout/HomeShell.jsx";
import { AuthProvider, useAuth } from "./auth/AuthContext.jsx";
import "./fonts.css";
import "./styles.css";

const MenuPreview = import.meta.env.DEV ? lazy(() => import("./menu/MenuManagement.jsx")) : null;

function AuthenticatedHome() {
  const { token } = useAuth();
  return <HomeShell authorization={token ? `Bearer ${token}` : ""} />;
}

function App() {
  if (import.meta.env.DEV && window.location.pathname === "/preview/menu") {
    return <Suspense fallback={<main aria-busy="true"><h1>Xem thử thực đơn</h1><p role="status">Đang tải giao diện thực đơn…</p></main>}><MenuPreview /></Suspense>;
  }
  return (
    <AuthProvider>
      <AuthenticatedHome />
    </AuthProvider>
  );
}

createRoot(document.getElementById("root")).render(
  <StrictMode>
    <App />
  </StrictMode>
);
