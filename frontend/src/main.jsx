import { lazy, StrictMode, Suspense, useSyncExternalStore } from "react";
import { createRoot } from "react-dom/client";
import HomeShell from "./layout/HomeShell.jsx";
import "./fonts.css";
import "./styles.css";

const MenuPreview = lazy(() => import("./menu/MenuManagement.jsx"));

function readToken() {
  try { return sessionStorage.getItem("smart_cafe_token") || ""; }
  catch { return ""; }
}

function subscribeAuth(callback) {
  const events = ["storage", "smart-cafe-auth-change", "focus"];
  events.forEach(event => window.addEventListener(event, callback));
  return () => events.forEach(event => window.removeEventListener(event, callback));
}

function App() {
  const token = useSyncExternalStore(subscribeAuth, readToken);
  if (window.location.pathname === "/preview/menu") {
    return <Suspense fallback={<main aria-busy="true"><h1>Xem thử thực đơn</h1><p role="status">Đang tải giao diện thực đơn…</p></main>}><MenuPreview /></Suspense>;
  }
  return <HomeShell authorization={token ? `Bearer ${token}` : ""} />;
}

createRoot(document.getElementById("root")).render(<StrictMode><App /></StrictMode>);
