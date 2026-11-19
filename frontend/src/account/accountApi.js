import { passwordStatusFrom } from "../auth/authApi.js";

const messages = {
  UNAUTHORIZED: "Phiên đăng nhập đã hết hiệu lực. Vui lòng đăng nhập lại.",
  FORBIDDEN: "Bạn không có quyền truy cập hồ sơ nhân sự.",
  PROFILE_CONFLICT: "Hồ sơ đã được thay đổi. Dữ liệu mới được tải lại; hãy kiểm tra trước khi chỉnh sửa tiếp.",
  PROFILE_INCOMPLETE: "Hồ sơ nhân sự chưa đầy đủ. Vui lòng liên hệ quản trị nhân sự.",
  INVALID_PROFILE_REQUEST: "Thông tin cập nhật không hợp lệ. Vui lòng kiểm tra các trường được sửa.",
  CURRENT_PASSWORD_INCORRECT: "Mật khẩu hiện tại không đúng.",
  PASSWORD_MISMATCH: "Xác nhận mật khẩu không khớp.",
  PASSWORD_REUSED: "Mật khẩu mới phải khác mật khẩu hiện tại.",
  INVALID_PASSWORD: "Mật khẩu mới không đáp ứng chính sách mật khẩu.",
  PASSWORD_CONFLICT: "Mật khẩu đã thay đổi ở phiên khác. Vui lòng xác thực lại.",
  PROFILE_UNAVAILABLE: "Dịch vụ hồ sơ tạm thời chưa khả dụng. Vui lòng thử lại sau.",
};
const fields = { CURRENT_PASSWORD_INCORRECT: "currentPassword", PASSWORD_MISMATCH: "confirmNewPassword",
  PASSWORD_REUSED: "newPassword", INVALID_PASSWORD: "newPassword" };

export function accountError(code, status = 0) {
  return Object.assign(new Error(messages[code] || "Không thể hoàn tất yêu cầu. Vui lòng thử lại sau."),
    { code, status, field: fields[code] });
}

async function request(token, method, path, body, signal) {
  try {
    const response = await fetch(`/api/v1/profiles/me${path}`, {
      method, headers: { Accept: "application/json", Authorization: `Bearer ${token}`,
        ...(body ? { "Content-Type": "application/json" } : {}) },
      ...(body ? { body: JSON.stringify(body) } : {}), cache: "no-store",
      signal: AbortSignal.any([...(signal ? [signal] : []), AbortSignal.timeout(15000)]),
    });
    let data;
    try { data = await response.json(); } catch { /* Never show raw responses or sensitive server details. */ }
    if (!response.ok) throw accountError(response.status === 401 ? "UNAUTHORIZED"
      : response.status === 403 ? "FORBIDDEN" : response.status === 503 ? "PROFILE_UNAVAILABLE" : data?.code, response.status);
    if (!data || (path === "/password" ? typeof data.token !== "string" || !data.token || !passwordStatusFrom(data)
      : typeof data.id !== "string" || !/^[1-9]\d*$/.test(data.id)
        || typeof data.version !== "string" || !/^(0|[1-9]\d{0,18})$/.test(data.version)
        || BigInt(data.version) > 9223372036854775807n || typeof data.name !== "string"
        || typeof data.username !== "string" || ["phone", "address", "birthday", "gender", "salary", "avatarUrl"]
          .some(field => data[field] !== null && typeof data[field] !== "string")
        || !["ADMIN", "STAFF"].includes(data.role) || typeof data.canUpdateProfile !== "boolean"
        || !passwordStatusFrom(data))) throw accountError("INVALID_RESPONSE");
    return data;
  } catch (error) {
    if (signal?.aborted) throw error;
    if (error.status !== undefined && error.code !== "INVALID_RESPONSE") throw error;
    if (path === "/password") {
      throw Object.assign(accountError("PASSWORD_OUTCOME_UNKNOWN"), { uncertain: true,
        message: "Chưa xác nhận được kết quả đổi mật khẩu. Hãy đăng nhập lại bằng mật khẩu mới; nếu chưa đổi, dùng mật khẩu cũ hoặc khôi phục tài khoản." });
    }
    throw accountError("NETWORK_ERROR");
  }
}

export const getProfile = (token, signal) => request(token, "GET", "", undefined, signal);
export const patchProfile = (token, patch, signal) => request(token, "PATCH", "", patch, signal);
export const changePassword = (token, passwords, signal) => request(token, "POST", "/password", {
  currentPassword: passwords.currentPassword, newPassword: passwords.newPassword,
  confirmNewPassword: passwords.confirmNewPassword,
}, signal);
