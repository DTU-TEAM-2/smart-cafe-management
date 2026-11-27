import { expect, test } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mkdir } from "node:fs/promises";
import { productMocks, loginFromPage, profileFromHeader } from "./account-fixtures.js";

const evidence = new URL("../../docs/handover/evidence/dt2-28/", import.meta.url);
async function screenshot(page, name) {
  await mkdir(evidence, { recursive: true });
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.evaluate(async () => {
    await document.fonts.ready;
    window.scrollTo({ top: 0, behavior: "instant" });
    await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
  });
  await page.screenshot({ path: new URL(name, evidence).pathname.replace(/^\/(\w:)/, "$1"), fullPage: true });
}
async function open(page, overrides) {
  const api = await productMocks(page, overrides);
  await loginFromPage(page);
  await profileFromHeader(page);
  return api;
}
async function fillPasswords(page, current = "OldPassword1", next = "NewPassword2", confirm = next) {
  await page.getByLabel("Mật khẩu hiện tại", { exact: true }).fill(current);
  await page.getByLabel("Mật khẩu mới", { exact: true }).fill(next);
  await page.getByLabel("Xác nhận mật khẩu mới", { exact: true }).fill(confirm);
}

for (const role of ["ADMIN", "STAFF"]) test(`${role} profile uses one layout and only allows inline name/address/phone`, async ({ page }, info) => {
  const api = await open(page, { role });
  await expect(page.getByRole("banner")).toHaveCount(1);
  await expect(page.getByRole("contentinfo")).toHaveCount(1);
  await expect(page.locator(".personnel-fields")).toContainText("8.500.000");
  await expect(page.locator(".personnel-fields")).toContainText("15/03/1998");
  await expect(page.locator(".personnel-fields")).not.toContainText("synthetic@example.test");
  await expect(page.locator(".personnel-page input")).toHaveCount(0);
  if (info.project.name === "desktop") { await page.setViewportSize({ width: 1440, height: 900 }); await screenshot(page, `profile-${role.toLowerCase()}.png`); }
  await page.getByRole("button", { name: "Cập nhật thông tin" }).click();
  await expect(page.getByLabel("Họ và tên", { exact: true })).toBeFocused();
  await expect(page.locator(".personnel-page input")).toHaveCount(3);
  await page.getByLabel("Họ và tên", { exact: true }).fill("Nguyễn Ánh O'An");
  await page.getByLabel("Địa chỉ", { exact: true }).fill("  Đường O'Connell, Đà Nẵng  ");
  await page.getByLabel("Điện thoại", { exact: true }).fill("+84 900 123 456");
  if (info.project.name === "desktop" && role === "STAFF") await screenshot(page, "profile-edit.png");
  await page.getByRole("button", { name: "Lưu thay đổi" }).click();
  await expect(page.getByRole("status")).toContainText("cập nhật thành công");
  await expect(page.getByRole("button", { name: "Tài khoản Nguyễn Ánh O'An" })).toBeVisible();
  expect(api.calls.filter(call => call.method === "PATCH")[0].body).toEqual({ version: "7", name: "Nguyễn Ánh O'An", address: "  Đường O'Connell, Đà Nẵng  ", phone: "+84 900 123 456" });
  if (info.project.name === "desktop" && role === "STAFF") await screenshot(page, "profile-success.png");
  await page.reload();
  await expect(page.locator(".personnel-fields")).toContainText("Nguyễn Ánh O'An");
});

