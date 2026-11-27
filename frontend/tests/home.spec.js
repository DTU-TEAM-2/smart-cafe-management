import { expect, test } from "@playwright/test";

const newest = [
  { id: "7", name: "Caramel Latte", price: "45000" },
  { id: "8", name: "Matcha Latte", price: "48000" },
  { id: "9", name: "Tiramisu", price: "52000" },
  { id: "10", name: "Bạc xỉu", price: "40000" },
  { id: "11", name: "Trà đào", price: "42000" },
];
test.beforeEach(async ({ page }) => {
  await page.route("**/api/v1/home/account", route => route.fulfill({ status: 403, json: {} }));
});
const mostOrdered = [0, 3, 1, 4, 2].map((index, rank) => ({
  id: newest[index].id, name: newest[index].name, orderedQuantity: String(5 - rank),
}));

test("shows the Figma homepage structure and server-provided ranked lists", async ({ page, isMobile }) => {
  await page.route("**/api/v1/home/highlights", route => route.fulfill({ json: { newest, mostOrdered } }));
  await page.goto("/");
  await expect(page.getByRole("banner")).toHaveCount(1);
  await expect(page.getByRole("main")).toHaveCount(1);
  await expect(page.getByRole("contentinfo")).toHaveCount(1);
  await expect(page.getByRole("heading", { name: "High Quality COFFEE" })).toBeVisible();
  await expect(page.locator("#about article")).toHaveCount(3);
  const heroImage = page.locator(".hero-art img");
  await expect(heroImage).toHaveAttribute("src", "/images/home-coffee-hero.jpg");
  await expect.poll(() => heroImage.evaluate(img => img.naturalWidth)).toBeGreaterThan(0);
  await expect(page.locator("#about .special-tile")).toHaveCount(3);
  await expect(page.locator("#newest li")).toHaveCount(5);
  await expect(page.locator("#most-ordered li")).toHaveCount(5);
  await expect(page.locator("#newest li").first()).toContainText("45.000");
  await expect(page.locator("#most-ordered li").nth(1)).toContainText("Bạc xỉu");
  await expect(page.locator("#most-ordered li").first()).toContainText("5 món");
  await expect(page.locator(".ranking-list img")).toHaveCount(0);
  const footer = page.getByRole("contentinfo");
  const visit = footer.getByRole("region", { name: "Ghé LECKER" });
  await expect(visit.getByText("295 Nguyễn Tất Thành", { exact: true })).toBeVisible();
  await expect(visit.getByRole("link", { name: "0934678320" })).toHaveAttribute("href", "tel:0934678320");
  await expect(visit.getByRole("link", { name: "lecker@gmail.com" })).toHaveAttribute("href", "mailto:lecker@gmail.com");
  await expect(visit.getByRole("link", { name: "Gọi cho LECKER" })).toHaveAttribute("href", "tel:0934678320");
  await expect(footer.getByRole("heading", { name: "Giờ mở cửa" })).toBeVisible();
  await expect(footer.getByRole("navigation", { name: "Khám phá" })).toBeVisible();
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

  await footer.getByRole("link", { name: "Về đầu trang", exact: true }).click();
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBeLessThan(1);
});

