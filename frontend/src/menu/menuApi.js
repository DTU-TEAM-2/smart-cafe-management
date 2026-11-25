const root = '/api/v1/menu';
const exactFields = new Set(['price', 'imageMediaId', 'mediaId', 'version']);

function parseMenuJson(text) {
  return JSON.parse(text, (key, value, context) => {
    if (!exactFields.has(key) || typeof value !== 'number') return value;
    if (/^\d+$/.test(context?.source ?? '')) return context.source;
    if (Number.isSafeInteger(value)) return String(value);
    throw new Error('Trình duyệt không đọc được số nguyên chính xác trong dữ liệu thực đơn.');
  });
}

export function menuPath(kind, code) {
  return code === undefined ? `/${kind}` : `/${kind}/${encodeURIComponent(code)}`;
}

async function request(path, authorization, options = {}) {
  const { method = 'GET', headers, body, signal } = options;
  const response = await fetch(root + path, {
    method, signal, credentials: 'same-origin', cache: 'no-store', body,
    headers: { Accept: 'application/json', Authorization: authorization.header ?? authorization, ...headers },
  });
  if (response.ok) return response.status === 204 ? null : parseMenuJson(await response.text());
  if (response.status === 401 && !signal?.aborted) authorization.onUnauthorized?.();
  const problem = await response.json().catch(() => ({}));
  const messages = {
    401: 'Phiên đã hết hạn. Vui lòng đăng nhập lại.',
    403: 'Yêu cầu bị từ chối do quyền quản lý thực đơn hoặc CSRF. Hãy tải lại và kiểm tra quyền truy cập.',
    404: 'Mục này không còn trong thực đơn. Hãy tải lại danh sách.',
    409: 'Dữ liệu đã thay đổi hoặc mã đã được dùng. Hãy tải lại trước khi thử lại.',
  };
  const error = new Error(messages[response.status] ?? problem.detail ?? 'Không thể xử lý yêu cầu. Vui lòng thử lại.');
  error.status = response.status;
  error.code = problem.code;
  error.fields = problem.fieldErrors;
  throw error;
}

export function listMenu(kind, params, authorization, signal) {
  return request(`${menuPath(kind)}?${new URLSearchParams(params)}`, authorization, { signal });
}

export function readMenu(kind, code, authorization, signal) {
  return request(menuPath(kind, code), authorization, { signal });
}

export async function loadCategories(authorization, signal) {
  const size = 100;
  let previous;
  // Offset pages are not a snapshot. Publish only two matching, complete passes.
  for (let attempt = 0; attempt < 3; attempt += 1) {
    const first = await listMenu('categories', { page: 0, size }, authorization, signal);
    const rows = [...first.content];
    let consistent = true;
    for (let page = 1; page < Math.ceil(first.totalElements / size); page += 1) {
      const result = await listMenu('categories', { page, size }, authorization, signal);
      if (result.totalElements !== first.totalElements) { consistent = false; break; }
      rows.push(...result.content);
    }
    consistent &&= rows.length === first.totalElements && new Set(rows.map(row => row.code)).size === rows.length;
    const snapshot = JSON.stringify(rows.map(row => [row.code, row.name, row.version, row.itemCount]));
    if (consistent && snapshot === previous) return rows;
    previous = consistent ? snapshot : undefined;
  }
  throw new Error('Danh sách nhóm món đang thay đổi. Vui lòng tải lại nhóm món rồi thử lại.');
}

async function writeMenu(path, method, body, authorization, signal) {
  const csrf = await request('/csrf', authorization, { signal });
  const headers = { [csrf.headerName]: csrf.token };
  if (body !== undefined && !(body instanceof FormData)) headers['Content-Type'] = 'application/json';
  try {
    return await request(path, authorization, { method, body, headers, signal });
  } catch (error) {
    if (!signal?.aborted && (!error.status || error.status >= 500)) error.uncertain = true;
    throw error;
  }
}

export async function saveMenu(kind, value, editing, authorization, signal, uploadedImages) {
  const { code, name, categoryCode, price, available, imageFile } = value;
  let imageMediaId = value.imageMediaId ?? null;
  if (kind === 'items' && imageFile) {
    const uploadUnknown = () => Object.assign(new Error('Chưa xác nhận được kết quả tải ảnh. Ảnh có thể đã được lưu; không gửi lại. Hãy hủy biểu mẫu hoặc tiếp tục không dùng ảnh mới.'),
      { uncertain: true, pendingUpload: true });
    if (uploadedImages.get(imageFile)?.uncertain) throw uploadUnknown();
    if (!uploadedImages.has(imageFile)) {
      const data = new FormData();
      data.append('file', imageFile);
      try {
        const uploaded = await writeMenu('/images', 'POST', data, authorization, signal);
        if (!/^[1-9]\d*$/.test(uploaded?.mediaId ?? '')) throw uploadUnknown();
        uploadedImages.set(imageFile, uploaded.mediaId);
      } catch (error) {
        // This code is emitted before any provider call; retry remains safe.
        if (error.status === 503 && error.code === 'IMAGE_NOT_CONFIGURED') throw error;
        if (!error.uncertain) throw error;
        uploadedImages.set(imageFile, { uncertain: true });
        throw uploadUnknown();
      }
    }
    imageMediaId = uploadedImages.get(imageFile);
  }
  const payload = { code, name, version: editing ? value.version : 0 };
  if (kind === 'items') Object.assign(payload, { categoryCode, price, available, imageMediaId });
  try {
    return await writeMenu(menuPath(kind, editing ? code : undefined), editing ? 'PUT' : 'POST',
      JSON.stringify(payload), authorization, signal);
  } catch (error) {
    if (!error.uncertain) throw error;
    // A dropped response (including an unreadable success body) is not a rejected write.
    error.pendingSave = { kind, payload, editing };
    error.message = 'Chưa xác nhận được kết quả lưu. Hãy kiểm tra kết quả trước khi tiếp tục; không gửi lại thao tác lưu.';
    throw error;
  }
}

export function deleteMenu(kind, row, authorization, signal) {
  return writeMenu(`${menuPath(kind, row.code)}?${new URLSearchParams({ version: row.version })}`,
    'DELETE', undefined, authorization, signal);
}

export async function reconcileMenuSave(pending, authorization, signal) {
  const { kind, payload, editing } = pending;
  const actual = await readMenu(kind, payload.code, authorization, signal);
  const expectedVersion = editing ? BigInt(payload.version) + 1n : 0n;
  const fields = kind === 'items' ? ['code', 'name', 'categoryCode', 'price', 'available', 'imageMediaId'] : ['code', 'name'];
  const matches = fields.every(field => (actual[field] ?? null) === (payload[field] ?? null))
    && BigInt(actual.version) === expectedVersion;
  if (!matches) throw new Error('Dữ liệu trên máy chủ khác bản vừa gửi hoặc đã thay đổi tiếp. Hãy hủy biểu mẫu và tải lại để kiểm tra; không tự ghi đè.');
  return actual;
}
