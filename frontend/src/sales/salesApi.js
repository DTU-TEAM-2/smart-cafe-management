import { useEffect, useState } from "react";

export async function readApi(path, { authorization, credentials, signal, body, headers = {} }) {
  let response;
  try {
    response = await fetch(path, { method: body ? "POST" : "GET", credentials, cache: "no-store",
      signal: AbortSignal.any([signal, AbortSignal.timeout(12000)]),
      headers: { Authorization: authorization, ...headers, ...(body ? { "Content-Type": "application/json" } : {}) },
      ...(body ? { body: JSON.stringify(body) } : {}) });
  } catch (error) {
    if (signal.aborted) throw error;
    throw new Error("Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại.");
  }
  if (response.ok) return response.status === 204 ? null : response.json();
  const messages = { 400: "Dữ liệu chưa hợp lệ hoặc bàn chưa có món để tính tiền.",
    401: "Phiên đăng nhập đã hết hạn.", 403: "Bạn không có quyền thực hiện thao tác này hoặc phiên đã hết hạn.",
    404: "Không tìm thấy dữ liệu.", 409: "Dữ liệu bàn hoặc hóa đơn đã thay đổi. Tải lại trước khi tiếp tục." };
  const error = new Error(messages[response.status] ?? "Máy chủ chưa xử lý được. Vui lòng thử lại.");
  error.status = response.status;
  throw error;
}

export async function postSales(path, body, options, beforeDispatch) {
  const csrf = await readApi("/api/v1/sales/csrf", options);
  if (typeof csrf?.headerName !== "string" || !csrf.headerName || typeof csrf?.token !== "string" || !csrf.token) {
    throw new Error("Chưa lấy được mã xác thực yêu cầu. Vui lòng thử lại.");
  }
  options.signal.throwIfAborted();
  beforeDispatch?.();
  return readApi(path, { ...options, body, headers: { [csrf.headerName]: csrf.token } });
}

export function useSalesResource(path, authorization, credentials, poll = 0) {
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState(null);
  const key = `${path}:${attempt}`;
  useEffect(() => {
    if (!path) return;
    const controller = new AbortController();
    let timer;
    async function refresh() {
      try {
        const data = await readApi(path, { authorization, credentials, signal: controller.signal });
        if (!controller.signal.aborted) setResult({ path, authorization, credentials, key, data, error: null });
      } catch (error) {
        if (!controller.signal.aborted) {
          const transient = !error.status || error.status === 429 || error.status >= 500;
          setResult(previous => ({
            path, authorization, credentials, key, error,
            data: poll > 0 && transient && previous?.path === path
              && previous.authorization === authorization && previous.credentials === credentials ? previous.data : null,
          }));
        }
      } finally {
        if (!controller.signal.aborted && poll > 0) timer = setTimeout(refresh, poll);
      }
    }
    void refresh();
    return () => { controller.abort(); clearTimeout(timer); };
  }, [path, authorization, credentials, poll, key]);
  const current = path && result?.path === path && result.authorization === authorization
    && result.credentials === credentials ? result : null;
  return { data: current?.data ?? null, error: current?.error ?? null, loading: Boolean(path && !current),
    refreshing: Boolean(current && current.key !== key),
    reload: () => setAttempt(value => value + 1) };
}

const money = new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" });
export function vnd(value) { return /^\d+$/.test(value ?? "") ? money.format(BigInt(value)) : "—"; }
