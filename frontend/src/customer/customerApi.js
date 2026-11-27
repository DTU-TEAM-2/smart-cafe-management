/**
 * Customer REST API Client for Smart Cafe DT2-4.
 * Interacts with Backend Customer Endpoints:
 * - /api/v1/customer/menu/**
 * - /api/v1/customer/csrf
 * - /api/v1/customer/sessions/{sessionId}/orders
 * - /api/v1/customer/sessions/{sessionId}/order-items/{itemId}
 * - /api/v1/customer/sessions/{sessionId}/waiter-requests
 * - /api/v1/customer/sessions/{sessionId}/bill-requests
 * - /api/v1/customer/feedback
 */

function requireIdempotencyKey(key) {
  if (!key) throw new Error("Thiếu mã chống gửi trùng cho thao tác này.");
  return key;
}

const isCount = value => Number.isSafeInteger(value) && value >= 0;
const isDecimal = value => typeof value === "string" ? /^\d+$/.test(value) : isCount(value);
const isId = value => typeof value === "string" ? /^[1-9]\d*$/.test(value) : isCount(value) && value > 0;
const itemStatuses = ["SUBMITTED", "PREPARING", "SERVED", "CANCELLED"];

function validOrderItem(item) {
  return item != null && isId(item.id) && isId(item.menuItemId) && typeof item.name === "string"
    && isDecimal(item.unitPrice) && isDecimal(item.lineTotal) && isDecimal(item.version)
    && Number.isSafeInteger(item.quantity) && item.quantity > 0 && itemStatuses.includes(item.status)
    && BigInt(item.unitPrice) * BigInt(item.quantity) === BigInt(item.lineTotal);
}

function normalizeOrder(data, response, sessionId, batchId = null) {
  if (!isId(data?.id) || !isId(data.sessionId) || String(data.sessionId) !== String(sessionId)
      || (batchId !== null && String(data.id) !== String(batchId))
      || !Array.isArray(data.items) || !data.items.length || !data.items.every(validOrderItem)
      || !isDecimal(data.totalAmount) || !isDecimal(data.sessionTotalAmount)
      || typeof data.editableUntil !== "string" || !Number.isFinite(Date.parse(data.editableUntil))) {
    throw new Error("Chưa xác minh được đơn món. Vui lòng thử lại.");
  }
  const receivedAt = Date.now();
  const serverDate = response?.headers?.get("Date");
  const serverNow = serverDate ? Date.parse(serverDate) : Number.NaN;
  const editableUntil = data.editableUntil ? Date.parse(data.editableUntil) : Number.NaN;
  const editableClientDeadline = Number.isFinite(serverNow) && Number.isFinite(editableUntil)
    ? receivedAt + Math.max(0, editableUntil - serverNow)
    : null;
  return {
    id: String(data.id),
    sessionId: String(data.sessionId),
    totalAmount: String(data.totalAmount),
    sessionTotalAmount: String(data.sessionTotalAmount),
    editableUntil: data.editableUntil,
    editableClientDeadline,
    message: data.message,
    items: data.items.map((item) => ({
      id: String(item.id),
      menuItemId: String(item.menuItemId),
      name: item.name,
      unitPrice: String(item.unitPrice),
      quantity: item.quantity,
      lineTotal: String(item.lineTotal),
      status: item.status,
      version: String(item.version),
    })),
  };
}

export async function fetchCsrfToken(url = "/api/v1/customer/csrf") {
  try {
    const res = await fetch(url, {
      method: "GET",
      headers: { Accept: "application/json" },
      credentials: "same-origin",
      cache: "no-store",
    });
    if (!res.ok) return null;
    return await res.json();
  } catch {
    return null;
  }
}

export async function fetchCategories() {
  const res = await fetch("/api/v1/customer/menu/categories", {
    headers: { Accept: "application/json" },
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.detail || "Không thể tải danh mục thực đơn.");
  }
  return res.json();
}

