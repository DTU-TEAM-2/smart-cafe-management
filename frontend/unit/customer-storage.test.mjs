import test from "node:test";
import assert from "node:assert/strict";
import { getOrCreateOrderAttempt, readOrderAttempt, finishOrderAttempt, getOrCreateAttempt, clearAttempt } from "../src/customer/customerSession.js";

test("orders require durable storage while service requests can use memory", () => {
  const values = new Map();
  globalThis.sessionStorage = {
    getItem: (key) => values.get(key) || null,
    setItem: () => { throw new Error("QuotaExceededError"); },
    removeItem: () => { throw new Error("Storage disabled"); },
  };
  const session = { sessionId: "storage-test", token: "test-only", clientStorageId: "a" };
  const payload = [{ menuItemId: "9007199254740993", quantity: 1 }];
  assert.throws(() => getOrCreateOrderAttempt(session, payload, [], []), /Không thể lưu mã gửi đơn/);
  assert.equal(readOrderAttempt(session), null);
  for (const operation of ["waiter", "bill"]) {
    const key = getOrCreateAttempt(session, operation);
    assert.equal(getOrCreateAttempt(session, operation), key);
    clearAttempt(session, operation);
    assert.notEqual(getOrCreateAttempt(session, operation), key);
  }
  globalThis.sessionStorage.setItem = (key, value) => values.set(key, value);
  const first = getOrCreateOrderAttempt(session, payload, [], []);
  assert.deepEqual(readOrderAttempt(session).payload, first.payload);
  assert.equal(getOrCreateOrderAttempt(session, [{ menuItemId: "2", quantity: 2 }]).idempotencyKey, first.idempotencyKey);
  finishOrderAttempt(session, first, [], []);
  assert.equal(readOrderAttempt(session), null);
  assert.notEqual(getOrCreateOrderAttempt(session, first.payload, [], []).idempotencyKey, first.idempotencyKey);
  delete globalThis.sessionStorage;
});
