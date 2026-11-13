export async function loginWithExistingModule(username, password, signal) {
  const response = await fetch("/api/v1/auth/login", {
    method: "POST", credentials: "same-origin", cache: "no-store",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: username.trim(), password }),
    signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]),
  });
  if (response.status === 401) throw new Error("Tên đăng nhập/email hoặc mật khẩu không đúng.");
  if (response.status === 429) throw new Error("Bạn đã thử đăng nhập quá nhiều lần. Vui lòng đợi rồi thử lại.");
  if (!response.ok) throw new Error("Đăng nhập chưa sẵn sàng. Vui lòng thử lại sau.");
  const data = await response.json();
  if (typeof data.token !== "string" || !data.token || data.token.length > 2048) throw new Error("Không xác minh được phiên đăng nhập.");
  // Validate through the consumer's current database authority before retaining the session.
  const accountResponse = await fetch("/api/v1/home/account", {
    headers: { Authorization: `Bearer ${data.token}` }, credentials: "same-origin", cache: "no-store",
    signal: AbortSignal.any([signal, AbortSignal.timeout(10000)]),
  });
  if (!accountResponse.ok) throw new Error("Không xác minh được phiên đăng nhập. Vui lòng thử lại.");
  const account = await accountResponse.json();
  if (typeof account.id !== "string" || !["ADMIN", "STAFF", "CUSTOMER"].includes(account.role)) {
    throw new Error("Không xác minh được phiên đăng nhập.");
  }
  if (signal.aborted) throw new DOMException("Aborted", "AbortError");
  return data.token;
}

export function clearLocalSession() {
  for (const storageName of ["sessionStorage", "localStorage"]) {
    try {
      window[storageName].removeItem("smart_cafe_token");
      window[storageName].removeItem("smart_cafe_user");
    } catch { /* Continue clearing the other storage even if this one is unavailable. */ }
  }
  window.dispatchEvent(new Event("smart-cafe-auth-change"));
}
