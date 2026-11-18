import { expect, test } from "@playwright/test";

test.describe("Figma DT2-2 Authentication Pixel-Perfect Verification", () => {
  test.beforeEach(async ({ page }) => {
    await page.route("**/api/v1/home/account", route =>
      route.fulfill({ status: 401, json: {} })
    );
    await page.route("**/api/v1/home/highlights", route =>
      route.fulfill({ json: { newest: [], mostOrdered: [] } })
    );
  });

  test("Login page renders Figma sidebar and form with brand assets", async ({ page }) => {
    await page.setViewportSize({ width: 1366, height: 768 });
    await page.goto("/#login");

    // Check Atmospheric Story Side components (Image 2)
    await expect(page.locator(".login-story-side")).toBeVisible();
    await expect(page.locator(".login-story-eyebrow")).toContainText("LECKER COFFEE CAFE");
    await expect(page.locator(".login-story-title")).toContainText("Một tách cà phê.");
    await expect(page.locator(".login-story-desc")).toHaveText("Chào mừng bạn trở lại với LECKER.");

    // Check Form elements & Brand Header
    await expect(page.locator(".auth-brand-tag")).toBeVisible();
    await expect(page.getByRole("heading", { name: "Đăng nhập", exact: true })).toBeVisible();
    await expect(page.getByLabel("Tên đăng nhập", { exact: true })).toBeVisible();
    await expect(page.getByLabel("Mật khẩu", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Đăng nhập", exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Hủy", exact: true })).toBeVisible();
    await expect(page.getByRole("link", { name: "← Về trang chủ", exact: true })).toBeVisible();
    await expect(page.getByRole("link", { name: "Quên mật khẩu?", exact: true })).toBeVisible();
    await expect(page.getByRole("link", { name: "Đăng ký (Chưa khả dụng)", exact: true })).toBeVisible();

    // Verify submit button is reachable and not clipped
    const submitBtn = page.getByRole("button", { name: "Đăng nhập", exact: true });
    await expect(submitBtn).toBeVisible();
  });

  test("Forgot password validates email format and navigates to OTP", async ({ page }) => {
    await page.setViewportSize({ width: 1366, height: 768 });
    await page.goto("/#forgot-password");

    await expect(page.locator(".auth-sidebar")).toBeVisible();
    await expect(page.getByRole("heading", { name: "Quên mật khẩu", exact: true })).toBeVisible();
    await expect(page.getByText("Bước 01 / 03 · Khôi phục tài khoản")).toBeVisible();

    // Invalid email validation (Frame 57:139)
    await page.getByLabel("Địa chỉ Email", { exact: true }).fill("not-an-email");
    await page.getByRole("button", { name: "Gửi mã OTP" }).click();
    await expect(page.getByRole("alert")).toContainText("Địa chỉ email không đúng định dạng.");

    // Successful submission (Frame 57:238)
    await page.route("**/api/v1/auth/forgot-password", route =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          message: "Mã OTP đã được gửi.",
          challengeId: "test-challenge-figma",
          cooldownSeconds: 60,
          expiresInSeconds: 600,
        }),
      })
    );

    await page.getByLabel("Địa chỉ Email", { exact: true }).fill("valid@smartcafe.vn");
    await page.getByRole("button", { name: "Gửi mã OTP" }).click();

    await expect(page).toHaveURL(/#verify-otp/);
    await expect(page.getByRole("heading", { name: "Nhập mã OTP", exact: true })).toBeVisible();
  });

  test("OTP page renders 6 individual boxes and handles input", async ({ page }) => {
    await page.route("**/api/v1/auth/forgot-password", route =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          message: "OTP sent",
          challengeId: "test-challenge-123",
          cooldownSeconds: 60,
          expiresInSeconds: 600,
        }),
      })
    );

    await page.goto("/#forgot-password");
    await page.getByLabel("Địa chỉ Email", { exact: true }).fill("customer@example.com");
    await page.getByRole("button", { name: "Gửi mã OTP" }).click();
    await expect(page).toHaveURL(/#verify-otp/);

    // Verify 6 OTP visual boxes (Frame 5:24)
    const boxes = page.locator(".otp-box");
    await expect(boxes).toHaveCount(6);

    // Fill OTP
    const otpInput = page.getByLabel("Mã xác minh 6 số", { exact: true });
    await otpInput.fill("654321");

    // Verify boxes display the digits
    await expect(boxes.nth(0)).toHaveText("6");
    await expect(boxes.nth(1)).toHaveText("5");
    await expect(boxes.nth(2)).toHaveText("4");
    await expect(boxes.nth(3)).toHaveText("3");
    await expect(boxes.nth(4)).toHaveText("2");
    await expect(boxes.nth(5)).toHaveText("1");
  });

  test("Reset password validates password match and renders success state", async ({ page }) => {
    await page.route(/\/api\/v1\/auth\/verify-reset-token/, route =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ valid: true, email: "user@smartcafe.vn" }),
      })
    );

    await page.goto("/#reset-password?token=valid-figma-token");
    await expect(page.getByRole("heading", { name: "Đặt lại mật khẩu", exact: true })).toBeVisible();

    // Password mismatch validation (Frame 57:187)
    await page.getByLabel("Mật khẩu mới", { exact: true }).fill("Password@123");
    await page.getByLabel("Xác nhận mật khẩu mới", { exact: true }).fill("Password@999");
    await page.getByRole("button", { name: "Cập nhật mật khẩu" }).click();
    await expect(page.getByRole("alert")).toContainText("Xác nhận mật khẩu không khớp.");

    // Successful reset (Frame 57:206)
    await page.route("**/api/v1/auth/reset-password", route =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ message: "Mật khẩu đã được cập nhật thành công!" }),
      })
    );

    await page.getByLabel("Xác nhận mật khẩu mới", { exact: true }).fill("Password@123");
    await page.getByRole("button", { name: "Cập nhật mật khẩu" }).click();

    // Verify Frame 57:206 success view
    await expect(page.getByRole("heading", { name: "Đổi mật khẩu thành công!" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Đăng nhập ngay" })).toBeVisible();
  });

  test("Register shows its unavailable notice immediately without collecting personal data", async ({ page }) => {
    const mutations = [];
    page.on("request", request => {
      if (!["GET", "HEAD", "OPTIONS"].includes(request.method())) mutations.push(request.url());
    });
    await page.goto("/#register");
    await expect(page.locator(".auth-sidebar")).toBeVisible();
    await expect(page.getByRole("heading", { name: "Đăng ký tài khoản", exact: true })).toBeVisible();
    await expect(page.getByRole("status")).toHaveText(
      "Đăng ký tài khoản trực tuyến hiện chưa khả dụng. Vui lòng đăng nhập bằng tài khoản hiện có hoặc liên hệ nhân viên quán để được hỗ trợ."
    );
    await expect(page.locator("main input, main textarea, main select, main form")).toHaveCount(0);
    await expect(page.getByRole("button", { name: /ĐĂNG KÝ & NHẬN OTP|KÍCH HOẠT TÀI KHOẢN/i })).toHaveCount(0);
    await expect(page.getByText(/Xem Frame|Xem thử màn|Verify Demo|Preview OTP/i)).toHaveCount(0);
    await page.getByRole("button", { name: "QUAY VỀ ĐĂNG NHẬP", exact: true }).click();
    await expect(page).toHaveURL(/#login$/);
    await expect(page.getByRole("heading", { name: "Đăng nhập", exact: true })).toBeVisible();
    expect(mutations).toEqual([]);
  });

  test("Login identifies registration as unavailable and keeps working navigation", async ({ page }) => {
    await page.goto("/#login");
    const registration = page.getByRole("link", { name: "Đăng ký (Chưa khả dụng)", exact: true });
    await expect(registration).toContainText("Chưa khả dụng");
    await registration.click();
    await expect(page).toHaveURL(/#register$/);
    await expect(page.getByRole("status")).toContainText("Đăng ký tài khoản trực tuyến hiện chưa khả dụng.");
    await page.getByRole("link", { name: "← Về trang chủ", exact: true }).click();
    await expect(page).toHaveURL(/#top$/);
  });

  for (const legacyRoute of ["#register/verify", "#register-verify", "#register?step=verify&challengeId=legacy"]) {
    test(`legacy registration route ${legacyRoute} cannot activate an account`, async ({ page }) => {
      const mutations = [];
      page.on("request", request => {
        if (!["GET", "HEAD", "OPTIONS"].includes(request.method())) mutations.push(request.url());
      });
      await page.goto(`/${legacyRoute}`);
      await expect(page.getByRole("status")).toContainText("Đăng ký tài khoản trực tuyến hiện chưa khả dụng.");
      await expect(page.locator("main input, main form")).toHaveCount(0);
      await expect(page.getByRole("heading", { name: "Xác minh tài khoản", exact: true })).toHaveCount(0);
      await expect(page.getByRole("button", { name: "KÍCH HOẠT TÀI KHOẢN", exact: true })).toHaveCount(0);
      await page.getByRole("button", { name: "QUAY VỀ ĐĂNG NHẬP", exact: true }).click();
      await expect(page).toHaveURL(/#login$/);
      expect(mutations).toEqual([]);
    });
  }

  test("Direct access to verify without valid challenge shows clean invalid session notice", async ({ page }) => {
    await page.goto("/#verify-otp");
    await expect(page.getByRole("heading", { name: "Phiên xác minh đã kết thúc", exact: true })).toBeVisible();
    await expect(page.getByLabel("Mã xác minh 6 số", { exact: true })).toHaveCount(0);
    await page.getByRole("button", { name: "Bắt đầu lại", exact: true }).click();
    await expect(page).toHaveURL(/#forgot-password$/);
  });

  test("Desktop responsive verification (1920x1080, 1440x900, 1366x768, 1280x800) with screenshots", async ({ page }, testInfo) => {
    test.setTimeout(90000);
    const desktopViewports = [
      { name: "desktop-1920x1080", width: 1920, height: 1080 },
      { name: "desktop-1440x900", width: 1440, height: 900 },
      { name: "desktop-1366x768", width: 1366, height: 768 },
      { name: "desktop-1280x800", width: 1280, height: 800 },
    ];

    const routes = [
      { path: "/#login", label: "login" },
      { path: "/#register", label: "register" },
      { path: "/#forgot-password", label: "forgot-password" },
    ];

    for (const vp of desktopViewports) {
      await page.setViewportSize({ width: vp.width, height: vp.height });

      for (const r of routes) {
        await page.goto(r.path);

        const hasOverflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
        expect(hasOverflow, `Horizontal overflow on ${r.label} at ${vp.name}`).toBe(false);

        await expect(page.locator(".auth-card")).toBeVisible();

        if (r.label === "register") {
          const submitBtn = page.getByRole("button", { name: "QUAY VỀ ĐĂNG NHẬP", exact: true });
          await expect(page.getByRole("status")).toContainText("Đăng ký tài khoản trực tuyến hiện chưa khả dụng.");
          await submitBtn.scrollIntoViewIfNeeded();
          await expect(submitBtn).toBeVisible();
        }

        if (vp.name === "desktop-1366x768" || vp.name === "desktop-1920x1080") {
          const filename = `${r.label}_${vp.name}.png`;
          await page.screenshot({
            path: testInfo.outputPath(filename),
            fullPage: false,
          });
        }
      }
    }
  });

  test("Tablet responsive verification (1024x768, 768x1024) with screenshots", async ({ page }, testInfo) => {
    test.setTimeout(60000);
    const tabletViewports = [
      { name: "tablet-1024x768", width: 1024, height: 768 },
      { name: "tablet-768x1024", width: 768, height: 1024 },
    ];

    const routes = [
      { path: "/#login", label: "login" },
      { path: "/#register", label: "register" },
      { path: "/#forgot-password", label: "forgot-password" },
    ];

    for (const vp of tabletViewports) {
      await page.setViewportSize({ width: vp.width, height: vp.height });

      for (const r of routes) {
        await page.goto(r.path);

        const hasOverflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
        expect(hasOverflow, `Horizontal overflow on ${r.label} at ${vp.name}`).toBe(false);

        await expect(page.locator(".auth-card")).toBeVisible();

        if (vp.name === "tablet-768x1024") {
          const filename = `${r.label}_${vp.name}.png`;
          await page.screenshot({
            path: testInfo.outputPath(filename),
            fullPage: false,
          });
        }
      }
    }
  });

  test("Mobile responsive verification (430x932, 390x844, 375x812, 360x800) with screenshots", async ({ page }, testInfo) => {
    test.setTimeout(90000);
    const mobileViewports = [
      { name: "mobile-430x932", width: 430, height: 932 },
      { name: "mobile-390x844", width: 390, height: 844 },
      { name: "mobile-375x812", width: 375, height: 812 },
      { name: "mobile-360x800", width: 360, height: 800 },
    ];

    const routes = [
      { path: "/#login", label: "login" },
      { path: "/#register", label: "register" },
      { path: "/#forgot-password", label: "forgot-password" },
    ];

    for (const vp of mobileViewports) {
      await page.setViewportSize({ width: vp.width, height: vp.height });

      for (const r of routes) {
        await page.goto(r.path);

        const hasOverflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
        expect(hasOverflow, `Horizontal overflow on ${r.label} at ${vp.name}`).toBe(false);

        const card = page.locator(".auth-card");
        await expect(card).toBeVisible();

        if (r.label === "login") {
          // Verify 2-column mobile layout (Image 5 / Figma)
          await expect(page.locator(".auth-sidebar-mobile-brand")).toBeVisible();
          await expect(page.locator(".mobile-brand-title")).toHaveText("LECKER");
          await expect(page.locator(".mobile-tagline-cafe")).toHaveText("CAFE");

          const loginBtn = page.getByRole("button", { name: /ĐĂNG NHẬP/i });
          await expect(loginBtn).toBeVisible();
          await expect(loginBtn).toBeInViewport();

          const registerLink = page.getByRole("link", { name: /Đăng ký/i });
          await expect(registerLink).toBeVisible();
          await expect(registerLink).toBeInViewport();

          // On mobile 390x844, ensure entire login card is visible without vertical scroll
          if (vp.name === "mobile-390x844") {
            const cardBox = await card.boundingBox();
            expect(cardBox).not.toBeNull();
            expect(cardBox.height).toBeLessThan(vp.height);
          }
        }

        if (r.label === "register") {
          const submitBtn = page.getByRole("button", { name: "QUAY VỀ ĐĂNG NHẬP", exact: true });
          await expect(page.getByRole("status")).toContainText("Đăng ký tài khoản trực tuyến hiện chưa khả dụng.");
          await submitBtn.scrollIntoViewIfNeeded();
          await expect(submitBtn).toBeVisible();
        }

        if (vp.name === "mobile-390x844" || vp.name === "mobile-375x812" || vp.name === "mobile-360x800") {
          const filename = `${r.label}_${vp.name}.png`;
          await page.screenshot({
            path: testInfo.outputPath(filename),
            fullPage: false,
          });
        }
      }
    }
  });
});
