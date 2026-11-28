import { expect, test } from "@playwright/test";

// HTTP-mocked lifecycle tests. The test harness is not the approved product screen.
const expiry = { mustChangePassword: true, passwordChangedAt: "2026-10-01T00:00:00Z", passwordExpiresAt: "2026-10-31T00:00:00Z" };
const user = { id: 2, username: "synthetic-staff", fullName: "Nhân viên tổng hợp", role: "STAFF" };
const profile = { id: String(user.id), username: user.username, name: user.fullName, email: "synthetic@example.test",
  address: null, phone: null, birthday: "1998-01-02", gender: "FEMALE", salary: "999999999999999999",
  avatarUrl: null, role: "STAFF", canUpdateProfile: true, version: "9007199254740993", ...expiry };
const replacement = { token: "replacement-test-only", ...expiry, mustChangePassword: false,
  passwordChangedAt: "2026-11-18T10:00:00Z", passwordExpiresAt: "2026-12-18T10:00:00Z" };

async function setup(page, { role = "STAFF", flagged = true } = {}) {
  await page.route("**/api/v1/auth/login", route => route.fulfill({ json: { token: "initial-test-only", user: { ...user, role }, ...expiry, mustChangePassword: flagged } }));
  await page.route("**/api/v1/auth/me", route => route.fulfill({ json: { ...user, role } }));
  await page.route("**/api/v1/auth/logout", route => route.fulfill({ json: {} }));
  await page.route("**/api/v1/profiles/me", route => route.fulfill({ json: { ...profile, role, mustChangePassword: flagged } }));
}

async function signIn(page) {
  await page.getByLabel("Tên đăng nhập", { exact: true }).fill(user.username);
  await page.getByLabel("Mật khẩu", { exact: true }).fill("OldPassword1");
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
}
async function open(page, options) {
  await setup(page, options);
  await page.goto("/tests/account-harness.html");
  await signIn(page);
  await expect(page.getByTestId("status")).toHaveText(options?.role === "CUSTOMER" ? "forbidden" : "ready");
}
async function passwordForm(page, current = "OldPassword1", next = "NewPassword2", confirmation = next) {
  await page.getByRole("button", { name: "Change password", exact: true }).click();
  await page.getByLabel("currentPassword", { exact: true }).fill(current);
  await page.getByLabel("newPassword", { exact: true }).fill(next);
  await page.getByLabel("confirmNewPassword", { exact: true }).fill(confirmation);
}

for (const role of ["ADMIN", "STAFF"]) test(`${role} reads authoritative decimal strings and date-only fields`, async ({ page }) => {
  await open(page, { role });
  await expect(page.getByTestId("salary")).toHaveText("999999999999999999");
  await expect(page.getByTestId("version")).toHaveText("9007199254740993");
  await expect(page.getByTestId("birthday")).toHaveText("1998-01-02");
  await expect(page.getByTestId("address")).toHaveText("Chưa cập nhật");
  await expect(page.getByTestId("expiry")).toHaveText("true");
  await page.reload();
  await expect(page.getByTestId("status")).toHaveText("ready");
  await expect(page.getByTestId("expiry")).toHaveText("true");
});

test("CUSTOMER and anonymous cannot fetch personnel through the lifecycle", async ({ page }) => {
  let requests = 0;
  page.on("request", request => { if (request.url().endsWith("/profiles/me")) requests++; });
  await setup(page, { role: "CUSTOMER" });
  await page.goto("/tests/account-harness.html");
  await expect(page.getByTestId("salary")).toHaveCount(0);
  await signIn(page);
  await expect(page.getByTestId("status")).toHaveText("forbidden");
  expect(requests).toBe(0);
  await expect(page.getByTestId("salary")).toHaveCount(0);
});

test("a malformed user cache does not discard a valid stored bearer on reload", async ({ page }) => {
  await setup(page);
  await page.addInitScript(() => {
    sessionStorage.setItem("smart_cafe_token", "initial-test-only");
    sessionStorage.setItem("smart_cafe_user", "{broken-cache");
  });
  await page.goto("/tests/account-harness.html");
  await expect(page.getByTestId("status")).toHaveText("ready");
  await expect(page.getByTestId("name")).toHaveText(profile.name);
  expect(await page.evaluate(() => sessionStorage.getItem("smart_cafe_token") === "initial-test-only")).toBe(true);
});

