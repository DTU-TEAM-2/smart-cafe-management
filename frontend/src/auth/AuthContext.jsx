import { createContext, useContext, useEffect, useState } from "react";
import { getCurrentUserApi, loginApi, logoutApi } from "./authApi.js";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => {
    try {
      return sessionStorage.getItem("smart_cafe_token") || "";
    } catch {
      return "";
    }
  });

  const [user, setUser] = useState(() => {
    try {
      const stored = sessionStorage.getItem("smart_cafe_user");
      return stored ? JSON.parse(stored) : null;
    } catch {
      return null;
    }
  });

  const [isLoading, setIsLoading] = useState(false);
  const [otpChallenge, setOtpChallenge] = useState(null);
  const [recoveryToken, setRecoveryToken] = useState("");

  useEffect(() => {
    if (!token) {
      sessionStorage.removeItem("smart_cafe_token");
      sessionStorage.removeItem("smart_cafe_user");
      setUser(null);
      window.dispatchEvent(new CustomEvent("smart-cafe-auth-change"));
      return;
    }

    sessionStorage.setItem("smart_cafe_token", token);
    window.dispatchEvent(new CustomEvent("smart-cafe-auth-change"));

    let ignore = false;
    async function fetchUser() {
      try {
        const currentUser = await getCurrentUserApi(token);
        if (!ignore) {
          setUser(currentUser);
          sessionStorage.setItem("smart_cafe_user", JSON.stringify(currentUser));
        }
      } catch {
        if (!ignore) {
          setToken("");
          setUser(null);
          sessionStorage.removeItem("smart_cafe_token");
          sessionStorage.removeItem("smart_cafe_user");
        }
      }
    }

    if (!user) {
      fetchUser();
    }

    return () => {
      ignore = true;
    };
  }, [token]);

  async function login(username, password) {
    setIsLoading(true);
    try {
      const res = await loginApi(username, password);
      const tokenValue = res.accessToken || res.token;
      setToken(tokenValue);
      setUser(res.user);
      sessionStorage.setItem("smart_cafe_token", tokenValue);
      sessionStorage.setItem("smart_cafe_user", JSON.stringify(res.user));
      window.dispatchEvent(new CustomEvent("smart-cafe-auth-change"));
      return res.user;
    } finally {
      setIsLoading(false);
    }
  }

  async function logout() {
    try {
      if (token) {
        await logoutApi(token);
      }
    } finally {
      setToken("");
      setUser(null);
      sessionStorage.removeItem("smart_cafe_token");
      sessionStorage.removeItem("smart_cafe_user");
      window.dispatchEvent(new CustomEvent("smart-cafe-auth-change"));
    }
  }

  function beginOtp(challenge) {
    setOtpChallenge({
      ...challenge,
      expiresAt: Date.now() + (challenge.expiresInSeconds || 600) * 1000,
      resendAt: Date.now() + (challenge.retryAfterSeconds || 60) * 1000,
    });
  }

  function completeOtp(grantToken) {
    if (grantToken) {
      setRecoveryToken(grantToken);
    }
    setOtpChallenge(null);
  }

  function clearOtp() {
    setOtpChallenge(null);
    setRecoveryToken("");
  }

  return (
    <AuthContext.Provider
      value={{
        token,
        user,
        isLoading,
        otpChallenge,
        recoveryToken,
        login,
        logout,
        beginOtp,
        completeOtp,
        clearOtp,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
