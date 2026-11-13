/**
 * Client API for authentication and password recovery
 */

async function handleResponse(res) {
  const isJson = res.headers.get("content-type")?.includes("application");
  const data = isJson ? await res.json() : null;

  if (!res.ok) {
    let message = data?.detail || data?.message;
    if (!message && data?.fieldErrors) {
      message = Object.values(data.fieldErrors).join(", ");
    }
    const error = new Error(message || `Yêu cầu thất bại với mã lỗi HTTP ${res.status}`);
    error.status = res.status;
    error.code = data?.code;
    error.retryAfterSeconds = data?.retryAfterSeconds;
    throw error;
  }

  return data;
}

export async function loginApi(username, password, signal) {
  const res = await fetch("/api/v1/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
    signal,
  });
  return handleResponse(res);
}

export async function getCurrentUserApi(token, signal) {
  const res = await fetch("/api/v1/auth/me", {
    headers: {
      Authorization: `Bearer ${token}`,
    },
    signal,
    cache: "no-store",
  });
  return handleResponse(res);
}

export async function logoutApi(token) {
  try {
    const res = await fetch("/api/v1/auth/logout", {
      method: "POST",
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    return handleResponse(res);
  } catch {
    return { message: "Logged out" };
  }
}

export async function forgotPasswordApi(email) {
  const res = await fetch("/api/v1/auth/forgot-password", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email }),
  });
  return handleResponse(res);
}

export async function verifyOtpApi(challengeId, otp) {
  const res = await fetch("/api/v1/auth/verify-otp", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ challengeId, otp }),
  });
  return handleResponse(res);
}

export async function resendOtpApi(challengeId) {
  const res = await fetch("/api/v1/auth/resend-otp", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ challengeId }),
  });
  return handleResponse(res);
}

export async function verifyResetTokenApi(token) {
  const res = await fetch("/api/v1/auth/verify-reset-token", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ token }),
  });
  const data = await handleResponse(res);
  return data?.valid === true;
}

export async function resetPasswordApi(token, newPassword, confirmPassword) {
  const res = await fetch("/api/v1/auth/reset-password", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ token, newPassword, confirmPassword }),
  });
  return handleResponse(res);
}
