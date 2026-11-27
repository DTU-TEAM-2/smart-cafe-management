import { expect, test } from "@playwright/test";

const admin = {
  id: "3",
  username: "admin-user",
  name: "Quản lý mẫu",
  email: "admin@example.invalid",
  role: "ADMIN",
  salary: "15000000",
};

const customer = {
  id: "4",
  username: "customer-user",
  name: "Khách mẫu",
  email: "cust@example.invalid",
  role: "CUSTOMER",
  salary: null,
};

const mockBills = [
  {
    id: 1,
    billCode: "BILL-2026-001",
    tableId: 1,
    tableCode: "T01",
    tableName: "Bàn 01",
    sessionId: 101,
    creatorId: 2,
    creatorName: "Nhân viên A",
    totalAmount: 180000,
    status: "PAID",
    createdAt: "2026-11-10T14:30:00Z",
    paidAt: "2026-11-10T15:00:00Z",
  },
  {
    id: 2,
    billCode: "BILL-2026-002",
    tableId: 2,
    tableCode: "T02",
    tableName: "Bàn 02",
    sessionId: 102,
    creatorId: 2,
    creatorName: "Nhân viên A",
    totalAmount: 95000,
    status: "PAID",
    createdAt: "2026-11-11T09:15:00Z",
    paidAt: "2026-11-11T09:45:00Z",
  },
];

const mockBillDetail1 = {
  id: 1,
  billCode: "BILL-2026-001",
  tableId: 1,
  tableCode: "T01",
  tableName: "Bàn 01",
  sessionId: 101,
  creatorId: 2,
  creatorName: "Nhân viên A",
  totalAmount: 180000,
  status: "PAID",
  createdAt: "2026-11-10T14:30:00Z",
  paidAt: "2026-11-10T15:00:00Z",
  items: [
    { itemId: 10, itemName: "Cà phê sữa đá", quantity: 2, unitPrice: 35000, lineTotal: 70000 },
    { itemId: 11, itemName: "Bạc xỉu", quantity: 1, unitPrice: 40000, lineTotal: 40000 },
    { itemId: 12, itemName: "Bánh croissant", quantity: 1, unitPrice: 70000, lineTotal: 70000 },
  ],
};

const mockStats = {
  summary: {
    from: "2026-11-01",
    to: "2026-11-11",
    timezone: "Asia/Ho_Chi_Minh",
    totalRevenue: "275000",
    totalBills: 2,
    averageBillAmount: "137500",
  },
  dailyRevenue: [
    { date: "2026-11-10", revenue: "180000", billCount: 1 },
    { date: "2026-11-11", revenue: "95000", billCount: 1 },
  ],
  revenueByPaymentMethod: [
    { method: "CASH", revenue: "180000", billCount: 1 },
    { method: "BANK_TRANSFER", revenue: "95000", billCount: 1 },
  ],

};

async function setToken(page, token) {
  await page.evaluate(value => {
    if (value) sessionStorage.setItem("smart_cafe_token", value);
    else sessionStorage.removeItem("smart_cafe_token");
    window.dispatchEvent(new Event("smart-cafe-auth-change"));
  }, token);
}

async function navigateToBills(page) {
  const mobileToggle = page.getByRole("button", { name: "Điều hướng" });
  if (await mobileToggle.isVisible()) {
    await mobileToggle.click();
  }
  const billsNav = page.getByRole("link", { name: "Quản lý hóa đơn" });
  await expect(billsNav).toBeVisible();
  await billsNav.click();
}

