import { expect, test } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

const API = "**/api/v1/admin/feedbacks";
const user = { id: "95001", username: "feedback-admin-test", fullName: "Quản lý kiểm thử", email: "admin@example.invalid", role: "ADMIN" };
const rows = [
  { id: "9007199254740993", creator: "Khách A", email: "guest-a@example.invalid", date: "2026-11-19", createdAt: "2026-11-19T05:00:00Z", content: "Đồ uống ngon, phục vụ thân thiện.", rating: 5 },
  { id: "95002", creator: "Khách B", email: "guest-b@example.invalid", date: "2026-11-18", createdAt: "2026-11-18T05:00:00Z", content: "Góp ý về không gian quán.", rating: 4 },
];
const pageData = (content = rows, total = content.length) => ({ content, totalElements: total, page: 0, size: 20 });
const evidence = async (page, info, name) => page.screenshot({ path: `../docs/handover/evidence/dt2-5/${name}-${info.project.name}.png`, fullPage: true });

async function setup(page, role = "ADMIN") {
  await page.addInitScript((value) => {
    sessionStorage.setItem("smart_cafe_token", "synthetic-feedback-test-only");
    sessionStorage.setItem("smart_cafe_user", JSON.stringify(value));
  }, { ...user, role });
  await page.route("**/api/v1/auth/me", route => route.fulfill({ json: { ...user, role } }));
  await page.route("**/api/v1/home/account", route => route.fulfill({ json: { ...user, role, name: user.fullName } }));
  await page.route("**/api/v1/home/notifications", route => route.fulfill({ json: { recipientId: user.id, items: [] } }));
  await page.route(`${API}?*`, route => route.fulfill({ json: pageData() }));
  await page.route(`${API}/*`, route => {
    const row = rows.find(item => item.id === route.request().url().split("/").pop());
    return route.fulfill({ json: { ...row, images: [] } });
  });
}

test.beforeEach(async ({ page }) => setup(page));

test("Feedback list reuses Admin layout and renders all five columns", async ({ page }, info) => {
  await page.goto("/#admin-feedback");
  await expect(page.getByRole("heading", { name: "Feedback Management" })).toBeVisible();
  await expect(page.locator(".feedback-table th[scope=col]")).toHaveText(["ID", "Creator", "Email", "Date", "Feedback"]);
  await expect(page.getByRole("button", { name: "Xem phản hồi 9007199254740993" })).toBeVisible();
  await expect(page.locator(".feedback-table")).toContainText("guest-a@example.invalid");
  await evidence(page, info, "01-list");
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  expect((await new AxeBuilder({ page }).include(".feedback-management").analyze()).violations).toEqual([]);
});

test("Feedback A to B clears old detail and displays exact selected ID", async ({ page }, info) => {
  await page.goto("/#admin-feedback");
  await page.getByRole("button", { name: `Xem phản hồi ${rows[0].id}` }).click();
  await expect(page.locator(".feedback-detail")).toContainText("Khách A");
  await expect(page.locator(".feedback-detail")).toContainText("5/5");
  await evidence(page, info, "02-detail");
  await page.getByRole("button", { name: "Xem phản hồi 95002" }).click();
  await expect(page.locator(".feedback-detail")).toContainText("Khách B");
  await expect(page.locator(".feedback-detail")).not.toContainText("Khách A");
  await expect(page.getByText("Không có ảnh đính kèm.")).toBeVisible();
});

