const id = value => typeof value === "string" && /^[1-9]\d*$/.test(value);
const amount = value => typeof value === "string" && /^\d+$/.test(value);
const timestamp = value => typeof value === "string" && Number.isFinite(Date.parse(value));

// Match CheckoutService's String.strip() (Character.isWhitespace).
// eslint-disable-next-line no-control-regex -- Java treats these control characters as whitespace.
const javaEdgeWhitespace = /^[\t-\r\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+|[\t-\r\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+$/g;

export function normalizePaymentReference(method, reference) {
  return method === "BANK_TRANSFER" ? reference?.replace(javaEdgeWhitespace, "") || null : null;
}

export function checkedPaymentAmount(value) {
  if (!amount(value)) throw new Error("Chưa xác minh được số tiền hóa đơn. Vui lòng mở lại hóa đơn.");
  return value;
}

export function paymentAttemptKey(accountId, sessionCode, billId) {
  return id(accountId) && typeof sessionCode === "string" && sessionCode && id(billId)
    ? `lecker_payment_attempt:${accountId}:${encodeURIComponent(sessionCode)}:${billId}`
    : null;
}

export function readPaymentAttempt(key) {
  if (!key) throw new Error("Chưa xác minh được tài khoản hoặc hóa đơn để lưu giao dịch.");
  let saved;
  try {
    const raw = sessionStorage.getItem(key);
    if (raw === null) return null;
    saved = JSON.parse(raw);
  } catch {
    throw new Error("Không thể đọc giao dịch đã lưu. Kiểm tra bộ nhớ phiên trước khi xác nhận thu tiền.");
  }
  if (!saved || !["CASH", "BANK_TRANSFER"].includes(saved.method)
      || typeof saved.idempotencyKey !== "string" || !/^[!-~]{1,64}$/.test(saved.idempotencyKey)
      || (Object.hasOwn(saved, "expectedAmount") && !amount(saved.expectedAmount))
      || (saved.referenceCode !== null && (saved.method !== "BANK_TRANSFER"
        || typeof saved.referenceCode !== "string" || !saved.referenceCode.length
        || saved.referenceCode.length > 128))) {
    throw new Error("Giao dịch đã lưu không hợp lệ. Hãy kiểm tra hóa đơn trước khi xác nhận thu tiền.");
  }
  return saved;
}

export function persistPaymentAttempt(key, command) {
  try {
    sessionStorage.setItem(key, JSON.stringify(command));
  } catch {
    throw new Error("Không thể lưu mã giao dịch. Chưa gửi xác nhận thu tiền, vui lòng kiểm tra bộ nhớ phiên.");
  }
}

export function clearConfirmedPayment(key) {
  try {
    sessionStorage.removeItem(key);
  } catch {
    // Safe to retain: a server-confirmed PAID bill cannot dispatch another payment.
  }
}

export function clearRejectedPayment(key, rejectedKey) {
  if (readPaymentAttempt(key)?.idempotencyKey !== rejectedKey) return;
  sessionStorage.removeItem(key);
}

export function verifiedPaymentBill(receipt, billId, sessionCode, command, expectedAmount) {
  const { bill, payment } = receipt ?? {};
  if (bill?.id !== billId || bill.sessionCode !== sessionCode || bill.status !== "PAID"
      || !timestamp(bill.paidAt) || !amount(bill.totalAmount)
      || BigInt(bill.totalAmount) !== BigInt(expectedAmount)
      || ![bill.billCode, bill.tableName, bill.creatorName].every(value => typeof value === "string")
      || !Array.isArray(bill.items) || !bill.items.length || !bill.items.every(item =>
        item && id(item.orderItemId) && typeof item.itemName === "string" && amount(item.unitPrice)
        && Number.isSafeInteger(item.quantity) && item.quantity > 0 && amount(item.lineTotal)
        && BigInt(item.unitPrice) * BigInt(item.quantity) === BigInt(item.lineTotal))
      || bill.items.reduce((total, item) => total + BigInt(item.lineTotal), 0n) !== BigInt(bill.totalAmount)
      || !id(payment?.id) || payment.method !== command.method
      || payment.referenceCode !== normalizePaymentReference(command.method, command.referenceCode)
      || payment.amount !== bill.totalAmount || !timestamp(payment.receivedAt)) {
    throw new Error("Chưa xác minh được kết quả thu tiền. Đối soát lại hóa đơn trước khi tiếp tục.");
  }
  return bill;
}
