export const moneyFormatter = new Intl.NumberFormat("vi-VN", {
  style: "currency",
  currency: "VND",
  maximumFractionDigits: 0,
});

export function vnd(amount) {
  if (amount == null || amount === "") return "0 ₫";
  try {
    const numeric = typeof amount === "number" ? BigInt(Math.round(amount)) : BigInt(amount);
    return moneyFormatter.format(numeric);
  } catch {
    const num = Number(amount);
    return Number.isFinite(num) ? moneyFormatter.format(num) : `${amount} ₫`;
  }
}

const dtf = new Intl.DateTimeFormat("vi-VN", {
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  timeZone: "Asia/Ho_Chi_Minh",
});

export function formatDateTime(isoString) {
  if (!isoString) return "—";
  try {
    const d = new Date(isoString);
    return Number.isFinite(d.getTime()) ? dtf.format(d) : String(isoString);
  } catch {
    return String(isoString);
  }
}

export function fetchBills({ code, from, to, status, page = 0, size = 10, sort = "createdAt,desc", ...options }) {
  const params = new URLSearchParams({ page: String(page), size: String(size), sort });
  if (code?.trim()) params.set("code", code.trim());
  if (from) params.set("from", from);
  if (to) params.set("to", to);
  if (status && status !== "ALL") params.set("status", status);
  return readBilling(`/api/v1/bills?${params}`, "danh sách hóa đơn", options);
}

export function fetchBillDetail(id, options) {
  return readBilling(`/api/v1/bills/${encodeURIComponent(id)}`, "chi tiết hóa đơn", options);
}

export function fetchRevenueStatistics({ period, from, to, ...options }) {
  const params = new URLSearchParams();
  if (from) params.set("from", from);
  if (to) params.set("to", to);
  if (period) params.set("period", period);
  return readBilling(`/api/v1/statistics/revenue?${params}`, "số liệu thống kê", options);
}

async function readBilling(path, resource, { authorization, credentials = "same-origin", signal }) {
  const timeout = AbortSignal.timeout(12000);
  const requestSignal = signal ? AbortSignal.any([signal, timeout]) : timeout;
  let response;
  try {
    response = await fetch(path, {
      headers: authorization ? { Authorization: authorization } : {},
      signal: requestSignal,
      cache: "no-store",
      credentials,
    });
  } catch (error) {
    if (signal?.aborted) throw error;
    throw new Error("Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại.");
  }

  if (response.status === 401 || response.status === 403) throw new Error("UNAUTHORIZED");
  if (response.status === 404) throw new Error("NOT_FOUND");
  if (response.status === 503) throw new Error("Dữ liệu tạm thời không khả dụng (HTTP 503).");
  if (!response.ok) throw new Error(`Lỗi tải ${resource} (HTTP ${response.status})`);
  return response.json();
}
