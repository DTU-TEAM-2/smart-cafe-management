import { StrictMode, useSyncExternalStore } from "react";
import { createRoot } from "react-dom/client";
import HomeShell from "./layout/HomeShell.jsx";
import "./fonts.css";
import "./styles.css";

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
  return <HomeShell authorization={token ? `Bearer ${token}` : ""} />;
}

createRoot(document.getElementById("root")).render(<StrictMode><App /></StrictMode>);
