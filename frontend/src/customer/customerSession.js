const SESSION_STORAGE_KEY = "lecker_customer_session";
const CART_STORAGE_PREFIX = "lecker_customer_cart";
const ORDERS_STORAGE_PREFIX = "lecker_customer_orders";
const ATTEMPT_STORAGE_PREFIX = "lecker_customer_attempt";

function randomId() {
  if (typeof crypto !== "undefined" && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function validSession(value) {
  return Boolean(value && value.sessionId && value.token);
}

export function saveCustomerSession(value) {
  if (!validSession(value)) {
    throw new Error("Phiên khách hàng phải có sessionId và token do máy chủ cấp.");
  }

  const session = {
    sessionId: String(value.sessionId),
    token: String(value.token),
    tableNumber: String(value.tableNumber || ""),
    clientStorageId: String(value.clientStorageId || randomId()),
  };
  sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
  return session;
}

export function readCustomerSession() {
  try {
    stripLegacyCredentialUrl();
    const saved = sessionStorage.getItem(SESSION_STORAGE_KEY);
    if (!saved) return null;
    const parsed = JSON.parse(saved);
    if (!validSession(parsed)) return null;

    // Older handoffs did not include a non-secret client storage namespace.
    // Add one once so data from another token/client can never share cart state.
    if (!parsed.clientStorageId) {
      return saveCustomerSession(parsed);
    }
    return parsed;
  } catch {
    return null;
  }
}

function stripLegacyCredentialUrl() {
  const hash = window.location.hash;
  if (!hash.startsWith("#customer?") || !/(?:^|[?&])(?:token|access_token)=/i.test(hash)) return;
  window.history.replaceState(window.history.state, "", `${window.location.pathname}${window.location.search}#customer`);
}

export function clearCustomerSession() {
  sessionStorage.removeItem(SESSION_STORAGE_KEY);
}

function sessionScope(session) {
  if (!validSession(session) || !session.clientStorageId) return null;
  return `${encodeURIComponent(session.sessionId)}:${encodeURIComponent(session.clientStorageId)}`;
}

function scopedKey(prefix, session) {
  const scope = sessionScope(session);
  return scope ? `${prefix}:${scope}` : null;
}

export function readSessionCart(session) {
  return readJson(scopedKey(CART_STORAGE_PREFIX, session), []);
}

export function writeSessionCart(session, cart) {
  writeJson(scopedKey(CART_STORAGE_PREFIX, session), cart);
}

export function readSessionOrders(session) {
  return readJson(scopedKey(ORDERS_STORAGE_PREFIX, session), []);
}

export function writeSessionOrders(session, orders) {
  writeJson(scopedKey(ORDERS_STORAGE_PREFIX, session), orders);
}

export function getOrCreateOrderAttempt(session, newPayload) {
  const key = scopedKey(`${ATTEMPT_STORAGE_PREFIX}:order`, session);
  if (!key) throw new Error("Chưa có phiên khách hàng hợp lệ.");

  const saved = readJson(key, null);
  // If an unresolved attempt already exists (e.g. from a prior network drop/failure),
  // reuse the EXACT immutable payload & idempotencyKey so retries never duplicate or mix new items!
  if (saved?.idempotencyKey && Array.isArray(saved.payload) && saved.payload.length > 0) {
    return {
      idempotencyKey: saved.idempotencyKey,
      payload: saved.payload,
      isRetry: true,
    };
  }

  const idempotencyKey = randomId();
  writeJson(key, { idempotencyKey, payload: newPayload });
  return {
    idempotencyKey,
    payload: newPayload,
    isRetry: false,
  };
}

export function readOrderAttempt(session) {
  const key = scopedKey(`${ATTEMPT_STORAGE_PREFIX}:order`, session);
  return readJson(key, null);
}

export function getOrCreateAttempt(session, operation, payload = null) {
  const key = scopedKey(`${ATTEMPT_STORAGE_PREFIX}:${operation}`, session);
  if (!key) throw new Error("Chưa có phiên khách hàng hợp lệ.");

  const saved = readJson(key, null);
  if (saved?.idempotencyKey) {
    return saved.idempotencyKey;
  }

  const idempotencyKey = randomId();
  writeJson(key, { idempotencyKey, payload });
  return idempotencyKey;
}

export function clearAttempt(session, operation) {
  const key = scopedKey(`${ATTEMPT_STORAGE_PREFIX}:${operation}`, session);
  if (key) sessionStorage.removeItem(key);
}

function readJson(key, fallback) {
  if (!key) return fallback;
  try {
    const value = sessionStorage.getItem(key);
    return value ? JSON.parse(value) : fallback;
  } catch {
    return fallback;
  }
}

function writeJson(key, value) {
  if (!key) return;
  try {
    sessionStorage.setItem(key, JSON.stringify(value));
  } catch {
    // Storage can be unavailable in privacy mode; the current in-memory state
    // still works, but the caller must not treat persistence as guaranteed.
  }
}
