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
    await page.addInitScript(() => {
      if (!sessionStorage.getItem("lecker_customer_session")) {
        sessionStorage.setItem(
          "lecker_customer_session",
          JSON.stringify({
            sessionId: "10",
            token: "mock-token-abc",
            tableNumber: "04",
            clientStorageId: "client-a",
          })
        );
      }
    });

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
    await page.goto("/#customer?sessionId=10&token=legacy-token-that-must-not-remain&table=04");

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
    expect(page.url()).not.toContain("token=");
    expect(page.url()).toMatch(/#customer$/);
  });

  test("2. Adds item to cart via item detail modal and verifies cart badge", async ({ page }) => {
    await page.goto("/#customer");

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
          id: "1001",
          sessionId: "10",
          totalAmount: "45000",
          sessionTotalAmount: "45000",
          editableUntil: new Date(Date.now() + 180000).toISOString(),
          items: [
            {
              id: "item-1",
              menuItemId: "101",
              name: "Caramel Latte",
              unitPrice: "45000",
              lineTotal: "45000",
              quantity: 1,
              version: "0",
              status: "SUBMITTED",
            },
          ],
        }),
      });
    });

    await page.goto("/#customer");

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
        JSON.stringify({ sessionId: "10", token: "mock-token-abc", tableNumber: "04", clientStorageId: "client-a" })
      );
      sessionStorage.setItem(
        "lecker_customer_orders:10:client-a",
        JSON.stringify([
          {
            id: "1001",
            sessionId: "10",
            totalAmount: "45000",
            sessionTotalAmount: "45000",
            editableUntil: futureTime,
            items: [
              {
                id: "item-1",
                menuItemId: "101",
                name: "Caramel Latte",
                unitPrice: "45000",
                lineTotal: "45000",
                quantity: 1,
                version: "0",
                status: "SUBMITTED",
              },
            ],
          },
        ])
      );
    });

    let currentVersion = 0;
    let currentQuantity = 1;
    let currentStatus = "SUBMITTED";
    const expectedVersions = [];
    const response = () => ({
      id: "1001",
      sessionId: "10",
      totalAmount: String(45000 * (currentStatus === "CANCELLED" ? 0 : currentQuantity)),
      sessionTotalAmount: String(45000 * (currentStatus === "CANCELLED" ? 0 : currentQuantity)),
      editableUntil: new Date(Date.now() + 175000).toISOString(),
      items: [{
        id: "item-1",
        menuItemId: "101",
        name: "Caramel Latte",
        unitPrice: "45000",
        lineTotal: String(45000 * currentQuantity),
        quantity: currentQuantity,
        version: String(currentVersion),
        status: currentStatus,
      }],
    });

    // Mock the real backend response: PATCH/DELETE both return the full Order.
    await page.route("**/api/v1/customer/sessions/10/order-items/item-1", (route) => {
      const body = route.request().postDataJSON();
      expectedVersions.push(body.expectedVersion);
      if (route.request().method() === "PATCH") {
        currentQuantity = body.quantity;
        currentVersion += 1;
        return route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(response()),
        });
      }
      if (route.request().method() === "DELETE") {
        currentStatus = "CANCELLED";
        currentVersion += 1;
        return route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(response()),
        });
      }
      return route.continue();
    });

    await page.goto("/#customer");

    // Navigate to Orders
    await page.getByRole("button", { name: "Đơn món" }).click();
    await expect(page.getByRole("heading", { name: "Đơn hàng của bạn" })).toBeVisible();

    // Verify timer badge is visible
    await expect(page.locator(".c-timer-badge")).toContainText("Sửa/hủy:");

    // Three consecutive edits must use versions 0, 1 and 2 from server responses.
    for (let update = 0; update < 3; update += 1) {
      await page.getByTitle("Sửa số lượng").click();
      await expect(page.getByRole("heading", { name: "Chỉnh sửa số lượng" })).toBeVisible();
      await page.getByRole("button", { name: "Tăng số lượng" }).click();
      await page.getByRole("button", { name: "CẬP NHẬT" }).click();
    }

    // Cancel item
    await page.getByTitle("Hủy món").click();
    await expect(page.getByRole("heading", { name: "Xác nhận hủy món" })).toBeVisible();
    await page.getByRole("button", { name: "XÁC NHẬN HỦY" }).click();

    // Verify status updated to "ĐÃ HỦY"
    await expect(page.locator(".c-item-status-badge")).toHaveText("Đã hủy");
    expect(expectedVersions).toEqual([0, 1, 2, 3]);
  });

  test("5. Retries an uncertain order with the same Idempotency-Key", async ({ page }) => {
    const keys = [];
    let calls = 0;
    await page.route("**/api/v1/customer/sessions/10/orders", (route) => {
      calls += 1;
      keys.push(route.request().headers()["idempotency-key"]);
      if (calls === 1) return route.abort("connectionfailed");
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          id: "1002",
          sessionId: "10",
          totalAmount: "45000",
          sessionTotalAmount: "45000",
          editableUntil: new Date(Date.now() + 180000).toISOString(),
          items: [{
            id: "item-2",
            menuItemId: "101",
            name: "Caramel Latte",
            unitPrice: "45000",
            lineTotal: "45000",
            quantity: 1,
            version: "0",
            status: "SUBMITTED",
          }],
        }),
      });
    });

    await page.goto("/#customer");
    await page.getByLabel("Thêm Caramel Latte vào giỏ hàng").click();
    await page.getByRole("button", { name: /Giỏ hàng/ }).click();
    await page.getByRole("button", { name: /GỬI ĐƠN ĐẶT MÓN/ }).click();
    await expect(page.locator(".c-result-title")).toHaveText("Đặt món thất bại");
    await page.getByRole("button", { name: "THỬ LẠI" }).click();
    await expect(page.locator(".c-result-title")).toHaveText("Đặt món thành công!");
    expect(keys).toHaveLength(2);
    expect(keys[1]).toBe(keys[0]);
  });

  test("6. Isolates cart and orders when the customer session changes", async ({ page }) => {
    await page.goto("/#customer");
    await page.getByLabel("Thêm Caramel Latte vào giỏ hàng").click();
    await expect(page.locator(".c-dock-badge")).toHaveText("1");

    await page.evaluate(() => {
      sessionStorage.setItem(
        "lecker_customer_session",
        JSON.stringify({
          sessionId: "20",
          token: "different-server-token",
          tableNumber: "08",
          clientStorageId: "client-b",
        })
      );
    });
    await page.reload();

    await expect(page.locator(".c-table-badge")).toContainText("BÀN 08");
    await expect(page.locator(".c-dock-badge")).toHaveCount(0);
    await page.getByRole("button", { name: /Giỏ hàng/ }).click();
    await expect(page.getByRole("heading", { name: "Giỏ hàng trống" })).toBeVisible();
    await page.getByRole("button", { name: "Đơn món" }).click();
    await expect(page.getByRole("heading", { name: "Chưa có đơn món" })).toBeVisible();
  });

  test("7. Service Requests: calls waiter and allows client B without local orders to request bill", async ({ page }) => {
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

    // Client B at the same table (session 10) has no local orders (orders were placed by Client A)
    await page.addInitScript(() => {
      sessionStorage.setItem(
        "lecker_customer_session",
        JSON.stringify({ sessionId: "10", token: "mock-token-client-b", tableNumber: "04", clientStorageId: "client-b" })
      );
    });

    await page.goto("/#customer");

    // Navigate to Service tab
    await page.getByRole("button", { name: "Phục vụ" }).click();
    await expect(page.getByRole("heading", { name: "Dịch vụ tại bàn" })).toBeVisible();

    // Call waiter
    await page.getByRole("button", { name: "GỌI PHỤC VỤ NGAY" }).click();
    await expect(page.locator(".c-result-title")).toHaveText("Đã gọi nhân viên!");
    expect(waiterCalled).toBe(true);

    // Close modal
    await page.getByRole("button", { name: "ĐÓNG", exact: true }).click();

    // Request bill: Client B without local orders can still request bill, server accepts because session has items
    await page.getByRole("button", { name: "YÊU CẦU HÓA ĐƠN" }).click();
    await expect(page.locator(".c-result-title")).toHaveText("Đã yêu cầu thanh toán!");
    expect(billRequested).toBe(true);
  });

  test("8. Multi-client session: displays server 400 error when table session truly has no orders", async ({ page }) => {
    await page.route("**/api/v1/customer/sessions/10/bill-requests", (route) => {
      return route.fulfill({
        status: 400,
        contentType: "application/problem+json",
        body: JSON.stringify({
          title: "Yêu cầu không hợp lệ",
          status: 400,
          detail: "Bàn chưa có món nào được đặt để yêu cầu thanh toán.",
        }),
      });
    });

    await page.addInitScript(() => {
      sessionStorage.setItem(
        "lecker_customer_session",
        JSON.stringify({ sessionId: "10", token: "mock-token-client-b", tableNumber: "04", clientStorageId: "client-b" })
      );
    });

    await page.goto("/#customer");
    await page.getByRole("button", { name: "Phục vụ" }).click();
    await page.getByRole("button", { name: "YÊU CẦU HÓA ĐƠN" }).click();
    await expect(page.locator(".c-result-title")).toHaveText("Yêu cầu thanh toán thất bại");
    await expect(page.locator(".c-result-detail")).toContainText("Bàn chưa có món nào được đặt để yêu cầu thanh toán.");
  });

  test("9. Customer feedback form requires identity fields and submits feedback", async ({ page }) => {
    let submittedBody = null;
    await page.route("**/api/v1/customer/feedback", (route) => {
      submittedBody = route.request().postDataJSON();
      return route.fulfill({
        status: 201,
        contentType: "application/json",
        body: JSON.stringify({ id: "fb-123", message: "Cảm ơn bạn đã gửi phản hồi!" }),
      });
    });

    await page.goto("/#customer");

    // Navigate to Feedback tab
    await page.getByRole("button", { name: "Đánh giá" }).click();
    await expect(page.getByRole("heading", { name: "Đánh giá trải nghiệm" })).toBeVisible();

    await page.locator("#feedback-content").fill("Nội dung hợp lệ nhưng còn thiếu người gửi.");
    await expect(page.getByRole("button", { name: "GỬI PHẢN HỒI NGAY" })).toBeDisabled();

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

  test("10. Preserves unsubmitted cart items across retry with immutable attempt and separate batch for new items", async ({ page }) => {
    let orderCallCount = 0;
    const recordedKeys = [];
    const recordedPayloads = [];

    await page.route("**/api/v1/customer/sessions/10/orders", async (route) => {
      orderCallCount += 1;
      const key = route.request().headers()["idempotency-key"];
      const payload = route.request().postDataJSON();
      recordedKeys.push(key);
      recordedPayloads.push(payload);

      if (orderCallCount === 1) {
        // First attempt fails with network/server drop (500)
        return route.fulfill({
          status: 500,
          contentType: "application/json",
          body: JSON.stringify({ detail: "Mạng chập chờn, kết nối gián đoạn." }),
        });
      }

      if (orderCallCount === 2) {
        // Retry of attempt 1 succeeds for Item A
        return route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            id: "1005",
            sessionId: "10",
            totalAmount: "45000",
            sessionTotalAmount: "45000",
            editableUntil: new Date(Date.now() + 180000).toISOString(),
            items: [{
              id: "item-5",
              menuItemId: "101",
              name: "Caramel Latte",
              unitPrice: "45000",
              lineTotal: "45000",
              quantity: 1,
              version: "0",
              status: "SUBMITTED",
            }],
          }),
        });
      }

      // Order call 3 is for separate Item B
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          id: "1006",
          sessionId: "10",
          totalAmount: "35000",
          sessionTotalAmount: "80000",
          editableUntil: new Date(Date.now() + 180000).toISOString(),
          items: [{
            id: "item-6",
            menuItemId: "102",
            name: "Bạc xỉu Sài Gòn",
            unitPrice: "35000",
            lineTotal: "35000",
            quantity: 1,
            version: "0",
            status: "SUBMITTED",
          }],
        }),
      });
    });

    await page.goto("/#customer");

    // Add Item 1 (Caramel Latte) to cart
    await page.getByLabel("Thêm Caramel Latte vào giỏ hàng").click();
    await page.getByRole("button", { name: /Giỏ hàng/ }).click();

    // Click checkout -> Attempt 1 (will fail)
    await page.getByRole("button", { name: /GỬI ĐƠN ĐẶT MÓN/ }).click();
    await expect(page.locator(".c-result-title")).toHaveText("Đặt món thất bại");
    await page.getByRole("button", { name: "ĐÓNG" }).click();

    // While attempt 1 is unconfirmed, user adds Item 2 (Bạc xỉu Sài Gòn)
    await page.getByRole("button", { name: "Thực đơn", exact: true }).click();
    await page.getByLabel("Thêm Bạc xỉu Sài Gòn vào giỏ hàng").click();

    // Navigate to Cart and click Retry
    await page.getByRole("button", { name: /Giỏ hàng/ }).click();
    await page.getByRole("button", { name: /GỬI ĐƠN ĐẶT MÓN/ }).click();

    // Success modal appears for Item A!
    await expect(page.locator(".c-result-title")).toHaveText("Đặt món thành công!");
    await page.getByRole("button", { name: "TIẾP TỤC CHỌN MÓN" }).click();

    // Verify Attempt 1 and Retry 2 had the EXACT SAME idempotency key and payload (only Item A!)
    expect(recordedKeys[0]).toBeDefined();
    expect(recordedKeys[1]).toBe(recordedKeys[0]); // SAME IDEMPOTENCY KEY
    expect(recordedPayloads[1].items).toHaveLength(1);
    expect(recordedPayloads[1].items[0].menuItemId).toBe(101); // ONLY ITEM A, B not mixed!

    // Verify cart still preserves Item B (Bạc xỉu Sài Gòn)
    await page.getByRole("button", { name: /Giỏ hàng/ }).click();
    await expect(page.getByText("Bạc xỉu Sài Gòn")).toBeVisible();
    await expect(page.getByRole("button", { name: /Giỏ hàng/ }).locator(".c-dock-badge")).toHaveText("1");

    // Now submit Item B as its own separate batch
    await page.getByRole("button", { name: /GỬI ĐƠN ĐẶT MÓN/ }).click();
    await expect(page.locator(".c-result-title")).toHaveText("Đặt món thành công!");
    await page.getByRole("button", { name: "TIẾP TỤC CHỌN MÓN" }).click();

    // Verify Attempt 3 had a NEW idempotency key and only Item B
    expect(recordedKeys[2]).not.toBe(recordedKeys[0]); // NEW IDEMPOTENCY KEY
    expect(recordedPayloads[2].items[0].menuItemId).toBe(102);

    // Cart is now empty
    await page.getByRole("button", { name: /Giỏ hàng/ }).click();
    await expect(page.getByText("Giỏ hàng trống")).toBeVisible();
  });

  test("11. Menu pagination: handles 503 retry without skipping pages and preserves filter switch", async ({ page }) => {
    // Generate 55 mock items
    const all55Items = Array.from({ length: 55 }, (_, i) => ({
      id: String(200 + i),
      code: `CF${i + 1}`,
      name: `Món cà phê số ${i + 1}`,
      price: "40000",
      available: true,
      categoryId: i < 30 ? "1" : "2",
      categoryName: i < 30 ? "Cà phê" : "Trà & Trái cây",
    }));

    let page1Attempts = 0;

    await page.route(/\/api\/v1\/customer\/menu\/items(\?.*)?$/, (route) => {
      const url = new URL(route.request().url());
      const pageIndex = Number(url.searchParams.get("page") || "0");
      const pageSize = Number(url.searchParams.get("size") || "20");
      const categoryId = url.searchParams.get("categoryId");

      // Simulate temporary 503 on first try of page 1
      if (pageIndex === 1 && categoryId === null && page1Attempts === 0) {
        page1Attempts += 1;
        return route.fulfill({
          status: 503,
          contentType: "application/json",
          body: JSON.stringify({ detail: "Dịch vụ tạm thời bận. Vui lòng thử lại." }),
        });
      }

      let filtered = all55Items;
      if (categoryId) {
        filtered = filtered.filter((it) => it.categoryId === categoryId);
      }

      const start = pageIndex * pageSize;
      const slice = filtered.slice(start, start + pageSize);

      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          content: slice,
          totalElements: filtered.length,
          page: pageIndex,
          size: pageSize,
        }),
      });
    });

    await page.goto("/#customer");

    // Page 0: Displays 20 of 55 items
    await expect(page.getByText("Hiển thị: 20/55 món")).toBeVisible();
    await expect(page.getByText("Món cà phê số 1", { exact: true })).toBeVisible();

    // Click load more -> page 1 fails with 503
    await page.getByRole("button", { name: /XEM THÊM MÓN/ }).click();
    await expect(page.getByText(/Dịch vụ tạm thời bận/)).toBeVisible();

    // Click load more AGAIN to retry page 1 -> succeeds!
    await page.getByRole("button", { name: /XEM THÊM MÓN/ }).click();
    await expect(page.getByText("Hiển thị: 40/55 món")).toBeVisible();
    // Confirms items 21-40 were NOT skipped:
    await expect(page.getByText("Món cà phê số 21")).toBeVisible();

    // Click load more -> loads page 2 (items 41-55)
    await page.getByRole("button", { name: /XEM THÊM MÓN/ }).click();
    await expect(page.getByText("Hiển thị: 55/55 món")).toBeVisible();
    await expect(page.getByText("Món cà phê số 51")).toBeVisible();

    // Button disappears when all 55 items are loaded
    await expect(page.getByRole("button", { name: /XEM THÊM MÓN/ })).toHaveCount(0);

    // Switch category filter to Trà & Trái cây (25 items)
    await page.getByRole("tab", { name: /Trà & Trái cây/ }).click();
    await expect(page.getByText("Hiển thị: 20/25 món")).toBeVisible();
    await expect(page.getByText("Món cà phê số 31")).toBeVisible();

    // Load more button for new category is NOT locked
    await expect(page.getByRole("button", { name: /XEM THÊM MÓN/ })).toBeEnabled();
    await page.getByRole("button", { name: /XEM THÊM MÓN/ }).click();
    await expect(page.getByText("Hiển thị: 25/25 món")).toBeVisible();
  });

  test("12. Clears a rejected order attempt before submitting a different cart", async ({ page }) => {
    const keys = [];
    const payloads = [];
    let calls = 0;
    await page.route("**/api/v1/customer/sessions/10/orders", (route) => {
      calls += 1;
      keys.push(route.request().headers()["idempotency-key"]);
      payloads.push(route.request().postDataJSON());
      if (calls === 1) {
        return route.fulfill({
          status: 409,
          contentType: "application/problem+json",
          body: JSON.stringify({ detail: "Món đã ngừng phục vụ." }),
        });
      }
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          id: "1010",
          sessionId: "10",
          totalAmount: "35000",
          sessionTotalAmount: "35000",
          editableUntil: new Date(Date.now() + 180000).toISOString(),
          items: [{ id: "item-10", menuItemId: "102", name: "Bạc xỉu Sài Gòn", unitPrice: "35000", lineTotal: "35000", quantity: 1, version: "0", status: "SUBMITTED" }],
        }),
      });
    });

    await page.goto("/#customer");
    await page.getByLabel("Thêm Caramel Latte vào giỏ hàng").click();
    await page.getByRole("button", { name: /Giỏ hàng/ }).click();
    await page.getByRole("button", { name: /GỬI ĐƠN ĐẶT MÓN/ }).click();
    await expect(page.locator(".c-result-detail")).toContainText("Món đã ngừng phục vụ");
    await page.getByRole("button", { name: "ĐÓNG", exact: true }).click();
    await page.getByLabel("Xóa Caramel Latte khỏi giỏ").click();
    await page.getByRole("button", { name: "Thực đơn", exact: true }).click();
    await page.getByLabel("Thêm Bạc xỉu Sài Gòn vào giỏ hàng").click();
    await page.getByRole("button", { name: /Giỏ hàng/ }).click();
    await page.getByRole("button", { name: /GỬI ĐƠN ĐẶT MÓN/ }).click();

    await expect(page.locator(".c-result-title")).toHaveText("Đặt món thành công!");
    expect(keys[1]).not.toBe(keys[0]);
    expect(payloads[1].items).toEqual([{ menuItemId: 102, quantity: 1 }]);
  });

  test("13. Keeps unavailable menu items visible but impossible to add", async ({ page }) => {
    await page.route(/\/api\/v1\/customer\/menu\/items(\?.*)?$/, (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          content: [{ id: "103", code: "CF03", name: "Cold Brew", price: "50000", available: false, categoryId: "1", categoryName: "Cà phê" }],
          totalElements: 1,
          page: 0,
          size: 20,
        }),
      })
    );

    await page.goto("/#customer");
    await page.getByLabel("Chỉ món có sẵn").uncheck();
    const quickAdd = page.getByRole("button", { name: "Cold Brew tạm hết", exact: true });
    await expect(quickAdd).toBeDisabled();
    await page.getByText("Cold Brew", { exact: true }).click();
    await expect(page.getByRole("button", { name: "MÓN TẠM HẾT" })).toBeDisabled();
    await expect(page.locator(".c-dock-badge")).toHaveCount(0);
  });

  test("14. Uses the server clock for the three-minute edit window", async ({ page }) => {
    await page.addInitScript(() => {
      const actualNow = Date.now.bind(Date);
      Date.now = () => actualNow() + 5 * 60 * 1000;
    });
    await page.route("**/api/v1/customer/sessions/10/orders", (route) => {
      const serverNow = new Date();
      return route.fulfill({
        status: 200,
        headers: { Date: serverNow.toUTCString() },
        contentType: "application/json",
        body: JSON.stringify({
          id: "1011",
          sessionId: "10",
          totalAmount: "45000",
          sessionTotalAmount: "45000",
          editableUntil: new Date(serverNow.getTime() + 180000).toISOString(),
          items: [{ id: "item-11", menuItemId: "101", name: "Caramel Latte", unitPrice: "45000", lineTotal: "45000", quantity: 1, version: "0", status: "SUBMITTED" }],
        }),
      });
    });

    await page.goto("/#customer");
    await page.getByLabel("Thêm Caramel Latte vào giỏ hàng").click();
    await page.getByRole("button", { name: /Giỏ hàng/ }).click();
    await page.getByRole("button", { name: /GỬI ĐƠN ĐẶT MÓN/ }).click();
    await page.getByRole("button", { name: "XEM ĐƠN MÓN" }).click();
    await expect(page.locator(".c-timer-badge")).toContainText(/Sửa\/hủy: 02:5\d/);
    await expect(page.getByTitle("Sửa số lượng")).toBeVisible();
  });

  test("15. Formats DECIMAL(18,0) prices without Number precision loss", async ({ page }) => {
    await page.route(/\/api\/v1\/customer\/menu\/items(\?.*)?$/, (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          content: [{ id: "104", code: "CF04", name: "Gói sự kiện", price: "999999999999999999", available: true, categoryId: "1", categoryName: "Cà phê" }],
          totalElements: 1,
          page: 0,
          size: 20,
        }),
      })
    );

    await page.goto("/#customer");
    await expect(page.getByText("999.999.999.999.999.999 đ")).toBeVisible();
    await page.getByLabel("Thêm Gói sự kiện vào giỏ hàng").click();
    await page.getByRole("button", { name: /Giỏ hàng/ }).click();
    await expect(page.locator(".c-cart-price")).toHaveText("999.999.999.999.999.999 đ");
    await expect(page.locator(".c-summary-total-price")).toHaveText("999.999.999.999.999.999 đ");
  });

  test("16. Ignores a late load-more response from the previous filter", async ({ page }) => {
    let releaseOldPage;
    let oldPageStarted = false;
    const oldPageGate = new Promise((resolve) => { releaseOldPage = resolve; });
    const page0 = Array.from({ length: 20 }, (_, index) => ({
      id: String(300 + index), code: `OLD${index}`, name: `Món cũ ${index + 1}`, price: "40000", available: true, categoryId: "1", categoryName: "Cà phê",
    }));
    const page1 = Array.from({ length: 5 }, (_, index) => ({
      id: String(400 + index), code: `LATE${index}`, name: `Món phản hồi trễ ${index + 1}`, price: "41000", available: true, categoryId: "1", categoryName: "Cà phê",
    }));
    const tea = [{ id: "500", code: "TEA1", name: "Trà đúng bộ lọc", price: "30000", available: true, categoryId: "2", categoryName: "Trà & Trái cây" }];

    await page.route(/\/api\/v1\/customer\/menu\/items(\?.*)?$/, async (route) => {
      const url = new URL(route.request().url());
      const categoryId = url.searchParams.get("categoryId");
      const pageIndex = Number(url.searchParams.get("page") || "0");
      if (!categoryId && pageIndex === 1) {
        oldPageStarted = true;
        await oldPageGate;
        return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ content: page1, totalElements: 25, page: 1, size: 20 }) });
      }
      const content = categoryId === "2" ? tea : page0;
      return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ content, totalElements: categoryId === "2" ? 1 : 25, page: 0, size: 20 }) });
    });

    await page.goto("/#customer");
    await page.getByRole("button", { name: /XEM THÊM MÓN/ }).click();
    await expect.poll(() => oldPageStarted).toBe(true);
    await page.getByRole("tab", { name: /Trà & Trái cây/ }).click();
    await expect(page.getByText("Trà đúng bộ lọc")).toBeVisible();
    releaseOldPage();
    await page.waitForTimeout(100);
    await expect(page.getByText("Món phản hồi trễ 1")).toHaveCount(0);
    await expect(page.getByText("Trà đúng bộ lọc")).toBeVisible();
  });
});
