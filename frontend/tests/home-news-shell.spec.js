import { expect, test } from "@playwright/test";

async function setup(page, role = "ADMIN") {
  await page.addInitScript(() => sessionStorage.setItem("smart_cafe_token", "isolated-shell-fixture"));
  await page.route("**/api/v1/auth/me", route => route.fulfill({ json: { id: 1, username: "ui-fixture", role } }));
  await page.route("**/api/v1/home/account", route => route.fulfill({ json: { id: "1", name: "Tài khoản kiểm thử", role } }));
  await page.route("**/api/v1/home/notifications", route => route.fulfill({ json: { recipientId: "1", items: [] } }));
  await page.route("**/api/v1/news?*", route => route.fulfill({ json: { content: [], totalElements: 0, totalPages: 0, last: true, page: 0, size: 6 } }));
}

test("an ADMIN notification recheck hides the editor and preserves the same draft", async ({ page }) => {
  await page.clock.install();
  await setup(page);
  let finishRecheck, notifications = 0, writes = 0;
  const recheck = new Promise(resolve => { finishRecheck = resolve; });
  const identity = { id: "1", name: "Tài khoản kiểm thử", role: "ADMIN" };
  await page.route("**/api/v1/home/account", async route => {
    if (notifications > 1) await recheck;
    await route.fulfill({ json: identity });
  });
  await page.route("**/api/v1/home/notifications", route => ++notifications === 1
    ? route.fulfill({ json: { recipientId: "1", items: [] } }) : route.fulfill({ status: 403, json: {} }));
  const pixel = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=", "base64");
  await page.route("**/api/v1/news/images", route => route.fulfill({ status: 201, json: { mediaId: "42", url: "/api/v1/news/images/42" } }));
  await page.route("**/api/v1/news/images/42", route => route.fulfill({ contentType: "image/png", body: pixel }));
  await page.route("**/api/v1/news", route => { writes++; return route.fulfill({ status: 500, json: {} }); });
  await page.goto("/#news/new");
  const title = page.locator("#news-title-input");
  const summary = page.locator("#news-summary");
  const body = page.locator("#news-body");
  await title.fill("Bản nháp chưa đăng");
  await summary.fill("Tóm tắt cần giữ");
  await body.fill("Nội dung cần giữ sau khi xác minh lại.");
  await page.getByRole("checkbox", { name: "Nhân viên" }).check();
  await page.locator("#news-images").setInputFiles({ name: "bai-viet.png", mimeType: "image/png", buffer: pixel });
  await expect(page.getByRole("button", { name: "Gỡ ảnh 1" })).toBeVisible();
  await expect.poll(() => notifications).toBe(1);
  await page.clock.fastForward(5500);
  await expect(page.getByText("Đang kiểm tra phiên đăng nhập…")).toBeVisible();
  await expect(title).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Lưu bản nháp" })).toHaveCount(0);
  finishRecheck();
  await expect(title).toHaveValue("Bản nháp chưa đăng");
  await expect(summary).toHaveValue("Tóm tắt cần giữ");
  await expect(body).toHaveValue("Nội dung cần giữ sau khi xác minh lại.");
  await expect(page.getByRole("checkbox", { name: "Nhân viên" })).toBeChecked();
  await expect(page.getByRole("button", { name: "Gỡ ảnh 1" })).toBeVisible();
  expect(writes).toBe(0);
});

test("a POST that loses its response during recheck stays in manual recovery", async ({ page }) => {
  await setup(page);
  await page.clock.install();
  const recheck = Promise.withResolvers(), postStarted = Promise.withResolvers(), releasePost = Promise.withResolvers();
  let notices = 0, writes = 0;
  await page.route("**/api/v1/home/account", async route => {
    if (notices > 1) await recheck.promise;
    return route.fulfill({ json: { id: "1", name: "Tài khoản kiểm thử", role: "ADMIN" } });
  });
  await page.route("**/api/v1/home/notifications", route => ++notices === 1
    ? route.fulfill({ json: { recipientId: "1", items: [] } }) : route.fulfill({ status: 403, json: {} }));
  await page.route("**/api/v1/news", async route => {
    writes++;
    postStarted.resolve();
    await releasePost.promise;
    await route.abort("connectionreset");
  });
  await page.goto("/#news/new");
  const title = page.locator("#news-title-input");
  await title.fill("Tin chưa rõ đã lưu");
  await page.locator("#news-body").fill("Nội dung cần đối soát.");
  await page.getByRole("checkbox", { name: "Nhân viên" }).check();
  await page.getByRole("button", { name: "Lưu bản nháp" }).click();
  await postStarted.promise;
  await page.clock.fastForward(5500);
  await expect(title).toHaveCount(0);
  const failed = page.waitForEvent("requestfailed", { predicate: request => request.url().endsWith("/api/v1/news") });
  releasePost.resolve();
  await failed;
  recheck.resolve();
  await expect(page.getByRole("alert")).toContainText("Chưa xác nhận được kết quả lưu bài.");
  await expect(title).toHaveValue("Tin chưa rõ đã lưu");
  await expect(page.getByRole("button", { name: "Kiểm tra bài đã lưu" })).toBeEnabled();
  expect(writes).toBe(1);
});

