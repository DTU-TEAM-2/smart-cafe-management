import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import { getCurrentUserApi, loginApi, logoutApi, passwordStatusFrom } from "./authApi.js";

const AuthContext = createContext(null);
const emptySession = { token: "", user: null, passwordStatus: null, generation: 0 };

function readSession() {
  let token;
  try { token = sessionStorage.getItem("smart_cafe_token") || ""; } catch { return null; }
  if (!token) return emptySession;
  let stored;
  try {
    stored = JSON.parse(sessionStorage.getItem("smart_cafe_user") || "null");
  } catch { /* A damaged user cache does not invalidate a signed bearer. */ }
  if (!stored || typeof stored !== "object" || Array.isArray(stored)) stored = null;
  const { _passwordStatus, ...user } = stored || {};
  return { token, user: stored ? user : null, passwordStatus: passwordStatusFrom(_passwordStatus), generation: 0 };
}

function persistSession(session) {
  let synchronized = true;
  try {
    if (session.token) {
      sessionStorage.setItem("smart_cafe_token", session.token);
      sessionStorage.setItem("smart_cafe_user", JSON.stringify({ ...session.user, _passwordStatus: session.passwordStatus }));
    } else {
      sessionStorage.removeItem("smart_cafe_token");
      sessionStorage.removeItem("smart_cafe_user");
    }
  } catch { synchronized = false; }
  try {
    localStorage.removeItem("smart_cafe_token");
    localStorage.removeItem("smart_cafe_user");
  } catch { /* Legacy auth data must never be used as a fallback. */ }
  return synchronized;
}

export function AuthProvider({ children }) {
  const [session, setSession] = useState(() => readSession() || emptySession);
  const current = useRef(session);
  const [isLoading, setIsLoading] = useState(false);
  const [otpChallenge, setOtpChallenge] = useState(null);
  const [recoveryToken, setRecoveryToken] = useState("");
  const loginAttempt = useRef(0);
  const profileRevision = useRef(0);
  const publishing = useRef(false);
  const storageSynchronized = useRef(true);

  const captureSession = useCallback(() => ({ token: current.current.token, generation: current.current.generation }), []);
  const isCurrentSession = useCallback(snapshot => Boolean(snapshot) && current.current.token === snapshot.token
    && current.current.generation === snapshot.generation, []);
  const commitSession = useCallback(next => {
    // Publish storage and the guard synchronously, before subscribers can start requests.
    current.current = next;
    storageSynchronized.current = persistSession(next);
    setSession(next);
    publishing.current = true;
    try { window.dispatchEvent(new CustomEvent("smart-cafe-auth-change")); } finally { publishing.current = false; }
    return { token: next.token, generation: next.generation };
  }, []);

  const expireSession = useCallback(snapshot => {
    if (!isCurrentSession(snapshot)) return;
    loginAttempt.current += 1;
    setIsLoading(false);
    setOtpChallenge(null);
    setRecoveryToken("");
    commitSession({ ...emptySession, generation: current.current.generation + 1 });
  }, [commitSession, isCurrentSession]);

  useEffect(() => {
    function syncExternalSession(event) {
      if (publishing.current || (event.type === "focus" && !storageSynchronized.current)) return;
      const stored = readSession();
      if (!stored) return;
      if (stored.token === current.current.token) return;
      if (!stored.token) expireSession(captureSession());
      else commitSession({ ...stored, generation: current.current.generation + 1 });
    }
    const events = ["smart-cafe-auth-change", "storage", "focus"];
    events.forEach(event => window.addEventListener(event, syncExternalSession));
    return () => events.forEach(event => window.removeEventListener(event, syncExternalSession));
  }, [captureSession, commitSession, expireSession]);

  useEffect(() => {
    const snapshot = { token: session.token, generation: session.generation };
    if (!snapshot.token) { persistSession(emptySession); return; }
    const controller = new AbortController();
    const expectedProfileRevision = profileRevision.current;
    async function verify() {
      try {
        const user = await getCurrentUserApi(snapshot.token,
          AbortSignal.any([controller.signal, AbortSignal.timeout(15000)]));
        if (!controller.signal.aborted && isCurrentSession(snapshot) && profileRevision.current === expectedProfileRevision) {
          commitSession({ ...current.current, user });
        }
      } catch (error) {
        if (!controller.signal.aborted && isCurrentSession(snapshot) && [401, 403].includes(error.status)) expireSession(snapshot);
        // A network/503 failure is not proof that a valid session was revoked.
      }
    }
    void verify();
    return () => controller.abort();
  }, [session.token, session.generation, commitSession, expireSession, isCurrentSession]);

  async function login(username, password) {
    const attempt = ++loginAttempt.current;
    const snapshot = captureSession();
    setIsLoading(true);
    try {
      const response = await loginApi(username, password);
      if (attempt !== loginAttempt.current || !isCurrentSession(snapshot)) throw new Error("Yêu cầu đăng nhập đã được hủy.");
      const token = response.accessToken || response.token;
      if (!token || !response.user) throw new Error("Chưa xác nhận được phiên đăng nhập.");
      commitSession({ token, user: response.user, passwordStatus: passwordStatusFrom(response),
        generation: current.current.generation + 1 });
      setOtpChallenge(null);
      setRecoveryToken("");
      return response.user;
    } finally {
      if (attempt === loginAttempt.current) setIsLoading(false);
    }
  }

  const replaceToken = useCallback((response, snapshot) => {
    if (!isCurrentSession(snapshot)) return null;
    const passwordStatus = passwordStatusFrom(response);
    if (typeof response.token !== "string" || !response.token || !passwordStatus) throw new Error("Invalid replacement session");
    return commitSession({ ...current.current, token: response.token, passwordStatus, accountNotice: "Đổi mật khẩu thành công. Phiên đăng nhập của bạn đã được cập nhật.",
      generation: current.current.generation + 1 });
  }, [commitSession, isCurrentSession]);

  const clearAccountNotice = useCallback(() => commitSession({ ...current.current, accountNotice: "" }), [commitSession]);

  const updateProfile = useCallback((profile, snapshot) => {
    if (!isCurrentSession(snapshot)) return;
    profileRevision.current += 1;
    const previous = current.current;
    commitSession({ ...previous, user: { ...previous.user, id: profile.id, username: profile.username, fullName: profile.name,
      email: profile.email, phone: profile.phone, role: profile.role },
      passwordStatus: passwordStatusFrom(profile) || previous.passwordStatus });
  }, [commitSession, isCurrentSession]);

  async function logout() {
    const snapshot = captureSession();
    expireSession(snapshot);
    // Finish locally even when the old acknowledgement is delayed or fails.
    if (snapshot.token) void logoutApi(snapshot.token).catch(() => {});
  }

  function beginOtp(challenge) {
    setOtpChallenge({ ...challenge, expiresAt: Date.now() + (challenge.expiresInSeconds || 600) * 1000,
      resendAt: Date.now() + (challenge.retryAfterSeconds || 60) * 1000 });
  }
  function completeOtp(grantToken) {
    if (grantToken) setRecoveryToken(grantToken);
    setOtpChallenge(null);
  }
  function clearOtp() { setOtpChallenge(null); setRecoveryToken(""); }

  return <AuthContext.Provider value={{ token: session.token, user: session.user, passwordStatus: session.passwordStatus,
    sessionGeneration: session.generation, isLoading, otpChallenge, recoveryToken, login, logout, beginOtp, completeOtp, clearOtp,
    captureSession, isCurrentSession, expireSession, replaceToken, updateProfile,
    accountNotice: session.accountNotice, clearAccountNotice }}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used within an AuthProvider");
  return context;
}
