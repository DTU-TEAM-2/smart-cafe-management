import { expect, test } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

async function addTable(page, id, state = "Nội dung State thử") {
  await page.getByRole("button", { name: "Thêm bàn (Add)", exact: true }).click();
  await page.getByLabel("ID *", { exact: true }).fill(id);
  await page.getByLabel("State *", { exact: true }).fill(state);
  await page.getByRole("button", { name: "Lưu bàn mới", exact: true }).click();
}

test("adds, rejects missing/duplicate fields, edits only selected table and resets on reload without API/storage", async ({ page }) => {
  const apiCalls = [];
  page.on("request", request => { if (new URL(request.url()).pathname.startsWith("/api/")) apiCalls.push(request.url()); });
  await page.goto("/preview/tables");
  await expect(page.getByRole("heading", { name: "Chưa có bàn trong bản thử" })).toBeVisible();
  await page.getByRole("button", { name: "Sửa bàn (Edit)", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("No table is chosen");
  await page.getByRole("button", { name: "Xóa bàn (Remove)", exact: true }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await page.getByRole("button", { name: "Thêm bàn (Add)", exact: true }).click();
  await page.getByRole("button", { name: "Lưu bàn mới", exact: true }).click();
  await expect(page.getByLabel("ID *", { exact: true })).toBeFocused();
  await expect(page.getByLabel("State *", { exact: true })).toHaveAttribute("aria-invalid", "true");
  await page.getByLabel("ID *", { exact: true }).fill("  T-01  ");
  await page.getByLabel("State *", { exact: true }).fill("Trạng thái thử A");
  await page.getByRole("button", { name: "Lưu bàn mới", exact: true }).click();
  await expect(page.getByRole("button", { name: "Chọn bàn T-01", exact: true })).toHaveAttribute("aria-pressed", "true");
  await addTable(page, "t-01");
  await expect(page.getByRole("alert")).toContainText("ID đã tồn tại");
  await expect(page.getByRole("button", { name: "Chọn bàn T-01", exact: true })).toHaveCount(1);
  await page.getByLabel("ID *", { exact: true }).fill("T-02");
  await page.getByRole("button", { name: "Lưu bàn mới", exact: true }).click();
  await page.getByRole("button", { name: "Chọn bàn T-01", exact: true }).click();
  await expect(page.getByLabel("ID *", { exact: true })).toHaveAttribute("readonly", "");
  await page.getByLabel("State *", { exact: true }).fill("State đã sửa");
  await page.getByRole("button", { name: "Lưu thay đổi", exact: true }).click();
  await expect(page.getByRole("row", { name: /T-01 State đã sửa/ })).toBeVisible();
  await expect(page.getByRole("row", { name: /T-02 Nội dung State thử/ })).toBeVisible();
  expect(await page.evaluate(() => ({ local: localStorage.length, session: sessionStorage.length }))).toEqual({ local: 0, session: 0 });
  await page.reload();
  await expect(page.getByRole("heading", { name: "Chưa có bàn trong bản thử" })).toBeVisible();
  expect(apiCalls).toEqual([]);
});

test("searches ID/State, handles no result/empty keyword and Show List Table", async ({ page }) => {
  await page.goto("/preview/tables");
  await addTable(page, "T-01", "Khu A");
  await addTable(page, "T-02", "Khu B");
  await page.getByRole("button", { name: "Tìm kiếm", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("nhập từ khóa");
  await page.getByRole("searchbox").fill("t-01");
  await page.getByRole("button", { name: "Tìm kiếm", exact: true }).click();
  await expect(page.getByRole("button", { name: "Chọn bàn T-01", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Chọn bàn T-02", exact: true })).toHaveCount(0);
  await expect(page.getByLabel("ID *", { exact: true })).toHaveCount(0);
  await page.getByRole("combobox", { name: "Tìm theo", exact: true }).selectOption("state");
  await page.getByRole("searchbox").fill("KHU b");
  await page.getByRole("button", { name: "Tìm kiếm", exact: true }).click();
  await expect(page.getByRole("button", { name: "Chọn bàn T-02", exact: true })).toBeVisible();
  await page.getByRole("searchbox").fill("không có");
  await page.getByRole("button", { name: "Tìm kiếm", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Không tìm thấy bàn" })).toBeVisible();
  await page.getByRole("button", { name: "Show List Table", exact: true }).click();
  await expect(page.getByRole("button", { name: /^Chọn bàn/ })).toHaveCount(2);
  await expect(page.getByRole("searchbox")).toHaveValue("");
  await page.getByRole("button", { name: "Chọn bàn T-01", exact: true }).click();
  await page.screenshot({ path: test.info().outputPath("tables-overview.png"), fullPage: true });
});

test("cancel, Escape and keyboard focus protect selection during removal", async ({ page }) => {
  await page.goto("/preview/tables");
  await addTable(page, "T-01");
  const remove = page.getByRole("button", { name: "Xóa bàn (Remove)", exact: true });
  await remove.click();
  const dialog = page.getByRole("dialog");
  const cancel = dialog.getByRole("button", { name: "Hủy xóa", exact: true });
  await expect(cancel).toBeFocused();
  await page.keyboard.press("Shift+Tab");
  await expect(dialog.getByRole("button", { name: "Xác nhận xóa", exact: true })).toBeFocused();
  await page.keyboard.press("Tab");
  await expect(cancel).toBeFocused();
  await page.keyboard.press("Escape");
  await expect(dialog).toHaveCount(0);
  await expect(remove).toBeFocused();
  await expect(page.getByRole("button", { name: "Chọn bàn T-01", exact: true })).toBeVisible();
  await remove.click();
  await cancel.click();
  await expect(remove).toBeFocused();
  await remove.click();
  await dialog.getByRole("button", { name: "Xác nhận xóa", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Chưa có bàn trong bản thử" })).toBeVisible();
  await expect(remove).toBeFocused();
  await page.getByRole("button", { name: "Sửa bàn (Edit)", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("No table is chosen");
});

test("pagination keeps new and edited rows visible and clamps after deletion", async ({ page }) => {
  await page.goto("/preview/tables");
  for (let index = 1; index <= 11; index++) await addTable(page, `T-${index}`);
  await expect(page.getByText("Trang 2 / 2", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Chọn bàn T-11", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Xóa bàn (Remove)", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Xác nhận xóa", exact: true }).click();
  await expect(page.getByText("Trang 1 / 1", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: /^Chọn bàn/ })).toHaveCount(10);
});

test("accessible responsive layout supports long content and modal", async ({ page }) => {
  await page.goto("/preview/tables");
  await addTable(page, "Bàn-" + "A".repeat(65), "Nội dung State thử ".repeat(8));
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await page.getByRole("button", { name: "Xóa bàn (Remove)", exact: true }).click();
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await page.keyboard.press("Escape");
  await page.screenshot({ path: test.info().outputPath("tables.png"), fullPage: true });
});

test("production excludes the table preview", async ({ page }) => {
  await page.route("**/api/v1/**", route => route.fulfill({ status: 503, json: {} }));
  await page.goto("http://127.0.0.1:15174/preview/tables");
  await expect(page.getByRole("heading", { name: "Quản lý bàn", exact: true })).toHaveCount(0);
  await expect(page.getByText("Bản thử giao diện độc lập", { exact: true })).toHaveCount(0);
});
