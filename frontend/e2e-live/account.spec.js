import { expect, test } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { mkdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { loginFromPage, profileFromHeader } from "../tests/account-fixtures.js";

// No page.route: every application request uses the actual Spring Boot/MySQL server.
const originalPassword = process.env.PROFILE_LIVE_PASSWORD;
const nextPassword = process.env.PROFILE_LIVE_NEW_PASSWORD;
const api = process.env.PROFILE_LIVE_API_URL;
if (!originalPassword || !nextPassword || !api || !process.env.PROFILE_LIVE_SCHEMA?.endsWith("_test")) throw new Error("Run via scripts/profile-browser-live.py");
function sql(operation, account) {
  return JSON.parse(execFileSync(process.env.PYTHON_EXECUTABLE || "python", [fileURLToPath(new URL("../../scripts/profile-browser-live.py", import.meta.url)), "sql", operation, account], { encoding: "utf-8" }));
}
async function signIn(page, account, password = originalPassword) {
  await loginFromPage(page, "pm_profile_" + account, password);
  await profileFromHeader(page);
}
async function capture(page, name) {
  const directory = new URL("../../docs/handover/evidence/dt2-28/", import.meta.url);
  await mkdir(directory, { recursive: true });
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.evaluate(async () => {
    await document.fonts.ready;
    window.scrollTo({ top: 0, behavior: "instant" });
    await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
  });
  await page.screenshot({ path: fileURLToPath(new URL("live-" + name + ".png", directory)), fullPage: true });
}
async function fillPasswords(page, current, next, confirm = next) {
  await page.getByLabel("Mật khẩu hiện tại", { exact: true }).fill(current);
  await page.getByLabel("Mật khẩu mới", { exact: true }).fill(next);
  await page.getByLabel("Xác nhận mật khẩu mới", { exact: true }).fill(confirm);
}

for (const role of ["admin", "staff"]) test(`${role}: Header to Profile, update, reload and SQL persistence`, async ({ page }) => {
  await signIn(page, role);
  const before = sql("snapshot", role);
  await page.getByRole("button", { name: "Cập nhật thông tin" }).click();
  const name = "Nguyễn Ánh " + role;
  await page.getByLabel("Họ và tên", { exact: true }).fill(name);
  await page.getByLabel("Địa chỉ", { exact: true }).fill("Đường O'Connell, Đà Nẵng");
  await page.getByLabel("Điện thoại", { exact: true }).fill("+84 900 000 123");
  const response = page.waitForResponse(response => response.url().endsWith("/profiles/me") && response.request().method() === "PATCH");
  await page.getByRole("button", { name: "Lưu thay đổi" }).click();
  expect((await response).status()).toBe(200);
  await expect(page.getByRole("button", { name: "Tài khoản " + name, exact: true })).toBeVisible();
  await page.reload();
  await expect(page.locator(".personnel-fields")).toContainText(name);
  const after = sql("snapshot", role);
  expect(after).toMatchObject({ name, address: "Đường O'Connell, Đà Nẵng", phone: "+84 900 000 123", version: String(BigInt(before.version) + 1n) });
  await capture(page, role + "-profile");
});

test("Cancel performs no PATCH and SQL remains unchanged", async ({ page }) => {
  await signIn(page, "admin");
  const before = sql("snapshot", "admin");
  let patches = 0;
  page.on("request", request => { if (request.method() === "PATCH") patches++; });
  await page.getByRole("button", { name: "Cập nhật thông tin" }).click();
  await page.getByLabel("Họ và tên", { exact: true }).fill("Discarded synthetic draft");
  await page.getByRole("button", { name: "Hủy thay đổi" }).click();
  expect(patches).toBe(0);
  expect(sql("snapshot", "admin")).toEqual(before);
});

test("password replacement is usable, old bearer is revoked and only new password logs in", async ({ page, request }) => {
  await signIn(page, "staff");
  const before = sql("snapshot", "staff");
  const oldToken = await page.evaluate(() => sessionStorage.getItem("smart_cafe_token"));
  await page.getByRole("link", { name: "Đổi mật khẩu", exact: true }).click();
  await fillPasswords(page, originalPassword, nextPassword);
  const changed = page.waitForResponse(response => response.url().endsWith("/profiles/me/password"));
  await page.getByRole("button", { name: "Cập nhật mật khẩu", exact: true }).click();
  expect((await changed).status()).toBe(200);
  await expect(page).toHaveURL(/#account$/);
  await expect(page.getByRole("status").filter({ hasText: "Đổi mật khẩu thành công" })).toBeVisible();
  await expect(page.locator(".personnel-fields")).toBeVisible();
  const newToken = await page.evaluate(() => sessionStorage.getItem("smart_cafe_token"));
  expect(Boolean(newToken) && newToken !== oldToken).toBe(true);
  expect((await request.get(api + "/api/v1/profiles/me", { headers: { Authorization: "Bearer " + oldToken } })).status()).toBe(401);
  expect((await request.get(api + "/api/v1/profiles/me", { headers: { Authorization: "Bearer " + newToken } })).status()).toBe(200);
  expect((await request.post(api + "/api/v1/auth/login", { data: { username: "pm_profile_staff", password: nextPassword } })).status()).toBe(200);
  expect((await request.post(api + "/api/v1/auth/login", { data: { username: "pm_profile_staff", password: originalPassword } })).status()).toBe(401);
  const after = sql("snapshot", "staff");
  expect(after.authVersion).toBe(String(BigInt(before.authVersion) + 1n));
  expect(after.version).toBe(String(BigInt(before.version) + 1n));
  await capture(page, "password-success");
});

test("incorrect current password, confirmation mismatch and reuse leave SQL unchanged", async ({ page }) => {
  await signIn(page, "admin");
  const before = sql("snapshot", "admin");
  await page.getByRole("link", { name: "Đổi mật khẩu", exact: true }).click();
  await fillPasswords(page, "WrongPassword9", nextPassword);
  const response = page.waitForResponse(response => response.url().endsWith("/profiles/me/password"));
  await page.getByRole("button", { name: "Cập nhật mật khẩu", exact: true }).click();
  expect((await response).status()).toBe(400);
  await expect(page.getByRole("alert")).toContainText("không đúng");
  await fillPasswords(page, originalPassword, nextPassword, "DifferentPassword9");
  await page.getByRole("button", { name: "Cập nhật mật khẩu", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("khớp chính xác");
  await fillPasswords(page, originalPassword, originalPassword);
  await page.getByRole("button", { name: "Cập nhật mật khẩu", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("phải khác");
  expect(sql("snapshot", "admin")).toEqual(before);
});

test("concurrent SQL writer causes real 409 without overwriting the latest row", async ({ page }) => {
  await signIn(page, "admin");
  await page.getByRole("button", { name: "Cập nhật thông tin" }).click();
  await page.getByLabel("Họ và tên", { exact: true }).fill("Stale browser draft");
  sql("concurrent", "admin");
  const beforeSubmit = sql("snapshot", "admin");
  const response = page.waitForResponse(response => response.url().endsWith("/profiles/me") && response.request().method() === "PATCH");
  await page.getByRole("button", { name: "Lưu thay đổi" }).click();
  expect((await response).status()).toBe(409);
  await expect(page.getByRole("alert")).toContainText("kiểm tra");
  await expect(page.locator(".personnel-fields")).toContainText("Concurrent synthetic owner");
  expect(sql("snapshot", "admin")).toEqual(beforeSubmit);
  await capture(page, "conflict");
});

test("server-expired account warns, changes password, clears warning and refreshes", async ({ page }) => {
  await signIn(page, "expired");
  await expect(page.getByRole("complementary", { name: "Cảnh báo mật khẩu" })).toBeVisible();
  await page.reload();
  await expect(page.getByRole("complementary", { name: "Cảnh báo mật khẩu" })).toBeVisible();
  await capture(page, "expiry");
  await page.getByRole("complementary", { name: "Cảnh báo mật khẩu" }).getByRole("link", { name: "Đổi mật khẩu" }).click();
  await fillPasswords(page, originalPassword, nextPassword);
  await page.getByRole("button", { name: "Cập nhật mật khẩu", exact: true }).click();
  await expect(page).toHaveURL(/#account$/);
  await expect(page.getByRole("complementary", { name: "Cảnh báo mật khẩu" })).toHaveCount(0);
  await expect(page.locator(".personnel-fields")).toBeVisible();
  expect(sql("snapshot", "expired")).toMatchObject({ mustChangePassword: 0, authVersion: "1" });
});

test("anonymous and CUSTOMER are denied by the actual personnel API and UI", async ({ page, request }) => {
  expect((await request.get(api + "/api/v1/profiles/me")).status()).toBe(401);
  await page.goto("/#account");
  await expect(page.getByRole("heading", { name: "Vui lòng đăng nhập" })).toBeVisible();
  await loginFromPage(page, "pm_profile_customer", originalPassword);
  const token = await page.evaluate(() => sessionStorage.getItem("smart_cafe_token"));
  expect((await request.get(api + "/api/v1/profiles/me", { headers: { Authorization: "Bearer " + token } })).status()).toBe(403);
  await page.goto("/#account-password");
  await expect(page.getByRole("heading", { name: "Không có quyền truy cập" })).toBeVisible();
  await expect(page.locator(".personnel-fields")).toHaveCount(0);
});
