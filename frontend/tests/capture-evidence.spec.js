import { expect, test } from "@playwright/test";
import path from "node:path";
import fs from "node:fs";

test("Capture high-fidelity screenshots for DT2-4 Frontend Evidence", async ({ page }) => {
  const outputDir = path.resolve("..", "docs", "handover", "evidence", "dt2-4", "screenshots");
  if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
  }

  const mockCategories = [
    { id: "1", code: "COFFEE", name: "Cà phê", activeItemCount: 2 },
    { id: "2", code: "TEA", name: "Trà & Trái cây", activeItemCount: 1 },
  ];

  const mockMenuItems = {
    content: [
      {
        id: "101",
        code: "CF01",
        name: "Caramel Latte",
        price: "45000",
        imageUrl: "/images/latte.jpg",
        available: true,
        categoryId: "1",
        categoryName: "Cà phê",
      },
      {
        id: "102",
        code: "CF02",
        name: "Bạc xỉu Sài Gòn",
        price: "35000",
        imageUrl: "/images/iced-coffee.jpg",
        available: true,
        categoryId: "1",
        categoryName: "Cà phê",
      },
    ],
    totalElements: 2,
    page: 0,
    size: 50,
  };

  await page.route("**/api/v1/customer/menu/categories", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockCategories) })
  );
  await page.route(/\/api\/v1\/customer\/menu\/items(\?.*)?$/, (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockMenuItems) })
  );
  await page.route("**/api/v1/customer/csrf", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ headerName: "X-CSRF-TOKEN", token: "mock" }) })
  );
  await page.route(/\/api\/v1\/customer\/menu\/items\/[^/?]+$/, (route) =>
    route.fulfill({ json: mockMenuItems.content.find((item) => item.id === route.request().url().split("/").pop()) })
  );
  await page.route(/\/api\/v1\/customer\/sessions\/10\/orders\/[^/?]+$/, async (route) => {
    const id = route.request().url().split("/").pop();
    const order = await page.evaluate((batchId) =>
      JSON.parse(sessionStorage.getItem("lecker_customer_checkout:10:evidence-client"))?.orders.find(batch => String(batch.id) === batchId), id);
    return route.fulfill({ json: order, headers: { Date: new Date().toUTCString() } });
  });
  await page.route("**/api/v1/customer/feedback/csrf", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ headerName: "X-CSRF-TOKEN", token: "mock" }) })
  );
  await page.addInitScript(() => {
    sessionStorage.setItem(
      "lecker_customer_session",
      JSON.stringify({
        sessionId: "10",
        token: "mock-token-abc",
        tableNumber: "04",
        clientStorageId: "evidence-client",
      })
    );
  });

  // Set mobile viewport
  await page.setViewportSize({ width: 414, height: 896 });

  // 1. Menu view
  await page.goto("/#customer");
  await page.waitForTimeout(600);
  await page.screenshot({ path: path.join(outputDir, "01-fe-menu.png") });

  // 2. Item detail modal
  await page.getByText("Caramel Latte").click();
  await page.waitForTimeout(400);
  await page.screenshot({ path: path.join(outputDir, "02-fe-item-detail.png") });

  // Add to cart
  await page.locator(".c-modal-dialog").getByRole("button", { name: /THÊM VÀO GIỎ/ }).click();
  await page.waitForTimeout(700);

  // 3. Cart view
  await page.getByRole("button", { name: /Giỏ hàng/ }).click();
  await page.waitForTimeout(500);
  await page.screenshot({ path: path.join(outputDir, "03-fe-cart.png") });

  // 4. Order success modal
  await page.route("**/api/v1/customer/sessions/10/orders", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        id: "1001",
        sessionId: "10",
        totalAmount: "45000",
        sessionTotalAmount: "45000",
        editableUntil: new Date(Date.now() + 180000).toISOString(),
        items: [{ id: "501", menuItemId: "101", name: "Caramel Latte", unitPrice: "45000", lineTotal: "45000", quantity: 1, version: "0", status: "SUBMITTED" }],
      }),
    })
  );
  await page.getByRole("button", { name: /GỬI ĐƠN ĐẶT MÓN/ }).click();
  await expect(page.locator(".c-result-title")).toHaveText("Đặt món thành công!");
  await page.screenshot({ path: path.join(outputDir, "04-fe-order-success.png") });

  // 5. Active orders view
  await page.locator(".c-result-box").getByRole("button", { name: "XEM ĐƠN MÓN" }).click();
  await page.waitForTimeout(500);
  await page.screenshot({ path: path.join(outputDir, "05-fe-active-orders.png") });

  // 6. Service requests view
  await page.getByRole("button", { name: "Phục vụ" }).click();
  await page.waitForTimeout(500);
  await page.screenshot({ path: path.join(outputDir, "06-fe-service-requests.png") });

  // 7. Feedback view
  await page.getByRole("button", { name: "Đánh giá" }).click();
  await page.waitForTimeout(500);
  await page.screenshot({ path: path.join(outputDir, "07-fe-feedback.png") });
});
