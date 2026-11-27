export const moneyFormatter = new Intl.NumberFormat("vi-VN", {
  style: "currency",
  currency: "VND",
  maximumFractionDigits: 0,
});

export const billStatusLabels = {
  PAID: "Đã thanh toán",
  PENDING: "Chờ thanh toán",
  VOID: "Đã hủy",
};

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

const isCount = value => Number.isSafeInteger(value) && value >= 0;
const isAmount = value => typeof value === "string" ? /^\d+$/.test(value) : isCount(value);
const isBill = bill => bill != null && /^\d+$/.test(bill.id ?? "")
  && typeof bill.billCode === "string" && Object.hasOwn(billStatusLabels, bill.status) && isAmount(bill.totalAmount);
const validPage = page => page != null && isCount(page.page) && isCount(page.totalElements)
  && isCount(page.totalPages) && Array.isArray(page.content) && page.content.every(isBill);
const validItem = item => item != null && typeof item.itemName === "string" && isCount(item.quantity)
  && isAmount(item.unitPrice) && isAmount(item.lineTotal);
const validRevenue = entry => entry != null && isAmount(entry.revenue) && isCount(entry.billCount);

function validStatistics(data) {
  const summary = data?.summary;
  return summary != null && typeof summary.from === "string" && typeof summary.to === "string"
    && isAmount(summary.totalRevenue) && isAmount(summary.averageBillAmount) && isCount(summary.totalBills)
    && Array.isArray(data.dailyRevenue)
    && data.dailyRevenue.every(day => validRevenue(day) && typeof day.date === "string")
    && Array.isArray(data.revenueByPaymentMethod)
    && data.revenueByPaymentMethod.every(entry => validRevenue(entry) && typeof entry.method === "string");
}

export function fetchBills({ code, from, to, status, page = 0, size = 10, sort = "createdAt,desc", ...options }) {
  const params = new URLSearchParams({ page: String(page), size: String(size), sort });
  if (code?.trim()) params.set("code", code.trim());
  if (from) params.set("from", from);
  if (to) params.set("to", to);
  if (status && status !== "ALL") params.set("status", status);
  return readBilling(`/api/v1/bills?${params}`, "danh sách hóa đơn", options, validPage);
}

export function fetchBillDetail(id, options) {
  return readBilling(`/api/v1/bills/${encodeURIComponent(id)}`, "chi tiết hóa đơn", options,
    bill => isBill(bill) && String(bill.id) === String(id) && Array.isArray(bill.items) && bill.items.every(validItem));
}

export function fetchRevenueStatistics({ period, from, to, ...options }) {
  const params = new URLSearchParams();
  if (from) params.set("from", from);
  if (to) params.set("to", to);
  if (period) params.set("period", period);
  return readBilling(`/api/v1/statistics/revenue?${params}`, "thống kê doanh thu", options, validStatistics);
}

async function readBilling(path, resource, { authorization, credentials = "same-origin", signal, onDenied }, validate) {
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

  if (response.status === 401 || response.status === 403) {
    const error = new Error("UNAUTHORIZED");
    error.status = response.status;
    if (!signal?.aborted) onDenied?.();
    throw error;
  }
  if (response.status === 404) throw new Error("NOT_FOUND");
  if (response.status === 503) throw new Error("Dữ liệu tạm thời không khả dụng (HTTP 503).");
  if (!response.ok) throw new Error(`Lỗi tải ${resource} (HTTP ${response.status})`);
  const data = await response.json().catch(() => null);
  if (!validate(data)) throw new Error(`Phản hồi ${resource} không hợp lệ. Vui lòng thử lại.`);
  return data;
}