export async function fetchMenuItems({ categoryId, keyword, availableOnly, page = 0, size = 20 } = {}) {
  const params = new URLSearchParams();
  if (categoryId) params.set("categoryId", categoryId);
  if (keyword && keyword.trim()) params.set("keyword", keyword.trim());
  if (availableOnly) params.set("availableOnly", "true");
  params.set("page", String(page));
  params.set("size", String(size));

  const res = await fetch(`/api/v1/customer/menu/items?${params.toString()}`, {
    headers: { Accept: "application/json" },
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.detail || "Không thể tải danh sách món ăn.");
  }
  const data = await res.json().catch(() => null);
  if (!Array.isArray(data?.content) || data.page !== page || !isCount(data.totalElements)
      || !data.content.every(item => isId(item?.id) && typeof item.name === "string" && isDecimal(item.price))) {
    throw new Error("Phản hồi thực đơn không hợp lệ. Vui lòng thử lại.");
  }
  return data;
}

export async function fetchMenuItem(itemId) {
  const res = await fetch(`/api/v1/customer/menu/items/${encodeURIComponent(itemId)}`, {
    headers: { Accept: "application/json" },
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.detail || "Không tìm thấy thông tin món.");
  }
  return res.json();
}

export async function fetchOrder({ sessionId, token, batchId }) {
  const res = await fetch(`/api/v1/customer/sessions/${encodeURIComponent(sessionId)}/orders/${encodeURIComponent(batchId)}`, {
    headers: { Accept: "application/json", Authorization: `Bearer ${token}` },
    credentials: "same-origin",
    cache: "no-store",
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.detail || "Không thể tải lại đơn món. Vui lòng thử lại.");
  return normalizeOrder(data, res, sessionId, batchId);
}

export async function createOrder({ sessionId, token, items, idempotencyKey, csrf = null }) {
  const headers = {
    "Content-Type": "application/json",
    Accept: "application/json",
    "Idempotency-Key": requireIdempotencyKey(idempotencyKey),
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  if (csrf?.headerName && csrf?.token) {
    headers[csrf.headerName] = csrf.token;
  }

  const res = await fetch(`/api/v1/customer/sessions/${encodeURIComponent(sessionId)}/orders`, {
    method: "POST",
    headers,
    credentials: "same-origin",
    body: JSON.stringify({
      items: items.map((it) => ({
        menuItemId: String(it.menuItemId ?? it.id),
        quantity: Number(it.quantity),
        ...(it.expectedUnitPrice != null ? { expectedUnitPrice: String(it.expectedUnitPrice) } : {}),
      })),
    }),
  });

  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    const error = new Error(data.detail || data.title || "Gửi đơn gọi món thất bại.");
    error.status = res.status;
    // This describes only this HTTP request. A rejection cannot settle an
    // earlier request whose outcome was unknown.
    error.requestRejected = [400, 409].includes(res.status) && typeof data?.detail === "string"
      && (data.status === res.status || res.headers.get("Content-Type")?.includes("application/problem+json"));
    throw error;
  }
  const order = normalizeOrder(data, res, sessionId);
  const expectedMenuIds = new Set(items.map(item => String(item.menuItemId ?? item.id)));
  const actualMenuIds = new Set(order.items.map(item => item.menuItemId));
  const batchTotal = order.items.reduce((sum, item) =>
    item.status === "CANCELLED" ? sum : sum + BigInt(item.lineTotal), 0n);
  if (order.items.length !== expectedMenuIds.size || actualMenuIds.size !== expectedMenuIds.size
      || !order.items.every(item => expectedMenuIds.has(item.menuItemId))
      || new Set(order.items.map(item => item.id)).size !== order.items.length
      || BigInt(order.totalAmount) !== batchTotal || BigInt(order.sessionTotalAmount) < batchTotal) {
    throw new Error("Chưa xác minh được đơn món. Vui lòng thử lại.");
  }
  return order;
}

export async function updateOrderItem({ sessionId, token, itemId, quantity, expectedVersion, csrf = null }) {
  const headers = {
    "Content-Type": "application/json",
    Accept: "application/json",
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  if (csrf?.headerName && csrf?.token) {
    headers[csrf.headerName] = csrf.token;
  }

  const res = await fetch(`/api/v1/customer/sessions/${encodeURIComponent(sessionId)}/order-items/${encodeURIComponent(itemId)}`, {
    method: "PATCH",
    headers,
    credentials: "same-origin",
    body: JSON.stringify({
      quantity: Number(quantity),
      expectedVersion: Number(expectedVersion),
    }),
  });

  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(data.detail || data.title || "Không thể cập nhật số lượng món.");
  }
  return normalizeOrder(data, res, sessionId);
}

export async function cancelOrderItem({ sessionId, token, itemId, expectedVersion, csrf = null }) {
  const headers = {
    "Content-Type": "application/json",
    Accept: "application/json",
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  if (csrf?.headerName && csrf?.token) {
    headers[csrf.headerName] = csrf.token;
  }

  const res = await fetch(`/api/v1/customer/sessions/${encodeURIComponent(sessionId)}/order-items/${encodeURIComponent(itemId)}`, {
    method: "DELETE",
    headers,
    credentials: "same-origin",
    body: JSON.stringify({
      expectedVersion: Number(expectedVersion),
    }),
  });

  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(data.detail || data.title || "Không thể hủy món.");
  }
  return normalizeOrder(data, res, sessionId);
}

export async function sendWaiterRequest({ sessionId, token, idempotencyKey, csrf = null }) {
  const headers = {
    Accept: "application/json",
    "Idempotency-Key": requireIdempotencyKey(idempotencyKey),
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  if (csrf?.headerName && csrf?.token) {
    headers[csrf.headerName] = csrf.token;
  }

  const res = await fetch(`/api/v1/customer/sessions/${encodeURIComponent(sessionId)}/waiter-requests`, {
    method: "POST",
    headers,
    credentials: "same-origin",
  });

  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(data.detail || data.title || "Không thể gửi yêu cầu phục vụ.");
  }
  return data;
}

export async function sendBillRequest({ sessionId, token, idempotencyKey, csrf = null }) {
  const headers = {
    Accept: "application/json",
    "Idempotency-Key": requireIdempotencyKey(idempotencyKey),
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  if (csrf?.headerName && csrf?.token) {
    headers[csrf.headerName] = csrf.token;
  }

  const res = await fetch(`/api/v1/customer/sessions/${encodeURIComponent(sessionId)}/bill-requests`, {
    method: "POST",
    headers,
    credentials: "same-origin",
  });

  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(data.detail || data.title || "Không thể gửi yêu cầu thanh toán.");
  }
  return data;
}

export async function submitFeedback({ name, email, content, rating, images = [], csrf = null }) {
  const feedbackData = {
    name: name?.trim() || null,
    email: email?.trim() || null,
    content: content.trim(),
    rating: Number(rating),
  };

  const headers = {
    Accept: "application/json",
  };
  if (csrf?.headerName && csrf?.token) {
    headers[csrf.headerName] = csrf.token;
  }

  let body;
  if (images && images.length > 0) {
    const formData = new FormData();
    const jsonBlob = new Blob([JSON.stringify(feedbackData)], { type: "application/json" });
    formData.append("feedback", jsonBlob);
    for (const img of images) {
      formData.append("images", img);
    }
    body = formData;
  } else {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(feedbackData);
  }

  const res = await fetch("/api/v1/customer/feedback", {
    method: "POST",
    headers,
    credentials: "same-origin",
    body,
  });

  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(data.detail || data.title || "Gửi phản hồi không thành công.");
  }
  return data;
}
