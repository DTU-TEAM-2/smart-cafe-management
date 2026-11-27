import { expect, test } from "@playwright/test";

const table = (id, name, operationalStatus = "ACTIVE", occupancy = "OCCUPIED") => ({ id, name, operationalStatus, occupancy,
  pendingWaiterRequests: 0, pendingBillRequests: 0, submittedOrderItems: occupancy === "OCCUPIED" ? 1 : 0 });
const item = (name, quantity = 1) => ({ id: "1", orderItemId: "1", itemName: name, quantity, unitPrice: "25000", lineTotal: String(25000 * quantity) });
const services = (id, code, name, quantity = 1, currentBill = null, activeRequests = []) => ({ tableId: id,
  sessionCode: code, items: name ? [item(name, quantity)] : [], totalAmount: name ? String(25000 * quantity) : "0", activeRequests, currentBill });
const bill = (status = "PENDING") => ({ id: "91", billCode: "HD-91", sessionCode: "SESSION-OLD", tableName: "Bàn snapshot",
  creatorName: "Quản lý kiểm thử", totalAmount: "91000", status, items: [{ ...item("Món snapshot"), unitPrice: "91000", lineTotal: "91000" }] });

async function setup(page, role = "ADMIN", handler = () => false) {
  await page.addInitScript(() => sessionStorage.setItem("smart_cafe_token", "playwright-test-only"));
  await page.route("**/api/v1/**", async route => {
    const url = new URL(route.request().url());
    if (await handler(route, url)) return;
    if (url.pathname.endsWith("/auth/me")) return route.fulfill({ json: { id: 1, username: "ui-fixture", role } });
    if (url.pathname.endsWith("/home/account")) return route.fulfill({ json: { id: "1", name: "Kiểm thử", role } });
    if (url.pathname.endsWith("/home/notifications")) return route.fulfill({ json: { recipientId: "1", items: [] } });
    if (url.pathname.endsWith("/sales/tables")) return route.fulfill({ json: [table("1", "Bàn 01"), table("2", "Bàn 02"), table("3", "Bàn 03", "OUT_OF_SERVICE", "EMPTY")] });
    if (url.pathname.endsWith("/sales/csrf")) return route.fulfill({ json: { headerName: "X-CSRF-TOKEN", token: "fixture-only" } });
    return route.fulfill({ status: 404, json: {} });
  });
}

test("staff cannot open income directly and customer cannot query sales", async ({ page }) => {
  let forbiddenApiCalls = 0;
  let role = "STAFF";
  await setup(page, role, async (route, url) => {
    if (url.pathname.endsWith("/home/account")) { await route.fulfill({ json: { id: "1", name: "Kiểm thử", role } }); return true; }
    if (url.pathname.endsWith("/income") || url.pathname.endsWith("/sales/tables")) forbiddenApiCalls++;
    return false;
  });
  await page.goto("/#sale-income");
  await expect(page.getByRole("alert")).toContainText("Chỉ quản lý");
  await expect(page.getByRole("link", { name: "Thu nhập" })).toHaveCount(0);
  role = "CUSTOMER";
  await page.goto("/?customer-check#sale");
  await expect(page.getByRole("alert")).toContainText("Bạn cần đăng nhập");
  expect(forbiddenApiCalls).toBe(0);
});

