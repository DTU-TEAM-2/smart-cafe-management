import { expect, test } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mkdir } from "node:fs/promises";
import { productMocks, loginFromPage, profileFromHeader, staffProfile } from "./account-fixtures.js";

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

test("cancelling account button navigation leaves browser history untouched", async ({ page }) => {
  await open(page);
  await page.getByRole("button", { name: "Cập nhật thông tin" }).click();
  const name = page.getByLabel("Họ và tên", { exact: true });
  await name.fill("Bản nháp");
  const historyLength = await page.evaluate(() => history.length);
  await page.getByRole("button", { name: /^Tài khoản / }).click();
  await page.getByRole("button", { name: "Bán hàng theo bàn", exact: true }).click();
  await expect(page).toHaveURL(/#account$/);
  await page.getByRole("button", { name: "Tiếp tục chỉnh sửa" }).click();
  await expect(name).toHaveValue("Bản nháp");
  expect(await page.evaluate(() => history.length)).toBe(historyLength);
  await page.getByRole("button", { name: "Hủy thay đổi" }).click();
  await page.goBack();
  await expect(page).toHaveURL(/#staff$/);
});

for (const fallback of [false, true]) test(`cancelling browser Back preserves both previous entry and draft (${fallback ? "history.state" : "native"})`, async ({ page }) => {
  if (fallback) await page.addInitScript(() => {
    Object.defineProperty(window, "navigation", { value: undefined });
    history.replaceState({ agencyState: "preserved" }, "");
  });
  const api = await productMocks(page);
  await page.addInitScript(token => sessionStorage.setItem("smart_cafe_token", token), api.token());
  await page.goto("/#top");
  if (fallback) {
    expect(await page.evaluate(() => window.navigation)).toBeUndefined();
    expect(await page.evaluate(() => history.state?.agencyState)).toBe("preserved");
  }
  await profileFromHeader(page);
  await page.getByRole("button", { name: "Cập nhật thông tin" }).click();
  const name = page.getByLabel("Họ và tên", { exact: true });
  await name.fill("Bản nháp");
  await page.goBack();
  const confirmation = page.getByRole("dialog", { name: "Rời trang đang chỉnh sửa?" });
  await confirmation.getByRole("button", { name: "Tiếp tục chỉnh sửa" }).click();
  await expect(page).toHaveURL(/#account$/);
  await expect(name).toHaveValue("Bản nháp");
  await page.goBack();
  await confirmation.getByRole("button", { name: "Rời trang" }).click();
  await expect(page).toHaveURL(/#top$/);
  if (fallback) expect(await page.evaluate(() => history.state?.agencyState)).toBe("preserved");
  await page.goForward();
  await expect(page).toHaveURL(/#account$/);
  await expect(name).toHaveCount(0);
  if (fallback) expect(await page.evaluate(() => history.state?.__leckerHashEntry)).toBeGreaterThan(0);
});

test("a late profile response cannot override a denied staff recheck", async ({ page }) => {
  await productMocks(page);
  let socket, sessions = 0, recheck = false;
  const profileGate = Promise.withResolvers();
  const profileStarted = Promise.withResolvers();
  const profileFinished = Promise.withResolvers();
  const accountGate = Promise.withResolvers();
  const accountStarted = Promise.withResolvers();
  await page.routeWebSocket("**/api/v1/news/events", connection => {
    sessions++;
    connection.onMessage(() => {
      socket = connection;
      connection.send(JSON.stringify({ type: "READY" }));
    });
  });
  await page.route("**/api/v1/home/account", async route => {
    if (!recheck) return route.fallback();
    accountStarted.resolve();
    await accountGate.promise;
    return route.fulfill({ status: 403, json: {} });
  });
  await page.route("**/api/v1/profiles/me", async route => {
    profileStarted.resolve();
    await profileGate.promise;
    await route.fulfill({ json: { ...staffProfile, name: "Hồ sơ tải chậm" } });
    profileFinished.resolve();
  });
  await loginFromPage(page);
  await expect.poll(() => Boolean(socket)).toBe(true);
  await page.getByRole("button", { name: /^Tài khoản / }).click();
  await page.getByRole("button", { name: "Thông tin tài khoản", exact: true }).click();
  await profileStarted.promise;
  recheck = true;
  await socket.close({ code: 1008 });
  await accountStarted.promise;
  await expect(page.getByRole("button", { name: /^Thông báo/ })).toHaveCount(0);
  profileGate.resolve();
  await profileFinished.promise;
  await expect(page.getByRole("status")).toContainText("Đang xác minh tài khoản");
  await expect(page.locator(".personnel-fields")).toHaveCount(0);
  accountGate.resolve();
  await expect(page.getByRole("link", { name: "Đăng nhập", exact: true }).first()).toBeVisible();
  await expect(page.getByRole("button", { name: /^Tài khoản / })).toHaveCount(0);
  await expect(page.locator(".personnel-fields")).toHaveCount(0);
  expect(sessions).toBe(1);
});

test("an older identity response cannot replace a confirmed profile edit", async ({ page }) => {
  await productMocks(page);
  let denyingFeed = false;
  const stopFeed = Promise.withResolvers();
  const checking = Promise.withResolvers();
  const confirmIdentity = Promise.withResolvers();
  const patchStarted = Promise.withResolvers();
  const releasePatch = Promise.withResolvers();
  await page.route("**/api/v1/home/notifications", async route => {
    await stopFeed.promise;
    denyingFeed = true;
    return route.fulfill({ status: 403, json: {} });
  });
  await page.route("**/api/v1/home/account", async route => {
    if (!denyingFeed) return route.fallback();
    checking.resolve();
    await confirmIdentity.promise;
    return route.fulfill({ json: staffProfile });
  });
  await page.route("**/api/v1/profiles/me", async route => {
    if (route.request().method() !== "PATCH") return route.fallback();
    patchStarted.resolve();
    await releasePatch.promise;
    return route.fulfill({ json: { ...staffProfile, name: "Nguyễn Ánh", version: "8" } });
  });
  await loginFromPage(page);
  await profileFromHeader(page);
  await page.getByRole("button", { name: "Cập nhật thông tin" }).click();
  await page.getByLabel("Họ và tên", { exact: true }).fill("Nguyễn Ánh");
  await page.getByRole("button", { name: "Lưu thay đổi" }).click();
  await patchStarted.promise;
  stopFeed.resolve();
  await checking.promise;
  try {
    await expect(page.locator(".personnel-fields")).toHaveCount(0);
    const patch = page.waitForResponse(response => response.url().endsWith("/api/v1/profiles/me")
      && response.request().method() === "PATCH");
    releasePatch.resolve();
    await patch;
    await expect(page.locator(".personnel-fields")).toHaveCount(0);
    await expect.poll(() => page.evaluate(() => JSON.parse(sessionStorage.getItem("smart_cafe_user")).fullName))
      .toBe(staffProfile.name);
  } finally {
    releasePatch.resolve();
    confirmIdentity.resolve();
  }
  await expect(page.getByRole("button", { name: "Tài khoản Nguyễn Ánh", exact: true })).toBeVisible();
  await expect(page.locator(".personnel-fields")).toContainText("Nguyễn Ánh");
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