test("Search date, no results, empty search and Show All reset selection", async ({ page }, info) => {
  const requests = [];
  await page.route(`${API}?*`, route => {
    const params = new URL(route.request().url()).searchParams;
    requests.push(Object.fromEntries(params));
    return route.fulfill({ json: pageData(params.get("date") === "2026-11-20" ? [] : params.has("date") ? [rows[0]] : rows) });
  });
  await page.goto("/#admin-feedback");
  await page.getByRole("button", { name: `Xem phản hồi ${rows[0].id}` }).click();
  await expect(page.locator(".feedback-detail")).toContainText("Khách A");
  await page.getByLabel("Search by Date").fill("2026-11-19");
  await page.getByRole("button", { name: "Search", exact: true }).click();
  await expect(page.locator(".feedback-table tbody tr")).toHaveCount(1);
  await expect(page.locator(".feedback-detail")).not.toContainText("Khách A");
  await evidence(page, info, "03-search");
  await page.getByLabel("Search by Date").fill("2026-11-20");
  await page.getByRole("button", { name: "Search", exact: true }).click();
  await expect(page.getByText("Not found!", { exact: true })).toBeVisible();
  await expect(page.locator(".feedback-table tbody tr")).toHaveCount(0);
  await evidence(page, info, "04-not-found");
  await page.getByLabel("Search by Date").fill("");
  await page.getByRole("button", { name: "Search", exact: true }).click();
  await expect(page.getByText("Please enter a keyword!", { exact: true })).toBeVisible();
  await expect(page.locator(".feedback-table")).toHaveCount(0);
  await evidence(page, info, "05-empty-search");
  await page.getByRole("button", { name: "Show All" }).click();
  await expect(page.locator(".feedback-table tbody tr")).toHaveCount(2);
  expect(requests.at(-1)).toEqual({ page: "0", size: "20", sort: "newest" });
  await evidence(page, info, "06-show-all");
});

test("Pagination and sort reset page and selected information", async ({ page }) => {
  const requests = [];
  await page.route(`${API}?*`, route => {
    const params = new URL(route.request().url()).searchParams;
    requests.push(Object.fromEntries(params));
    return route.fulfill({ json: pageData(params.get("page") === "1" ? [rows[1]] : [rows[0]], 21) });
  });
  await page.goto("/#admin-feedback");
  await page.getByRole("button", { name: `Xem phản hồi ${rows[0].id}` }).click();
  await expect(page.locator(".feedback-detail")).toContainText("Khách A");
  await page.getByRole("button", { name: "Trang sau" }).click();
  await expect(page.getByRole("button", { name: "Xem phản hồi 95002" })).toBeVisible();
  await expect(page.locator(".feedback-detail")).not.toContainText("Khách A");
  await page.getByLabel("Thứ tự").selectOption("oldest");
  await expect(page.getByRole("button", { name: `Xem phản hồi ${rows[0].id}` })).toBeVisible();
  expect(requests.at(-1).page).toBe("0");
  expect(requests.at(-1).sort).toBe("oldest");
});

test("Late detail and list responses cannot overwrite the latest choice", async ({ page }) => {
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  await page.route(`${API}/${rows[0].id}`, async route => { await gate; await route.fulfill({ json: { ...rows[0], images: [] } }).catch(() => {}); });
  await page.goto("/#admin-feedback");
  await page.getByRole("button", { name: `Xem phản hồi ${rows[0].id}` }).click();
  await expect(page.getByText("Đang tải chi tiết…")).toBeVisible();
  await page.getByRole("button", { name: "Xem phản hồi 95002" }).click();
  await expect(page.locator(".feedback-detail")).toContainText("Khách B");
  release();
  await expect(page.locator(".feedback-detail")).not.toContainText("Khách A");
  let releaseList;
  const gateList = new Promise(resolve => { releaseList = resolve; });
  await page.route(`${API}?*`, async route => {
    if (new URL(route.request().url()).searchParams.get("date")) { await gateList; await route.fulfill({ json: pageData([rows[0]]) }).catch(() => {}); }
    else await route.fulfill({ json: pageData([rows[1]]) });
  });
  await page.getByLabel("Search by Date").fill("2026-11-19");
  await page.getByRole("button", { name: "Search", exact: true }).click();
  await expect(page.getByText("Đang tải phản hồi…")).toBeVisible();
  await page.getByRole("button", { name: "Show All" }).click();
  await expect(page.locator(".feedback-table tbody tr")).toContainText("Khách B");
  releaseList();
  await expect(page.locator(".feedback-table tbody tr")).not.toContainText("Khách A");
});

