import { test } from "node:test";
import assert from "node:assert/strict";
import { passwordErrors, profilePatch } from "../src/account/profileValidation.js";
import { passwordStatusFrom } from "../src/auth/authApi.js";

test("PATCH preserves snapshot version, exact text, omissions and decimal precision", () => {
  const snapshot = { version: "9007199254740993", name: "Tên cũ", address: null, phone: null };
  assert.deepEqual(profilePatch(snapshot, { name: "  Nguyễn O'Ánh  ", role: "ADMIN", salary: "0" }), {
    patch: { version: snapshot.version, name: "  Nguyễn O'Ánh  " }, errors: {},
  });
  assert.equal(profilePatch(snapshot, {}).patch, null);
  assert.equal(profilePatch(snapshot, { name: snapshot.name }).patch, null);
  assert.deepEqual(Object.keys(profilePatch(snapshot, { address: null }).errors), []);
  assert.deepEqual(Object.keys(profilePatch(snapshot, { address: "" }).errors), ["address"]);
});

test("Profile limits count code points and use Java whitespace plus space characters", () => {
  const snapshot = { version: "7" };
  for (const [field, max] of Object.entries({ name: 160, address: 255, phone: 20 })) {
    assert.deepEqual(profilePatch(snapshot, { [field]: "😀".repeat(max) }).errors, {});
    assert.ok(profilePatch(snapshot, { [field]: "😀".repeat(max + 1) }).errors[field]);
    for (const value of ["", " \t\n", "\u00a0\u2007\u202f", "\u001c", null, 1]) {
      assert.ok(profilePatch(snapshot, { [field]: value }).errors[field]);
    }
    assert.deepEqual(profilePatch(snapshot, { [field]: "\ufeff" }).errors, {});
  }
});

test("Password policy matches Java UTF-16 length, UTF-8 bytes and Unicode case/digits", () => {
  const validate = value => passwordErrors({ currentPassword: "OldPassword1", newPassword: value, confirmNewPassword: value });
  for (const password of ["Password1", "ĐổiMậtKhẩu١", " Aa1    ", "Aá1" + "á".repeat(33)]) assert.deepEqual(validate(password), {});
  for (const password of ["aA1", "password1", "PASSWORD1", "Password", "OldPassword1", "Aá1" + "á".repeat(35), "Aa1" + "x".repeat(62), "𝔸aaaaaa1"]) {
    assert.ok(validate(password).newPassword, password);
  }
  assert.ok(passwordErrors({ currentPassword: "x".repeat(257), newPassword: "Password1", confirmNewPassword: "Different1" }).currentPassword);
  assert.ok(passwordErrors({ currentPassword: "Password0", newPassword: "Password1", confirmNewPassword: "Password1 " }).confirmNewPassword);
});

test("Expiry comes from top-level server fields without computing a client deadline", () => {
  const status = { mustChangePassword: true, passwordChangedAt: "2026-10-01T00:00:00Z", passwordExpiresAt: "2026-10-31T00:00:00Z" };
  assert.deepEqual(passwordStatusFrom({ token: "test-only", ...status }), status);
  assert.equal(passwordStatusFrom({ user: status }), null);
});
