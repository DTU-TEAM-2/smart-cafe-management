export async function fetchCsrf(authorization = "") {
  try {
    const response = await fetch("/api/v1/staff/csrf", {
      headers: authorization ? { Authorization: authorization } : {},
      cache: "no-store",
    });
    if (!response.ok) return null;
    return await response.json();
  } catch {
    return null;
  }
}

export async function fetchStaffList({ keyword = "", page = 0, size = 10, authorization = "" }) {
  const params = new URLSearchParams();
  if (keyword && keyword.trim()) params.set("keyword", keyword.trim());
  params.set("page", String(page));
  params.set("size", String(size));

  const response = await fetch(`/api/v1/staff?${params.toString()}`, {
    headers: authorization ? { Authorization: authorization } : {},
    cache: "no-store",
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.detail || errorData.message || "Không thể tải danh sách nhân viên.");
  }
  return await response.json();
}

export async function fetchStaffById(id, authorization = "") {
  const response = await fetch(`/api/v1/staff/${id}`, {
    headers: authorization ? { Authorization: authorization } : {},
    cache: "no-store",
  });
  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.detail || "Không tìm thấy thông tin nhân viên.");
  }
  return await response.json();
}

export async function createStaff(data, authorization = "", csrf = null) {
  const headers = {
    "Content-Type": "application/json",
  };
  if (authorization) headers.Authorization = authorization;
  if (csrf?.headerName && csrf?.token) headers[csrf.headerName] = csrf.token;

  const response = await fetch("/api/v1/staff", {
    method: "POST",
    headers,
    body: JSON.stringify(data),
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.detail || errorData.message || "Thêm mới nhân viên thất bại.");
  }
  return await response.json();
}

export async function updateStaff(id, data, authorization = "", csrf = null) {
  const headers = {
    "Content-Type": "application/json",
  };
  if (authorization) headers.Authorization = authorization;
  if (csrf?.headerName && csrf?.token) headers[csrf.headerName] = csrf.token;

  const response = await fetch(`/api/v1/staff/${id}`, {
    method: "PUT",
    headers,
    body: JSON.stringify(data),
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.detail || errorData.message || "Cập nhật nhân viên thất bại.");
  }
  return await response.json();
}

export async function deleteStaff(id, authorization = "", csrf = null) {
  const headers = {};
  if (authorization) headers.Authorization = authorization;
  if (csrf?.headerName && csrf?.token) headers[csrf.headerName] = csrf.token;

  const response = await fetch(`/api/v1/staff/${id}`, {
    method: "DELETE",
    headers,
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.detail || errorData.message || "Xóa nhân viên thất bại.");
  }
  return true;
}

export async function uploadAvatar(file, authorization = "", csrf = null) {
  const headers = {};
  if (authorization) headers.Authorization = authorization;
  if (csrf?.headerName && csrf?.token) headers[csrf.headerName] = csrf.token;

  const formData = new FormData();
  formData.append("file", file);

  const response = await fetch("/api/v1/staff/avatar", {
    method: "POST",
    headers,
    body: formData,
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.detail || errorData.message || "Tải ảnh đại diện thất bại.");
  }
  return await response.json();
}