test("HTTPS image is displayed; invalid URLs and broken private images have fallback", async ({ page }, info) => {
  await page.route("https://images.example.invalid/feedback.png", route => route.fulfill({ path: "../docs/qa/postman/fixtures/feedback-test.png", contentType: "image/png" }));
  await page.route("https://images.example.invalid/private.png", route => route.fulfill({ status: 403, body: "" }));
  await page.route(`${API}/${rows[0].id}`, route => route.fulfill({ json: { ...rows[0], images: [
    { id: "1", url: "https://images.example.invalid/feedback.png" }, { id: "2", url: "javascript:alert(1)" }, { id: "3", url: "gs://private/image" },
    { id: "4", url: "https://images.example.invalid/private.png" },
  ] } }));
  await page.goto("/#admin-feedback");
  await page.getByRole("button", { name: `Xem phản hồi ${rows[0].id}` }).click();
  const image = page.getByRole("img", { name: "Ảnh đính kèm phản hồi 1", exact: true });
  await expect(image).toBeVisible();
  await expect.poll(() => image.evaluate(img => img.complete && img.naturalWidth > 0)).toBe(true);
  await expect(page.getByText("Ảnh chưa khả dụng hoặc chưa được cấp quyền xem.")).toHaveCount(3);
  await evidence(page, info, "07-image");
});

for (const status of [401, 403, 500, 503]) test(`List ${status} has clear error with no stale data`, async ({ page }) => {
  await page.route(`${API}?*`, route => route.fulfill({ status, json: { detail: "internal-sensitive-details-not-for-display" } }));
  await page.goto("/#admin-feedback");
  await expect(page.locator(".feedback-error")).toBeVisible();
  await expect(page.locator(".feedback-table")).toHaveCount(0);
  await expect(page.locator(".feedback-detail")).not.toContainText("Khách A");
  await expect(page.getByText("internal-sensitive-details-not-for-display")).toHaveCount(0);
});

test("Detail 404 and failed list retry clear selected data", async ({ page }) => {
  await page.route(`${API}/95002`, route => route.fulfill({ status: 404, json: {} }));
  await page.goto("/#admin-feedback");
  await page.getByRole("button", { name: `Xem phản hồi ${rows[0].id}` }).click();
  await expect(page.locator(".feedback-detail")).toContainText("Khách A");
  await page.getByRole("button", { name: "Xem phản hồi 95002" }).click();
  await expect(page.locator(".feedback-detail")).toContainText("Không tìm thấy phản hồi.");
  await expect(page.locator(".feedback-detail")).not.toContainText("Khách A");
  await page.route(`${API}?*`, route => route.abort());
  await page.getByRole("button", { name: "Show All" }).click();
  await expect(page.locator(".feedback-error")).toContainText("Không thể tải phản hồi.");
  await expect(page.locator(".feedback-table")).toHaveCount(0);
  await page.route(`${API}?*`, route => route.fulfill({ json: pageData([]) }));
  await page.getByRole("button", { name: "Thử lại", exact: true }).click();
  await expect(page.getByText("Chưa có phản hồi.")).toBeVisible();
});

for (const role of ["STAFF", "CUSTOMER"]) test(`${role} cannot open Admin Feedback or request its API`, async ({ page }) => {
  await setup(page, role);
  let reads = 0;
  await page.route(`${API}?*`, route => { reads++; return route.fulfill({ json: pageData() }); });
  await page.goto("/#admin-feedback");
  await expect(page).toHaveURL(/#top$/);
  await expect(page.getByRole("heading", { name: "Feedback Management" })).toHaveCount(0);
  expect(reads).toBe(0);
});

test("Anonymous cannot open Admin Feedback", async ({ page }) => {
  await page.addInitScript(() => sessionStorage.clear());
  let reads = 0;
  await page.route(`${API}?*`, route => { reads++; return route.fulfill({ json: pageData() }); });
  await page.goto("/#admin-feedback");
  await expect(page).toHaveURL(/#login$/);
  expect(reads).toBe(0);
});

test("List timeout produces a recoverable error without cached rows", async ({ page }) => {
  await page.route(`${API}?*`, () => {});
  await page.goto("/#admin-feedback");
  await expect(page.getByText("Không thể tải phản hồi. Kiểm tra kết nối và thử lại.", { exact: true })).toBeVisible({ timeout: 20000 });
  await expect(page.locator(".feedback-table")).toHaveCount(0);
});
