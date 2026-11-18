import { useEffect, useState } from "react";
import { AuthProvider } from "./auth/AuthContext.jsx";
import SiteLayout from "./layout/SiteLayout.jsx";
import HomePage from "./home/HomePage.jsx";
import LoginPage from "./auth/LoginPage.jsx";
import ForgotPasswordPage from "./auth/ForgotPasswordPage.jsx";
import OtpPage from "./auth/OtpPage.jsx";
import ResetPasswordPage from "./auth/ResetPasswordPage.jsx";
import AdminDashboard from "./dashboard/AdminDashboard.jsx";
import StaffDashboard from "./dashboard/StaffDashboard.jsx";
import "./styles.css";

export default function App() {
  const [route, setRoute] = useState(() => {
    return window.location.hash || (window.location.pathname === "/login" ? "#login" : "#top");
  });

  useEffect(() => {
    function handleHashChange() {
      setRoute(window.location.hash || "#top");
    }
    window.addEventListener("hashchange", handleHashChange);
    return () => window.removeEventListener("hashchange", handleHashChange);
  }, []);

  function navigate(targetHash) {
    if (!targetHash.startsWith("#")) {
      targetHash = "#" + targetHash.replace(/^\//, "");
    }
    window.location.hash = targetHash;
    setRoute(targetHash);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  const baseRoute = route.split("?")[0];

  let content;
  switch (baseRoute) {
    case "#login":
      content = <LoginPage onNavigate={navigate} />;
      break;
    case "#forgot-password":
      content = <ForgotPasswordPage onNavigate={navigate} />;
      break;
    case "#otp":
    case "#verify-otp":
      content = <OtpPage onNavigate={navigate} />;
      break;
    case "#reset-password":
      content = <ResetPasswordPage onNavigate={navigate} />;
      break;
    case "#admin":
      content = <AdminDashboard onNavigate={navigate} />;
      break;
    case "#staff":
      content = <StaffDashboard onNavigate={navigate} />;
      break;
    case "#top":
    default:
      content = <HomePage onNavigate={navigate} />;
      break;
  }

  return (
    <AuthProvider>
      <SiteLayout currentRoute={route} onNavigate={navigate}>
        {content}
      </SiteLayout>
    </AuthProvider>
  );
}