test("Cancel restores snapshot and never writes; leaving a dirty form requires a choice", async ({ page }) => {
  const api = await open(page);
  await page.getByRole("button", { name: "Cập nhật thông tin" }).click();
  await page.getByLabel("Họ và tên", { exact: true }).fill("Bản nháp");
  await page.getByRole("link", { name: "Bỏ qua điều hướng", exact: true }).focus();
  await page.keyboard.press("Enter");
  await expect(page.getByRole("main")).toBeFocused();
  await expect(page).toHaveURL(/#account$/);
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await page.getByRole("link", { name: "LECKER Coffee Cafe Trang chủ", exact: true }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByLabel("Họ và tên", { exact: true })).toHaveValue("Bản nháp");
  await page.getByLabel("Họ và tên", { exact: true }).fill("   ");
  await page.getByRole("link", { name: "LECKER Coffee Cafe Trang chủ", exact: true }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.getByRole("button", { name: "Tiếp tục chỉnh sửa", exact: true }).click();
  await page.getByRole("button", { name: "Hủy thay đổi", exact: true }).click();
  await expect(page.getByRole("button", { name: "Cập nhật thông tin" })).toBeFocused();
  expect(api.calls.filter(call => call.method === "PATCH")).toHaveLength(0);
  await page.getByRole("link", { name: "Đổi mật khẩu", exact: true }).click();
  await expect(page.getByLabel("Mật khẩu hiện tại", { exact: true })).toBeFocused();
  await page.getByRole("button", { name: "Hủy", exact: true }).click();
  await expect(page).toHaveURL(/#account$/);
  await page.goBack();
  await expect(page).toHaveURL(/#account-password$/);
  await page.goForward();
  await expect(page).toHaveURL(/#account$/);
});

test("logout from the header confirms before discarding a dirty profile", async ({ page }) => {
  await open(page);
  await page.getByRole("button", { name: "Cập nhật thông tin" }).click();
  await page.getByLabel("Họ và tên", { exact: true }).fill("Bản nháp");
  const trigger = page.getByRole("button", { name: /^Tài khoản / });
  const signOut = page.getByRole("button", { name: "Đăng xuất", exact: true });
  const confirmation = page.getByRole("dialog", { name: "Rời trang đang chỉnh sửa?" });
  await trigger.click();
  await signOut.click();
  await confirmation.getByRole("button", { name: "Tiếp tục chỉnh sửa" }).click();
  await expect(page.getByLabel("Họ và tên", { exact: true })).toHaveValue("Bản nháp");
  await trigger.click();
  await signOut.click();
  await confirmation.getByRole("button", { name: "Rời trang" }).click();
  await expect(page).toHaveURL(/#login$/);
});

test("validation has field links, focus and no mutation", async ({ page }, info) => {
  const api = await open(page);
  await page.getByRole("button", { name: "Cập nhật thông tin" }).click();
  await page.getByLabel("Họ và tên", { exact: true }).fill("   ");
  await page.getByLabel("Điện thoại", { exact: true }).fill("x".repeat(21));
  await page.getByRole("button", { name: "Lưu thay đổi" }).click();
  await expect(page.getByRole("alert")).toBeFocused();
  await expect(page.getByLabel("Họ và tên", { exact: true })).toHaveAttribute("aria-invalid", "true");
  await page.getByRole("link", { name: "Họ và tên không được để trống." }).click();
  await expect(page.getByLabel("Họ và tên", { exact: true })).toBeFocused();
  expect(api.calls.filter(call => call.method === "PATCH")).toHaveLength(0);
  if (info.project.name === "desktop") await screenshot(page, "profile-validation.png");
});

test("server capability and decimal precision remain authoritative in the product view", async ({ page }) => {
  await open(page, { canUpdateProfile: false, salary: "999999999999999999", version: "9007199254740993" });
  await expect(page.getByRole("button", { name: "Cập nhật thông tin" })).toHaveCount(0);
  await expect(page.locator(".personnel-fields")).toContainText("999.999.999.999.999.999");
});

test("Header and Profile use the replacement in memory when browser storage rejects writes", async ({ page }) => {
  const api = await productMocks(page);
  await page.addInitScript(() => {
    const original = Storage.prototype.setItem;
    Storage.prototype.setItem = function(key, value) {
      if (key.startsWith("smart_cafe_")) throw new DOMException("Test storage quota", "QuotaExceededError");
      return original.call(this, key, value);
    };
  });
  await loginFromPage(page);
  await profileFromHeader(page);
  await page.getByRole("link", { name: "Đổi mật khẩu", exact: true }).click();
  await fillPasswords(page);
  await page.getByRole("button", { name: "Cập nhật mật khẩu", exact: true }).click();
  await expect(page).toHaveURL(/#account$/);
  await expect(page.locator(".personnel-fields")).toBeVisible();
  await page.evaluate(() => window.dispatchEvent(new Event("focus")));
  await expect(page.getByRole("button", { name: "Tài khoản Nguyễn Minh An", exact: true })).toBeVisible();
  expect(api.calls.filter(call => call.method === "GET").at(-1).authorization === `Bearer ${api.token()}`).toBe(true);
});

test("leaving the password page discards secrets after explicit confirmation", async ({ page }) => {
  const api = await open(page);
  await page.getByRole("link", { name: "Đổi mật khẩu", exact: true }).click();
  await fillPasswords(page);
  await page.getByRole("link", { name: "LECKER Coffee Cafe Trang chủ", exact: true }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.getByRole("button", { name: "Rời trang", exact: true }).click();
  await expect(page).toHaveURL(/#top$/);
  await page.goBack();
  await expect(page.getByLabel("Mật khẩu hiện tại", { exact: true })).toHaveValue("");
  expect(api.calls.filter(call => call.method === "POST")).toHaveLength(0);
});

test("409 reloads the latest profile and requires a new edit snapshot", async ({ page }, info) => {
  await open(page);
  let patches = 0;
  const { staffProfile } = await import("./account-fixtures.js");
  await page.route("**/api/v1/profiles/me", route => {
    if (route.request().method() === "PATCH") { patches++; return route.fulfill({ status: 409, json: { code: "PROFILE_CONFLICT" } }); }
    return route.fulfill({ json: { ...staffProfile, version: "8", name: "Dữ liệu mới từ máy chủ" } });
  });
  await page.getByRole("button", { name: "Cập nhật thông tin" }).click();
  await page.getByLabel("Họ và tên", { exact: true }).fill("Bản nháp cũ");
  await page.getByRole("button", { name: "Lưu thay đổi" }).click();
  await expect(page.locator(".personnel-fields")).toContainText("Dữ liệu mới từ máy chủ");
  await expect(page.getByRole("alert")).toContainText("kiểm tra");
  await expect(page.locator(".personnel-page input")).toHaveCount(0);
  expect(patches).toBe(1);
  if (info.project.name === "desktop") await screenshot(page, "profile-conflict.png");
});

test("password warning survives reload; change rotates bearer and returns to Profile", async ({ page }, info) => {
  const api = await open(page, { mustChangePassword: true });
  await expect(page.getByRole("complementary", { name: "Cảnh báo mật khẩu" })).toBeVisible();
  await page.reload();
  await expect(page.getByRole("complementary", { name: "Cảnh báo mật khẩu" })).toBeVisible();
  if (info.project.name === "desktop") await screenshot(page, "profile-expiry.png");
  await page.getByRole("complementary", { name: "Cảnh báo mật khẩu" }).getByRole("link", { name: "Đổi mật khẩu" }).click();
  await expect(page.getByLabel("Mật khẩu hiện tại", { exact: true })).toHaveAttribute("type", "password");
  await page.getByRole("button", { name: "Hiện mật khẩu hiện tại", exact: true }).click();
  await expect(page.getByLabel("Mật khẩu hiện tại", { exact: true })).toHaveAttribute("type", "text");
  await page.getByRole("button", { name: "Ẩn mật khẩu hiện tại", exact: true }).click();
  if (info.project.name === "desktop") await screenshot(page, "change-password.png");
  await fillPasswords(page, "OldPassword1", "NewPassword2", "DoesNotMatch1");
  await page.getByRole("button", { name: "Cập nhật mật khẩu", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("khớp chính xác");
  await page.getByLabel("Xác nhận mật khẩu mới", { exact: true }).fill("NewPassword2");
  await page.getByRole("button", { name: "Cập nhật mật khẩu", exact: true }).click();
  await expect(page).toHaveURL(/#account$/);
  await expect(page.getByRole("status").filter({ hasText: "Đổi mật khẩu thành công" })).toBeVisible();
  await expect(page.getByRole("complementary", { name: "Cảnh báo mật khẩu" })).toHaveCount(0);
  await expect(page.getByLabel("Mật khẩu hiện tại", { exact: true })).toHaveCount(0);
  expect(api.calls.filter(call => call.method === "POST")).toHaveLength(1);
  const lastGet = api.calls.filter(call => call.method === "GET").at(-1);
  expect(lastGet.authorization === `Bearer ${api.token()}`).toBe(true);
  if (info.project.name === "desktop") await screenshot(page, "password-success.png");
});

test("loading, nullable fields, unavailable and retry are explicit", async ({ page }, info) => {
  await open(page, { address: null, phone: null, salary: null, birthday: null, gender: null });
  await expect(page.locator(".personnel-fields").getByText("Chưa cập nhật", { exact: true })).toHaveCount(5);
  if (info.project.name === "desktop") await screenshot(page, "profile-empty.png");
  let release;
  const pending = new Promise(resolve => { release = resolve; });
  await page.route("**/api/v1/profiles/me", async route => { await pending; return route.fulfill({ status: 503, json: { code: "PROFILE_UNAVAILABLE" } }); });
  await page.reload();
  await expect(page.getByRole("status")).toContainText("Đang tải hồ sơ");
  if (info.project.name === "desktop") await screenshot(page, "profile-loading.png");
  release();
  await expect(page.getByRole("alert")).toContainText("tạm thời");
  if (info.project.name === "desktop") await screenshot(page, "profile-error.png");
  const { staffProfile } = await import("./account-fixtures.js");
  await page.route("**/api/v1/profiles/me", route => route.fulfill({ json: staffProfile }));
  await page.getByRole("button", { name: "Thử lại", exact: true }).click();
  await expect(page.locator(".personnel-fields")).toContainText("Nguyễn Minh An");
});

test("responsive layouts and axe checks cover view/edit/password states", async ({ page }, info) => {
  const errors = [];
  const consoleErrors = [];
  const guestDenials = new Map();
  page.on("pageerror", error => errors.push(error.message));
  page.on("response", response => {
    if (response.status() === 401 && new URL(response.url()).pathname === "/api/v1/home/account"
      && !response.request().headers().authorization) guestDenials.set(response.url(), (guestDenials.get(response.url()) || 0) + 1);
  });
  page.on("console", message => { if (message.type() === "error") consoleErrors.push({ text: message.text(), url: message.location().url }); });
  await open(page);
  const sizes = info.project.name === "desktop" ? [[1920,1080], [1440,900], [1366,768], [1024,768], [768,1024], [390,844]] : [[390,844]];
  for (const [width, height] of sizes) {
    await page.setViewportSize({ width, height });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await expect(page.getByRole("button", { name: "Cập nhật thông tin" })).toBeVisible();
    if (info.project.name === "desktop") await screenshot(page, `profile-${width}x${height}.png`);
  }
  for (const state of ["view", "edit", "password"]) {
    if (state === "edit") await page.getByRole("button", { name: "Cập nhật thông tin" }).click();
    if (state === "password") { await page.getByRole("button", { name: "Hủy thay đổi" }).click(); await page.getByRole("link", { name: "Đổi mật khẩu", exact: true }).click(); }
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  }
  expect(errors).toEqual([]);
  expect(guestDenials.size).toBe(1);
  // Only the observed anonymous Header denial is expected; authenticated or additional failures still fail.
  const unexpected = consoleErrors.filter(error => {
    const count = guestDenials.get(error.url) || 0;
    if (!error.text.includes("status of 401") || count === 0) return true;
    guestDenials.set(error.url, count - 1);
    return false;
  });
  expect(unexpected).toEqual([]);
  if (info.project.name === "desktop") await screenshot(page, "password-mobile.png");
});
