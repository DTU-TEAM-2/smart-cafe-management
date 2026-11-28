import { expect, test } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

// These checks exercise browser behavior with HTTP fixtures, not authenticated E2E.
test.beforeEach(async ({ page }) => {
  await page.route("**/api/v1/home/account", route => route.fulfill({ status: 401, json: {} }));
  await page.route("**/api/v1/home/highlights", route => route.fulfill({ json: { newest: [], mostOrdered: [] } }));
});

for (const [label, invalid] of [
  ["missing ID", { id: undefined }], ["zero ID", { id: 0 }], ["negative ID", { id: -1 }],
  ["out-of-range ID", { id: "9223372036854775808" }], ["unsafe numeric ID", { id: 9007199254740992 }],
  ["unknown role", { role: "OWNER" }], ["invalid display field", { fullName: { label: "Invalid" } }],
]) test(`login rejects a malformed Auth identity with ${label}`, async ({ page }) => {
  const identity = { id: 1, username: "ui-fixture", role: "STAFF", ...invalid };
  await page.route("**/api/v1/auth/login", route => route.fulfill({ json: { token: "invalid-identity-fixture" } }));
  await page.route("**/api/v1/auth/me", route => route.fulfill({ json: identity }));
  const initialAccount = page.waitForResponse(response => response.url().endsWith("/api/v1/home/account"));
  await page.goto("/#login");
  await initialAccount;
  await page.getByLabel("Tên đăng nhập", { exact: true }).pressSequentially("ui-fixture");
  await page.getByLabel("Mật khẩu", { exact: true }).pressSequentially("UnitPassword1");
  const authCheck = page.waitForResponse(response => response.url().endsWith("/api/v1/auth/me") && response.ok());
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await authCheck;
  await expect(page.getByRole("alert")).toContainText("Không xác minh được phiên đăng nhập.");
  expect(await page.evaluate(() => sessionStorage.getItem("smart_cafe_token"))).toBeNull();
});

test("required login fields prevent requests and Staff login reaches its dashboard", async ({ page }) => {
  let loginRequests = 0;
  const user = { id: 2, username: "staff-fixture", fullName: "Staff fixture", role: "STAFF" };
  await page.route("**/api/v1/auth/login", route => {
    loginRequests++;
    return route.fulfill({ json: { token: "staff-fixture-token", user } });
  });
  await page.route("**/api/v1/auth/me", route => route.fulfill({ json: user }));
  await page.route("**/api/v1/home/account", route => route.request().headers().authorization
    ? route.fulfill({ json: { id: String(user.id), username: user.username, name: user.fullName, role: user.role } })
    : route.fallback());
  await page.goto("/#login");
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Vui lòng nhập đầy đủ");
  expect(loginRequests).toBe(0);
  await page.getByLabel("Tên đăng nhập", { exact: true }).fill(user.username);
  await page.getByLabel("Mật khẩu", { exact: true }).fill("Fixture123!");
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page).toHaveURL(/#staff$/);
  await expect(page.getByRole("button", { name: "Đăng xuất", exact: true })).toBeVisible();
  expect(loginRequests).toBe(1);
});

const viewports = [[1920, 1080], [1366, 768], [768, 1024], [430, 932], [390, 844], [375, 812], [360, 800]];

for (const [width, height] of viewports) {
  test(`auth screens remain accessible and usable at ${width}x${height}`, async ({ page }, testInfo) => {
    test.setTimeout(60000);
    const errors = [];
    page.on("pageerror", error => errors.push(error.message));
    await page.route("**/api/v1/auth/forgot-password", route => route.fulfill({ json: {
      challengeId: "browser-fixture-challenge", expiresInSeconds: 600, retryAfterSeconds: 60,
    } }));
    await page.route("**/api/v1/auth/verify-reset-token", route => route.fulfill({ json: { valid: true } }));
    await page.setViewportSize({ width, height });
    for (const route of ["login", "register", "forgot-password", "verify-otp", "reset-password?token=browser-fixture-grant"]) {
      if (route === "verify-otp") {
        await page.goto("/#forgot-password");
        await page.getByLabel("Địa chỉ Email", { exact: true }).fill("fixture@example.test");
        await page.getByRole("button", { name: "Gửi mã OTP", exact: true }).click();
        await expect(page).toHaveURL(/#verify-otp$/);
      } else await page.goto(`/#${route}`);
      if (route === "register") {
        await expect(page.getByRole("status")).toContainText("Đăng ký tài khoản trực tuyến hiện chưa khả dụng.");
        await expect(page.locator("main input, main form")).toHaveCount(0);
      } else await expect(page.locator(".auth-form")).toBeVisible();
      await page.evaluate(() => document.fonts.ready);
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
      const submit = route === "register"
        ? page.getByRole("button", { name: "QUAY VỀ ĐĂNG NHẬP", exact: true })
        : page.locator(".auth-form button[type=submit]");
      await submit.scrollIntoViewIfNeeded();
      await expect(submit).toBeInViewport();
      const scan = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21aa"]).analyze();
      expect(scan.violations, `${route} at ${width}x${height}`).toEqual([]);
      await page.screenshot({ path: testInfo.outputPath(`${route.split("?")[0]}-${width}x${height}.png`), fullPage: true });
    }
    expect(errors).toEqual([]);
  });
}
