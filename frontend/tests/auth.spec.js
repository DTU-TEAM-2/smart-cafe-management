import { expect, test } from "@playwright/test";

test.describe("Authentication and Password Recovery (DT2-2)", () => {
  test.beforeEach(async ({ page }) => {
    await page.route("**/api/v1/home/account", route =>
      route.fulfill({ status: 401, json: {} })
    );
    await page.route("**/api/v1/home/highlights", route =>
      route.fulfill({ json: { newest: [], mostOrdered: [] } })
    );

    await page.route("**/api/v1/auth/me", route =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          id: 1,
          username: "admin",
          email: "admin@smartcafe.vn",
          fullName: "Quản trị viên",
          role: "ADMIN",
          status: "ACTIVE",
        }),
      })
    );

    await page.route(/\/api\/v1\/auth\/verify-reset-token/, route =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          valid: true,
          email: "customer@example.com",
        }),
      })
    );
  });

  test("Login page renders correctly and toggles password visibility", async ({ page }) => {
    await page.goto("/#login");
    await expect(page.getByRole("heading", { name: "Đăng nhập", exact: true })).toBeVisible();

    const usernameInput = page.getByLabel("Tên đăng nhập", { exact: true });
    const passwordInput = page.getByLabel("Mật khẩu", { exact: true });
    await expect(usernameInput).toBeVisible();
    await expect(passwordInput).toBeVisible();

    await passwordInput.fill("Secret123!");
    await expect(passwordInput).toHaveAttribute("type", "password");

    const toggleBtn = page.getByRole("button", { name: "Hiện mật khẩu" });
    await toggleBtn.click();
    await expect(passwordInput).toHaveAttribute("type", "text");

    const hideBtn = page.getByRole("button", { name: "Ẩn mật khẩu" });
    await hideBtn.click();
    await expect(passwordInput).toHaveAttribute("type", "password");
  });

  test("Login success redirects Admin to Admin Dashboard", async ({ page }) => {
    await page.route("**/api/v1/auth/login", route =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          accessToken: "mock-admin-token",
          tokenType: "Bearer",
          expiresIn: 86400,
          user: {
            id: 1,
            username: "admin",
            email: "admin@smartcafe.vn",
            fullName: "Quản trị viên Hệ thống",
            role: "ADMIN",
            status: "ACTIVE",
          },
        }),
      })
    );

    await page.goto("/#login");
    await page.getByLabel("Tên đăng nhập", { exact: true }).fill("admin");
    await page.getByLabel("Mật khẩu", { exact: true }).fill("Admin@123");
    await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();

    await expect(page).toHaveURL(/#admin$/);
    await expect(page.getByRole("heading", { name: /Bảng Điều Khiển Quản Lý/i })).toBeVisible();

    const storedToken = await page.evaluate(() => sessionStorage.getItem("smart_cafe_token"));
    expect(storedToken).toBe("mock-admin-token");
  });

  test("Login failure displays error alert message", async ({ page }) => {
    await page.route("**/api/v1/auth/login", route =>
      route.fulfill({
        status: 401,
        contentType: "application/json",
        body: JSON.stringify({
          status: 401,
          error: "Unauthorized",
          message: "Tài khoản hoặc mật khẩu không chính xác.",
        }),
      })
    );

    await page.goto("/#login");
    await page.getByLabel("Tên đăng nhập", { exact: true }).fill("wronguser");
    await page.getByLabel("Mật khẩu", { exact: true }).fill("WrongPass123!");
    await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();

    await expect(page.getByRole("alert")).toContainText("Tài khoản hoặc mật khẩu không chính xác.");
    await expect(page).toHaveURL(/#login$/);
  });

  for (const mode of ["pending", "network failure", "HTTP failure"]) {
    test(`logout clears credentials with ${mode} acknowledgement`, async ({ page }) => {
      let releaseLogout;
      const pending = new Promise(resolve => { releaseLogout = resolve; });
      await page.route("**/api/v1/auth/logout", async route => {
        if (mode === "pending") await pending;
        if (mode === "network failure") await route.abort();
        else await route.fulfill({ status: mode === "HTTP failure" ? 503 : 200, json: { message: "Fixture logout response" } });
      });
      await page.addInitScript(() => {
        if (sessionStorage.getItem("logout_fixture_initialized")) return;
        sessionStorage.setItem("logout_fixture_initialized", "true");
        sessionStorage.setItem("smart_cafe_token", "fixture-admin-token");
        sessionStorage.setItem("smart_cafe_user", JSON.stringify({ username: "admin", fullName: "Fixture", role: "ADMIN" }));
        localStorage.setItem("smart_cafe_token", "fixture-legacy-token");
        localStorage.setItem("smart_cafe_user", "fixture-legacy-user");
      });
      const errors = [];
      page.on("pageerror", error => errors.push(error.message));
      try {
        await page.goto("/#admin");
        const completed = mode === "network failure"
          ? page.waitForEvent("requestfailed", { predicate: request => request.url().endsWith("/api/v1/auth/logout") })
          : page.waitForResponse(response => response.url().endsWith("/api/v1/auth/logout"));
        await page.getByRole("button", { name: "Đăng xuất", exact: true }).click();
        await expect.poll(() => page.evaluate(() => sessionStorage.getItem("smart_cafe_token"))).toBeNull();
        await expect(page).toHaveURL(/#login$/);
        await expect(page.getByRole("heading", { name: "Đăng nhập", exact: true })).toBeVisible();
        await expect.poll(() => page.evaluate(() => sessionStorage.getItem("smart_cafe_user"))).toBeNull();
        releaseLogout();
        await completed;
        await expect.poll(() => page.evaluate(() => sessionStorage.getItem("smart_cafe_token"))).toBeNull();
        expect(await page.evaluate(() => ["smart_cafe_token", "smart_cafe_user"].map(key => localStorage.getItem(key)))).toEqual([null, null]);
        await page.reload();
        await expect(page.getByRole("heading", { name: "Đăng nhập", exact: true })).toBeVisible();
        expect(await page.evaluate(() => ["smart_cafe_token", "smart_cafe_user"].map(key => sessionStorage.getItem(key)))).toEqual([null, null]);
        expect(errors).toEqual([]);
      } finally {
        releaseLogout();
      }
    });
  }

  test("Forgot password flow navigates to OTP page and verifies code", async ({ page }) => {
    await page.route("**/api/v1/auth/forgot-password", route =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          message: "Nếu email tồn tại trên hệ thống, mã xác minh OTP đã được gửi.",
          challengeId: "mock-challenge-123",
          cooldownSeconds: 60,
          expiresInSeconds: 600,
        }),
      })
    );

    await page.route("**/api/v1/auth/verify-otp", route =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          resetToken: "mock-reset-token-xyz",
          message: "Mã OTP hợp lệ. Vui lòng thiết lập mật khẩu mới.",
          expiresInSeconds: 900,
        }),
      })
    );

    await page.goto("/#forgot-password");
    await expect(page.getByRole("heading", { name: "Quên mật khẩu", exact: true })).toBeVisible();

    await page.getByLabel("Địa chỉ Email", { exact: true }).fill("customer@example.com");
    await page.getByRole("button", { name: "Gửi mã OTP" }).click();

    await expect(page).toHaveURL(/#verify-otp/);
    await expect(page.getByRole("heading", { name: "Nhập mã OTP", exact: true })).toBeVisible();

    const otpInput = page.getByLabel("Mã xác minh 6 số", { exact: true });
    await otpInput.fill("123456");

    await page.getByRole("button", { name: "Xác minh OTP" }).click();

    await expect(page).toHaveURL(/#reset-password/);
    await expect(page.getByRole("heading", { name: "Đặt lại mật khẩu", exact: true })).toBeVisible();
  });

  test("Reset password validates and submits new password", async ({ page }) => {
    await page.route("**/api/v1/auth/reset-password", route => {
      if (route.request().method() === "POST") {
        return route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            message: "Đặt lại mật khẩu thành công. Vui lòng đăng nhập bằng mật khẩu mới.",
          }),
        });
      }
      return route.fallback();
    });

    await page.goto("/#reset-password?token=mock-reset-token-xyz");
    await expect(page.getByRole("heading", { name: "Đặt lại mật khẩu", exact: true })).toBeVisible();

    const newPass = page.getByLabel("Mật khẩu mới", { exact: true });
    const confirmPass = page.getByLabel("Xác nhận mật khẩu mới", { exact: true });

    await newPass.fill("NewSecret@2026");
    await confirmPass.fill("NewSecret@2026");

    await page.getByRole("button", { name: "Cập nhật mật khẩu" }).click();

    await expect(page.getByText(/Đặt lại mật khẩu thành công/)).toBeVisible();
    await expect(page.getByRole("button", { name: "Đăng nhập ngay" })).toBeVisible();
  });
});
