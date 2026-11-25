import test from "node:test";
import assert from "node:assert/strict";
import { getOrCreateOrderAttempt, readOrderAttempt, getOrCreateAttempt, clearAttempt } from "../src/customer/customerSession.js";

test("attempts survive failed storage writes and stale storage cannot override memory", () => {
  const values = new Map();
  globalThis.sessionStorage = {
    getItem: (key) => values.get(key) || null,
    setItem: () => { throw new Error("QuotaExceededError"); },
    removeItem: () => { throw new Error("Storage disabled"); },
  };
  const session = { sessionId: "storage-test", token: "test-only", clientStorageId: "a" };
  const first = getOrCreateOrderAttempt(session, [{ menuItemId: "9007199254740993", quantity: 1 }]);
  assert.deepEqual(readOrderAttempt(session).payload, first.payload);
  assert.equal(getOrCreateOrderAttempt(session, [{ menuItemId: "2", quantity: 2 }]).idempotencyKey, first.idempotencyKey);
  for (const operation of ["waiter", "bill"]) {
    const key = getOrCreateAttempt(session, operation);
    assert.equal(getOrCreateAttempt(session, operation), key);
    clearAttempt(session, operation);
    assert.notEqual(getOrCreateAttempt(session, operation), key);
  }
  values.set("lecker_customer_attempt:order:storage-test:a", JSON.stringify(first));
  clearAttempt(session, "order");
  assert.equal(readOrderAttempt(session), null);
  assert.notEqual(getOrCreateOrderAttempt(session, first.payload).idempotencyKey, first.idempotencyKey);
  delete globalThis.sessionStorage;
});
