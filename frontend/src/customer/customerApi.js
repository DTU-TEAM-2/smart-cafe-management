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

function generateUuid() {
  if (typeof crypto !== "undefined" && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
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
  return res.json();
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

export async function createOrder({ sessionId, token, items, idempotencyKey = generateUuid(), csrf = null }) {
  const headers = {
    "Content-Type": "application/json",
    Accept: "application/json",
    "Idempotency-Key": idempotencyKey,
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
        menuItemId: Number(it.menuItemId || it.id),
        quantity: Number(it.quantity),
      })),
    }),
  });

  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(data.detail || data.title || "Gửi đơn gọi món thất bại.");
  }
  return data;
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
  return data;
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
  return data;
}

export async function sendWaiterRequest({ sessionId, token, idempotencyKey = generateUuid(), csrf = null }) {
  const headers = {
    Accept: "application/json",
    "Idempotency-Key": idempotencyKey,
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

export async function sendBillRequest({ sessionId, token, idempotencyKey = generateUuid(), csrf = null }) {
  const headers = {
    Accept: "application/json",
    "Idempotency-Key": idempotencyKey,
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