test.describe("DT2-8 Quản lý, xem hóa đơn và biểu đồ thống kê", () => {
  test.beforeEach(async ({ page }) => {
    await page.route("**/api/v1/auth/me", route => {
      const token = route.request().headers().authorization;
      const role = token === "Bearer staff-token" ? "STAFF" : token === "Bearer cust-token" ? "CUSTOMER" : "ADMIN";
      return route.fulfill({ json: { ...(role === "CUSTOMER" ? customer : admin), role } });
    });
    await page.route("**/api/v1/home/highlights", route =>
      route.fulfill({ json: { newest: [], mostOrdered: [] } })
    );
    await page.route("**/api/v1/home/notifications", route =>
      route.fulfill({ json: { recipientId: "3", items: [] } })
    );
  });

  test("Customers cannot access bill management", async ({ page }) => {
    await page.route("**/api/v1/home/account", route =>
      route.fulfill({ json: customer })
    );

    await page.goto("/#bills");
    await setToken(page, "cust-token");

    // Nav bar should not show "Quản lý hóa đơn"
    await expect(page.getByRole("link", { name: "Quản lý hóa đơn" })).toHaveCount(0);
    // Page should display restricted access notice
    await expect(page.getByText("Khu vực dành cho nhân viên và Quản trị viên (ADMIN).")).toBeVisible();
  });

  test("Admin user can navigate to bills, view table, and see formatted amounts", async ({ page }) => {
    await page.route("**/api/v1/home/account", route =>
      route.fulfill({ json: admin })
    );
    await page.route("**/api/v1/bills**", route => {
      return route.fulfill({
        json: {
          content: mockBills,
          totalElements: 2,
          totalPages: 1,
          page: 0,
          size: 10,
        },
      });
    });

    await page.goto("/");
    await setToken(page, "admin-token");
    await navigateToBills(page);

    await expect(page.getByRole("heading", { name: "Hóa đơn", exact: true })).toBeVisible();
    await page.getByRole("link", { name: "Bỏ qua điều hướng" }).focus();
    await page.keyboard.press("Enter");
    await expect(page.getByRole("main")).toBeFocused();
    await expect(page).toHaveURL(/#bills$/);
    await expect(page.getByText("BILL-2026-001")).toBeVisible();
    await expect(page.getByText("BILL-2026-002")).toBeVisible();
    await expect(page.getByText("180.000 ₫")).toBeVisible();
    await expect(page.getByText("95.000 ₫")).toBeVisible();
    await page.getByRole("main").evaluate(element => element.blur());
    await page.evaluate(() => document.fonts.ready.then(() => true));

    if (page.viewportSize()?.width === 1440) {
      await page.screenshot({ path: test.info().outputPath("dt2-8-bills-desktop.png"), fullPage: true });
    } else {
      await page.screenshot({ path: test.info().outputPath("dt2-8-bills-mobile.png"), fullPage: true });
    }
  });

  test("Empty search input triggers 'Please enter a keyword!' notice", async ({ page }) => {
    await page.route("**/api/v1/home/account", route =>
      route.fulfill({ json: admin })
    );
    await page.route("**/api/v1/bills**", route =>
      route.fulfill({
        json: { content: mockBills, totalElements: 2, totalPages: 1, page: 0, size: 10 },
      })
    );

    await page.goto("/#bills");
    await setToken(page, "admin-token");

    // Search by ID with empty input
    const searchIdBtn = page.getByRole("button", { name: "Search by ID" });
    await searchIdBtn.click();
    await expect(page.getByText("Please enter a keyword!")).toBeVisible();

    // Switch to Search by Date
    await page.getByRole("radio", { name: "Search by Date" }).check();
    const searchDateBtn = page.getByRole("button", { name: "Search by Date" });
    await searchDateBtn.click();
    await expect(page.getByText("Please enter a keyword!")).toBeVisible();
  });

  test("Search returns 'Not found!' when no bills match, Show List Bill resets", async ({ page }) => {
    let requestedCode = null;
    await page.route("**/api/v1/home/account", route =>
      route.fulfill({ json: admin })
    );
    await page.route("**/api/v1/bills**", route => {
      const url = new URL(route.request().url());
      requestedCode = url.searchParams.get("code");
      if (requestedCode === "NON-EXISTENT") {
        return route.fulfill({
          json: { content: [], totalElements: 0, totalPages: 0, page: 0, size: 10 },
        });
      }
      return route.fulfill({
        json: { content: mockBills, totalElements: 2, totalPages: 1, page: 0, size: 10 },
      });
    });

    await page.goto("/#bills");
    await setToken(page, "admin-token");

    const input = page.getByLabel("Từ khóa tìm kiếm hóa đơn");
    await input.fill("NON-EXISTENT");
    await page.getByRole("button", { name: "Search by ID" }).click();

    await expect(page.getByText("Not found!")).toBeVisible();
    await expect(page.getByText("Không có hóa đơn nào.")).toBeVisible();

    // Click Show List Bill
    await page.getByRole("button", { name: "Show List Bill" }).click();
    await expect(page.getByText("BILL-2026-001")).toBeVisible();
    await expect(page.getByText("Not found!")).toHaveCount(0);
  });

  test("Detail button without row selection warns user, selection opens detail modal", async ({ page }) => {
    await page.route("**/api/v1/home/account", route =>
      route.fulfill({ json: admin })
    );
    await page.route("**/api/v1/bills**", route => {
      const url = new URL(route.request().url());
      if (url.pathname.endsWith("/1")) {
        return route.fulfill({ json: mockBillDetail1 });
      }
      return route.fulfill({
        json: { content: mockBills, totalElements: 2, totalPages: 1, page: 0, size: 10 },
      });
    });

    await page.goto("/#bills");
    await setToken(page, "admin-token");

    // Click Detail without selecting a row
    const detailBtn = page.getByRole("button", { name: "Xem chi tiết" });
    await detailBtn.click();
    await expect(page.getByText("Vui lòng chọn hóa đơn cụ thể!")).toBeVisible();

    // Click row 1
    const row1 = page.getByText("BILL-2026-001");
    await row1.click();

    // Now click Detail
    await detailBtn.click();

    // Modal dialog opens
    const dialog = page.getByRole("dialog", { name: /Chi tiết hóa đơn/ });
    await expect(dialog).toBeVisible();
    await expect(dialog.getByText("Cà phê sữa đá")).toBeVisible();
    await expect(dialog.getByText("Bạc xỉu")).toBeVisible();
    await expect(dialog.getByText("Bánh croissant")).toBeVisible();
    await expect(dialog.getByRole("button", { name: "In hóa đơn" })).toBeVisible();
    await page.emulateMedia({ media: "print" });
    await expect(dialog.getByText("Cà phê sữa đá")).toBeVisible();
    await expect(dialog.getByRole("button", { name: "In hóa đơn" })).toBeHidden();
    await page.emulateMedia({ media: "screen" });
    await page.evaluate(() => document.fonts.ready.then(() => true));

    if (page.viewportSize()?.width === 1440) {
      await page.screenshot({ path: test.info().outputPath("dt2-8-detail-modal-desktop.png") });
    }

    // Close modal
    await page.keyboard.press("Escape");
    await expect(dialog).toHaveCount(0);
    await expect(detailBtn).toBeFocused();
  });

  test("Pagination uses the last submitted search rather than the edited input", async ({ page }) => {
    await page.route("**/api/v1/home/account", route => route.fulfill({ json: admin }));
    await page.route("**/api/v1/bills?**", route => {
      const query = new URL(route.request().url()).searchParams;
      const current = Number(query.get("page"));
      return route.fulfill({
        json: { content: [mockBills[current]], totalElements: 11, totalPages: 2, page: current },
      });
    });
    await page.goto("/#bills");
    await setToken(page, "admin-token");
    const keyword = page.getByRole("textbox", { name: "Từ khóa tìm kiếm hóa đơn" });
    await keyword.fill("BILL");
    await page.getByRole("button", { name: "Search by ID" }).click();
    await expect(page.getByText("BILL-2026-001")).toBeVisible();
    await keyword.fill("NOT-SUBMITTED");
    const next = page.waitForRequest(request => {
      const url = new URL(request.url());
      return url.pathname === "/api/v1/bills" && url.searchParams.get("page") === "1";
    });
    await page.getByRole("button", { name: "Trang tiếp theo" }).click();
    expect(new URL((await next).url()).searchParams.get("code")).toBe("BILL");
    await expect(page.getByText("BILL-2026-002")).toBeVisible();
  });

  test("Retries the selected bill after a connection failure", async ({ page }) => {
    await page.route("**/api/v1/home/account", route => route.fulfill({ json: admin }));
    await page.route("**/api/v1/bills?**", route => route.fulfill({
      json: { content: mockBills, totalElements: 2, totalPages: 1, page: 0 },
    }));
    let attempts = 0;
    await page.route("**/api/v1/bills/1", route => {
      attempts += 1;
      return attempts === 1 ? route.abort("failed") : route.fulfill({ json: mockBillDetail1 });
    });

    await page.goto("/#bills");
    await setToken(page, "admin-token");
    await page.getByRole("button", { name: "BILL-2026-001" }).click();
    await page.getByRole("button", { name: "Xem chi tiết", exact: true }).click();
    await expect(page.getByRole("alert")).toContainText("Không kết nối được máy chủ");
    await expect(page.getByRole("dialog")).toHaveCount(0);
    await page.getByRole("button", { name: "Thử lại chi tiết" }).click();
    await expect(page.getByRole("dialog")).toContainText("BILL-2026-001");
    expect(attempts).toBe(2);
  });

  test("Invalid list and mismatched detail responses offer retry without displaying wrong bills", async ({ page }) => {
    await page.route("**/api/v1/home/account", route => route.fulfill({ json: admin }));
    let list = {};
    await page.route("**/api/v1/bills?**", route => route.fulfill({
      json: list,
    }));
    let wrongDetail = true;
    await page.route("**/api/v1/bills/1", route => route.fulfill({
      json: wrongDetail ? { ...mockBillDetail1, id: 2, billCode: "WRONG-BILL" } : mockBillDetail1,
    }));

    await page.goto("/#bills");
    await setToken(page, "admin-token");
    await expect(page.getByRole("alert")).toContainText("Phản hồi danh sách hóa đơn không hợp lệ");
    list = { content: [{ ...mockBills[0], creatorName: { invalid: true } }], totalElements: 1, totalPages: 1, page: 0 };
    await page.getByRole("button", { name: "Thử lại", exact: true }).click();
    await expect(page.getByRole("alert")).toContainText("Phản hồi danh sách hóa đơn không hợp lệ");
    list = { content: mockBills, totalElements: 2, totalPages: 1, page: 0 };
    await page.getByRole("button", { name: "Thử lại", exact: true }).click();
    await page.getByRole("button", { name: "BILL-2026-001" }).click();
    await page.getByRole("button", { name: "Xem chi tiết", exact: true }).click();
    await expect(page.getByRole("alert")).toContainText("Phản hồi chi tiết hóa đơn không hợp lệ");
    await expect(page.getByRole("dialog")).toHaveCount(0);
    wrongDetail = false;
    await page.getByRole("button", { name: "Thử lại chi tiết" }).click();
    await expect(page.getByRole("dialog")).toContainText("BILL-2026-001");
  });

  test("Invalid revenue breakdown offers retry instead of crashing the statistics page", async ({ page }) => {
    await page.route("**/api/v1/home/account", route => route.fulfill({ json: admin }));
    let invalidBreakdown = true;
    await page.route("**/api/v1/statistics/revenue**", route => route.fulfill({
      json: invalidBreakdown ? { ...mockStats, revenueByPaymentMethod: null } : mockStats,
    }));
    await page.goto("/#statistics");
    await setToken(page, "admin-token");
    await expect(page.getByRole("alert")).toContainText("Phản hồi thống kê doanh thu không hợp lệ");
    invalidBreakdown = false;
    await page.getByRole("button", { name: "Thử lại", exact: true }).click();
    await expect(page.getByRole("list", { name: "Tỷ trọng doanh thu theo phương thức thanh toán" })).toBeVisible();
  });

  test("Statistics route renders column chart and summary KPI cards", async ({ page }) => {
    await page.route("**/api/v1/home/account", route =>
      route.fulfill({ json: admin })
    );
    await page.route("**/api/v1/bills**", route =>
      route.fulfill({
        json: { content: mockBills, totalElements: 2, totalPages: 1, page: 0, size: 10 },
      })
    );
    await page.route("**/api/v1/statistics/revenue**", route =>
      route.fulfill({ json: mockStats })
    );

    await page.goto("/#bills");
    await setToken(page, "admin-token");

    // Switch to Statistics tab
    const statsTab = page.getByRole("link", { name: "Thống kê doanh thu" });
    await statsTab.click();

    // Verify KPI cards
    await expect(page.getByText("275.000 ₫")).toBeVisible();
    await expect(page.getByText("137.500 ₫")).toBeVisible();

    // Verify charts
    await expect(page.getByRole("img", { name: "Biểu đồ cột: Doanh thu theo ngày" })).toBeVisible();
    const breakdown = page.getByRole("list", { name: "Tỷ trọng doanh thu theo phương thức thanh toán" });
    await expect(breakdown.getByText("Tiền mặt")).toBeVisible();
    await expect(breakdown.getByText(/65,5%/)).toBeVisible();
    await expect(breakdown.getByText(/34,5%/)).toBeVisible();
    await expect(page).toHaveURL(/#statistics$/);
    await page.evaluate(() => document.fonts.ready.then(() => true));

    if (page.viewportSize()?.width === 1440) {
      await page.screenshot({ path: test.info().outputPath("dt2-8-statistics-desktop.png"), fullPage: true });
    }
  });

  test("Year chart groups days by month without overflowing a 320px page", async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 720 });
    await page.route("**/api/v1/home/account", route => route.fulfill({ json: admin }));
    await page.route("**/api/v1/statistics/revenue**", route => {
      if (!route.request().url().includes("in_year")) return route.fulfill({ json: mockStats });
      const dailyRevenue = Array.from({ length: 365 }, (_, index) => ({
        date: new Date(Date.UTC(2026, 0, index + 1)).toISOString().slice(0, 10),
        revenue: index < 2 ? "1500" : "0",
        billCount: index < 2 ? 1 : 0,
      }));
      return route.fulfill({ json: {
        summary: { ...mockStats.summary, from: "2026-01-01", to: "2026-12-31", totalRevenue: "3000", averageBillAmount: "1500" },
        dailyRevenue,
        revenueByPaymentMethod: [{ method: "CASH", revenue: "3000", billCount: 2 }],
      } });
    });

    await page.goto("/#statistics");
    await setToken(page, "admin-token");
    await page.getByRole("radio", { name: "Năm nay (In Year)" }).check();
    const chart = page.getByRole("img", { name: "Biểu đồ cột: Doanh thu theo tháng" });
    await expect(chart.locator(".billing-column-bar")).toHaveCount(12);
    await expect(chart.locator(".billing-column-bar-group").first()).toHaveAttribute("title", /2026-01: 3\.000.*2 hóa đơn/);
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(320);
  });

  test("Staff sees bill detail without print or revenue controls", async ({ page }) => {
    await page.route("**/api/v1/home/account", route => route.fulfill({ json: { ...admin, role: "STAFF" } }));
    await page.route("**/api/v1/bills?**", route => route.fulfill({
      json: { content: mockBills, totalElements: 2, totalPages: 1, page: 0 },
    }));
    await page.route("**/api/v1/bills/1", route => route.fulfill({ json: mockBillDetail1 }));
    let revenueRequests = 0;
    await page.route("**/api/v1/statistics/revenue**", route => {
      revenueRequests += 1;
      return route.fulfill({ status: 403 });
    });
    await page.goto("/#bills");
    await setToken(page, "staff-token");
    await expect(page.getByText("KHÔNG GIAN NHÂN VIÊN")).toBeVisible();
    await expect(page.getByRole("link", { name: "Thống kê doanh thu" })).toHaveCount(0);
    await page.getByRole("button", { name: "BILL-2026-001" }).click();
    await page.getByRole("button", { name: "Xem chi tiết", exact: true }).click();
    const detail = page.getByRole("dialog");
    await expect(detail).toContainText("BILL-2026-001");
    await expect(detail.getByRole("button", { name: "In hóa đơn" })).toHaveCount(0);
    await page.goto("/#statistics");
    await expect(page.getByText("Chỉ Quản trị viên (ADMIN) được xem thống kê.")).toBeVisible();
    expect(revenueRequests).toBe(0);
  });

  for (const [role, target] of [["ADMIN", "#statistics"], ["STAFF", "#bills"]]) {
    test(`DT2-8 ${role} checks REST denial and expires only a revoked session`, async ({ page }) => {
      let revoked = false;
      let denied = false;
      let authChecks = 0;
      const token = role === "STAFF" ? "staff-token" : "admin-token";
      await page.addInitScript(value => sessionStorage.setItem("smart_cafe_token", value), token);
      await page.route("**/api/v1/auth/me", route => {
        authChecks++;
        return route.fulfill(revoked ? { status: 401, json: {} } : { json: { ...admin, role } });
      });
      await page.route("**/api/v1/home/account", route => route.request().headers().authorization
        ? route.fulfill({ json: { ...admin, role } }) : route.fulfill({ status: 401, json: {} }));
      const path = target === "#statistics" ? "**/api/v1/statistics/revenue?**" : "**/api/v1/bills?**";
      const payload = target === "#statistics" ? mockStats : { content: mockBills, totalElements: 2, totalPages: 1, page: 0 };
      await page.route(path, route => denied ? route.fulfill({ status: 403, json: {} }) : route.fulfill({ json: payload }));
      await page.goto("/" + target);
      await expect(page.locator(".billing-content")).toBeVisible();
      await expect.poll(() => authChecks).toBeGreaterThan(0);
      await expect(page.getByText(target === "#statistics" ? "275.000 ₫" : "BILL-2026-001", { exact: true })).toBeVisible();
      denied = true;
      const initial = authChecks;
      if (target === "#statistics") await page.getByRole("radio", { name: "Năm nay (In Year)" }).check();
      else await page.getByRole("button", { name: "Show List Bill" }).click();
      await expect(page.getByRole("alert")).toContainText("Bạn không có quyền");
      await expect.poll(() => authChecks).toBeGreaterThan(initial);
      expect(await page.evaluate(() => sessionStorage.getItem("smart_cafe_token"))).toBe(token);
      revoked = true;
      await page.getByRole("button", { name: "Thử lại", exact: true }).click();
      await expect(page.locator(".billing-content")).toHaveCount(0);
      await expect.poll(() => page.evaluate(() => sessionStorage.getItem("smart_cafe_token"))).toBeNull();
    });
  }

  test("A delayed Billing auth denial cannot clear a replacement token", async ({ page }) => {
    await page.addInitScript(() => sessionStorage.setItem("smart_cafe_token", "admin-token"));
    await page.route("**/api/v1/home/account", route => route.fulfill({ json: admin }));
    let deny = false;
    let oldCheck;
    await page.route("**/api/v1/auth/me", route => {
      if (deny && route.request().headers().authorization === "Bearer admin-token") {
        oldCheck = route;
        return;
      }
      return route.fulfill({ json: admin });
    });
    await page.route("**/api/v1/bills?**", route => {
      if (deny && route.request().headers().authorization === "Bearer admin-token") return route.fulfill({ status: 403, json: {} });
      return route.fulfill({ json: { content: mockBills, totalElements: 2, totalPages: 1, page: 0 } });
    });
    const initial = page.waitForResponse(response => response.url().endsWith("/api/v1/auth/me") && response.ok());
    await page.goto("/#bills");
    await initial;
    await expect(page.getByText("BILL-2026-001", { exact: true })).toBeVisible();
    deny = true;
    await page.getByRole("button", { name: "Show List Bill" }).click();
    await expect.poll(() => Boolean(oldCheck)).toBe(true);
    await setToken(page, "replacement-token");
    await expect(page.getByText("BILL-2026-001", { exact: true })).toBeVisible();
    await oldCheck.fulfill({ status: 401, json: {} });
    await expect(page.getByText("BILL-2026-001", { exact: true })).toBeVisible();
    expect(await page.evaluate(() => sessionStorage.getItem("smart_cafe_token"))).toBe("replacement-token");
  });


  test("Actual A4 PDF preserves long names, exact VND, all rows and repeated headers", async ({ page }, testInfo) => {
    const longName = "W".repeat(160);
    const mediumName = "M".repeat(80);
    const items = [
      { orderItemId: "9007199254740993", itemName: longName, quantity: 3, unitPrice: "123456789012345678", lineTotal: "370370367037037034" },
      { orderItemId: "9007199254740994", itemName: mediumName, quantity: 7, unitPrice: "23456789", lineTotal: "164197523" },
      ...Array.from({ length: 150 }, (_, i) => ({
        orderItemId: String(i + 10), itemName: `PRINT-ROW-${String(i + 1).padStart(3, "0")}`,
        quantity: i % 9 + 1, unitPrice: String(10000 + i), lineTotal: String((10000 + i) * (i % 9 + 1)),
      })),
    ];
    const totalAmount = items.reduce((sum, item) => sum + BigInt(item.lineTotal), 0n).toString();
    const detail = { ...mockBillDetail1, id: "1", sessionCode: "PRINT-SESSION", totalAmount, items };
    const summary = { ...mockBills[0], id: "1", totalAmount };
    await page.route("**/api/v1/home/account", route => route.fulfill({ json: admin }));
    await page.route("**/api/v1/bills?**", route => route.fulfill({
      json: { content: [summary], totalElements: 1, totalPages: 1, page: 0, size: 10 },
    }));
    await page.route("**/api/v1/bills/1", route => route.fulfill({ json: detail }));
    await page.goto("/#bills");
    await setToken(page, "admin-token");
    await page.getByRole("button", { name: summary.billCode }).click();
    await page.getByRole("button", { name: "Xem chi tiết", exact: true }).click();
    const dialog = page.getByRole("dialog");
    await expect(dialog.getByRole("button", { name: "In hóa đơn" })).toBeVisible();
    await page.evaluate(() => document.fonts.ready);
    const path = testInfo.outputPath("dt2-8-print-a4.pdf");
    const buffer = await page.pdf({ path, format: "A4", margin: { top: "12mm", bottom: "12mm", left: "12mm", right: "12mm" } });
    const { getDocument } = await import("pdfjs-dist/legacy/build/pdf.mjs");
    const loadingTask = getDocument({ data: new Uint8Array(buffer), isEvalSupported: false });
    const pdf = await loadingTask.promise;
    const compact = value => value.normalize("NFC").replace(/\s/gu, "");
    const money = value => compact(new Intl.NumberFormat("vi-VN", {
      style: "currency", currency: "VND", maximumFractionDigits: 0,
    }).format(BigInt(value)));
    try {
      expect(pdf.numPages).toBeGreaterThan(1);
      const texts = [];
      for (let n = 1; n <= pdf.numPages; n++) {
        const printedPage = await pdf.getPage(n);
        const viewport = printedPage.getViewport({ scale: 1 });
        const content = await printedPage.getTextContent();
        const spans = content.items.filter(item => "str" in item && item.str.trim());
        for (const span of spans) {
          expect(span.transform[4], `PDF page ${n}: ${span.str} starts outside paper`).toBeGreaterThanOrEqual(-1);
          expect(span.transform[4] + span.width, `PDF page ${n}: ${span.str} ends outside paper`).toBeLessThanOrEqual(viewport.width + 1);
          expect(span.transform[5], `PDF page ${n}: ${span.str} is below paper`).toBeGreaterThanOrEqual(-1);
          expect(span.transform[5] + span.height, `PDF page ${n}: ${span.str} is above paper`).toBeLessThanOrEqual(viewport.height + 1);
        }
        const text = compact(spans.map(item => item.str).join(""));
        texts.push(text);
        if (text.includes("PRINT-ROW-") || text.includes("WWWWWW")) {
          for (const heading of ["Tên món", "Số lượng", "Đơn giá", "Thành tiền"]) expect(text).toContain(compact(heading));
        }
      }
      const full = texts.join("");
      let previousPosition = -1;
      for (const item of items) {
        const name = compact(item.itemName);
        const row = name + item.quantity + money(item.unitPrice) + money(item.lineTotal);
        expect(full.split(name).length - 1, `Printed name count for ${item.orderItemId}`).toBe(1);
        expect(texts.filter(text => text.includes(row)).length, `Exact row on one page: ${item.orderItemId}`).toBe(1);
        const position = full.indexOf(row);
        expect(position, `Printed row order: ${item.orderItemId}`).toBeGreaterThan(previousPosition);
        previousPosition = position;
      }
      expect(full).toContain(compact("Tổng cộng:") + money(totalAmount));
      for (const hidden of ["Search by ID", "Show List Bill", "KHU VỰC QUẢN TRỊ", "In hóa đơn"]) expect(full).not.toContain(compact(hidden));
      await testInfo.attach("Actual A4 invoice", { path, contentType: "application/pdf" });
    } finally {
      await loadingTask.destroy();
    }
  });


  for (const outcome of ["recover-first", "recover", "empty", "shrink-again", "network-error"]) {
    test(`Billing recovers stale pagination: ${outcome}`, async ({ page }) => {
      await page.route("**/api/v1/home/account", route => route.fulfill({ json: admin }));
      const initialTotal = outcome === "recover-first" ? 11 : 21;
      const rows = Array.from({ length: initialTotal }, (_, i) => ({
        ...mockBills[0], id: String(i + 1), billCode: `PENDING-${i + 1}`, status: "PENDING",
      }));
      let shrinking = false;
      let requests = [];
      await page.route("**/api/v1/bills?**", route => {
        const params = new URL(route.request().url()).searchParams;
        const index = Number(params.get("page"));
        if (params.get("status") !== "PENDING") return route.fulfill({ json: { content: [], totalElements: 0, totalPages: 0, page: 0 } });
        if (!shrinking) return route.fulfill({ json: { content: rows.slice(index * 10, index * 10 + 10), totalElements: initialTotal, totalPages: Math.ceil(initialTotal / 10), page: index } });
        requests.push(index);
        expect(params.get("status")).toBe("PENDING");
        const total = outcome === "empty" ? 0 : outcome === "recover-first" ? 10 : index === 2 ? 11 : outcome === "shrink-again" ? 10 : 11;
        if (outcome === "network-error" && index === 1) return route.abort("failed");
        return route.fulfill({ json: { content: rows.slice(index * 10, Math.min(total, index * 10 + 10)), totalElements: total, totalPages: Math.ceil(total / 10), page: index } });
      });
      await page.goto("/#bills");
      await setToken(page, "admin-token");
      await page.getByLabel("Lọc theo trạng thái").selectOption("PENDING");
      await expect(page.getByRole("button", { name: "PENDING-1", exact: true })).toBeVisible();
      if (outcome !== "recover-first") {
        await page.getByRole("button", { name: "Trang tiếp theo" }).click();
        await expect(page.getByRole("button", { name: "PENDING-11", exact: true })).toBeVisible();
      }
      await page.getByRole("button", { name: outcome === "recover-first" ? "PENDING-1" : "PENDING-11", exact: true }).click();
      shrinking = true;
      await page.getByRole("button", { name: "Trang tiếp theo" }).click();
      const pagination = page.locator(".billing-pagination-controls");
      if (outcome === "recover-first") {
        await expect(page.getByRole("button", { name: "PENDING-1", exact: true })).toHaveAttribute("aria-pressed", "false");
        await expect(page.getByRole("button", { name: "PENDING-10", exact: true })).toBeVisible();
        await expect(pagination).toContainText("1 / 1");
        await expect(page.getByRole("button", { name: "Trang trước" })).toBeDisabled();
        await expect(page.getByRole("button", { name: "Trang tiếp theo" })).toBeDisabled();
      } else if (outcome === "recover") {
        await expect(page.getByRole("button", { name: "PENDING-11", exact: true })).toHaveAttribute("aria-pressed", "false");
        await expect(pagination).toContainText("2 / 2");
        await expect(page.getByRole("button", { name: "Trang tiếp theo" })).toBeDisabled();
      } else if (outcome === "empty") {
        await expect(page.getByText("Không có hóa đơn nào.")).toBeVisible();
        await expect(pagination).toContainText("0 / 0");
        await expect(page.getByRole("button", { name: "Trang trước" })).toBeDisabled();
      } else {
        await expect(page.getByRole("button", { name: "Thử lại", exact: true })).toBeVisible();
        await expect(pagination).toContainText("0 / 0");
        await expect(page.getByRole("button", { name: "PENDING-11", exact: true })).toHaveCount(0);
      }
      expect(requests).toEqual(outcome === "empty" ? [2] : outcome === "recover-first" ? [1, 0] : [2, 1]);
      if (outcome === "shrink-again" || outcome === "network-error") {
        shrinking = false;
        await page.getByRole("button", { name: "Thử lại", exact: true }).click();
        await expect(page.getByRole("button", { name: "PENDING-21", exact: true })).toBeVisible();
        await expect(pagination).toContainText("3 / 3");
      }
      await page.getByRole("button", { name: "Xem chi tiết", exact: true }).click();
      await expect(page.getByText("Vui lòng chọn hóa đơn cụ thể!")).toBeVisible();
      await expect(page.getByRole("dialog")).toHaveCount(0);
    });
  }

  for (const [role, status] of [["STAFF", "PAID"], ["ADMIN", "PENDING"], ["ADMIN", "VOID"]]) {
    test(`Dedicated receipt template excludes ${role} ${status}`, async ({ page }) => {
      await page.route("**/api/v1/home/account", route => route.fulfill({ json: { ...admin, role } }));
      const bill = { ...mockBillDetail1, status };
      await page.route("**/api/v1/bills?**", route => route.fulfill({ json: { content: [{ ...mockBills[0], status }], totalElements: 1, totalPages: 1, page: 0 } }));
      await page.route("**/api/v1/bills/1", route => route.fulfill({ json: bill }));
      await page.goto("/#bills");
      await setToken(page, role === "STAFF" ? "staff-token" : "admin-token");
      await page.getByRole("button", { name: bill.billCode }).click();
      await page.getByRole("button", { name: "Xem chi tiết", exact: true }).click();
      const dialog = page.getByRole("dialog");
      await expect(dialog).toBeVisible();
      await expect(dialog.getByRole("button", { name: "In hóa đơn" })).toHaveCount(0);
      await expect(dialog).not.toHaveAttribute("data-receipt-print", "");
      await page.emulateMedia({ media: "print" });
      await expect(dialog.locator(".billing-items-table")).toHaveCSS("table-layout", "auto");
      expect(await page.locator("#root").evaluate(el => getComputedStyle(el).display)).not.toBe("none");
      await page.emulateMedia({ media: "screen" });
      await expect(dialog.getByText("Cà phê sữa đá")).toBeVisible();
      await page.keyboard.press("Escape");
      await expect(dialog).toHaveCount(0);
    });
  }


  test("A delayed page recovery cannot overwrite a newer status filter", async ({ page }) => {
    await page.route("**/api/v1/home/account", route => route.fulfill({ json: admin }));
    let recovering = false;
    let deferred;
    await page.route("**/api/v1/bills?**", route => {
      const params = new URL(route.request().url()).searchParams;
      const index = Number(params.get("page"));
      if (params.get("status") === "PENDING") {
        if (index === 1) {
          recovering = true;
          return route.fulfill({ json: { content: [], totalElements: 10, totalPages: 1, page: 1 } });
        }
        if (recovering) { deferred = route; return; }
        return route.fulfill({ json: { content: [{ ...mockBills[0], status: "PENDING" }], totalElements: 11, totalPages: 2, page: 0 } });
      }
      return route.fulfill({ json: { content: [mockBills[1]], totalElements: 1, totalPages: 1, page: 0 } });
    });
    await page.goto("/#bills");
    await setToken(page, "admin-token");
    await page.getByLabel("Lọc theo trạng thái").selectOption("PENDING");
    await expect(page.getByText("BILL-2026-001", { exact: true })).toBeVisible();
    await page.getByRole("button", { name: "Trang tiếp theo" }).click();
    await expect.poll(() => Boolean(deferred)).toBe(true);
    await page.getByLabel("Lọc theo trạng thái").selectOption("PAID");
    await expect(page.getByText("BILL-2026-002", { exact: true })).toBeVisible();
    await deferred.fulfill({ json: { content: [{ ...mockBills[0], status: "PENDING" }], totalElements: 10, totalPages: 1, page: 0 } });
    await expect(page.getByText("BILL-2026-002", { exact: true })).toBeVisible();
    await expect(page.getByText("BILL-2026-001", { exact: true })).toHaveCount(0);
    await expect(page.locator(".billing-pagination-controls")).toContainText("1 / 1");
  });

});
