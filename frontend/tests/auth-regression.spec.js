import { expect, test } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

// These checks exercise browser behavior with HTTP fixtures, not authenticated E2E.
test.beforeEach(async ({ page }) => {
  await page.route("**/api/v1/home/account", route => route.fulfill({ status: 401, json: {} }));
  await page.route("**/api/v1/home/highlights", route => route.fulfill({ json: { newest: [], mostOrdered: [] } }));
});

test("required login fields prevent requests and Staff login reaches its dashboard", async ({ page }) => {
  let loginRequests = 0;
  const user = { id: 2, username: "staff-fixture", fullName: "Staff fixture", role: "STAFF" };
  await page.route("**/api/v1/auth/login", route => {
    loginRequests++;
    return route.fulfill({ json: { token: "staff-fixture-token", user } });
  });
  await page.route("**/api/v1/auth/me", route => route.fulfill({ json: user }));
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

test("auth screens remain accessible and usable at every required viewport", async ({ page }, testInfo) => {
  test.setTimeout(180000);
  const errors = [];
  page.on("pageerror", error => errors.push(error.message));
  await page.route("**/api/v1/auth/forgot-password", route => route.fulfill({ json: {
    challengeId: "browser-fixture-challenge", expiresInSeconds: 600, retryAfterSeconds: 60,
  } }));
  await page.route("**/api/v1/auth/verify-reset-token", route => route.fulfill({ json: { valid: true } }));
  const viewports = [[1920, 1080], [1366, 768], [768, 1024], [430, 932], [390, 844], [375, 812], [360, 800]];
  for (const [width, height] of viewports) {
    await page.setViewportSize({ width, height });
    for (const route of ["login", "register", "forgot-password", "verify-otp", "reset-password?token=browser-fixture-grant"]) {
      if (route === "verify-otp") {
        await page.goto("/#forgot-password");
        await page.getByLabel("Địa chỉ Email", { exact: true }).fill("fixture@example.test");
        await page.getByRole("button", { name: "Gửi mã OTP", exact: true }).click();
        await expect(page).toHaveURL(/#verify-otp$/);
      } else await page.goto(`/#${route}`);
      await expect(page.locator(".auth-form")).toBeVisible();
      await page.evaluate(() => document.fonts.ready);
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
      const submit = page.locator(".auth-form button[type=submit]");
      await submit.scrollIntoViewIfNeeded();
      await expect(submit).toBeInViewport();
      const scan = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21aa"]).analyze();
      expect(scan.violations, `${route} at ${width}x${height}`).toEqual([]);
      await page.screenshot({ path: testInfo.outputPath(`${route.split("?")[0]}-${width}x${height}.png`), fullPage: true });
    }
  }
  expect(errors).toEqual([]);
});