test("partial PATCH retains captured version, quotes and spaces; duplicate submit is blocked", async ({ page }) => {
  await open(page);
  let release;
  const pending = new Promise(resolve => { release = resolve; });
  const bodies = [];
  await page.route("**/api/v1/profiles/me", async route => {
    const body = route.request().postDataJSON();
    bodies.push(body);
    await pending;
    await route.fulfill({ json: { ...profile, name: body.name, version: "9007199254740994" } });
  });
  await page.getByRole("button", { name: "Edit profile", exact: true }).click();
  await expect(page.locator("form input")).toHaveCount(3);
  await page.getByLabel("name", { exact: true }).fill("  Nguyễn O'Ánh  ");
  await page.locator("form").evaluate(form => { form.requestSubmit(); form.requestSubmit(); });
  await expect.poll(() => bodies.length).toBe(1);
  expect(bodies[0]).toEqual({ version: profile.version, name: "  Nguyễn O'Ánh  " });
  release();
  await expect(page.getByTestId("name")).toHaveText("Nguyễn O'Ánh");
  await expect(page.getByTestId("version")).toHaveText("9007199254740994");
  await expect(page.getByTestId("identity")).toHaveText("Nguyễn O'Ánh");
  await expect(page.getByRole("button", { name: "Save profile" })).toHaveCount(0);
});

