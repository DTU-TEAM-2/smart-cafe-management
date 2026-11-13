import { expect, test } from "@playwright/test";

// UI-only HTTP fixtures; the local end-to-end report separately exercises the real issuer and MySQL.
const fixture = { id: "9007199254740993", title: "Những ngày thơm hương cà phê", summary: "Câu chuyện từ quầy bar LECKER.", body: "Dòng thứ nhất.\nDòng thứ hai.",
  status: "PUBLISHED", slug: "ca-phe", authorName: "LECKER", authorId: "1", audiences: ["STAFF", "CUSTOMER"], images: [],
  publishedAt: "2026-11-11T01:00:00Z", createdAt: "2026-11-11T01:00:00Z", version: 0 };

async function setup(page, role = "ADMIN", { error = false, empty = false } = {}) {
  await page.addInitScript(() => sessionStorage.setItem("smart_cafe_token", "isolated-ui-fixture-token"));
  await page.route("**/api/v1/home/account", route => route.fulfill({ json: { id: "1", name: "Tài khoản kiểm thử", username: "ui-fixture", role } }));
  await page.route("**/api/v1/home/highlights", route => route.fulfill({ json: { newest: [], mostOrdered: [] } }));
  await page.route("**/api/v1/home/notifications", route => route.fulfill({ json: { recipientId: "1", items: [] } }));
  await page.routeWebSocket("**/api/v1/news/events", ws => ws.onMessage(() => ws.send(JSON.stringify({ type: "READY" }))));
  await page.route("**/api/v1/news?*", route => route.fulfill({ status: error ? 503 : 200,
    json: error ? {} : { content: empty ? [] : [fixture], totalElements: empty ? 0 : 1, totalPages: empty ? 0 : 1, last: true, page: 0, size: 6 } }));
  await page.route(`**/api/v1/news/${fixture.id}`, route => route.fulfill({ json: fixture, headers: { ETag: '"0"' } }));
}

test("admin creates news with explicit recipients and safe text content", async ({ page }) => {
  await setup(page);
  let posted;
  await page.route("**/api/v1/news", async route => {
    posted = route.request().postDataJSON();
    await route.fulfill({ status: 201, json: { ...fixture, ...posted }, headers: { ETag: '"0"' } });
  });
  await page.goto("/#news");
  await page.getByRole("link", { name: "Viết tin mới" }).click();
  await page.getByRole("button", { name: "Lưu bản nháp", exact: true }).click();
  await expect(page.getByText("Chọn ít nhất một nhóm người nhận.", { exact: true })).toBeVisible();
  await expect(page.getByLabel("Tiêu đề", { exact: false })).toHaveAttribute("aria-invalid", "true");
  await page.getByLabel("Tiêu đề", { exact: false }).fill("Câu chuyện mới");
  await page.getByLabel("Nội dung", { exact: false }).fill("<script>window.__unsafe = true</script>");
  await page.getByRole("checkbox", { name: /Nhân viên/ }).check();
  await page.getByRole("checkbox", { name: /Khách hàng/ }).check();
  await page.getByLabel("Trạng thái", { exact: true }).selectOption("PUBLISHED");
  await page.getByRole("button", { name: "Đăng tin", exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`#news/${fixture.id}$`));
  expect(posted.audiences).toEqual(["STAFF", "CUSTOMER"]);
  expect(posted.status).toBe("PUBLISHED");
  expect(posted.imageMediaIds).toEqual([]);
  expect(await page.evaluate(() => window.__unsafe)).toBeUndefined();
});

test("editing sends If-Match and keeps unsaved text after a conflict", async ({ page }) => {
  await setup(page);
  let ifMatch;
  await page.route(`**/api/v1/news/${fixture.id}`, route => {
    if (route.request().method() === "PUT") {
      ifMatch = route.request().headers()["if-match"];
      return route.fulfill({ status: 412, json: {} });
    }
    return route.fulfill({ json: fixture, headers: { ETag: '"0"' } });
  });
  await page.goto(`/#news/${fixture.id}/edit`);
  await page.getByLabel("Tiêu đề", { exact: false }).fill("Nội dung tôi chưa muốn mất");
  await page.getByRole("button", { name: "Lưu thay đổi" }).click();
  await expect(page.getByRole("alert")).toContainText("Bài viết đã được thay đổi ở nơi khác");
  await expect(page.getByLabel("Tiêu đề", { exact: false })).toHaveValue("Nội dung tôi chưa muốn mất");
  expect(ifMatch).toBe('"0"');
  await expect(page.getByRole("button", { name: "Lưu thay đổi" })).toBeDisabled();
});

