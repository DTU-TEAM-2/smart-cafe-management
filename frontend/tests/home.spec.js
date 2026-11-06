import { expect, test } from "@playwright/test";

const newest = [
  { id: "7", name: "Caramel Latte", price: "45000" },
  { id: "8", name: "Matcha Latte", price: "48000" },
  { id: "9", name: "Tiramisu", price: "52000" },
  { id: "10", name: "Bạc xỉu", price: "40000" },
  { id: "11", name: "Trà đào", price: "42000" },
];
const mostOrdered = [0, 3, 1, 4, 2].map((index, rank) => ({
  id: newest[index].id, name: newest[index].name, orderedQuantity: String(5 - rank),
}));

test("shows the Figma homepage structure and server-provided ranked lists", async ({ page, isMobile }) => {
  await page.route("**/api/v1/home/highlights", (route) => route.fulfill({ json: { newest, mostOrdered } }));
  await page.goto("/");
  await expect(page.getByRole("banner")).toHaveCount(1);
  await expect(page.getByRole("main")).toHaveCount(1);
  await expect(page.getByRole("contentinfo")).toHaveCount(1);
  await expect(page.getByRole("heading", { name: "High Quality COFFEE" })).toBeVisible();
  await expect(page.locator("#about article")).toHaveCount(3);
  await expect(page.locator("#newest li")).toHaveCount(5);
  await expect(page.locator("#most-ordered li")).toHaveCount(5);
  await expect(page.locator("#newest li").first()).toContainText("45.000");
  await expect(page.locator("#most-ordered li").nth(1)).toContainText("Bạc xỉu");
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);

  if (isMobile) {
    const nav = page.getByRole("button", { name: "Điều hướng" });
    await nav.focus();
    await page.keyboard.press("Enter");
    await expect(page.getByRole("navigation", { name: "Điều hướng chính" })).toBeVisible();
    await page.keyboard.press("Escape");
    await expect(nav).toBeFocused();
    await expect(nav).toHaveAttribute("aria-expanded", "false");
  }

  await page.getByRole("link", { name: "Khám phá menu" }).click();
  await expect(page).toHaveURL(/#newest$/);
  await page.getByRole("link", { name: "LECKER. Về đầu trang" }).click();
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBeLessThan(1);
});

test("can retry after failure and distinguishes an empty list from a short list", async ({ page }) => {
  let fail = true;
  await page.route("**/api/v1/home/highlights", (route) => fail
    ? route.fulfill({ status: 503, json: { detail: "Unavailable" } })
    : route.fulfill({ json: { newest: newest.slice(0, 1), mostOrdered: [] } }));
  await page.goto("/");
  await expect(page.getByRole("alert")).toContainText("Chưa tải được");
  fail = false;
  await page.getByRole("button", { name: "Thử lại" }).click();
  await expect(page.locator("#newest li")).toHaveCount(1);
  await expect(page.getByText("Chưa có lượt gọi món.")).toBeVisible();
});

test("opens the real account seam and limits a notification feed to its recipient and staff roles", async ({ page }) => {
  await page.goto("/tests/layout-harness.html");
  await page.getByRole("button", { name: "Tài khoản Nhân viên thử" }).click();
  await expect(page.getByRole("link", { name: "Thông tin tài khoản" })).toHaveAttribute("href", "/profile");
  await page.getByRole("button", { name: "Thông báo, 1 chưa đọc" }).click();
  await expect(page.getByText("Bàn thử gọi phục vụ")).toHaveCount(1);
  await page.getByRole("button", { name: "Đổi quản lý" }).click();
  await page.getByRole("button", { name: "Thông báo", exact: true }).click();
  await expect(page.getByText("Chưa kết nối thông báo phục vụ.")).toBeVisible();
  await expect(page.getByText("Bàn thử gọi phục vụ")).toHaveCount(0);
  await page.getByRole("button", { name: "Đổi khách" }).click();
  await expect(page.getByRole("button", { name: /Thông báo/ })).toHaveCount(0);
});
