import { createContext, useContext, useEffect, useState } from "react";
import { getCurrentUserApi, loginApi, logoutApi } from "./authApi.js";

const AuthContext = createContext(null);

function clearAuthStorage() {
  try {
    sessionStorage.removeItem("smart_cafe_token");
    sessionStorage.removeItem("smart_cafe_user");
  } catch {
    // ignore
  }
  try {
    localStorage.removeItem("smart_cafe_token");
    localStorage.removeItem("smart_cafe_user");
  } catch {
    // ignore
  }
}

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
      clearAuthStorage();
      setUser(null);
      window.dispatchEvent(new CustomEvent("smart-cafe-auth-change"));
      return;
    }

    try {
      sessionStorage.setItem("smart_cafe_token", token);
    } catch {
      // ignore
    }
    window.dispatchEvent(new CustomEvent("smart-cafe-auth-change"));

    let ignore = false;
    async function verifyAndFetchUser() {
      try {
        const currentUser = await getCurrentUserApi(token);
        if (!ignore) {
          setUser(currentUser);
          try {
            sessionStorage.setItem("smart_cafe_user", JSON.stringify(currentUser));
          } catch {
            // ignore
          }
        }
      } catch {
        if (!ignore) {
          setToken("");
          setUser(null);
          clearAuthStorage();
          window.dispatchEvent(new CustomEvent("smart-cafe-auth-change"));
        }
      }
    }

    verifyAndFetchUser();

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
    const previousToken = token;
    setToken("");
    setUser(null);
    setOtpChallenge(null);
    setRecoveryToken("");
    clearAuthStorage();
    window.dispatchEvent(new CustomEvent("smart-cafe-auth-change"));
    // Stateless logout must finish locally even if its acknowledgement never arrives.
    if (previousToken) void logoutApi(previousToken).catch(() => {});
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