test("dirty editor protects cancel and browser back navigation", async ({ page }) => {
  await setup(page);
  await page.goto("/#news");
  await page.getByRole("link", { name: "Viết tin mới" }).click();
  await page.getByLabel("Tiêu đề", { exact: false }).fill("Bản đang viết");
  page.once("dialog", dialog => dialog.dismiss());
  await page.getByRole("link", { name: "Hủy và quay lại" }).click();
  await expect(page).toHaveURL(/#news\/new$/);
  await expect(page.getByLabel("Tiêu đề", { exact: false })).toHaveValue("Bản đang viết");
  page.once("dialog", dialog => dialog.dismiss());
  await page.goBack();
  await expect(page).toHaveURL(/#news\/new$/);
  page.once("dialog", dialog => dialog.accept());
  await page.getByRole("link", { name: "Hủy và quay lại" }).click();
  await expect(page.getByRole("heading", { name: "Quản lý tin tức", exact: true })).toBeVisible();
});

test("archive confirmation traps focus, restores it and sends the current version", async ({ page }) => {
  await setup(page);
  let archived = false;
  await page.route(`**/api/v1/news/${fixture.id}`, route => {
    if (route.request().method() === "DELETE") {
      archived = route.request().headers()["if-match"] === '"0"';
      return route.fulfill({ status: 204 });
    }
    return route.fulfill({ json: fixture, headers: { ETag: '"0"' } });
  });
  await page.goto(`/#news/${fixture.id}`);
  await page.getByRole("button", { name: "Lưu trữ tin", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole("button", { name: "Giữ lại" })).toBeFocused();
  await page.keyboard.press("Shift+Tab");
  await expect(dialog.getByRole("button", { name: "Xác nhận lưu trữ" })).toBeFocused();
  await page.keyboard.press("Tab");
  await expect(dialog.getByRole("button", { name: "Giữ lại" })).toBeFocused();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("button", { name: "Lưu trữ tin", exact: true })).toBeFocused();
  await page.getByRole("button", { name: "Lưu trữ tin", exact: true }).click();
  await dialog.getByRole("button", { name: "Xác nhận lưu trữ" }).click();
  await expect(page).toHaveURL(/#news$/);
  expect(archived).toBe(true);
});

test("private images use bearer, uploading preserves images and invalid files are rejected", async ({ page }) => {
  await setup(page);
  let auth, posted;
  const pixel = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=", "base64");
  await page.route("**/api/v1/news/images", route => route.fulfill({ status: 201, json: { mediaId: "42", url: "/api/v1/news/images/42" } }));
  await page.route("**/api/v1/news/images/42", route => { auth = route.request().headers().authorization; return route.fulfill({ contentType: "image/png", body: pixel }); });
  await page.route("**/api/v1/news", route => { posted = route.request().postDataJSON(); return route.fulfill({ status: 201, json: fixture }); });
  await page.goto("/#news/new");
  await page.getByLabel("Tiêu đề", { exact: false }).fill("Tin có ảnh");
  await page.getByLabel("Nội dung", { exact: false }).fill("Nội dung tin có ảnh");
  await page.getByRole("checkbox", { name: /Nhân viên/ }).check();
  await page.locator('input[type="file"]').setInputFiles({ name: "bad.svg", mimeType: "image/svg+xml", buffer: Buffer.from("<svg/>") });
  await expect(page.getByRole("alert")).toContainText("Chọn ảnh PNG/JPEG");
  await page.locator('input[type="file"]').setInputFiles({ name: "image.png", mimeType: "image/png", buffer: pixel });
  await expect(page.getByRole("button", { name: "Gỡ ảnh 1" })).toBeVisible();
  await expect.poll(() => auth).toBe("Bearer isolated-ui-fixture-token");
  await page.getByRole("button", { name: "Lưu bản nháp", exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`#news/${fixture.id}$`));
  expect(posted.imageMediaIds).toEqual(["42"]);
});

for (const status of [204, 503]) test(`leaving during archive ignores the late ${status} response`, async ({ page }) => {
  await setup(page);
  const errors = [];
  page.on("pageerror", error => errors.push(error.message));
  let release, started, settled;
  const responseGate = new Promise(resolve => { release = resolve; });
  const requestStarted = new Promise(resolve => { started = resolve; });
  const requestSettled = new Promise(resolve => { settled = resolve; });
  const archivedPath = `/api/v1/news/${fixture.id}`;
  for (const event of ["requestfinished", "requestfailed"]) page.on(event, request => {
    if (request.method() === "DELETE" && new URL(request.url()).pathname === archivedPath) settled();
  });
  await page.route(`**${archivedPath}`, async route => {
    if (route.request().method() !== "DELETE") return route.fulfill({ json: fixture, headers: { ETag: '"0"' } });
    started(); await responseGate;
    try { await route.fulfill(status === 204 ? { status } : { status, json: {} }); }
    catch { /* Navigation may already have cancelled this request. */ }
  });
  await page.goto("/#top");
  await page.evaluate(() => { location.hash = "#news"; });
  await page.getByRole("heading", { name: fixture.title, exact: true }).getByRole("link").click();
  await page.getByRole("button", { name: "Lưu trữ tin", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Xác nhận lưu trữ", exact: true }).click();
  await requestStarted;
  await page.goBack(); await expect(page).toHaveURL(/#news$/);
  await page.goBack(); await expect(page).toHaveURL(/#top$/);
  release(); await requestSettled;
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(resolve)));
  await expect(page).toHaveURL(/#top$/);
  expect(errors).toEqual([]);
});

test("customer cannot access editor and article text never becomes HTML", async ({ page }) => {
  await setup(page, "CUSTOMER");
  await page.goto("/#news");
  await expect(page.getByRole("heading", { name: "Chuyện từ LECKER." })).toBeVisible();
  await expect(page.getByRole("link", { name: "Viết tin mới" })).toHaveCount(0);
  await expect(page.getByLabel("Trạng thái", { exact: true })).toHaveCount(0);
  await page.goto("/#news/new");
  await expect(page.getByRole("heading", { name: "Bạn không có quyền quản lý tin tức" })).toBeVisible();
  await page.route(`**/api/v1/news/${fixture.id}`, route => route.fulfill({ json: { ...fixture, title: "<img src=x onerror=alert(1)>", body: "<script>window.__unsafe=true</script>" } }));
  await page.goto(`/#news/${fixture.id}`);
  await expect(page.getByRole("heading", { name: "<img src=x onerror=alert(1)>" })).toBeVisible();
  await expect(page.getByText("<script>window.__unsafe=true</script>", { exact: true })).toBeVisible();
  expect(await page.evaluate(() => window.__unsafe)).toBeUndefined();
});

test("staff socket refreshes the feed and reconnects without putting tokens in URLs", async ({ page }) => {
  await setup(page, "STAFF");
  let socket, title = fixture.title;
  await page.routeWebSocket("**/api/v1/news/events", ws => { socket = ws; ws.onMessage(() => ws.send('{"type":"READY"}')); });
  await page.route("**/api/v1/news?*", route => route.fulfill({ json: { content: [{ ...fixture, title }], totalElements: 1, totalPages: 1, last: true } }));
  await page.goto("/#news");
  await expect(page.getByText("Tin tức tự cập nhật khi có thay đổi.")).toBeVisible();
  title = "Tin vừa được cập nhật"; socket.send('{"type":"NEWS_CHANGED"}');
  await expect(page.getByRole("heading", { name: title })).toBeVisible();
  expect(socket.url()).not.toContain("token");
  socket.close({ code: 1011, reason: "Test disconnect" });
  await expect(page.getByText(/Đang kết nối cập nhật tin tức/)).toBeVisible();
  await expect(page.getByText("Tin tức tự cập nhật khi có thay đổi.")).toBeVisible();
});

test("guest gate, empty feed, retry and mobile layout remain usable", async ({ page }) => {
  await setup(page, "CUSTOMER", { empty: true });
  await page.goto("/#news");
  await expect(page.getByRole("heading", { name: "Chưa có câu chuyện nào" })).toBeVisible();
  await page.route("**/api/v1/news?*", route => route.fulfill({ status: 503, json: {} }));
  await page.getByRole("button", { name: "Làm mới" }).click();
  await expect(page.getByRole("alert")).toContainText("Chưa tải được tin tức");
  await page.route("**/api/v1/news?*", route => route.fulfill({ json: { content: [fixture], totalElements: 1, totalPages: 1, last: true } }));
  await page.getByRole("button", { name: "Thử lại" }).click();
  await expect(page.getByRole("heading", { name: fixture.title })).toBeVisible();
  for (const width of [320, 390, 768, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  }
  await page.route("**/api/v1/home/account", route => route.fulfill({ status: 403, json: {} }));
  await page.reload();
  await expect(page.getByText("Đăng nhập để xem những tin tức dành cho bạn.")).toBeVisible();
});

test("policy-close reconnects only after the server confirms the STAFF identity again", async ({ page }) => {
  await setup(page, "STAFF");
  let accountChecks = 0, connections = 0, socket;
  await page.route("**/api/v1/home/account", route => {
    accountChecks++;
    return route.fulfill({ json: { id: "1", name: "Staff fixture", role: "STAFF" } });
  });
  await page.routeWebSocket("**/api/v1/news/events", ws => {
    connections++; socket = ws; ws.onMessage(() => ws.send('{"type":"READY"}'));
  });
  await page.goto("/#news");
  await expect(page.getByText("Tin tức tự cập nhật khi có thay đổi.")).toBeVisible();
  socket.close({ code: 1008, reason: "Revalidate current identity" });
  await expect.poll(() => accountChecks).toBeGreaterThan(1);
  await expect.poll(() => connections).toBe(2);
  await expect(page.getByText("Tin tức tự cập nhật khi có thay đổi.")).toBeVisible();
});

test("policy-close stops reconnecting when the current session is revoked", async ({ page }) => {
  await page.clock.install();
  await setup(page, "STAFF");
  let revoked = false, connections = 0, socket;
  await page.route("**/api/v1/home/account", route => route.fulfill(revoked
    ? { status: 403, json: {} } : { json: { id: "1", name: "Staff fixture", role: "STAFF" } }));
  await page.routeWebSocket("**/api/v1/news/events", ws => {
    connections++; socket = ws; ws.onMessage(() => ws.send('{"type":"READY"}'));
  });
  await page.goto("/#news");
  await expect(page.getByText("Tin tức tự cập nhật khi có thay đổi.")).toBeVisible();
  revoked = true; socket.close({ code: 1008, reason: "Session revoked" });
  await expect(page.getByText("Đăng nhập để xem những tin tức dành cho bạn.")).toBeVisible();
  await page.clock.fastForward(35000);
  expect(connections).toBe(1);
});

test("repeated policy-close uses backoff across successful identity rechecks", async ({ page }) => {
  await page.clock.install({ time: new Date("2026-11-12T00:00:00Z") });
  await setup(page, "STAFF");
  let accountChecks = 0, identityRevision = 0;
  const sockets = [];
  await page.route("**/api/v1/home/account", route => {
    accountChecks++;
    return route.fulfill({ json: { id: "1", name: `Staff fixture ${identityRevision}`, role: "STAFF" } });
  });
  await page.routeWebSocket("**/api/v1/news/events", ws => { sockets.push(ws); ws.onMessage(() => {}); });
  await page.goto("/#news");
  await expect.poll(() => sockets.length).toBe(1);
  await page.clock.pauseAt(new Date("2026-11-12T00:01:00Z"));
  for (let attempt = 0; attempt < 3; attempt++) {
    identityRevision = attempt + 1;
    const previousChecks = accountChecks;
    sockets[attempt].close({ code: 1008, reason: "Policy close before READY" });
    await expect.poll(() => accountChecks).toBeGreaterThan(previousChecks);
    await expect(page.getByRole("button", { name: `Tài khoản Staff fixture ${identityRevision}`, exact: true })).toBeVisible();
    const delay = 1000 * 2 ** attempt;
    await page.clock.fastForward(delay - 100);
    expect(sockets.length).toBe(attempt + 1);
    await page.clock.fastForward(100);
    await expect.poll(() => sockets.length).toBe(attempt + 2);
  }
});

for (const [code, message] of [["NEWS_WRITE_BUSY", "Dữ liệu đang có xung đột."],
  ["NEWS_SLUG_TAKEN", "Đường dẫn bài viết đã được sử dụng."]])
test(`conflict feedback identifies ${code} and preserves the draft`, async ({ page }) => {
  await setup(page);
  await page.route(`**/api/v1/news/${fixture.id}`, route => route.request().method() === "PUT"
    ? route.fulfill({ status: 409, json: { status: 409, code } })
    : route.fulfill({ json: fixture, headers: { ETag: '"0"' } }));
  await page.goto(`/#news/${fixture.id}/edit`);
  await page.getByLabel("Tiêu đề", { exact: false }).fill("Nội dung chưa muốn mất");
  await page.getByRole("button", { name: "Lưu thay đổi", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText(message);
  await expect(page.getByLabel("Tiêu đề", { exact: false })).toHaveValue("Nội dung chưa muốn mất");
  if (code !== "NEWS_SLUG_TAKEN") await expect(page.getByRole("alert")).not.toContainText("Đường dẫn");
});

test("admin filters and pagination retain query boundaries", async ({ page }) => {
  await setup(page);
  const queries = [];
  await page.route("**/api/v1/news?*", route => {
    const query = new URL(route.request().url()).searchParams;
    queries.push(Object.fromEntries(query));
    return route.fulfill({ json: { content: [fixture], totalElements: 7, totalPages: 2, page: Number(query.get("page")), size: 6, last: query.get("page") === "1" } });
  });
  await page.goto("/#news");
  await page.getByLabel("Trạng thái", { exact: true }).selectOption("ARCHIVED");
  await expect.poll(() => queries.at(-1)?.status).toBe("ARCHIVED");
  await page.getByRole("button", { name: "Trang sau →" }).click();
  await expect.poll(() => queries.at(-1)?.page).toBe("1");
  await expect(page.getByText("Trang 2 / 2", { exact: true })).toBeVisible();
  await page.getByLabel("Sắp xếp", { exact: true }).selectOption("updatedAt,desc");
  await expect.poll(() => queries.at(-1)).toEqual({ page: "0", size: "6", sort: "updatedAt,desc", status: "ARCHIVED" });
});

test("Firebase unavailable is actionable and never silently saves a missing image", async ({ page }) => {
  await setup(page);
  await page.route("**/api/v1/news/images", route => route.fulfill({ status: 503, json: {} }));
  await page.goto("/#news/new");
  await page.locator('input[type="file"]').setInputFiles({ name: "test.png", mimeType: "image/png", buffer: Buffer.from("test-file") });
  await expect(page.getByRole("alert")).toContainText("Chưa tải được ảnh.");
  await expect(page.getByRole("button", { name: "Gỡ ảnh 1" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Lưu bản nháp", exact: true })).toBeEnabled();
});

test("restoring archived news publishes the chosen status with the loaded ETag", async ({ page }) => {
  await setup(page);
  let saved;
  await page.route(`**/api/v1/news/${fixture.id}`, route => {
    if (route.request().method() === "PUT") {
      saved = { body: route.request().postDataJSON(), etag: route.request().headers()["if-match"] };
      return route.fulfill({ json: { ...fixture, ...saved.body, version: 3 }, headers: { ETag: '"3"' } });
    }
    return route.fulfill({ json: saved ? { ...fixture, ...saved.body, version: 3 } : { ...fixture, status: "ARCHIVED", version: 2 }, headers: { ETag: saved ? '"3"' : '"2"' } });
  });
  await page.goto(`/#news/${fixture.id}/edit`);
  await expect(page.getByRole("heading", { name: "Chỉnh sửa tin tức", exact: true })).toBeVisible();
  await expect(page.locator("#news-publish-status")).toHaveValue("ARCHIVED");
  await page.locator("#news-publish-status").selectOption("PUBLISHED");
  await page.getByRole("button", { name: "Lưu thay đổi", exact: true }).click();
  await expect(page.locator(".news-article-header .news-badge.published")).toBeVisible();
  expect(saved.body.status).toBe("PUBLISHED");
  expect(saved.etag).toBe('"2"');
});