test("opens the customer menu from the homepage for a guest without a table session", async ({ page, isMobile }) => {
  await page.route("**/api/v1/home/highlights", route => route.fulfill({ json: { newest: [], mostOrdered: [] } }));
  await page.route("**/api/v1/customer/menu/categories", route => route.fulfill({ json: [] }));
  await page.route("**/api/v1/customer/menu/items?*", route => route.fulfill({
    json: { content: [{ id: "1", name: "Bạc xỉu", price: "35000", available: true }], totalElements: 1 },
  }));
  await page.goto("/#about");
  if (isMobile) await page.getByRole("button", { name: "Điều hướng" }).click();
  await page.getByRole("navigation", { name: "Điều hướng chính" }).getByRole("link", { name: "Thực đơn", exact: true }).click();
  await expect(page).toHaveURL(/#customer$/);
  await expect(page.getByRole("heading", { name: "Thực đơn hôm nay" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Bạc xỉu" })).toBeVisible();
  await expect(page.getByText("Quét mã QR tại bàn", { exact: false })).toBeVisible();
  await expect(page.getByRole("navigation", { name: "Điều hướng chính cho khách" }))
    .toHaveCSS("position", isMobile ? "fixed" : "static");
  const gridColumns = await page.locator(".c-menu-grid").evaluate(grid => getComputedStyle(grid).gridTemplateColumns.split(" ").length);
  expect(gridColumns).toBe(isMobile ? 2 : 4);

  await page.getByRole("button", { name: "Về trang chủ" }).click();
  await page.getByRole("link", { name: "Khám phá thực đơn" }).click();
  await expect(page.getByRole("heading", { name: "Thực đơn hôm nay" })).toBeVisible();

  await page.getByRole("button", { name: "Về trang chủ" }).click();
  await page.getByRole("navigation", { name: "Khám phá" }).getByRole("link", { name: "Thực đơn", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Thực đơn hôm nay" })).toBeVisible();
});

test("opens each story from its card and returns focus when closed", async ({ page, isMobile }) => {
  await page.route("**/api/v1/home/highlights", route => route.fulfill({ json: { newest: [], mostOrdered: [] } }));
  await page.goto("/#about");

  for (const [title, detail] of [
    ["Đồ uống nổi bật", "Latte và cappuccino đều kết hợp espresso với sữa"],
    ["Không gian quán", "Có hôm bạn mang theo một cuốn sách"],
    ["Đội ngũ Barista", "người pha phải để ý đến độ mịn của bột"],
  ]) {
    const trigger = page.getByRole("button", { name: `Đọc câu chuyện ${title}` });
    if (isMobile) {
      const cardImage = page.getByRole("article").filter({ has: trigger }).locator(".special-tile");
      await cardImage.evaluate(image => image.scrollIntoView({ block: "center", behavior: "instant" }));
      await expect.poll(() => cardImage.evaluate(image => {
        const bounds = image.getBoundingClientRect();
        const target = document.elementFromPoint(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
        return target?.matches(".special-read") && target.closest("article") === image.closest("article");
      })).toBe(true);
      const bounds = await cardImage.boundingBox();
      await page.touchscreen.tap(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
    } else {
      await trigger.focus();
      await page.keyboard.press("Enter");
    }

    const dialog = page.getByRole("dialog", { name: title });
    await expect(dialog).toBeVisible();
    await expect(page.getByRole("dialog")).toHaveCount(1);
    await expect(dialog.getByText(detail, { exact: false })).toBeVisible();
    const close = dialog.getByRole("button", { name: "Đóng câu chuyện" });
    await expect(close).toBeFocused();
    if (isMobile) await close.tap();
    else await page.keyboard.press("Escape");
    await expect(dialog).toBeHidden();
    await expect(trigger).toBeFocused();
  }
});

test("closes a story when pressing its backdrop and keeps it open when pressing inside", async ({ page, isMobile }) => {
  await page.route("**/api/v1/home/highlights", route => route.fulfill({ json: { newest: [], mostOrdered: [] } }));
  await page.goto("/#about");
  const trigger = page.getByRole("button", { name: "Đọc câu chuyện Không gian quán" });
  await trigger.click();
  const dialog = page.getByRole("dialog", { name: "Không gian quán" });
  await expect(dialog).toBeVisible();
  await dialog.getByRole("heading", { name: "Không gian quán" }).click();
  await expect(dialog).toBeVisible();

  if (isMobile) await page.touchscreen.tap(4, 4);
  else await page.mouse.click(4, 4);
  await expect(dialog).toBeHidden();
  await expect(trigger).toBeFocused();
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
  await expect(page.locator(".hero-art img")).toBeVisible();
});

test("keeps the header pinned and ranked dishes readable at 320 pixels", async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 740 });
  const dish = {
    ...newest[0],
    name: "Cà phê sữa đá với hương vị đậm đà",
    price: "123456789012345678",
  };
  const popularDish = { id: dish.id, name: dish.name, orderedQuantity: "123456789012345678" };
  await page.route("**/api/v1/home/highlights", route => route.fulfill({ json: { newest: [dish], mostOrdered: [popularDish] } }));
  await page.goto("/");

  const header = page.getByRole("banner");
  const firstDish = page.locator("#newest li").first();
  await page.goto("/#newest");
  await expect(firstDish).toContainText(dish.name);
  await expect(firstDish).toContainText("123.456.789.012.345.678");
  await expect(page.locator("#most-ordered li").first()).toContainText("123.456.789.012.345.678 món");
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(100);
  await expect.poll(async () => (await header.boundingBox()).y).toBe(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
});

test("opens the real account seam and limits a notification feed to its recipient and staff roles", async ({ page }) => {
  await page.goto("/tests/layout-harness.html");
  await page.getByRole("button", { name: "Tài khoản Nhân viên thử" }).click();
  await page.getByRole("button", { name: "Thông tin tài khoản" }).click();
  await expect(page).toHaveURL(/#account$/);
  await page.getByRole("button", { name: "Thông báo, 1 chưa đọc" }).click();
  await expect(page.getByText("Bàn thử gọi phục vụ")).toHaveCount(1);
  await page.keyboard.press("Escape");
  await page.getByRole("button", { name: "Đổi quản lý" }).click();
  await page.getByRole("button", { name: "Thông báo", exact: true }).click();
  await expect(page.getByText("Chưa kết nối thông báo phục vụ.")).toBeVisible();
  await expect(page.getByText("Bàn thử gọi phục vụ")).toHaveCount(0);
  await page.keyboard.press("Escape");
  await page.getByRole("button", { name: "Đổi khách" }).click();
  await expect(page.getByRole("button", { name: /Thông báo/ })).toHaveCount(0);
});