test("switching sessions during recheck remounts the editor without the earlier draft", async ({ page }) => {
  await setup(page);
  await page.clock.install();
  let notifications = 0;
  const pendingRecheck = Promise.withResolvers(), releaseRecheck = Promise.withResolvers();
  const idFrom = route => route.request().headers().authorization === "Bearer second-editor-fixture" ? "2" : "1";
  await page.route("**/api/v1/auth/me", route => route.fulfill({ json: { id: idFrom(route), username: "ui-fixture", role: "ADMIN" } }));
  await page.route("**/api/v1/home/account", async route => {
    const id = idFrom(route);
    if (id === "1" && notifications > 1) {
      pendingRecheck.resolve();
      await releaseRecheck.promise;
    }
    await route.fulfill({ json: { id, name: `Editor ${id}`, role: "ADMIN" } }).catch(() => {});
  });
  await page.route("**/api/v1/home/notifications", route => idFrom(route) === "1" && ++notifications > 1
    ? route.fulfill({ status: 403, json: {} }) : route.fulfill({ json: { recipientId: idFrom(route), items: [] } }));
  await page.goto("/#news/new");
  const title = page.locator("#news-title-input");
  await title.fill("Nội dung riêng của phiên trước");
  await expect.poll(() => notifications).toBe(1);
  await page.clock.fastForward(5500);
  await pendingRecheck.promise;
  await expect(title).toHaveCount(0);
  await page.evaluate(() => {
    sessionStorage.setItem("smart_cafe_token", "second-editor-fixture");
    window.dispatchEvent(new Event("smart-cafe-auth-change"));
  });
  await expect(title).toHaveValue("");
  releaseRecheck.resolve();
  await expect(page.getByRole("button", { name: "Tài khoản Editor 2" })).toBeVisible();
  await expect(title).toHaveValue("");
});

