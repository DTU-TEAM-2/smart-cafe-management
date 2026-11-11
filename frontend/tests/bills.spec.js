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
    await page.route("**/api/v1/home/highlights", route =>
      route.fulfill({ json: { newest: [], mostOrdered: [] } })
    );
    await page.route("**/api/v1/home/notifications", route =>
      route.fulfill({ json: { recipientId: "3", items: [] } })
    );
  });

  test("Non-admin users cannot access bills management", async ({ page }) => {
    await page.route("**/api/v1/home/account", route =>
      route.fulfill({ json: customer })
    );

    await page.goto("/#bills");
    await setToken(page, "cust-token");

    // Nav bar should not show "Quản lý hóa đơn"
    await expect(page.getByRole("link", { name: "Quản lý hóa đơn" })).toHaveCount(0);
    // Page should display restricted access notice
    await expect(page.getByText("Khu vực quản lý chỉ dành cho Quản trị viên (ADMIN)")).toBeVisible();
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

    await expect(page.getByRole("heading", { name: "Quản lý hóa đơn", exact: true })).toBeVisible();
    await expect(page.getByText("BILL-2026-001")).toBeVisible();
    await expect(page.getByText("BILL-2026-002")).toBeVisible();
    await expect(page.getByText("180.000 ₫")).toBeVisible();
    await expect(page.getByText("95.000 ₫")).toBeVisible();

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
    const detailBtn = page.getByRole("button", { name: "Detail" });
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
    await expect(page).toHaveURL(/#statistics$/);

    if (page.viewportSize()?.width === 1440) {
      await page.screenshot({ path: test.info().outputPath("dt2-8-statistics-desktop.png"), fullPage: true });
    }
  });
});
