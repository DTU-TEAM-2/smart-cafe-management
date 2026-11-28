import { readFile } from "node:fs/promises";
import { expect, test } from "@playwright/test";

// UI-only HTTP fixtures; the local end-to-end report separately exercises the real issuer and MySQL.
const fixture = { id: "9007199254740993", title: "Những ngày thơm hương cà phê", summary: "Câu chuyện từ quầy bar LECKER.", body: "Dòng thứ nhất.\nDòng thứ hai.",
  status: "PUBLISHED", slug: "ca-phe", authorName: "LECKER", authorId: "1", audiences: ["STAFF", "CUSTOMER"], images: [],
  publishedAt: "2026-11-11T01:00:00Z", createdAt: "2026-11-11T01:00:00Z", version: 0 };
const firstPage = [fixture, ...Array.from({ length: 5 }, (_, index) => ({
  ...fixture, id: String(index + 2), title: `Bài viết ${index + 2}`,
}))];

function writeResponse(body, version = 0) {
  const { imageMediaIds, slug, ...fields } = body;
  return {
    json: { ...fixture, ...fields, slug: slug ?? fixture.slug, version,
      images: imageMediaIds.map((mediaId, sortOrder) => ({ mediaId, sortOrder, secureUrl: `/api/v1/news/images/${mediaId}` })) },
    headers: { ETag: `"${version}"` },
  };
}