for (const outcome of ["temporary", "revoked", "role changed"]) test(`an edit with a pending PUT handles ${outcome} identity recheck without replay`, async ({ page }) => {
  await page.clock.install();
  await setup(page);
  const id = "9007199254740993", releasedPut = Promise.withResolvers(), startedPut = Promise.withResolvers();
  let notifications = 0, failedCheck = false, writes = 0, changedRole = false;
  const image = { mediaId: "7", secureUrl: "/api/v1/news/images/7", sortOrder: 0 };
  const article = { id, title: "Bài hiện có", body: "Nội dung trên máy chủ.", summary: "Tóm tắt ban đầu",
    authorId: "1", authorName: "LECKER", slug: "bai-hien-co", status: "DRAFT", audiences: ["STAFF"],
    createdAt: "2026-11-11T01:00:00Z", publishedAt: null, version: 4, images: [image] };
  await page.route("**/api/v1/auth/me", route => route.fulfill({
    json: { id: 1, username: "ui-fixture", role: changedRole ? "STAFF" : "ADMIN" },
  }));
  await page.route("**/api/v1/home/account", route => {
    if (notifications > 1) {
      if (!failedCheck) { failedCheck = true; return route.fulfill({ status: 503, json: {} }); }
      if (outcome === "revoked") return route.fulfill({ status: 403, json: {} });
      if (outcome === "role changed") changedRole = true;
    }
    return route.fulfill({ json: { id: "1", name: "Tài khoản kiểm thử", role: changedRole ? "STAFF" : "ADMIN" } });
  });
  await page.route("**/api/v1/home/notifications", route => ++notifications === 1
    ? route.fulfill({ json: { recipientId: "1", items: [] } }) : route.fulfill({ status: 403, json: {} }));
  await page.route("**/api/v1/news/images/7", route => route.fulfill({ contentType: "image/png",
    body: Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==", "base64") }));
  await page.route(`**/api/v1/news/${id}`, async route => {
    if (route.request().method() === "GET") return route.fulfill({ json: article, headers: { ETag: '"4"' } });
    writes++;
    startedPut.resolve({ ...route.request().postDataJSON(), etag: route.request().headers()["if-match"] });
    await releasedPut.promise;
    await route.abort("connectionreset");
  });
  await page.goto(`/#news/${id}/edit`);
  const title = page.locator("#news-title-input"), body = page.locator("#news-body");
  await expect(title).toHaveValue(article.title);
  await title.fill("Bản sửa đang giữ");
  await body.fill("Nội dung chỉnh sửa đang giữ.");
  await page.locator("#news-summary").fill("Tóm tắt chỉnh sửa");
  await page.getByRole("checkbox", { name: "Khách hàng" }).check();
  await page.getByRole("button", { name: "Lưu thay đổi" }).click();
  expect(await startedPut.promise).toMatchObject({ title: "Bản sửa đang giữ", etag: '"4"', imageMediaIds: ["7"], audiences: ["STAFF", "CUSTOMER"] });
  await page.clock.fastForward(5500);
  await expect(page.getByText("Chưa tải được thông tin tài khoản.")).toBeVisible();
  await expect(title).toHaveCount(0);
  releasedPut.resolve();
  await page.getByRole("button", { name: "Thử lại", exact: true }).click();
  if (outcome === "temporary") {
    await expect(title).toHaveValue("Bản sửa đang giữ");
    await expect(body).toHaveValue("Nội dung chỉnh sửa đang giữ.");
    await expect(page.locator("#news-summary")).toHaveValue("Tóm tắt chỉnh sửa");
    await expect(page.getByRole("checkbox", { name: "Khách hàng" })).toBeChecked();
    await expect(page.getByRole("button", { name: "Gỡ ảnh 1" })).toBeVisible();
    await expect(page.getByRole("alert")).toContainText("Chưa xác nhận được kết quả lưu bài");
    await expect(page.getByRole("button", { name: "Lưu thay đổi" })).toBeDisabled();
  } else {
    await expect(page.getByText(outcome === "revoked" ? "Đăng nhập để xem những tin tức dành cho bạn." : "Bạn không có quyền quản lý tin tức")).toBeVisible();
    await expect(title).toHaveCount(0);
  }
  expect(writes).toBe(1);
});

test("scrolling to the top of a dirty editor keeps the draft without asking to leave", async ({ page }) => {
  await setup(page);
  let prompts = 0;
  page.on("dialog", dialog => { prompts++; return dialog.dismiss(); });
  await page.goto("/#news/new");
  const title = page.getByLabel("Tiêu đề", { exact: false });
  await title.fill("Bản nháp cần giữ");
  await page.getByRole("button", { name: "Về đầu trang", exact: true }).click();
  await expect(page.getByRole("main")).toBeFocused();
  expect(prompts).toBe(0);
  await expect(title).toHaveValue("Bản nháp cần giữ");
  await expect(page).toHaveURL(/#news\/new$/);
});

test("news draft Back and Forward work without the browser Navigation API", async ({ page }) => {
  await page.addInitScript(() => Object.defineProperty(window, "navigation", { value: undefined }));
  await setup(page);
  await page.goto("/#news");
  await page.getByRole("link", { name: "Viết tin mới" }).click();
  const title = page.getByLabel("Tiêu đề", { exact: false });
  await title.fill("Bản nháp chưa lưu");
  let prompts = 0;
  page.once("dialog", dialog => { prompts++; return dialog.dismiss(); });
  await page.goBack();
  await expect(page).toHaveURL(/#news\/new$/);
  await expect(title).toHaveValue("Bản nháp chưa lưu");
  page.once("dialog", dialog => { prompts++; return dialog.accept(); });
  await page.goBack();
  await expect(page.getByRole("heading", { name: "Quản lý tin tức" })).toBeVisible();
  await page.goForward();
  await expect(title).toHaveValue("");
  expect(prompts).toBe(2);
});

test("a temporary identity failure after policy-close recovers without reconnecting early", async ({ page }) => {
  await page.clock.install();
  await setup(page, "STAFF");
  let failNextCheck = false;
  const sockets = [];
  await page.route("**/api/v1/home/account", route => {
    if (failNextCheck) {
      failNextCheck = false;
      return route.fulfill({ status: 503, json: {} });
    }
    return route.fulfill({ json: { id: "1", name: "Staff fixture", role: "STAFF" } });
  });
  await page.routeWebSocket("**/api/v1/news/events", ws => {
    sockets.push(ws);
    ws.onMessage(() => ws.send('{"type":"READY"}'));
  });
  await page.goto("/#news");
  await expect(page.getByText("Tin tức tự cập nhật khi có thay đổi.")).toBeVisible();
  failNextCheck = true;
  sockets[0].close({ code: 1008 });
  await expect(page.getByText("Chưa tải được thông tin tài khoản.")).toBeVisible();
  expect(sockets).toHaveLength(1);
  await page.clock.fastForward(5500);
  await expect.poll(() => sockets.length).toBe(2);
  await expect(page.getByText("Tin tức tự cập nhật khi có thay đổi.")).toBeVisible();
});
