import { expect, test } from "@playwright/test";

test("production menu renders visible items from the Spring Boot API", async ({ page }) => {
  const [menuResponse] = await Promise.all([
    page.waitForResponse((response) =>
      new URL(response.url()).pathname === "/api/v1/customer/menu/items"
      && response.request().method() === "GET"),
    page.goto("/#customer"),
  ]);

  expect(menuResponse.status()).toBe(200);
  await expect(page.getByText("Caramel Latte kiểm thử", { exact: true })).toBeVisible();
  await expect(page.getByText("Món ẩn kiểm thử", { exact: true })).toHaveCount(0);
});
