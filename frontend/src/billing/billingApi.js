export const moneyFormatter = new Intl.NumberFormat("vi-VN", {
  style: "currency",
  currency: "VND",
  maximumFractionDigits: 0,
});

export function vnd(amount) {
  if (amount == null || amount === "") return "0 ₫";
  try {
    const numeric = typeof amount === "string" ? BigInt(amount) : BigInt(Math.round(amount));
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

export async function fetchBills({ code, from, to, status, page = 0, size = 10, sort = "createdAt,desc", authorization = "", signal }) {
  const params = new URLSearchParams();
  if (code && code.trim()) params.set("code", code.trim());
  if (from) params.set("from", from);
  if (to) params.set("to", to);
  if (status && status !== "ALL") params.set("status", status);
  params.set("page", String(page));
  params.set("size", String(size));
  params.set("sort", sort);

  const headers = {};
  if (authorization) headers.Authorization = authorization;

  const res = await fetch(`/api/v1/bills?${params.toString()}`, {
    headers,
    signal,
    cache: "no-store",
    credentials: "same-origin",
  });

  if (res.status === 401 || res.status === 403) {
    throw new Error("UNAUTHORIZED");
  }
  if (!res.ok) {
    throw new Error(`Lỗi tải danh sách hóa đơn (HTTP ${res.status})`);
  }
  return res.json();
}

export async function fetchBillDetail(id, authorization = "", signal) {
  const headers = {};
  if (authorization) headers.Authorization = authorization;

  const res = await fetch(`/api/v1/bills/${encodeURIComponent(id)}`, {
    headers,
    signal,
    cache: "no-store",
    credentials: "same-origin",
  });

  if (res.status === 401 || res.status === 403) {
    throw new Error("UNAUTHORIZED");
  }
  if (res.status === 404) {
    throw new Error("NOT_FOUND");
  }
  if (!res.ok) {
    throw new Error(`Lỗi tải chi tiết hóa đơn (HTTP ${res.status})`);
  }
  return res.json();
}

export async function fetchRevenueStatistics({ period, from, to, authorization = "", signal }) {
  const params = new URLSearchParams();
  if (from && to) {
    params.set("from", from);
    params.set("to", to);
  } else if (period) {
    params.set("period", period);
  }

  const headers = {};
  if (authorization) headers.Authorization = authorization;

  const res = await fetch(`/api/v1/statistics/revenue?${params.toString()}`, {
    headers,
    signal,
    cache: "no-store",
    credentials: "same-origin",
  });

  if (res.status === 401 || res.status === 403) {
    throw new Error("UNAUTHORIZED");
  }
  if (!res.ok) {
    throw new Error(`Lỗi tải số liệu thống kê (HTTP ${res.status})`);
  }
  return res.json();
}
