export const passwordStatus = { passwordChangedAt: "2026-11-01T00:00:00Z", passwordExpiresAt: "2026-12-01T00:00:00Z", mustChangePassword: false };
export const staffProfile = { id: "203", username: "minhan.staff", name: "Nguyễn Minh An", email: "synthetic@example.test", phone: "090 123 4567",
  address: "Hải Châu, Đà Nẵng", role: "STAFF", birthday: "1998-03-15", gender: "MALE", salary: "8500000", avatarUrl: null,
  canUpdateProfile: true, version: "7", ...passwordStatus };

export async function productMocks(page, overrides = {}) {
  let profile = { ...staffProfile, ...overrides };
  let token = "initial-ui-test-only";
  const calls = [];
  const user = () => ({ id: 203, username: profile.username, fullName: profile.name, email: profile.email, phone: profile.phone, role: profile.role });
  await page.route("**/api/v1/home/highlights", route => route.fulfill({ json: { newest: [], mostOrdered: [] } }));
  await page.route("**/api/v1/auth/login", route => route.fulfill({ json: { token, user: user(), ...passwordStatus, mustChangePassword: profile.mustChangePassword } }));
  await page.route("**/api/v1/auth/me", route => route.fulfill({ json: user() }));
  await page.route("**/api/v1/auth/logout", route => route.fulfill({ json: {} }));
  await page.route("**/api/v1/home/account", route => route.request().headers().authorization === `Bearer ${token}`
    ? route.fulfill({ json: profile }) : route.fulfill({ status: 401, json: { code: "UNAUTHORIZED" } }));
  await page.route("**/api/v1/home/notifications", route => route.fulfill({ json: { recipientId: profile.id, items: [] } }));
  await page.route("**/api/v1/profiles/me", route => {
    const request = route.request();
    calls.push({ method: request.method(), authorization: request.headers().authorization,
      body: request.method() === "PATCH" ? request.postDataJSON() : undefined });
    if (request.method() === "PATCH") profile = { ...profile, ...request.postDataJSON(), version: String(BigInt(profile.version) + 1n) };
    return route.fulfill({ json: profile });
  });
  await page.route("**/api/v1/profiles/me/password", route => {
    calls.push({ method: "POST", authorization: route.request().headers().authorization });
    token = "replacement-ui-test-only";
    profile = { ...profile, mustChangePassword: false, version: String(BigInt(profile.version) + 1n) };
    return route.fulfill({ json: { token, ...passwordStatus } });
  });
  return { calls, profile: () => profile, token: () => token };
}

export async function loginFromPage(page, username = staffProfile.username, password = "OldPassword1") {
  await page.goto("/#login");
  await page.getByLabel("Tên đăng nhập", { exact: true }).fill(username);
  await page.getByLabel("Mật khẩu", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await page.getByRole("button", { name: /^Tài khoản / }).waitFor();
}

export async function profileFromHeader(page) {
  await page.getByRole("button", { name: /^Tài khoản / }).click();
  await page.getByRole("link", { name: "Thông tin tài khoản", exact: true }).click();
  await page.getByRole("heading", { name: "Thông tin tài khoản", exact: true }).waitFor();
}
