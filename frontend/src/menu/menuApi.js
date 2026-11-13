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
    headers: { Accept: 'application/json', Authorization: authorization, ...headers },
  });
  if (response.ok) return response.status === 204 ? null : parseMenuJson(await response.text());
  const problem = await response.json().catch(() => ({}));
  const messages = {
    401: 'Phiên đã hết hạn. Vui lòng đăng nhập lại.',
    403: 'Bạn không có quyền quản lý thực đơn hoặc phiên đã hết hạn.',
    404: 'Mục này không còn trong thực đơn. Hãy tải lại danh sách.',
    409: 'Dữ liệu đã thay đổi hoặc mã đã được dùng. Hãy tải lại trước khi thử lại.',
  };
  const error = new Error(messages[response.status] ?? problem.detail ?? 'Không thể xử lý yêu cầu. Vui lòng thử lại.');
  error.fields = problem.fieldErrors;
  throw error;
}

export function listMenu(kind, params, authorization, signal) {
  return request(`${menuPath(kind)}?${new URLSearchParams(params)}`, authorization, { signal });
}

export function readMenu(kind, code, authorization, signal) {
  return request(menuPath(kind, code), authorization, { signal });
}

async function writeMenu(path, method, body, authorization, signal) {
  const csrf = await request('/csrf', authorization, { signal });
  const headers = { [csrf.headerName]: csrf.token };
  if (body !== undefined && !(body instanceof FormData)) headers['Content-Type'] = 'application/json';
  return request(path, authorization, { method, body, headers, signal });
}

export async function saveMenu(kind, value, editing, authorization, signal, uploadedImages) {
  const { code, name, categoryCode, price, available, imageFile } = value;
  let imageMediaId = value.imageMediaId ?? null;
  if (kind === 'items' && imageFile) {
    if (!uploadedImages.has(imageFile)) {
      const data = new FormData();
      data.append('file', imageFile);
      const uploaded = await writeMenu('/images', 'POST', data, authorization, signal);
      uploadedImages.set(imageFile, uploaded.mediaId);
    }
    imageMediaId = uploadedImages.get(imageFile);
  }
  const payload = { code, name, version: editing ? value.version : 0 };
  if (kind === 'items') Object.assign(payload, { categoryCode, price, available, imageMediaId });
  return writeMenu(menuPath(kind, editing ? code : undefined), editing ? 'PUT' : 'POST',
    JSON.stringify(payload), authorization, signal);
}

export function deleteMenu(kind, row, authorization, signal) {
  return writeMenu(`${menuPath(kind, row.code)}?${new URLSearchParams({ version: row.version })}`,
    'DELETE', undefined, authorization, signal);
}
