import { expect, test } from "@playwright/test";

async function setup(page, role = "ADMIN") {
  await page.addInitScript(() => sessionStorage.setItem("smart_cafe_token", "isolated-shell-fixture"));
  await page.route("**/api/v1/auth/me", route => route.fulfill({ json: { id: 1, username: "ui-fixture", role } }));
  await page.route("**/api/v1/home/account", route => route.fulfill({ json: { id: "1", name: "Tài khoản kiểm thử", role } }));
  await page.route("**/api/v1/home/notifications", route => route.fulfill({ json: { recipientId: "1", items: [] } }));
  await page.route("**/api/v1/news?*", route => route.fulfill({ json: { content: [], totalElements: 0, totalPages: 0, last: true, page: 0, size: 6 } }));
}

test("scrolling to the top of a dirty editor keeps the draft without asking to leave", async ({ page }) => {
  await setup(page);
  let prompts = 0;
  page.on("dialog", dialog => { prompts++; return dialog.dismiss(); });
  await page.goto("/#news/new");
  const title = page.getByLabel("Tiêu đề", { exact: false });
  await title.fill("Bản nháp cần giữ");
  await page.getByRole("button", { name: "Về đầu trang", exact: true }).click();
  await expect(page.getByRole("main")).toBeFocused();
  expect(prompts).toBe(0);
  await expect(title).toHaveValue("Bản nháp cần giữ");
  await expect(page).toHaveURL(/#news\/new$/);
});

test("news draft Back and Forward work without the browser Navigation API", async ({ page }) => {
  await page.addInitScript(() => Object.defineProperty(window, "navigation", { value: undefined }));
  await setup(page);
  await page.goto("/#news");
  await page.getByRole("link", { name: "Viết tin mới" }).click();
  const title = page.getByLabel("Tiêu đề", { exact: false });
  await title.fill("Bản nháp chưa lưu");
  let prompts = 0;
  page.once("dialog", dialog => { prompts++; return dialog.dismiss(); });
  await page.goBack();
  await expect(page).toHaveURL(/#news\/new$/);
  await expect(title).toHaveValue("Bản nháp chưa lưu");
  page.once("dialog", dialog => { prompts++; return dialog.accept(); });
  await page.goBack();
  await expect(page.getByRole("heading", { name: "Quản lý tin tức" })).toBeVisible();
  await page.goForward();
  await expect(title).toHaveValue("");
  expect(prompts).toBe(2);
});

test("a temporary identity failure after policy-close recovers without reconnecting early", async ({ page }) => {
  await page.clock.install();
  await setup(page, "STAFF");
  let failNextCheck = false;
  const sockets = [];
  await page.route("**/api/v1/home/account", route => {
    if (failNextCheck) {
      failNextCheck = false;
      return route.fulfill({ status: 503, json: {} });
    }
    return route.fulfill({ json: { id: "1", name: "Staff fixture", role: "STAFF" } });
  });
  await page.routeWebSocket("**/api/v1/news/events", ws => {
    sockets.push(ws);
    ws.onMessage(() => ws.send('{"type":"READY"}'));
  });
  await page.goto("/#news");
  await expect(page.getByText("Tin tức tự cập nhật khi có thay đổi.")).toBeVisible();
  failNextCheck = true;
  sockets[0].close({ code: 1008 });
  await expect(page.getByText("Chưa tải được thông tin tài khoản.")).toBeVisible();
  expect(sockets).toHaveLength(1);
  await page.clock.fastForward(5500);
  await expect.poll(() => sockets.length).toBe(2);
  await expect(page.getByText("Tin tức tự cập nhật khi có thay đổi.")).toBeVisible();
});