test("does not show an error as an empty table and retry loads actual services", async ({ page }) => {
  let unavailable = true;
  await setup(page, "STAFF", async (route, url) => {
    if (!url.pathname.endsWith("/1/services")) return false;
    if (unavailable) await route.fulfill({ status: 503, json: {} });
    else await route.fulfill({ json: services("1", "SESSION-OLD", "Cà phê sữa") });
    return true;
  });
  await page.goto("/#sale");
  await expect(page.getByRole("button", { name: /Bàn 03/ })).toContainText("Bảo trì");
  await page.getByRole("button", { name: /Bàn 01/ }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByText("Chưa có món để tính tiền.")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Tạo hóa đơn" })).toHaveCount(0);
  unavailable = false;
  await page.getByRole("button", { name: "Thử lại" }).click();
  await expect(page.getByRole("rowheader", { name: "Cà phê sữa" })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(await page.evaluate(() => innerWidth));
});

test("refreshes order totals after scoped acknowledgment and uses stable bill retry key", async ({ page }) => {
  let acknowledged = false;
  const keys = [];
  await setup(page, "STAFF", async (route, url) => {
    if (url.pathname.endsWith("/1/services")) {
      await route.fulfill({ json: services("1", "SESSION-OLD", "Cà phê", acknowledged ? 2 : 1, null,
        acknowledged ? [] : [{ id: "88", type: "WAITER", status: "PENDING" }]) }); return true;
    }
    if (url.pathname.endsWith("/requests/88/acknowledge")) {
      expect(route.request().postDataJSON()).toEqual({ expectedSessionCode: "SESSION-OLD" });
      acknowledged = true; await route.fulfill({ status: 204 }); return true;
    }
    if (url.pathname.endsWith("/1/bill")) {
      const command = route.request().postDataJSON(); keys.push(command.idempotencyKey);
      expect(command.expectedSessionCode).toBe("SESSION-OLD");
      expect(route.request().headers()["x-csrf-token"]).toBe("fixture-only");
      await route.fulfill(keys.length === 1 ? { status: 503, json: {} } : { status: 201, json: bill() }); return true;
    }
    return false;
  });
  await page.goto("/#sale");
  await page.getByRole("button", { name: /Bàn 01/ }).click();
  await page.getByRole("button", { name: "Tiếp nhận gọi phục vụ" }).click();
  await expect(page.getByRole("row", { name: /Cà phê 2/ })).toBeVisible();
  await page.getByRole("button", { name: "Tạo hóa đơn" }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await page.getByRole("button", { name: "Tạo hóa đơn" }).click();
  await expect(page.getByText("Đã tạo hóa đơn chờ xác nhận thanh toán.")).toBeVisible();
  expect(keys[0]).toBe(keys[1]);
  expect(keys[0].length).toBeLessThanOrEqual(64);
  await expect(page.getByRole("button", { name: "In hóa đơn" })).toHaveCount(0);
});

test("manager prints only confirmed server snapshot and reset dialog restores focus", async ({ page }) => {
  let paid = false;
  await setup(page, "ADMIN", async (route, url) => {
    if (url.pathname.endsWith("/1/services")) {
      await route.fulfill({ json: services("1", "SESSION-OLD", "Giá xem trước", 1, { id: "91", status: paid ? "PAID" : "PENDING" }) }); return true;
    }
    if (url.pathname.endsWith("/bills/91")) { await route.fulfill({ json: bill(paid ? "PAID" : "PENDING") }); return true; }
    if (url.pathname.endsWith("/bills/91/payments")) {
      expect(route.request().postDataJSON()).not.toHaveProperty("amount");
      paid = true; await route.fulfill({ json: { bill: bill("PAID"), payment: { amount: "91000" } } }); return true;
    }
    return false;
  });
  await page.addInitScript(() => { window.print = () => { window.printedBill = document.querySelector(".sale-receipt")?.textContent; }; });
  await page.goto("/#sale");
  await page.getByRole("button", { name: /Bàn 01/ }).click();
  await page.getByRole("button", { name: "Xem hóa đơn hiện tại" }).click();
  await expect(page.getByRole("button", { name: "In hóa đơn" })).toHaveCount(0);
  await page.getByRole("button", { name: "Xác nhận thu tiền" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Xác nhận", exact: true }).click();
  await page.getByRole("button", { name: "In hóa đơn" }).click();
  expect(await page.evaluate(() => window.printedBill)).toContain("Món snapshot");
  expect(await page.evaluate(() => window.printedBill)).not.toContain("Giá xem trước");
  await page.emulateMedia({ media: "print" });
  await expect(page.locator(".sale-receipt")).toHaveCSS("visibility", "visible");
  await page.emulateMedia({ media: "screen" });
  const reset = page.getByRole("button", { name: "Đặt lại bàn" });
  await reset.click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(reset).toBeFocused();
});

test("new session during polling invalidates an open reset confirmation", async ({ page }) => {
  let session = "SESSION-OLD";
  let resets = 0;
  await page.clock.install();
  await setup(page, "ADMIN", async (route, url) => {
    if (url.pathname.endsWith("/1/services")) {
      await route.fulfill({ json: services("1", session, session === "SESSION-OLD" ? "Món cũ" : "Món mới", 1,
        session === "SESSION-OLD" ? { id: "91", status: "PAID" } : null) }); return true;
    }
    if (url.pathname.endsWith("/reset")) resets++;
    return false;
  });
  await page.goto("/#sale");
  await page.getByRole("button", { name: /Bàn 01/ }).click();
  await page.getByRole("button", { name: "Đặt lại bàn" }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  session = "SESSION-NEW";
  await page.clock.runFor(8500);
  await expect(page.getByRole("rowheader", { name: "Món mới" })).toBeVisible();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  expect(resets).toBe(0);
});

test("income shows real zero and validates a custom range before querying", async ({ page }) => {
  let customQueries = 0;
  await setup(page, "ADMIN", async (route, url) => {
    if (!url.pathname.endsWith("/income")) return false;
    if (url.searchParams.has("from")) customQueries++;
    await route.fulfill({ json: { from: "2026-11-11", to: "2026-11-11", totalAmount: "0", billCount: 0,
      averageBillAmount: "0", dailyRevenue: [{ date: "2026-11-11", revenue: "0", billCount: 0 }] } });
    return true;
  });
  await page.goto("/#sale-income");
  await expect(page.getByRole("heading", { name: "Doanh thu hôm nay" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Biểu đồ doanh thu" })).toBeVisible();
  await page.getByLabel("Từ ngày").fill("2026-11-12");
  await page.getByLabel("Đến ngày").fill("2026-11-11");
  await page.getByRole("button", { name: "Tính thu nhập" }).click();
  await expect(page.getByRole("alert")).toContainText("Ngày bắt đầu");
  expect(customQueries).toBe(0);
  await page.getByLabel("Từ ngày").fill("2026-11-11");
  await page.getByRole("button", { name: "Tính thu nhập" }).click();
  await expect(page.getByRole("heading", { name: "Doanh thu trong kỳ" })).toBeVisible();
  expect(customQueries).toBe(1);
});
