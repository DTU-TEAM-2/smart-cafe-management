import { expect, test } from "@playwright/test";

test.describe("Customer Frontend Workflows (DT2-4)", () => {
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
        imageUrl: "https://example.invalid/latte.jpg",
        available: true,
        categoryId: "1",
        categoryName: "Cà phê",
      },
      {
        id: "102",
        code: "CF02",
        name: "Bạc xỉu Sài Gòn",
        price: "35000",
        imageUrl: "https://example.invalid/bacxiu.jpg",
        available: true,
        categoryId: "1",
        categoryName: "Cà phê",
      },
    ],
    totalElements: 2,
    page: 0,
    size: 50,
  };

  test.beforeEach(async ({ page }) => {
    // Mock menu endpoints
    await page.route("**/api/v1/customer/menu/categories", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(mockCategories),
      })
    );

    await page.route(/\/api\/v1\/customer\/menu\/items(\?.*)?$/, (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(mockMenuItems),
      })
    );

    // Mock CSRF
    await page.route("**/api/v1/customer/csrf", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ headerName: "X-CSRF-TOKEN", token: "mock-csrf-token" }),
      })
    );
    await page.route("**/api/v1/customer/feedback/csrf", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ headerName: "X-CSRF-TOKEN", token: "mock-csrf-token" }),
      })
    );
  });

  test("1. Renders customer layout with table badge and loads menu items", async ({ page }) => {
    await page.goto("/#customer?sessionId=10&token=mock-token-abc&table=04");

    // Header assertions
    await expect(page.locator(".c-brand-logo")).toContainText("LECKER");
    await expect(page.locator(".c-table-badge")).toContainText("BÀN 04");

    // Menu category pills
    await expect(page.getByRole("tab", { name: "Tất cả" })).toBeVisible();
    await expect(page.getByRole("tab", { name: /Cà phê/ })).toBeVisible();

    // Menu items
    await expect(page.getByText("Caramel Latte")).toBeVisible();
    await expect(page.getByText("45.000 đ")).toBeVisible();
    await expect(page.getByText("Bạc xỉu Sài Gòn")).toBeVisible();
  });

  test("2. Adds item to cart via item detail modal and verifies cart badge", async ({ page }) => {
    await page.goto("/#customer?sessionId=10&token=mock-token-abc&table=04");

    // Click on item to open modal
    await page.getByText("Caramel Latte").click();
    const modal = page.locator(".c-modal-dialog");
    await expect(modal).toBeVisible();
    await expect(modal.getByRole("heading", { name: "Caramel Latte" })).toBeVisible();

    // Increase quantity to 2
    await modal.getByRole("button", { name: "Tăng số lượng" }).click();
    await expect(modal.locator(".c-stepper-value")).toHaveText("2");

    // Click add to cart
    await modal.getByRole("button", { name: /THÊM VÀO GIỎ/ }).click();

    // Verify cart dock badge is updated to 2
    const cartBadge = page.locator(".c-dock-badge");
    await expect(cartBadge).toHaveText("2");
  });

  test("3. Submits order from cart and verifies success modal", async ({ page }) => {
    let orderPayload = null;
    await page.route("**/api/v1/customer/sessions/10/orders", (route) => {
      orderPayload = route.request().postDataJSON();
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          batchId: "B1001",
          sessionId: "10",
          totalVnd: "90000",
          editableUntil: new Date(Date.now() + 180000).toISOString(),
          items: [
            {
              id: "item-1",
              menuItemId: "101",
              name: "Caramel Latte",
              price: "45000",
              quantity: 2,
              version: 0,
              status: "SUBMITTED",
            },
          ],
        }),
      });
    });

    await page.goto("/#customer?sessionId=10&token=mock-token-abc&table=04");

    // Add item directly
    await page.getByLabel("Thêm Caramel Latte vào giỏ hàng").click();

    // Navigate to Cart
    await page.getByRole("button", { name: /Giỏ hàng/ }).click();
    await expect(page.getByRole("heading", { name: "Giỏ hàng của bạn" })).toBeVisible();
    await expect(page.getByText("Tạm tính")).toBeVisible();

    // Click checkout
    await page.getByRole("button", { name: /GỬI ĐƠN ĐẶT MÓN/ }).click();

    // Verify success modal
    await expect(page.locator(".c-result-title")).toHaveText("Đặt món thành công!");
    expect(orderPayload).not.toBeNull();
    expect(orderPayload.items[0].menuItemId).toBe(101);
  });

  test("4. Active orders tab shows placed order with 180s live timer and edit/cancel actions", async ({ page }) => {
    // Preload order history via sessionStorage
    await page.addInitScript(() => {
      const futureTime = new Date(Date.now() + 175000).toISOString();
      sessionStorage.setItem(
        "lecker_customer_session",
        JSON.stringify({ sessionId: "10", token: "mock-token-abc", tableNumber: "04" })
      );
      sessionStorage.setItem(
        "lecker_customer_orders",
        JSON.stringify([
          {
            batchId: "B1001",
            sessionId: "10",
            totalVnd: "45000",
            editableUntil: futureTime,
            items: [
              {
                id: "item-1",
                menuItemId: "101",
                name: "Caramel Latte",
                price: "45000",
                quantity: 1,
                version: 0,
                status: "SUBMITTED",
              },
            ],
          },
        ])
      );
    });

    // Mock update item
    await page.route("**/api/v1/customer/sessions/10/order-items/item-1", (route) => {
      if (route.request().method() === "PATCH") {
        return route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({ version: 1, quantity: 3 }),
        });
      }
      if (route.request().method() === "DELETE") {
        return route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({ status: "CANCELLED" }),
        });
      }
      return route.continue();
    });

    await page.goto("/#customer?sessionId=10&token=mock-token-abc&table=04");

    // Navigate to Orders
    await page.getByRole("button", { name: "Đơn món" }).click();
    await expect(page.getByRole("heading", { name: "Đơn hàng của bạn" })).toBeVisible();

    // Verify timer badge is visible
    await expect(page.locator(".c-timer-badge")).toContainText("Sửa/hủy:");

    // Edit item
    await page.getByTitle("Sửa số lượng").click();
    await expect(page.getByRole("heading", { name: "Chỉnh sửa số lượng" })).toBeVisible();
    await page.getByRole("button", { name: "CẬP NHẬT" }).click();

    // Cancel item
    await page.getByTitle("Hủy món").click();
    await expect(page.getByRole("heading", { name: "Xác nhận hủy món" })).toBeVisible();
    await page.getByRole("button", { name: "XÁC NHẬN HỦY" }).click();

    // Verify status updated to "ĐÃ HỦY"
    await expect(page.locator(".c-item-status-badge")).toHaveText("Đã hủy");
  });

  test("5. Service Requests: calls waiter and requests bill", async ({ page }) => {
    let waiterCalled = false;
    let billRequested = false;

    await page.route("**/api/v1/customer/sessions/10/waiter-requests", (route) => {
      waiterCalled = true;
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ id: "req-1", type: "WAITER", status: "PENDING" }),
      });
    });

    await page.route("**/api/v1/customer/sessions/10/bill-requests", (route) => {
      billRequested = true;
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ id: "req-2", type: "BILL", status: "PENDING" }),
      });
    });

    // Provide an existing order in session so bill request is enabled
    await page.addInitScript(() => {
      sessionStorage.setItem(
        "lecker_customer_session",
        JSON.stringify({ sessionId: "10", token: "mock-token-abc", tableNumber: "04" })
      );
      sessionStorage.setItem(
        "lecker_customer_orders",
        JSON.stringify([
          {
            batchId: "B1001",
            sessionId: "10",
            totalVnd: "45000",
            items: [{ id: "item-1", name: "Latte", price: "45000", quantity: 1, status: "SUBMITTED" }],
          },
        ])
      );
    });

    await page.goto("/#customer?sessionId=10&token=mock-token-abc&table=04");

    // Navigate to Service tab
    await page.getByRole("button", { name: "Phục vụ" }).click();
    await expect(page.getByRole("heading", { name: "Dịch vụ tại bàn" })).toBeVisible();

    // Call waiter
    await page.getByRole("button", { name: "GỌI PHỤC VỤ NGAY" }).click();
    await expect(page.locator(".c-result-title")).toHaveText("Đã gọi nhân viên!");
    expect(waiterCalled).toBe(true);

    // Close modal
    await page.getByRole("button", { name: "ĐÓNG", exact: true }).click();

    // Request bill
    await page.getByRole("button", { name: "YÊU CẦU HÓA ĐƠN" }).click();
    await expect(page.locator(".c-result-title")).toHaveText("Đã yêu cầu thanh toán!");
    expect(billRequested).toBe(true);
  });

  test("6. Customer feedback form validates and submits feedback", async ({ page }) => {
    let submittedBody = null;
    await page.route("**/api/v1/customer/feedback", (route) => {
      submittedBody = route.request().postDataJSON();
      return route.fulfill({
        status: 201,
        contentType: "application/json",
        body: JSON.stringify({ id: "fb-123", message: "Cảm ơn bạn đã gửi phản hồi!" }),
      });
    });

    await page.goto("/#customer?sessionId=10&token=mock-token-abc&table=04");

    // Navigate to Feedback tab
    await page.getByRole("button", { name: "Đánh giá" }).click();
    await expect(page.getByRole("heading", { name: "Đánh giá trải nghiệm" })).toBeVisible();

    // Fill form
    await page.locator("#feedback-name").fill("Nguyễn Văn A");
    await page.locator("#feedback-email").fill("customer@example.com");
    await page.locator("#feedback-content").fill("Cà phê rất đậm đà, nhân viên nhiệt tình chu đáo.");

    // Select 5 stars
    await page.getByRole("radio", { name: "5 sao" }).click();

    // Submit
    await page.getByRole("button", { name: "GỬI PHẢN HỒI NGAY" }).click();

    // Verify success modal
    await expect(page.locator(".c-result-title")).toHaveText("Cảm ơn bạn!");
    expect(submittedBody).not.toBeNull();
    expect(submittedBody.rating).toBe(5);
    expect(submittedBody.name).toBe("Nguyễn Văn A");
  });
});
