const messages = {
  401: "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
  403: "Bạn không có quyền xem phản hồi.",
  404: "Không tìm thấy phản hồi.",
  400: "Ngày hoặc tham số tìm kiếm không hợp lệ.",
};

export function imageUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === "https:" && !url.username && !url.password ? url.href : null;
  } catch { return null; }
}

export async function readFeedback(path, token, signal) {
  try {
    const response = await fetch(`/api/v1/admin/feedbacks${path}`, {
      headers: { Accept: "application/json", Authorization: `Bearer ${token}` },
      cache: "no-store",
      signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]),
    });
    if (!response.ok) throw Object.assign(new Error(messages[response.status] || "Dịch vụ phản hồi tạm thời không khả dụng. Vui lòng thử lại."), { status: response.status });
    return await response.json();
  } catch (error) {
    if (signal.aborted || error.status) throw error;
    throw new Error("Không thể tải phản hồi. Kiểm tra kết nối và thử lại.");
  }
}
