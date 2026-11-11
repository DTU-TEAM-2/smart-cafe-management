import { expect, test } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.route("**/api/v1/home/account", route => route.fulfill({ status: 401, json: {} }));
  await page.route("**/api/v1/home/highlights", route => route.fulfill({ json: { newest: [], mostOrdered: [] } }));
});

test("login is UI only, supports password visibility and never accepts old mock accounts", async ({ page }) => {
  const submissions = [];
  page.on("request", request => { if (request.method() !== "GET") submissions.push(request.url()); });
  await page.addInitScript(() => sessionStorage.setItem("smart_cafe_demo_username", "admin"));
  await page.goto("/");
  await page.getByRole("link", { name: "Đăng nhập", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Đăng nhập", exact: true })).toBeVisible();
  await expect(page.getByText(/demo|dùng thử/i)).toHaveCount(0);
  await page.getByLabel("Tên đăng nhập", { exact: true }).fill("admin");
  await page.getByLabel("Mật khẩu", { exact: true }).fill("123456");
  await page.getByRole("button", { name: "Hiện mật khẩu", exact: true }).click();
  await expect(page.getByLabel("Mật khẩu", { exact: true })).toHaveAttribute("type", "text");
  await page.getByRole("button", { name: "Ẩn mật khẩu", exact: true }).click();
  await expect(page.getByLabel("Mật khẩu", { exact: true })).toHaveAttribute("type", "password");
  await expect(page.getByRole("button", { name: "Đăng nhập", exact: true })).toBeDisabled();
  await page.getByLabel("Mật khẩu", { exact: true }).press("Enter");
  await expect(page).toHaveURL(/#login$/);
  expect(await page.evaluate(() => sessionStorage.getItem("smart_cafe_token"))).toBeNull();
  expect(submissions).toEqual([]);
  await page.reload();
  await expect(page.getByRole("heading", { name: "Đăng nhập", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: /^Tài khoản/ })).toHaveCount(0);
});

test("login remains responsive and shares Header/Footer with the homepage", async ({ page }) => {
  await page.goto("/#login");
  for (const width of [320, 390, 768, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    await expect(page.getByRole("button", { name: "Đăng nhập", exact: true })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  }
  await expect(page.getByRole("banner")).toHaveCount(1);
  await expect(page.getByRole("contentinfo")).toHaveCount(1);
  await page.getByRole("link", { name: "← Về trang chủ", exact: true }).click();
  await expect(page.getByRole("heading", { name: "High Quality COFFEE" })).toBeVisible();
});
