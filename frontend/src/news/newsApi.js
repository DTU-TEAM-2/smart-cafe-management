export class NewsApiError extends Error {
  constructor(status, message, code) { super(message); this.status = status; this.code = code; }
}

export async function newsRequest(path, authorization, { method = "GET", body, etag, signal, asResponse = false } = {}) {
  const response = await fetch(`/api/v1/news${path}`, {
    method, credentials: "same-origin", cache: "no-store",
    signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(15000)]) : AbortSignal.timeout(15000),
    headers: { Authorization: authorization, ...(body && !(body instanceof FormData) ? { "Content-Type": "application/json" } : {}),
      ...(etag ? { "If-Match": etag } : {}) },
    ...(body === undefined || method === "GET" ? {} : { body: body instanceof FormData ? body : JSON.stringify(body) }),
  });
  if (!response.ok) {
    const messages = { 401: "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.", 403: "Bạn không có quyền thực hiện thao tác này hoặc phiên đã hết hạn.",
      404: "Tin tức không còn tồn tại hoặc không dành cho bạn.", 409: "Dữ liệu đang có xung đột. Vui lòng tải lại hoặc thử lại.",
      412: "Bài viết đã được thay đổi ở nơi khác. Nội dung bạn đang soạn vẫn được giữ; hãy tải bản mới trước khi sửa tiếp.",
      428: "Chưa lấy được phiên bản bài viết. Vui lòng tải lại.", 413: "Ảnh tối đa 5 MiB.", 415: "Chỉ chấp nhận ảnh PNG hoặc JPEG hợp lệ.",
      503: "Dịch vụ chưa sẵn sàng. Vui lòng thử lại sau." };
    let problem;
    if (response.status === 400 || response.status === 409) { try { problem = await response.json(); } catch { /* Use the safe fallback. */ } }
    const message = response.status === 409 && problem?.code === "NEWS_SLUG_TAKEN"
      ? "Đường dẫn bài viết đã được sử dụng. Hãy chọn đường dẫn khác."
      : messages[response.status] ?? (typeof problem?.detail === "string" ? problem.detail : "Chưa thực hiện được thao tác. Vui lòng thử lại.");
    throw new NewsApiError(response.status, message, problem?.code);
  }
  const writeStatus = { POST: 201, PUT: 200, DELETE: 204 }[method];
  if (writeStatus && response.status !== writeStatus) throw new Error("Unexpected news write status");
  if (asResponse) return response;
  return { data: response.status === 204 ? null : await response.json(), etag: response.headers.get("ETag") };
}

export function isNewsId(value) {
  return typeof value === "string" && /^[1-9][0-9]{0,18}$/.test(value)
    && (value.length < 19 || value <= "9223372036854775807");
}

export function isNewsVersionTag(value) {
  const version = typeof value === "string" ? /^"([0-9]+)"$/.exec(value)?.[1] : undefined;
  return version === "0" || isNewsId(version);
}

const isOptionalText = value => value == null || typeof value === "string";

function isNewsSummary(post) {
  return isNewsId(post?.id) && typeof post.title === "string"
    && isOptionalText(post.summary) && isOptionalText(post.authorName)
    && isOptionalText(post.publishedAt) && isOptionalText(post.createdAt)
    && typeof post.status === "string" && Object.hasOwn(statusNames, post.status)
    && Array.isArray(post.audiences) && post.audiences.every(audience =>
      typeof audience === "string" && Object.hasOwn(audienceNames, audience));
}

export function isNewsPage(page, index, size) {
  return Array.isArray(page?.content) && page.content.every(isNewsSummary)
    && page.page === index && page.size === size && page.content.length <= size
    && Number.isSafeInteger(page.totalElements) && page.totalElements >= 0
    && page.content.length <= page.totalElements
    && page.totalPages === Math.ceil(page.totalElements / size)
    && page.last === (index + 1 >= page.totalPages)
    && (page.content.length === 0 || index < page.totalPages);
}

export function isNewsDetail(post, id) {
  return isNewsSummary(post) && (!id || post.id === id.replace(/^0+/, ""))
    && typeof post.slug === "string" && typeof post.body === "string"
    && Array.isArray(post.images) && post.images.every(image => isNewsId(image?.mediaId));
}

export function isUncertainNewsWrite(error) {
  return !(error instanceof NewsApiError) || error.status >= 500 || error.status === 408;
}

export function newsError(error) {
  return error instanceof NewsApiError ? error.message : "Chưa kết nối được máy chủ. Vui lòng thử lại.";
}

export function safeNewsImage(url) {
  if (typeof url !== "string") return null;
  if (/^\/api\/v1\/news\/images\/[0-9]+$/.test(url)) return url;
  try { const parsed = new URL(url); return parsed.protocol === "https:" && !parsed.username && !parsed.password ? url : null; }
  catch { return null; }
}

export const newsDate = value => {
  if (!value) return "Chưa xuất bản";
  const date = new Date(value);
  return Number.isFinite(date.getTime()) ? new Intl.DateTimeFormat("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric", timeZone: "Asia/Ho_Chi_Minh" }).format(date) : "";
};
export const statusNames = { DRAFT: "Bản nháp", PUBLISHED: "Đã đăng", ARCHIVED: "Đã lưu trữ" };
export const audienceNames = { STAFF: "Nhân viên", CUSTOMER: "Khách hàng" };