async function setup(page, role = "ADMIN", { error = false, empty = false } = {}) {
  await page.addInitScript(() => sessionStorage.setItem("smart_cafe_token", "isolated-ui-fixture-token"));
  await page.route("**/api/v1/auth/me", route => route.fulfill({ json: { id: 1, username: "ui-fixture", role } }));
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
    await route.fulfill({ status: 201, ...writeResponse(posted) });
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

test("editor validates and submits the same normalized Unicode text as the server", async ({ page }) => {
  await setup(page);
  let posted;
  await page.route("**/api/v1/news", route => {
    posted = route.request().postDataJSON();
    return route.fulfill({ status: 201, ...writeResponse(posted) });
  });
  await page.goto("/#news/new");
  const title = "ế".repeat(200);
  const summary = "à".repeat(500);
  await page.getByLabel("Tiêu đề").fill(` ${title}\u3000`);
  await page.getByLabel("Tóm tắt").fill(` ${summary}\u3000`);
  await page.getByLabel("Nội dung").fill("\u00a0Nội dung\u00a0");
  await page.getByRole("checkbox", { name: /Khách hàng/ }).check();
  await page.getByRole("button", { name: "Lưu bản nháp", exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`#news/${fixture.id}$`));
  expect(posted).toMatchObject({ title, summary, body: "\u00a0Nội dung\u00a0" });
});

test("lost create response and missing old slug never replay a POST", async ({ page }) => {
  await setup(page);
  const article = { slug: "duong-dan-cu", title: "Bài đang đối soát", summary: null, body: "Nội dung cũ",
    status: "PUBLISHED", audiences: ["STAFF"], imageMediaIds: ["42", "43"] };
  await page.addInitScript(body => {
    if (!sessionStorage.getItem("mock-original-attempt-loaded")) {
      sessionStorage.setItem("lecker_news_pending:1", JSON.stringify({ slug: body.slug, body }));
      sessionStorage.setItem("mock-original-attempt-loaded", "true");
    }
  }, article);
  let posts = 0;
  await page.route("**/api/v1/news", route => {
    posts++;
    return route.fulfill({ status: 201, json: fixture });
  });
  await page.route("**/api/v1/news/by-slug/duong-dan-cu", route => route.fulfill({ status: 404, json: {} }));
  await page.route("**/api/v1/news/images/*", route => route.fulfill({ status: 404 }));
  await page.goto("/#news/new");
  await page.reload();
  await expect(page.getByLabel("Tiêu đề")).toHaveValue(article.title);
  await expect(page.getByLabel("Tiêu đề")).toBeDisabled();
  await expect(page.getByLabel("Trạng thái", { exact: true })).toHaveValue("PUBLISHED");
  await expect(page.getByText("2/20", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Đăng tin", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Kiểm tra bài đã lưu" }).click();
  await expect(page.getByRole("alert")).toContainText("Chưa tìm thấy bài qua đường dẫn cũ");
  await expect(page.getByRole("button", { name: "Gửi lại lần lưu này" })).toHaveCount(0);
  const exportBackup = page.getByRole("button", { name: "Tải bản sao JSON" });
  await page.evaluate(() => {
    const click = HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click = function () {
      if (!this.download) click.call(this);
    };
  });
  await exportBackup.click();
  await expect(page.getByLabel("Tiêu đề")).toHaveValue(article.title);
  await page.reload();
  await expect(page.getByLabel("Nội dung")).toHaveValue(article.body);

  const download = page.waitForEvent("download");
  await exportBackup.click();
  const backup = await download;
  expect(backup.suggestedFilename()).toBe("tin-tuc-can-doi-soat.json");
  expect(JSON.parse(await readFile(await backup.path(), "utf8"))).toEqual({ slug: article.slug, body: article });
  await page.reload();
  await expect(page.getByLabel("Tiêu đề")).toHaveValue(article.title);
  const startAnother = page.getByRole("button", { name: "Bỏ đối soát và soạn bài khác" });
  page.once("dialog", dialog => dialog.dismiss());
  await startAnother.click();
  await expect(page.getByLabel("Tiêu đề")).toHaveValue(article.title);
  page.once("dialog", dialog => dialog.accept());
  await startAnother.click();
  await expect(page.getByLabel("Tiêu đề")).toHaveValue("");
  await expect(page.getByLabel("Tiêu đề")).toBeEditable();
  expect(posts).toBe(0);
});

test("a different article found at the same slug requires confirmation to leave recovery", async ({ page }) => {
  await setup(page);
  await page.addInitScript(() => sessionStorage.setItem("lecker_news_pending:1",
    JSON.stringify({ slug: "ca-phe", custom: false })));
  await page.route("**/api/v1/news/by-slug/ca-phe", route => route.fulfill({ json: fixture, headers: { ETag: '"0"' } }));
  await page.goto("/#news/new");
  await expect(page.getByLabel("Tiêu đề")).toBeDisabled();
  await expect(page.getByRole("button", { name: "Gửi lại lần lưu này" })).toHaveCount(0);
  await page.getByRole("button", { name: "Kiểm tra bài đã lưu" }).click();
  const open = page.getByRole("button", { name: "Mở bài tìm thấy" });
  page.once("dialog", dialog => dialog.dismiss());
  await open.click();
  await expect(page).toHaveURL(/#news\/new$/);
  expect(await page.evaluate(() => sessionStorage.getItem("lecker_news_pending:1"))).toContain("ca-phe");
  page.once("dialog", dialog => dialog.accept());
  await open.click();
  await expect(page).toHaveURL(new RegExp(`#news/${fixture.id}$`));
  expect(await page.evaluate(() => sessionStorage.getItem("lecker_news_pending:1"))).toBeNull();
});

test("incompatible recovery data stays exportable without breaking the editor", async ({ page }) => {
  await setup(page);
  const saved = { slug: fixture.slug, body: { ...fixture, summary: 123, imageMediaIds: [] } };
  await page.addInitScript(record => sessionStorage.setItem("lecker_news_pending:1", JSON.stringify(record)), saved);
  await page.route(`**/api/v1/news/by-slug/${fixture.slug}`, route => route.fulfill({ json: fixture, headers: { ETag: '"0"' } }));
  await page.goto("/#news/new");
  await expect(page.getByLabel("Tiêu đề")).toBeDisabled();
  const download = page.waitForEvent("download");
  await page.getByRole("button", { name: "Tải bản sao JSON" }).click();
  const backup = await download;
  expect(JSON.parse(await readFile(await backup.path(), "utf8"))).toEqual(saved);
  await page.getByRole("button", { name: "Kiểm tra bài đã lưu" }).click();
  await expect(page.getByRole("button", { name: "Mở bài tìm thấy" })).toBeVisible();
  await expect(page).toHaveURL(/#news\/new$/);
});

test("a storage failure preserves the draft before sending a creation", async ({ page }) => {
  await setup(page);
  await page.addInitScript(() => {
    const original = Storage.prototype.setItem;
    Storage.prototype.setItem = function (key, value) {
      if (key.startsWith("lecker_news_pending:")) throw new DOMException("Storage is full", "QuotaExceededError");
      return original.call(this, key, value);
    };
  });
  let posts = 0;
  await page.route("**/api/v1/news", route => {
    posts++;
    return route.fulfill({ status: 201, json: fixture });
  });
  await page.goto("/#news/new");
  await page.getByLabel("Tiêu đề").fill("Bản chưa gửi");
  await page.getByLabel("Nội dung").fill("Nội dung đang soạn");
  await page.getByRole("checkbox", { name: /Nhân viên/ }).check();
  await page.getByRole("button", { name: "Lưu bản nháp", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Trình duyệt không lưu được thông tin đối soát");
  await expect(page.getByLabel("Tiêu đề")).toHaveValue("Bản chưa gửi");
  await expect(page.getByLabel("Nội dung")).toBeEditable();
  expect(posts).toBe(0);
});

for (const role of ["ADMIN", "CUSTOMER"]) test(`News checks the current ${role} session after a REST denial`, async ({ page }) => {
  await setup(page, role);
  let revoked = false;
  let checks = 0;
  await page.route("**/api/v1/auth/me", route => {
    checks++;
    return route.fulfill({ status: revoked ? 401 : 200, json: { id: 1, username: "ui-fixture", role } });
  });
  await page.route("**/api/v1/home/account", route => route.request().headers().authorization
    ? route.fulfill({ json: { id: "1", name: "Tài khoản kiểm thử", role } })
    : route.fulfill({ status: 401, json: {} }));
  await page.route("**/api/v1/news?*", route => route.fulfill({ status: 403, json: {} }));
  await page.goto("/#news");
  await expect(page.getByRole("alert")).toContainText("Bạn không có quyền");
  await expect.poll(() => checks).toBeGreaterThanOrEqual(2);
  expect(await page.evaluate(() => sessionStorage.getItem("smart_cafe_token"))).toBe("isolated-ui-fixture-token");
  revoked = true;
  await page.getByRole("button", { name: "Thử lại" }).click();
  await expect(page.getByText("Đăng nhập để xem những tin tức dành cho bạn.")).toBeVisible();
  expect(await page.evaluate(() => sessionStorage.getItem("smart_cafe_token"))).toBeNull();
});

test("an auth check for the old News token cannot clear its replacement", async ({ page }) => {
  await setup(page);
  const initialAuth = page.waitForResponse(r => r.url().endsWith("/api/v1/auth/me") && r.ok());
  await page.goto("/#news");
  await initialAuth;
  await expect(page.getByRole("link", { name: fixture.title, exact: true })).toBeVisible();
  let delayed;
  await page.route("**/api/v1/auth/me", route => {
    if (route.request().headers().authorization === "Bearer isolated-ui-fixture-token") {
      delayed = route;
      return;
    }
    return route.fulfill({ json: { id: 1, username: "ui-fixture", role: "ADMIN" } });
  });
  await page.route("**/api/v1/news?*", route => route.fulfill(route.request().headers().authorization === "Bearer replacement"
    ? { json: { content: [fixture], totalElements: 1, totalPages: 1, last: true, page: 0, size: 6 } }
    : { status: 403, json: {} }));
  await page.getByRole("button", { name: "Làm mới" }).click();
  await expect.poll(() => Boolean(delayed)).toBe(true);
  await page.evaluate(() => {
    sessionStorage.setItem("smart_cafe_token", "replacement");
    window.dispatchEvent(new CustomEvent("smart-cafe-auth-change"));
  });
  await expect(page.getByRole("link", { name: fixture.title, exact: true })).toBeVisible();
  const response = page.waitForResponse(r => r.url().endsWith("/api/v1/auth/me") && r.status() === 401);
  await delayed.fulfill({ status: 401, json: {} });
  await (await response).finished();
  await expect(page.getByRole("link", { name: fixture.title, exact: true })).toBeVisible();
  expect(await page.evaluate(() => sessionStorage.getItem("smart_cafe_token"))).toBe("replacement");
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

test("a failed conflict reload keeps the draft visible and a successful retry replaces it", async ({ page }) => {
  await setup(page);
  let phase = "original";
  await page.route(`**/api/v1/news/${fixture.id}`, route => {
    if (route.request().method() === "PUT") return route.fulfill({ status: 412, json: {} });
    if (phase === "unavailable") return route.fulfill({ status: 503, json: {} });
    const latest = phase === "latest";
    return route.fulfill({ json: { ...fixture, title: latest ? "Bài đã cập nhật" : fixture.title, version: latest ? 1 : 0 },
      headers: { ETag: latest ? '"1"' : '"0"' } });
  });
  await page.goto(`/#news/${fixture.id}/edit`);
  const title = page.getByLabel("Tiêu đề");
  await title.fill("Bản nháp cần giữ");
  await page.getByRole("button", { name: "Lưu thay đổi" }).click();
  await expect(page.getByRole("alert")).toContainText("Bài viết đã được thay đổi ở nơi khác");
  phase = "unavailable";
  page.once("dialog", dialog => dialog.accept());
  await page.getByRole("button", { name: "Tải bản mới" }).click();
  await expect(page.getByRole("alert")).toContainText("Dịch vụ chưa sẵn sàng");
  await expect(title).toHaveValue("Bản nháp cần giữ");
  await expect(page.getByRole("button", { name: "Lưu thay đổi" })).toBeDisabled();
  phase = "latest";
  page.once("dialog", dialog => dialog.accept());
  await page.getByRole("button", { name: "Tải bản mới" }).click();
  await expect(title).toHaveValue("Bài đã cập nhật");
  await expect(page.getByRole("button", { name: "Lưu thay đổi" })).toBeEnabled();
});

test("a lost PUT response allows checking the newer version while preserving the edit", async ({ page }) => {
  await setup(page);
  let writes = 0;
  await page.route(`**/api/v1/news/${fixture.id}`, route => {
    if (route.request().method() === "PUT") {
      writes++;
      return route.abort("connectionreset");
    }
    return route.fulfill({ json: { ...fixture, version: writes }, headers: { ETag: `"${writes}"` } });
  });
  await page.goto(`/#news/${fixture.id}/edit`);
  await page.getByLabel("Tiêu đề").fill("Bản cập nhật đang soạn");
  await page.getByRole("button", { name: "Lưu thay đổi" }).click();
  await expect(page.getByRole("alert")).toContainText("Chưa xác nhận được kết quả lưu bài");
  await page.getByRole("button", { name: "Kiểm tra phiên bản máy chủ" }).click();
  await expect(page.getByRole("alert")).toContainText("Phiên bản trên máy chủ đã thay đổi");
  await expect(page.getByLabel("Tiêu đề")).toHaveValue("Bản cập nhật đang soạn");
  await expect(page.getByRole("button", { name: "Lưu thay đổi" })).toBeDisabled();
  expect(writes).toBe(1);
});

test("a lost PUT can be verified by version and content while keeping later edits", async ({ page }) => {
  await setup(page);
  let committed, writes = 0;
  await page.route(`**/api/v1/news/${fixture.id}`, route => {
    if (route.request().method() === "PUT") {
      writes++;
      committed = writeResponse(route.request().postDataJSON(), 1);
      return route.abort("connectionreset");
    }
    return route.fulfill(committed ?? { json: fixture, headers: { ETag: '"0"' } });
  });
  await page.goto(`/#news/${fixture.id}/edit`);
  const title = page.getByLabel("Tiêu đề");
  await title.fill("Bản đã lưu trên máy chủ");
  await page.getByRole("button", { name: "Lưu thay đổi" }).click();
  await expect(page.getByRole("alert")).toContainText("Chưa xác nhận được kết quả lưu bài");
  await title.fill("Bản đang soạn thêm");
  await page.getByRole("button", { name: "Kiểm tra phiên bản máy chủ" }).click();
  await expect(page.getByText("Đã đối chiếu và xác nhận lần lưu trước. Các chỉnh sửa sau đó đang chờ lưu.")).toBeVisible();
  await expect(title).toHaveValue("Bản đang soạn thêm");
  await expect(page.getByRole("button", { name: "Lưu thay đổi" })).toBeEnabled();
  expect(writes).toBe(1);
});

test("a 201 with mismatched content retains recovery until the saved article matches", async ({ page }) => {
  await setup(page);
  let committed, writes = 0;
  await page.route("**/api/v1/news", route => {
    writes++;
    committed = writeResponse(route.request().postDataJSON());
    return route.fulfill({ ...committed, status: 201, json: { ...committed.json, title: "Bài khác" } });
  });
  await page.route("**/api/v1/news/by-slug/*", route => route.fulfill(committed));
  await page.goto("/#news/new");
  await page.getByLabel("Tiêu đề").fill("Bài cần đối soát");
  await page.getByLabel("Nội dung").fill("Nội dung bài cần đối soát");
  await page.getByRole("checkbox", { name: /Nhân viên/ }).check();
  await page.getByRole("button", { name: "Lưu bản nháp", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Chưa xác nhận được kết quả lưu bài");
  await expect(page).toHaveURL(/#news\/new$/);
  await expect(page.getByLabel("Tiêu đề")).toHaveValue("Bài cần đối soát");
  await page.getByRole("button", { name: "Kiểm tra bài đã lưu" }).click();
  await expect(page).toHaveURL(new RegExp(`#news/${fixture.id}$`));
  expect(writes).toBe(1);
  expect(await page.evaluate(() => sessionStorage.getItem("lecker_news_pending:1"))).toBeNull();
});

for (const response of ["connection reset", "unexpected success status"]) test(`DELETE with ${response} requires reading the article before another archive`, async ({ page }) => {
  await setup(page);
  let writes = 0;
  await page.route(`**/api/v1/news/${fixture.id}`, route => {
    if (route.request().method() === "DELETE") {
      writes++;
      return response === "connection reset" ? route.abort("connectionreset") : route.fulfill({ status: 202, json: {} });
    }
    return route.fulfill({ json: { ...fixture, status: writes ? "ARCHIVED" : "PUBLISHED", version: writes },
      headers: { ETag: `"${writes}"` } });
  });
  await page.goto(`/#news/${fixture.id}`);
  await page.getByRole("button", { name: "Lưu trữ tin", exact: true }).click();
  await page.getByRole("button", { name: "Xác nhận lưu trữ" }).click();
  await expect(page.getByRole("alert")).toContainText("Chưa xác nhận được kết quả lưu trữ");
  await expect(page.getByRole("button", { name: "Lưu trữ tin", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Tải bản mới" }).click();
  await expect(page.getByText("Đã lưu trữ", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Lưu trữ tin", exact: true })).toHaveCount(0);
  expect(writes).toBe(1);
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

test("stale archive requires loading the latest version before retrying", async ({ page }) => {
  await setup(page);
  const versions = [];
  await page.route(`**/api/v1/news/${fixture.id}`, route => {
    if (route.request().method() === "DELETE") {
      versions.push(route.request().headers()["if-match"]);
      return route.fulfill({ status: versions.length === 1 ? 412 : 204 });
    }
    return route.fulfill({ json: { ...fixture, version: versions.length }, headers: { ETag: `"${versions.length}"` } });
  });
  await page.goto(`/#news/${fixture.id}`);
  const archive = page.getByRole("button", { name: "Lưu trữ tin", exact: true });
  const confirm = page.getByRole("button", { name: "Xác nhận lưu trữ" });
  await archive.click();
  await confirm.click();
  await expect(page.getByRole("alert")).toContainText("Phiên bản bài viết đã thay đổi");
  await expect(archive).toBeDisabled();
  await page.getByRole("button", { name: "Tải bản mới" }).click();
  await archive.click();
  await confirm.click();
  await expect(page).toHaveURL(/#news$/);
  expect(versions).toEqual(['"0"', '"1"']);
});

test("admin read without an ETag offers a retry before opening the editor", async ({ page }) => {
  await setup(page);
  let tag;
  await page.route(`**/api/v1/news/${fixture.id}`, route => route.fulfill({
    json: fixture, headers: tag ? { ETag: tag } : {},
  }));
  await page.goto(`/#news/${fixture.id}/edit`);
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("button", { name: "Lưu thay đổi" })).toHaveCount(0);
  tag = '"0"';
  await page.getByRole("button", { name: "Thử lại" }).click();
  await expect(page.getByLabel("Tiêu đề")).toHaveValue(fixture.title);
});

test("private images use bearer, uploading preserves images and invalid files are rejected", async ({ page }) => {
  await setup(page);
  let auth, posted;
  const pixel = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=", "base64");
  await page.route("**/api/v1/news/images", route => route.fulfill({ status: 201, json: { mediaId: "42", url: "/api/v1/news/images/42" } }));
  await page.route("**/api/v1/news/images/42", route => { auth = route.request().headers().authorization; return route.fulfill({ contentType: "image/png", body: pixel }); });
  await page.route("**/api/v1/news", route => { posted = route.request().postDataJSON(); return route.fulfill({ status: 201, ...writeResponse(posted) }); });
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
  await page.route("**/api/v1/news?*", route => route.fulfill({ json: { content: [{ ...fixture, title }], totalElements: 1, totalPages: 1, last: true, page: 0, size: 6 } }));
  await page.goto("/#news");
  await expect(page.getByText("Tin tức tự cập nhật khi có thay đổi.")).toBeVisible();
  title = "Tin vừa được cập nhật"; socket.send('{"type":"NEWS_CHANGED"}');
  await expect(page.getByRole("heading", { name: title })).toBeVisible();
  expect(socket.url()).not.toContain("token");
  socket.close({ code: 1011, reason: "Test disconnect" });
  await expect(page.getByText(/Đang kết nối cập nhật tin tức/)).toBeVisible();
  await expect(page.getByText("Tin tức tự cập nhật khi có thay đổi.")).toBeVisible();
});

test("staff reconnects when the socket opens but never confirms it is ready", async ({ page }) => {
  await page.clock.install();
  await setup(page, "STAFF");
  let connections = 0;
  let bearerFrames = 0;
  await page.routeWebSocket("**/api/v1/news/events", socket => {
    const attempt = ++connections;
    socket.onMessage(() => {
      bearerFrames++;
      if (attempt > 1) socket.send('{"type":"READY"}');
    });
  });
  await page.goto("/#news");
  await expect.poll(() => bearerFrames).toBe(1);
  await page.clock.runFor(17000);
  await expect.poll(() => connections).toBe(2);
  await expect(page.getByText("Tin tức tự cập nhật khi có thay đổi.")).toBeVisible();
  await page.clock.runFor(17000);
  expect(connections).toBe(2);
});

test("staff retries when socket construction and its first send fail", async ({ page }) => {
  await setup(page, "STAFF");
  await page.clock.install();
  await page.addInitScript(() => {
    const NativeSocket = window.WebSocket;
    window.newsSocketAttempts = 0;
    window.WebSocket = class extends NativeSocket {
      constructor(url, protocols) {
        if (!url.endsWith("/api/v1/news/events")) { super(url, protocols); return; }
        const attempt = ++window.newsSocketAttempts;
        if (attempt === 1) throw new DOMException("Could not open socket", "InvalidStateError");
        super(url, protocols);
        if (attempt === 2) this.send = () => {
          window.newsSocketSendFailed = true;
          throw new DOMException("Could not send", "InvalidStateError");
        };
      }
    };
  });
  const uncaught = [];
  page.on("pageerror", error => uncaught.push(error.message));
  await page.goto("/#news");
  const attempts = () => page.evaluate(() => window.newsSocketAttempts);
  await expect.poll(attempts).toBe(1);
  await page.clock.fastForward(1100);
  await expect.poll(attempts).toBe(2);
  await expect.poll(() => page.evaluate(() => window.newsSocketSendFailed)).toBe(true);
  await expect(page.getByText("Đang kết nối cập nhật tin tức.", { exact: false })).toBeVisible();
  await page.clock.fastForward(2100);
  await expect(page.getByText("Tin tức tự cập nhật khi có thay đổi.")).toBeVisible();
  expect(await attempts()).toBe(3);
  expect(uncaught).toEqual([]);
});

test("guest gate, empty feed, retry and mobile layout remain usable", async ({ page }) => {
  await setup(page, "CUSTOMER", { empty: true });
  await page.goto("/#news");
  await expect(page.getByRole("heading", { name: "Chưa có câu chuyện nào" })).toBeVisible();
  await expect.poll(() => page.locator(".news-banner img").evaluate(img => img.naturalWidth)).toBeGreaterThan(0);
  await expect(page.getByRole("link", { name: "Xem thực đơn" })).toHaveAttribute("href", "#customer");
  await page.route("**/api/v1/news?*", route => route.fulfill({ status: 503, json: {} }));
  await page.getByRole("button", { name: "Làm mới" }).click();
  await expect(page.getByRole("alert")).toContainText("Chưa tải được tin tức");
  await page.route("**/api/v1/news?*", route => route.fulfill({ json: { content: [fixture], totalElements: 1, totalPages: 1, last: true, page: 0, size: 6 } }));
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

for (const [path, broken, message] of [
  ["", { content: [{ ...fixture, audiences: null }], totalElements: 1, totalPages: 1, last: true }, "Chưa tải được tin tức"],
  [`/${fixture.id}`, { ...fixture, images: null }, "Chưa mở được bài viết"],
  [`/${fixture.id}/edit`, { ...fixture, summary: 123 }, "Chưa kết nối được máy chủ"],
]) test(`malformed news data at ${path || "feed"} can be retried`, async ({ page }) => {
  await setup(page);
  const apiPath = path.replace(/\/edit$/, "");
  let payload = broken;
  await page.route(apiPath ? `**/api/v1/news${apiPath}` : "**/api/v1/news?*", route => route.fulfill({
    json: payload,
    headers: { ETag: '"0"' },
  }));
  await page.goto(`/#news${path}`);
  await expect(page.getByRole("alert")).toContainText(message);
  payload = apiPath ? fixture : { content: [fixture], totalElements: 1, totalPages: 1, last: true, page: 0, size: 6 };
  await page.getByRole("button", { name: "Thử lại" }).click();
  if (path.endsWith("/edit")) await expect(page.getByLabel("Tiêu đề")).toHaveValue(fixture.title);
  else await expect(page.getByRole("heading", { name: fixture.title })).toBeVisible();
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
  await page.clock.pauseAt(new Date("2026-11-12T00:01:00Z"));
  await setup(page, "STAFF");
  let accountChecks = 0, identityRevision = 0;
  const sockets = [];
  await page.route("**/api/v1/home/account", route => {
    accountChecks++;
    return route.fulfill({ json: { id: "1", name: `Staff fixture ${identityRevision}`, role: "STAFF" } });
  });
  await page.routeWebSocket("**/api/v1/news/events", ws => { sockets.push(ws); ws.onMessage(() => {}); });
  await page.goto("/#news");
  await expect(page.getByRole("button", { name: "Tài khoản Staff fixture 0", exact: true })).toBeVisible();
  await page.clock.runFor(10);
  await expect.poll(() => sockets.length).toBe(1);
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
    const index = Number(query.get("page"));
    return route.fulfill({ json: { content: index ? [fixture] : firstPage, totalElements: 7, totalPages: 2, page: index, size: 6, last: index === 1 } });
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

test("an empty in-range page offers retry and an out-of-range page returns to the last available page", async ({ page }) => {
  await setup(page);
  let contracted = false;
  const pages = [];
  await page.route("**/api/v1/news?*", route => {
    const index = Number(new URL(route.request().url()).searchParams.get("page"));
    pages.push(index);
    return route.fulfill({ json: {
      content: index ? [] : contracted ? [fixture] : firstPage, totalElements: contracted ? 1 : 7,
      totalPages: contracted ? 1 : 2, last: contracted || index === 1, page: index, size: 6,
    } });
  });
  await page.goto("/#news");
  await page.getByRole("button", { name: "Trang sau →" }).click();
  await expect(page.getByRole("alert")).toContainText("Chưa tải được tin tức");
  contracted = true;
  await page.getByRole("button", { name: "Thử lại" }).click();
  await expect(page.getByRole("heading", { name: fixture.title })).toBeVisible();
  expect(pages.slice(-3)).toEqual([1, 1, 0]);
});

test("list rejects a response for a different page and retries the selected page", async ({ page }) => {
  await setup(page);
  let wrongPage = true;
  await page.route("**/api/v1/news?*", route => {
    const index = Number(new URL(route.request().url()).searchParams.get("page"));
    return route.fulfill({ json: {
      content: index ? [{ ...fixture, title: "Bài ở trang sau" }] : firstPage,
      totalElements: 7, totalPages: 2, last: !!index, size: 6, page: wrongPage ? 0 : index,
    } });
  });
  await page.goto("/#news");
  await page.getByRole("button", { name: "Trang sau →" }).click();
  await expect(page.getByRole("alert")).toContainText("Chưa tải được tin tức");
  await expect(page.getByRole("heading", { name: fixture.title })).toHaveCount(0);
  wrongPage = false;
  await page.getByRole("button", { name: "Thử lại" }).click();
  await expect(page.getByRole("heading", { name: "Bài ở trang sau" })).toBeVisible();
  await expect(page.getByText("Trang 2 / 2")).toBeVisible();
});

test("partial pages cannot replace the list and returning to page one keeps filters", async ({ page }) => {
  await setup(page);
  const queries = [];
  await page.route("**/api/v1/news?*", route => {
    const query = new URL(route.request().url()).searchParams;
    queries.push(Object.fromEntries(query));
    const index = Number(query.get("page"));
    return route.fulfill({ json: { content: index ? [fixture] : firstPage, totalElements: 8, totalPages: 2,
      page: index, size: 6, last: index === 1 } });
  });
  await page.goto("/#news");
  await page.getByLabel("Trạng thái", { exact: true }).selectOption("PUBLISHED");
  await page.getByRole("button", { name: "Trang sau →" }).click();
  await expect(page.getByRole("alert")).toContainText("Chưa tải được tin tức");
  await page.getByRole("button", { name: "Về trang đầu" }).click();
  await expect(page.getByText("Trang 1 / 2")).toBeVisible();
  expect(queries.at(-1)).toMatchObject({ page: "0", status: "PUBLISHED" });
});

for (const failure of ["connection reset", "HTTP 503"]) test(`upload ${failure} locks further uploads in the editor`, async ({ page }) => {
  await setup(page);
  let uploads = 0;
  await page.route("**/api/v1/news/images", route => {
    uploads++;
    return failure === "connection reset" ? route.abort("connectionreset") : route.fulfill({ status: 503, json: {} });
  });
  await page.goto("/#news/new");
  await page.locator('input[type="file"]').setInputFiles({ name: "test.png", mimeType: "image/png", buffer: Buffer.from("test-file") });
  await expect(page.getByRole("alert")).toContainText("Chưa rõ kết quả tải ảnh.");
  await expect(page.getByRole("button", { name: "Gỡ ảnh 1" })).toHaveCount(0);
  await expect(page.locator('input[type="file"]')).toBeDisabled();
  await expect(page.getByRole("button", { name: "Lưu bản nháp", exact: true })).toBeEnabled();
  expect(uploads).toBe(1);
});

const invalidUploads = {
  "incomplete data": {},
  "out-of-range ID": { mediaId: "9223372036854775808", url: "/api/v1/news/images/9223372036854775808" },
  "duplicate ID": { mediaId: "42", url: "/api/v1/news/images/42" },
};
for (const [reason, invalidResponse] of Object.entries(invalidUploads)) test(`upload with ${reason} keeps verified images`, async ({ page }) => {
  await setup(page);
  let uploaded = 0;
  let submitted;
  await page.route("**/api/v1/news/images", route => route.fulfill({
    status: 201, json: ++uploaded === 1 ? { mediaId: "42", url: "/api/v1/news/images/42" } : invalidResponse,
  }));
  await page.route("**/api/v1/news/images/42", route => route.fulfill({ status: 404 }));
  await page.route("**/api/v1/news", route => {
    submitted = route.request().postDataJSON();
    return route.fulfill({ status: 201, ...writeResponse(submitted) });
  });
  await page.goto("/#news/new");
  const input = page.locator('input[type="file"]');
  await input.setInputFiles(["confirmed", "incomplete"].map(name => ({
    name: `${name}.png`, mimeType: "image/png", buffer: Buffer.from(name),
  })));
  await expect(page.getByRole("alert")).toContainText("Chưa rõ kết quả tải ảnh.");
  await expect(input).toBeDisabled();
  await expect(page.getByText("1/20", { exact: true })).toBeVisible();
  await page.getByLabel("Tiêu đề").fill("Bài có ảnh");
  await page.getByLabel("Nội dung").fill("Nội dung");
  await page.getByRole("checkbox", { name: /Nhân viên/ }).check();
  await page.getByRole("button", { name: "Lưu bản nháp", exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`#news/${fixture.id}$`));
  expect(submitted.imageMediaIds).toEqual(["42"]);
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