test("Cancel and unchanged drafts issue no PATCH; invalid drafts remain editable", async ({ page }) => {
  await open(page);
  let patches = 0;
  page.on("request", request => { if (request.method() === "PATCH") patches++; });
  await page.getByRole("button", { name: "Edit profile", exact: true }).click();
  await page.getByLabel("name", { exact: true }).fill("Bỏ thay đổi");
  await page.getByRole("button", { name: "Cancel edit", exact: true }).click();
  await expect(page.getByTestId("name")).toHaveText(profile.name);
  await page.getByRole("button", { name: "Edit profile", exact: true }).click();
  await page.getByRole("button", { name: "Save profile", exact: true }).click();
  await expect(page.getByRole("button", { name: "Save profile" })).toHaveCount(0);
  await page.getByRole("button", { name: "Edit profile", exact: true }).click();
  await page.getByLabel("name", { exact: true }).fill(" \t ");
  await page.getByRole("button", { name: "Save profile", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("không được để trống");
  expect(patches).toBe(0);
});

test("409 conflict discards the old draft and reloads; no automatic replay", async ({ page }) => {
  await open(page);
  let patches = 0;
  await page.route("**/api/v1/profiles/me", route => {
    if (route.request().method() === "PATCH") {
      patches++;
      return route.fulfill({ status: 409, json: { code: "PROFILE_CONFLICT" } });
    }
    return route.fulfill({ json: { ...profile, name: "Dữ liệu mới", version: "9007199254740994" } });
  });
  await page.getByRole("button", { name: "Edit profile", exact: true }).click();
  await page.getByLabel("name", { exact: true }).fill("Draft cũ");
  await page.getByRole("button", { name: "Save profile", exact: true }).click();
  await expect(page.getByTestId("name")).toHaveText("Dữ liệu mới");
  await expect(page.getByRole("alert")).toContainText("kiểm tra");
  await expect(page.getByRole("button", { name: "Save profile" })).toHaveCount(0);
  expect(patches).toBe(1);
});

for (const [status, code, message] of [[409, "PROFILE_INCOMPLETE", "quản trị nhân sự"], [400, "INVALID_PROFILE_REQUEST", "không hợp lệ"], [503, "PROFILE_UNAVAILABLE", "tạm thời"]]) {
  test(`PATCH ${status}/${code} retains draft without a success message`, async ({ page }) => {
    await open(page);
    await page.route("**/api/v1/profiles/me", route => route.fulfill({ status, json: { code, detail: "SQL raw secret must not display" } }));
    await page.getByRole("button", { name: "Edit profile", exact: true }).click();
    await page.getByLabel("name", { exact: true }).fill("Tên draft");
    await page.getByRole("button", { name: "Save profile", exact: true }).click();
    await expect(page.getByRole("alert")).toContainText(message);
    await expect(page.getByLabel("name", { exact: true })).toHaveValue("Tên draft");
    await expect(page.getByText(/SQL raw secret|Đã lưu/)).toHaveCount(0);
  });
}

test("GET network failure is recoverable with explicit retry", async ({ page }) => {
  await open(page);
  await page.route("**/api/v1/profiles/me", route => route.abort("failed"));
  await page.getByRole("button", { name: "Retry GET", exact: true }).click();
  await expect(page.getByTestId("status")).toHaveText("error");
  await expect(page.getByRole("alert")).toBeVisible();
  await page.route("**/api/v1/profiles/me", route => route.fulfill({ json: profile }));
  await page.getByRole("button", { name: "Retry GET", exact: true }).click();
  await expect(page.getByTestId("status")).toHaveText("ready");
});

test("malformed profile data becomes a recoverable error, never a render crash", async ({ page }) => {
  await open(page);
  await page.route("**/api/v1/profiles/me", route => route.fulfill({ json: { ...profile, birthday: 19980102 } }));
  await page.getByRole("button", { name: "Retry GET", exact: true }).click();
  await expect(page.getByTestId("status")).toHaveText("error");
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByTestId("birthday")).toHaveCount(0);
});

test("network PATCH failure retains the exact draft and never claims success", async ({ page }) => {
  await open(page);
  await page.route("**/api/v1/profiles/me", route => route.abort("failed"));
  await page.getByRole("button", { name: "Edit profile", exact: true }).click();
  await page.getByLabel("name", { exact: true }).fill("  Chưa xác nhận  ");
  await page.getByRole("button", { name: "Save profile", exact: true }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByLabel("name", { exact: true })).toHaveValue("  Chưa xác nhận  ");
  await expect(page.getByText("Đã lưu", { exact: true })).toHaveCount(0);
});

test("current-session 401 expires credentials and 403 removes personnel data", async ({ page }) => {
  await open(page);
  await page.route("**/api/v1/profiles/me", route => route.fulfill({ status: 403, json: { code: "FORBIDDEN" } }));
  await page.getByRole("button", { name: "Retry GET", exact: true }).click();
  await expect(page.getByTestId("status")).toHaveText("forbidden");
  await expect(page.getByTestId("salary")).toHaveCount(0);
  await page.route("**/api/v1/profiles/me", route => route.fulfill({ status: 401, json: { code: "UNAUTHORIZED" } }));
  await page.getByRole("button", { name: "Retry GET", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Đăng nhập", exact: true })).toBeVisible();
  expect(await page.evaluate(() => sessionStorage.getItem("smart_cafe_token"))).toBeNull();
});

test("a canceled older GET cannot overwrite the latest retry response", async ({ page }) => {
  await open(page);
  let release, finish;
  const pending = new Promise(resolve => { release = resolve; });
  const finished = new Promise(resolve => { finish = resolve; });
  let reads = 0;
  await page.route("**/api/v1/profiles/me", async route => {
    if (++reads === 1) {
      await pending;
      await route.fulfill({ json: { ...profile, name: "Stale read" } }).catch(() => {});
      finish();
    } else await route.fulfill({ json: { ...profile, name: "Latest read", version: "9007199254740994" } });
  });
  await page.getByRole("button", { name: "Retry GET", exact: true }).click();
  await expect.poll(() => reads).toBe(1);
  await page.getByRole("button", { name: "Retry GET", exact: true }).click();
  await expect(page.getByTestId("name")).toHaveText("Latest read");
  release();
  await finished;
  await expect(page.getByTestId("name")).toHaveText("Latest read");
});

test("late auth verification 401 is canceled after password replacement", async ({ page }) => {
  await setup(page);
  let release, finish;
  const pending = new Promise(resolve => { release = resolve; });
  const finished = new Promise(resolve => { finish = resolve; });
  let initialReads = 0;
  await page.route("**/api/v1/auth/me", async route => {
    if (route.request().headers().authorization === "Bearer initial-test-only" && ++initialReads > 1) {
      await pending;
      await route.fulfill({ status: 401, json: { code: "UNAUTHORIZED" } }).catch(() => {});
      finish();
    } else await route.fulfill({ json: user });
  });
  await page.route("**/api/v1/profiles/me/password", async route => {
    // Make the old ready state observable until the replacement response arrives.
    await new Promise(resolve => setTimeout(resolve, 100));
    return route.fulfill({ json: replacement });
  });
  await page.goto("/tests/account-harness.html");
  await signIn(page);
  await expect(page.getByTestId("status")).toHaveText("ready");
  await passwordForm(page);
  await page.getByRole("button", { name: "Save password", exact: true }).click();
  await expect(page.getByTestId("status")).toHaveText("ready");
  await expect.poll(() => page.evaluate(expected => sessionStorage.getItem("smart_cafe_token") === expected, replacement.token)).toBe(true);
  release();
  await finished;
  expect(await page.evaluate(expected => sessionStorage.getItem("smart_cafe_token") === expected, replacement.token)).toBe(true);
  await expect(page.getByTestId("status")).toHaveText("ready");
});

test("logout while login is pending prevents the delayed login from restoring credentials", async ({ page }) => {
  await setup(page);
  let release;
  const pending = new Promise(resolve => { release = resolve; });
  await page.route("**/api/v1/auth/login", async route => { await pending; return route.fulfill({ json: { token: "late-login-test-only", user, ...expiry } }); });
  await page.goto("/tests/account-harness.html");
  const started = page.waitForRequest("**/api/v1/auth/login");
  await signIn(page);
  await started;
  await page.getByRole("button", { name: "Cancel session", exact: true }).click();
  release();
  await expect(page.getByRole("alert")).toContainText("đã được hủy");
  expect(await page.evaluate(() => sessionStorage.getItem("smart_cafe_token"))).toBeNull();
  await expect(page.getByTestId("salary")).toHaveCount(0);
});

test("an external session switch invalidates an earlier pending login", async ({ page }) => {
  await setup(page);
  let release;
  const pending = new Promise(resolve => { release = resolve; });
  await page.route("**/api/v1/auth/login", async route => {
    await pending;
    return route.fulfill({ json: { token: "late-login-test-only", user, ...expiry } });
  });
  await page.goto("/tests/account-harness.html");
  const started = page.waitForRequest("**/api/v1/auth/login");
  await signIn(page);
  await started;
  const loginSettled = page.waitForEvent("requestfinished", {
    predicate: request => request.url().endsWith("/api/v1/auth/login"),
    timeout: 5000,
  });
  const loginAborted = page.waitForEvent("requestfailed", {
    predicate: request => request.url().endsWith("/api/v1/auth/login"),
    timeout: 5000,
  });
  const settled = Promise.any([loginSettled, loginAborted]);
  await page.evaluate(currentUser => {
    sessionStorage.setItem("smart_cafe_token", "switched-session-test-only");
    sessionStorage.setItem("smart_cafe_user", JSON.stringify(currentUser));
    window.dispatchEvent(new CustomEvent("smart-cafe-auth-change"));
  }, user);
  await expect(page.getByTestId("status")).toHaveText("ready");
  release();
  await settled;
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
  expect(await page.evaluate(() => sessionStorage.getItem("smart_cafe_token"))).toBe("switched-session-test-only");
});

test("password conflict requires authentication and a transient auth GET failure preserves the session", async ({ page }) => {
  await setup(page);
  let verified = false;
  await page.route("**/api/v1/auth/me", route => {
    if (verified) return route.fulfill({ status: 503, json: {} });
    verified = true;
    return route.fulfill({ json: user });
  });
  await page.goto("/tests/account-harness.html");
  await signIn(page);
  await expect(page.getByTestId("status")).toHaveText("ready");
  await page.route("**/api/v1/profiles/me/password", route => route.fulfill({ status: 409, json: { code: "PASSWORD_CONFLICT" } }));
  await passwordForm(page);
  await page.getByRole("button", { name: "Save password", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("xác thực lại");
  await expect(page.getByRole("button", { name: "Save password", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Xác thực lại", exact: true }).click();
  expect(await page.evaluate(() => sessionStorage.getItem("smart_cafe_token"))).toBeNull();
});

test("password rotation replaces token before refresh and old-session 401 cannot log out replacement", async ({ page }) => {
  await open(page);
  await page.getByRole("button", { name: "Capture session", exact: true }).click();
  const profileHeaders = [];
  await page.route("**/api/v1/profiles/me", route => {
    profileHeaders.push(route.request().headers().authorization);
    return route.fulfill({ json: { ...profile, ...replacement, token: undefined, version: "9007199254740994" } });
  });
  let calls = 0;
  await page.route("**/api/v1/profiles/me/password", route => {
    calls++;
    expect(Object.keys(route.request().postDataJSON()).sort()).toEqual(["confirmNewPassword", "currentPassword", "newPassword"]);
    return route.fulfill({ json: replacement });
  });
  await passwordForm(page);
  await page.locator("form").evaluate(form => { form.requestSubmit(); form.requestSubmit(); });
  await expect(page.getByTestId("expiry")).toHaveText("false");
  await expect(page.getByTestId("status")).toHaveText("ready");
  expect(calls).toBe(1);
  expect(profileHeaders.length > 0 && profileHeaders.every(header => header === `Bearer ${replacement.token}`)).toBe(true);
  expect(await page.evaluate(expected => sessionStorage.getItem("smart_cafe_token") === expected, replacement.token)).toBe(true);
  await page.getByRole("button", { name: "Expire captured session", exact: true }).click();
  await expect(page.getByTestId("status")).toHaveText("ready");
  await expect(page.getByLabel("currentPassword", { exact: true })).toHaveCount(0);
  expect(await page.evaluate(() => JSON.stringify(sessionStorage).includes("NewPassword2"))).toBe(false);
});

for (const [code, field, message] of [["CURRENT_PASSWORD_INCORRECT", "currentPassword", "không đúng"], ["PASSWORD_REUSED", "newPassword", "phải khác"], ["PASSWORD_MISMATCH", "confirmNewPassword", "không khớp"], ["INVALID_PASSWORD", "newPassword", "chính sách"]]) {
  test(`password ${code} maps to ${field}`, async ({ page }) => {
    await open(page);
    await page.route("**/api/v1/profiles/me/password", route => route.fulfill({ status: 400, json: { code } }));
    await passwordForm(page);
    await page.getByRole("button", { name: "Save password", exact: true }).click();
    await expect(page.getByRole("alert").first()).toContainText(message);
    await expect(page.getByLabel(field, { exact: true })).toBeVisible();
  });
}

test("ambiguous password outcome has no retry and clears password inputs", async ({ page }) => {
  await open(page);
  let posts = 0;
  await page.route("**/api/v1/profiles/me/password", route => { posts++; return route.abort("failed"); });
  await passwordForm(page);
  await page.getByRole("button", { name: "Save password", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("đăng nhập lại bằng mật khẩu mới");
  await expect(page.getByLabel("currentPassword", { exact: true })).toHaveValue("");
  await expect(page.getByRole("button", { name: "Save password", exact: true })).toBeDisabled();
  expect(posts).toBe(1);
  await page.getByRole("button", { name: "Xác thực lại", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Đăng nhập", exact: true })).toBeVisible();
});

test("cancel password clears all fields without API calls", async ({ page }) => {
  await open(page);
  let posts = 0;
  page.on("request", request => { if (request.url().endsWith("/profiles/me/password")) posts++; });
  await passwordForm(page);
  await page.getByRole("button", { name: "Cancel password", exact: true }).click();
  await page.getByRole("button", { name: "Change password", exact: true }).click();
  for (const field of ["currentPassword", "newPassword", "confirmNewPassword"]) await expect(page.getByLabel(field, { exact: true })).toHaveValue("");
  expect(posts).toBe(0);
});

test("delayed PATCH and failed logout acknowledgement do not resurrect a previous user", async ({ page }) => {
  await open(page);
  let release;
  const pending = new Promise(resolve => { release = resolve; });
  await page.route("**/api/v1/profiles/me", async route => {
    if (route.request().method() === "PATCH") {
      await pending;
      return route.fulfill({ json: { ...profile, name: "Old private response", version: "9007199254740994" } }).catch(() => {});
    }
    return route.fulfill({ json: { ...profile, name: "New account", id: "3" } });
  });
  await page.route("**/api/v1/auth/logout", async route => { await pending; return route.fulfill({ status: 503, json: {} }); });
  await page.getByRole("button", { name: "Edit profile", exact: true }).click();
  await page.getByLabel("name", { exact: true }).fill("Draft");
  const patch = page.waitForRequest(request => request.method() === "PATCH");
  await page.getByRole("button", { name: "Save profile", exact: true }).click();
  await patch;
  await page.getByRole("button", { name: "Đăng xuất", exact: true }).click();
  await expect(page.getByTestId("salary")).toHaveCount(0);
  await page.route("**/api/v1/auth/login", route => route.fulfill({ json: { token: "second-user-test-only", user: { ...user, id: 3, fullName: "New account" }, ...expiry } }));
  await signIn(page);
  await expect(page.getByTestId("name")).toHaveText("New account");
  release();
  await expect(page.getByText("Old private response")).toHaveCount(0);
  await expect(page.getByTestId("name")).toHaveText("New account");
});
