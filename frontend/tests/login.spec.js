import { expect, test } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.route("**/api/v1/home/account", route => route.fulfill({ status: 401, json: {} }));
  await page.route("**/api/v1/home/highlights", route => route.fulfill({ json: { newest: [], mostOrdered: [] } }));
});

test("login calls the auth API and never accepts old mock accounts or invalid credentials", async ({ page }) => {
  const submissions = [];
  page.on("request", request => { if (request.method() !== "GET") submissions.push(request.url()); });
  await page.route("**/api/v1/auth/login", route => route.fulfill({ status: 401, json: {} }));
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
  await expect(page.getByRole("button", { name: "Đăng nhập", exact: true })).toBeEnabled();
  await page.getByLabel("Mật khẩu", { exact: true }).press("Enter");
  await expect(page.getByRole("alert")).toContainText("Tên đăng nhập/email hoặc mật khẩu không đúng.");
  await expect(page).toHaveURL(/#login$/);
  expect(await page.evaluate(() => sessionStorage.getItem("smart_cafe_token"))).toBeNull();
  expect(submissions).toHaveLength(1);
  expect(submissions[0]).toMatch(/\/api\/v1\/auth\/login$/);
  await page.reload();
  await expect(page.getByRole("heading", { name: "Đăng nhập", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: /^Tài khoản/ })).toHaveCount(0);
});

test("login verifies consumer identity, opens news and logout clears both token stores", async ({ page }) => {
  const token = "ui-only-issued-token-fixture";
  await page.route("**/api/v1/auth/login", route => route.fulfill({ json: { token, user: { role: "ADMIN" } } }));
  await page.route("**/api/v1/home/account", route => route.request().headers().authorization === `Bearer ${token}`
    ? route.fulfill({ json: { id: "5", username: "ui-fixture", name: "Khách kiểm thử", role: "CUSTOMER" } })
    : route.fulfill({ status: 403, json: {} }));
  await page.route("**/api/v1/news?*", route => route.fulfill({ json: { content: [], totalElements: 0, totalPages: 0, last: true } }));
  await page.addInitScript(() => localStorage.setItem("smart_cafe_token", "obsolete-token-fixture"));
  await page.goto("/#login");
  await page.getByLabel("Tên đăng nhập", { exact: true }).fill("test@example.invalid");
  await page.getByLabel("Mật khẩu", { exact: true }).fill("UI-fixture-password");
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page).toHaveURL(/#news$/);
  await expect(page.getByRole("heading", { name: "Chuyện từ LECKER." })).toBeVisible();
  // The role from the login response is not used to grant a management screen.
  await expect(page.getByRole("link", { name: "Viết tin mới" })).toHaveCount(0);
  await page.getByRole("button", { name: "Tài khoản Khách kiểm thử" }).click();
  await page.getByRole("button", { name: "Đăng xuất", exact: true }).click();
  await expect(page.getByText("Đăng nhập để xem những tin tức dành cho bạn.")).toBeVisible();
  expect(await page.evaluate(() => [sessionStorage.getItem("smart_cafe_token"), localStorage.getItem("smart_cafe_token")])).toEqual([null, null]);
});

test("login token is not persisted when the news backend rejects the session", async ({ page }) => {
  await page.route("**/api/v1/auth/login", route => route.fulfill({ json: { token: "ui-only-rejected-token" } }));
  await page.goto("/#login");
  await page.getByLabel("Tên đăng nhập", { exact: true }).fill("test@example.invalid");
  await page.getByLabel("Mật khẩu", { exact: true }).fill("UI-fixture-password");
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Không xác minh được phiên đăng nhập");
  expect(await page.evaluate(() => sessionStorage.getItem("smart_cafe_token"))).toBeNull();
  await expect(page).toHaveURL(/#login$/);
});

test("leaving an in-flight login cannot establish a stale session", async ({ page }) => {
  let pending;
  await page.route("**/api/v1/auth/login", route => { pending = route; });
  await page.goto("/#login");
  await page.getByLabel("Tên đăng nhập", { exact: true }).fill("test@example.invalid");
  await page.getByLabel("Mật khẩu", { exact: true }).fill("UI-fixture-password");
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page.getByRole("button", { name: "Đang đăng nhập…", exact: true })).toBeDisabled();
  await expect.poll(() => !!pending).toBe(true);
  await page.getByRole("link", { name: "← Về trang chủ", exact: true }).click();
  await expect(page.getByRole("heading", { name: "High Quality COFFEE" })).toBeVisible();
  await pending.fulfill({ json: { token: "ui-only-stale-token" } });
  expect(await page.evaluate(() => sessionStorage.getItem("smart_cafe_token"))).toBeNull();
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
