import { expect, test } from "@playwright/test";

// HTTP fixtures for automated UI tests only. Production uses the authenticated Header API.
const staff = { id: "2", username: "test-staff", name: "Nhân viên kiểm thử", email: "staff@example.invalid", role: "STAFF", address: "Đà Nẵng", salary: "8000000",
  phone: null, birthday: null, gender: null, avatarUrl: null, canUpdateProfile: true, version: "0",
  passwordChangedAt: "2026-11-18T00:00:00Z", passwordExpiresAt: "2026-12-18T00:00:00Z", mustChangePassword: false };
const admin = { ...staff, id: "3", name: "Quản lý kiểm thử", role: "ADMIN", salary: "12000000" };
const customer = { ...staff, id: "4", name: "Khách kiểm thử", role: "CUSTOMER", salary: null };

async function setToken(page, token) {
  await page.evaluate(value => {
    if (value) sessionStorage.setItem("smart_cafe_token", value);
    else sessionStorage.removeItem("smart_cafe_token");
    window.dispatchEvent(new Event("smart-cafe-auth-change"));
  }, token);
}

test("account route shares one layout; polling deduplicates notices and logout clears the old account", async ({ page, isMobile }) => {
  if (isMobile) await page.setViewportSize({ width: 320, height: 720 });
  await page.clock.install();
  let current = null;
  let notices = [{ id: "order:1", message: "Bàn 01 vừa gọi món" }];
  await page.route("**/api/v1/home/highlights", route => route.fulfill({ json: { newest: [], mostOrdered: [] } }));
  await page.route("**/api/v1/home/account", async route => {
    const header = route.request().headers().authorization;
    // Keep CUSTOMER identity reads slower than Profile to exercise out-of-order responses.
    if (header === "Bearer test-customer-token") await new Promise(resolve => setTimeout(resolve, 100));
    if (!header) return route.fulfill({ status: 403, json: {} });
    current = header === "Bearer test-staff-token" ? staff
      : header === "Bearer test-admin-token" ? admin : customer;
    return route.fulfill({ json: current });
  });
  await page.route("**/api/v1/auth/me", async route => {
    const token = route.request().headers().authorization;
    if (token === "Bearer test-customer-token") await new Promise(resolve => setTimeout(resolve, 100));
    const account = token === "Bearer test-staff-token" ? staff : token === "Bearer test-admin-token" ? admin : customer;
    return route.fulfill({ json: { ...account, fullName: account.name } });
  });
  await page.route("**/api/v1/profiles/me", route => {
    const token = route.request().headers().authorization;
    const account = token === "Bearer test-staff-token" ? staff : token === "Bearer test-admin-token" ? admin : null;
    return account ? route.fulfill({ json: account })
      : route.fulfill({ status: token ? 403 : 401, json: { code: token ? "FORBIDDEN" : "UNAUTHORIZED" } });
  });
  await page.route("**/api/v1/home/notifications", route => route.fulfill({
    json: { recipientId: current.id, items: [...notices, ...notices] },
  }));
  await page.goto("/");
  await setToken(page, "test-staff-token");
  await page.getByRole("button", { name: "Tài khoản Nhân viên kiểm thử" }).click();
  await page.getByRole("button", { name: "Thông tin tài khoản", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Thông tin cá nhân" })).toBeVisible();
  await expect(page.getByText("Đà Nẵng", { exact: true })).toBeVisible();
  await expect(page.getByText("8.000.000", { exact: false })).toBeVisible();
  await expect(page.getByRole("banner")).toHaveCount(1);
  await expect(page.getByRole("contentinfo")).toHaveCount(1);
  await page.getByRole("button", { name: /Thông báo/ }).click();
  const notificationPanel = page.getByRole("region", { name: "Thông báo phục vụ" });
  const bounds = await notificationPanel.boundingBox();
  expect(bounds.x).toBeGreaterThanOrEqual(0);
  expect(bounds.x + bounds.width).toBeLessThanOrEqual(page.viewportSize().width);
  await expect(page.getByText("Bàn 01 vừa gọi món", { exact: true })).toHaveCount(1);
  notices = [...notices, { id: "waiter:2", message: "Bàn 02 đang gọi phục vụ" }];
  await page.clock.runFor(5100);
  await expect(page.getByText("Bàn 02 đang gọi phục vụ", { exact: true })).toHaveCount(1);
  await setToken(page, "");
  await expect(page.getByText("Bàn 01 vừa gọi món", { exact: true })).toHaveCount(0);
  await expect(page.getByRole("button", { name: /Thông báo/ })).toHaveCount(0);
  await setToken(page, "test-admin-token");
  await expect(page.getByRole("button", { name: "Tài khoản Quản lý kiểm thử" })).toBeVisible();
  await setToken(page, "");
  await setToken(page, "test-customer-token");
  await expect(page.getByRole("button", { name: "Tài khoản Khách kiểm thử" })).toBeVisible();
  await expect(page.getByRole("button", { name: /Thông báo/ })).toHaveCount(0);
});

test("notification errors can be retried and a wrong recipient snapshot is never displayed", async ({ page }) => {
  let mode = "error";
  await page.route("**/api/v1/home/highlights", route => route.fulfill({ json: { newest: [], mostOrdered: [] } }));
  await page.route("**/api/v1/home/account", route => route.request().headers().authorization
    ? route.fulfill({ json: staff }) : route.fulfill({ status: 403, json: {} }));
  await page.route("**/api/v1/auth/me", route => route.fulfill({ json: { ...staff, fullName: staff.name } }));
  await page.route("**/api/v1/home/notifications", route => mode === "error"
    ? route.fulfill({ status: 503, json: {} })
    : route.fulfill({ json: mode === "wrong"
      ? { recipientId: "another-user", items: [{ id: "private", message: "Không được lộ" }] }
      : { recipientId: staff.id, items: [] } }));
  await page.goto("/");
  await setToken(page, "test-staff-token");
  await page.getByRole("button", { name: /Thông báo/ }).click();
  await expect(page.getByRole("alert")).toContainText("Chưa tải được thông báo");
  mode = "wrong";
  await page.getByRole("button", { name: "Thử lại", exact: true }).click();
  await expect(page.getByText("Không được lộ", { exact: true })).toHaveCount(0);
  await expect(page.getByRole("alert")).toContainText("Chưa tải được thông báo");
  mode = "ready";
  await page.getByRole("button", { name: "Thử lại", exact: true }).click();
  await expect(page.getByText("Chưa có thông báo.", { exact: true })).toBeVisible();
});
