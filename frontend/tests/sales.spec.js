import { expect, test } from "@playwright/test";

const table = (id, name, operationalStatus = "ACTIVE", occupancy = "OCCUPIED") => ({ id, name, operationalStatus, occupancy,
  pendingWaiterRequests: 0, pendingBillRequests: 0, submittedOrderItems: occupancy === "OCCUPIED" ? 1 : 0 });
const item = (name, quantity = 1) => ({ id: "1", orderItemId: "1", itemName: name, quantity, unitPrice: "25000", lineTotal: String(25000 * quantity) });
const services = (id, code, name, quantity = 1, currentBill = null, activeRequests = []) => ({ tableId: id,
  sessionCode: code, items: name ? [item(name, quantity)] : [], totalAmount: name ? String(25000 * quantity) : "0", activeRequests, currentBill });
const bill = (status = "PENDING") => ({ id: "91", billCode: "HD-91", sessionCode: "SESSION-OLD", tableName: "Bàn snapshot",
  creatorName: "Quản lý kiểm thử", totalAmount: "91000", status, items: [{ ...item("Món snapshot"), unitPrice: "91000", lineTotal: "91000" }] });
const receipt = command => {
  const paidAt = new Date().toISOString();
  return { bill: { ...bill("PAID"), paidAt }, payment: {
    id: "1", method: command.method, referenceCode: command.referenceCode, amount: "91000", receivedAt: paidAt,
  } };
};

async function setup(page, role = "ADMIN", handler = () => false) {
  await page.addInitScript(() => sessionStorage.setItem("smart_cafe_token", "playwright-test-only"));
  await page.route("**/api/v1/**", async route => {
    const url = new URL(route.request().url());
    if (await handler(route, url)) return;
    if (url.pathname.endsWith("/auth/me")) return route.fulfill({ json: { id: 1, username: "ui-fixture", role } });
    if (url.pathname.endsWith("/home/account")) return route.fulfill({ json: { id: "1", name: "Kiểm thử", role } });
    if (url.pathname.endsWith("/home/notifications")) return route.fulfill({ json: { recipientId: "1", items: [] } });
    if (url.pathname.endsWith("/sales/tables")) return route.fulfill({ json: [table("1", "Bàn 01"), table("2", "Bàn 02"), table("3", "Bàn 03", "OUT_OF_SERVICE", "EMPTY")] });
    if (url.pathname.endsWith("/sales/csrf")) return route.fulfill({ json: { headerName: "X-CSRF-TOKEN", token: "fixture-only" } });
    return route.fulfill({ status: 404, json: {} });
  });
}

test("staff cannot open income directly and customer cannot query sales", async ({ page }) => {
  let forbiddenApiCalls = 0;
  let role = "STAFF";
  await setup(page, role, async (route, url) => {
    if (url.pathname.endsWith("/home/account")) { await route.fulfill({ json: { id: "1", name: "Kiểm thử", role } }); return true; }
    if (url.pathname.endsWith("/income") || url.pathname.endsWith("/sales/tables")) forbiddenApiCalls++;
    return false;
  });
  await page.goto("/#sale-income");
  await expect(page.getByRole("alert")).toContainText("Chỉ quản lý");
  await expect(page.getByRole("link", { name: "Thu nhập" })).toHaveCount(0);
  role = "CUSTOMER";
  await page.goto("/?customer-check#sale");
  await expect(page.getByRole("alert")).toContainText("Bạn cần đăng nhập");
  expect(forbiddenApiCalls).toBe(0);
});

test("does not show an error as an empty table and retry loads actual services", async ({ page }) => {
  let unavailable = true;
  await setup(page, "STAFF", async (route, url) => {
    if (!url.pathname.endsWith("/1/services")) return false;
    if (unavailable) await route.fulfill({ status: 503, json: {} });
    else await route.fulfill({ json: services("1", "SESSION-OLD", "Cà phê sữa") });
    return true;
  });
  await page.goto("/#sale");
  await expect(page.getByRole("button", { name: /Bàn 03/ })).toContainText("Bảo trì");
  await page.getByRole("button", { name: /Bàn 01/ }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByText("Chưa có món để tính tiền.")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Tạo hóa đơn" })).toHaveCount(0);
  unavailable = false;
  await page.getByRole("button", { name: "Thử lại" }).click();
  await expect(page.getByRole("rowheader", { name: "Cà phê sữa" })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(await page.evaluate(() => innerWidth));
});

test("refreshes order totals after scoped acknowledgment and uses stable bill retry key", async ({ page }) => {
  let acknowledged = false;
  const keys = [];
  await setup(page, "STAFF", async (route, url) => {
    if (url.pathname.endsWith("/1/services")) {
      await route.fulfill({ json: services("1", "SESSION-OLD", "Cà phê", acknowledged ? 2 : 1, null,
        acknowledged ? [] : [{ id: "88", type: "WAITER", status: "PENDING" }]) }); return true;
    }
    if (url.pathname.endsWith("/requests/88/acknowledge")) {
      expect(route.request().postDataJSON()).toEqual({ expectedSessionCode: "SESSION-OLD" });
      acknowledged = true; await route.fulfill({ status: 204 }); return true;
    }
    if (url.pathname.endsWith("/1/bill")) {
      const command = route.request().postDataJSON(); keys.push(command.idempotencyKey);
      expect(command.expectedSessionCode).toBe("SESSION-OLD");
      expect(route.request().headers()["x-csrf-token"]).toBe("fixture-only");
      await route.fulfill(keys.length === 1 ? { status: 503, json: {} } : { status: 201, json: bill() }); return true;
    }
    return false;
  });
  await page.goto("/#sale");
  await page.getByRole("button", { name: /Bàn 01/ }).click();
  await page.getByRole("button", { name: "Tiếp nhận gọi phục vụ" }).click();
  await expect(page.getByRole("row", { name: /Cà phê 2/ })).toBeVisible();
  await page.getByRole("button", { name: "Tạo hóa đơn" }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await page.getByRole("button", { name: "Tạo hóa đơn" }).click();
  await expect(page.getByText("Đã tạo hóa đơn chờ xác nhận thanh toán.")).toBeVisible();
  expect(keys[0]).toBe(keys[1]);
  expect(keys[0].length).toBeLessThanOrEqual(64);
  await expect(page.getByRole("button", { name: "In hóa đơn" })).toHaveCount(0);
});

test("manager prints only confirmed server snapshot and reset dialog restores focus", async ({ page }) => {
  let paid = false;
  await setup(page, "ADMIN", async (route, url) => {
    if (url.pathname.endsWith("/1/services")) {
      await route.fulfill({ json: services("1", "SESSION-OLD", "Giá xem trước", 1, { id: "91", status: paid ? "PAID" : "PENDING" }) }); return true;
    }
    if (url.pathname.endsWith("/bills/91")) { await route.fulfill({ json: bill(paid ? "PAID" : "PENDING") }); return true; }
    if (url.pathname.endsWith("/bills/91/payments")) {
      const payment = route.request().postDataJSON();
      expect(payment).toMatchObject({ method: "CASH", referenceCode: null });
      expect(payment).not.toHaveProperty("amount");
      paid = true; await route.fulfill({ json: receipt(payment) }); return true;
    }
    return false;
  });
  await page.addInitScript(() => { window.print = () => { window.printedBill = document.querySelector(".sale-receipt")?.textContent; }; });
  await page.goto("/#sale");
  await page.getByRole("button", { name: /Bàn 01/ }).click();
  await page.getByRole("button", { name: "Xem hóa đơn hiện tại" }).click();
  await expect(page.getByRole("button", { name: "In hóa đơn" })).toHaveCount(0);
  await page.getByRole("button", { name: "Xác nhận thu tiền" }).click();
  await page.getByLabel("Phương thức").selectOption("BANK_TRANSFER");
  await page.getByLabel(/Mã tham chiếu/).fill("REF-TEST");
  await page.getByLabel("Phương thức").selectOption("CASH");
  await expect(page.getByLabel(/Mã tham chiếu/)).toHaveCount(0);
  await page.getByRole("dialog").getByRole("button", { name: "Xác nhận", exact: true }).click();
  await page.getByRole("button", { name: "In hóa đơn" }).click();
  expect(await page.evaluate(() => window.printedBill)).toContain("Món snapshot");
  expect(await page.evaluate(() => window.printedBill)).not.toContain("Giá xem trước");
  await page.emulateMedia({ media: "print" });
  await expect(page.locator(".sale-receipt")).toHaveCSS("visibility", "visible");
  await expect(page.locator(".sale-receipt table")).toHaveCSS("table-layout", "fixed");
  await expect(page.getByRole("heading", { name: /Chi tiết đơn hàng/ })).toBeHidden();
  await page.emulateMedia({ media: "screen" });
  const reset = page.getByRole("button", { name: "Đặt lại bàn" });
  await reset.click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(reset).toBeFocused();
});

for (const scenario of ["cancel", "retry"]) {
  test(`payment draft behaves correctly after ${scenario}`, async ({ page }) => {
    const payments = [];
    let paid = false;
    await setup(page, "ADMIN", async (route, url) => {
      if (url.pathname.endsWith("/1/services")) {
        await route.fulfill({ json: services("1", "SESSION-OLD", "Giá xem trước", 1, { id: "91", status: paid ? "PAID" : "PENDING" }) });
        return true;
      }
      if (url.pathname.endsWith("/bills/91")) { await route.fulfill({ json: bill(paid ? "PAID" : "PENDING") }); return true; }
      if (url.pathname.endsWith("/bills/91/payments")) {
        payments.push(route.request().postDataJSON());
        if (payments.length === 1) await route.abort("failed");
        else {
          paid = true;
          await route.fulfill({ json: receipt(payments.at(-1)) });
        }
        return true;
      }
      return false;
    });
    await page.goto("/#sale");
    await page.getByRole("button", { name: /Bàn 01/ }).click();
    await page.getByRole("button", { name: "Xem hóa đơn hiện tại" }).click();
    const openPayment = page.getByRole("button", { name: "Xác nhận thu tiền" });
    const dialog = page.getByRole("dialog");
    await openPayment.click();
    await dialog.getByLabel("Phương thức").selectOption("BANK_TRANSFER");
    await dialog.getByLabel(/Mã tham chiếu/).fill(" REF-RETRY ");
    if (scenario === "cancel") {
      await dialog.getByRole("button", { name: "Hủy", exact: true }).click();
      await openPayment.click();
      await expect(dialog.getByLabel("Phương thức")).toHaveValue("CASH");
      await dialog.getByLabel("Phương thức").selectOption("BANK_TRANSFER");
      await expect(dialog.getByLabel(/Mã tham chiếu/)).toHaveValue("");
      expect(payments).toHaveLength(0);
    } else {
      const confirm = dialog.getByRole("button", { name: "Xác nhận", exact: true });
      await confirm.click();
      await expect(dialog.getByRole("alert")).toContainText("Không kết nối được máy chủ");
      await expect(dialog.getByLabel(/Mã tham chiếu/)).toHaveValue(" REF-RETRY ");
      await confirm.click();
      await expect(page.getByRole("button", { name: "In hóa đơn" })).toBeVisible();
      expect(payments).toHaveLength(2);
      expect(payments[0]).toMatchObject({ method: "BANK_TRANSFER", referenceCode: "REF-RETRY" });
      expect(payments[1]).toEqual(payments[0]);
    }
  });
}

for (const outcome of ["pending", "paid"]) {
  test(`reconciles a ${outcome} payment after a polling outage and lost response`, async ({ page }) => {
    let unavailable = false;
    let paid = false;
    const payments = [];
    let releaseResponse;
    const delayedResponse = new Promise(resolve => { releaseResponse = resolve; });
    await page.clock.install();
    await setup(page, "ADMIN", async (route, url) => {
      if (unavailable && (url.pathname.endsWith("/sales/tables") || url.pathname.endsWith("/1/services"))) {
        await route.fulfill({ status: 503, json: {} });
        return true;
      }
      if (url.pathname.endsWith("/1/services")) {
        await route.fulfill({ json: services("1", "SESSION-OLD", "Cà phê", 1,
          { id: "91", status: paid ? "PAID" : "PENDING" }) });
        return true;
      }
      if (url.pathname.endsWith("/bills/91")) {
        await route.fulfill({ json: bill(paid ? "PAID" : "PENDING") });
        return true;
      }
      if (url.pathname.endsWith("/bills/91/payments")) {
        payments.push(route.request().postDataJSON());
        if (payments.length === 1) {
          await delayedResponse;
          paid = outcome === "paid";
          await route.abort("failed");
        } else {
          paid = true;
          await route.fulfill({ json: receipt(payments.at(-1)) });
        }
        return true;
      }
      return false;
    });
    await page.goto("/#sale");
    await page.getByRole("button", { name: /Bàn 01/ }).click();
    const viewBill = page.getByRole("button", { name: "Xem hóa đơn hiện tại" });
    await viewBill.click();
    await page.getByRole("button", { name: "Xác nhận thu tiền" }).click();
    const dialog = page.getByRole("dialog");
    await dialog.getByLabel("Phương thức").selectOption("BANK_TRANSFER");
    await dialog.getByLabel(/Mã tham chiếu/).fill("REF-POLL");
    await dialog.getByRole("button", { name: "Xác nhận", exact: true }).click();
    try {
      await expect.poll(() => payments.length).toBe(1);
      unavailable = true;
      await page.clock.runFor(8500);
      await expect(dialog.getByText("Đang chờ cập nhật trạng thái bàn và hóa đơn.")).toBeVisible();
      await expect(dialog.getByLabel(/Mã tham chiếu/)).toHaveValue("REF-POLL");
    } finally {
      unavailable = false;
      releaseResponse();
    }
    if (outcome === "paid") {
      await expect(dialog).toHaveCount(0);
      await expect(page.getByText("Hóa đơn đã thanh toán.")).toBeVisible();
      await viewBill.click();
      await expect(page.getByRole("button", { name: "In hóa đơn" })).toBeVisible();
      expect(payments).toHaveLength(1);
    } else {
      await expect(dialog.getByText(/Chưa xác nhận kết quả thu tiền/)).toBeVisible();
      await dialog.getByRole("button", { name: "Hủy", exact: true }).click();
      await page.getByRole("button", { name: "Đối soát thu tiền" }).click();
      await expect(dialog.getByLabel("Phương thức")).toBeDisabled();
      await expect(dialog.getByLabel(/Mã tham chiếu/)).toHaveValue("REF-POLL");
      await dialog.getByRole("button", { name: "Xác nhận", exact: true }).click();
      await expect(page.getByRole("button", { name: "In hóa đơn" })).toBeVisible();
      expect(payments).toHaveLength(2);
      expect(payments[1]).toEqual(payments[0]);
    }
  });
}

test("restores an uncertain payment after reload and rejects a wrong receipt", async ({ page }) => {
  const payments = [];
  await setup(page, "ADMIN", async (route, url) => {
    if (url.pathname.endsWith("/1/services")) {
      await route.fulfill({ json: services("1", "SESSION-OLD", "Cà phê", 1, { id: "91", status: "PENDING" }) });
      return true;
    }
    if (url.pathname.endsWith("/bills/91")) {
      await route.fulfill({ json: bill("PENDING") });
      return true;
    }
    if (url.pathname.endsWith("/bills/91/payments")) {
      const command = route.request().postDataJSON();
      payments.push(command);
      if (payments.length === 1) await route.abort("failed");
      else if (payments.length === 2) await route.fulfill({ status: 403, json: {} });
      else if (payments.length === 3) {
        const wrongTotal = receipt(command);
        wrongTotal.bill.totalAmount = "1";
        wrongTotal.bill.items[0].unitPrice = "1";
        wrongTotal.bill.items[0].lineTotal = "1";
        wrongTotal.payment.amount = "1";
        await route.fulfill({ json: wrongTotal });
      } else if (payments.length === 4) await route.fulfill({ status: 400, json: {} });
      else await route.fulfill({ json: { bill: { ...bill("PAID"), id: "92" } } });
      return true;
    }
    return false;
  });
  const openBill = async () => {
    await page.getByRole("button", { name: /Bàn 01/ }).click();
    await page.getByRole("button", { name: "Xem hóa đơn hiện tại" }).click();
  };
  await page.goto("/#sale");
  await openBill();
  await page.getByRole("button", { name: "Xác nhận thu tiền" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Phương thức").selectOption("BANK_TRANSFER");
  await dialog.getByLabel(/Mã tham chiếu/).fill(" REF-RELOAD ");
  const confirm = dialog.getByRole("button", { name: "Xác nhận", exact: true });
  await confirm.click();
  await expect(dialog.getByRole("alert")).toContainText("Không kết nối");
  await page.reload();
  await openBill();
  await page.getByRole("button", { name: "Đối soát thu tiền" }).click({ timeout: 3000 });
  await expect(dialog.getByLabel(/Mã tham chiếu/)).toHaveValue("REF-RELOAD");
  await expect(dialog.getByLabel("Phương thức")).toBeDisabled();
  await confirm.click();
  await expect(dialog.getByRole("alert")).toContainText("không có quyền");
  await confirm.click();
  await expect(dialog.getByRole("alert")).toContainText("Chưa xác minh được kết quả thu tiền");
  await confirm.click();
  await expect(dialog.getByRole("alert")).toContainText("Dữ liệu chưa hợp lệ");
  await expect(dialog.getByLabel("Phương thức")).toBeDisabled();
  await confirm.click();
  await expect(dialog.getByRole("alert")).toContainText("Chưa xác minh được kết quả thu tiền");
  await expect(page.getByText("Đã xác nhận thu tiền. Có thể in hóa đơn.")).toHaveCount(0);
  expect(payments).toEqual(Array(5).fill(payments[0]));
});

test("a first rejected payment can be corrected and uses Java-compatible reference trimming", async ({ page }) => {
  const payments = [];
  let paid = false;
  await setup(page, "ADMIN", async (route, url) => {
    if (url.pathname.endsWith("/1/services")) {
      await route.fulfill({ json: services("1", "SESSION-OLD", "Cà phê", 1, { id: "91", status: paid ? "PAID" : "PENDING" }) });
    } else if (url.pathname.endsWith("/bills/91")) {
      await route.fulfill({ json: bill() });
    } else if (url.pathname.endsWith("/bills/91/payments")) {
      payments.push(route.request().postDataJSON());
      if (payments.length === 1) await route.fulfill({ status: 400, json: {} });
      else {
        paid = true;
        await route.fulfill({ json: receipt(payments.at(-1)) });
      }
    } else return false;
    return true;
  });
  await page.goto("/#sale");
  await page.getByRole("button", { name: /Bàn 01/ }).click();
  await page.getByRole("button", { name: "Xem hóa đơn hiện tại" }).click();
  await page.getByRole("button", { name: "Xác nhận thu tiền" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Phương thức").selectOption("BANK_TRANSFER");
  const reference = dialog.getByLabel(/Mã tham chiếu/);
  await reference.fill("\u001c\u2003\u001f");
  const confirm = dialog.getByRole("button", { name: "Xác nhận", exact: true });
  await confirm.click();
  await expect(dialog.getByRole("alert")).toContainText("Dữ liệu chưa hợp lệ");
  await expect(reference).toBeEnabled();
  await reference.fill("\u001cREF-CORRECT\u001f");
  await confirm.click();
  await expect(page.getByRole("button", { name: "In hóa đơn" })).toBeVisible();
  expect(payments.map(command => command.referenceCode)).toEqual([null, "REF-CORRECT"]);
  expect(payments[1].idempotencyKey).not.toBe(payments[0].idempotencyKey);
  for (const command of payments) expect(command).not.toHaveProperty("expectedAmount");
});

test("requires CSRF and durable storage before sending and separates accounts", async ({ page }) => {
  let csrfReady = false;
  let accountId = "1";
  const payments = [];
  await page.addInitScript(() => {
    const setItem = Storage.prototype.setItem;
    Storage.prototype.setItem = function (key, value) {
      if (key.startsWith("lecker_payment_attempt:") && !this.getItem("allow-payment-write")) {
        throw new DOMException("test quota", "QuotaExceededError");
      }
      return setItem.call(this, key, value);
    };
  });
  await setup(page, "ADMIN", async (route, url) => {
    if (url.pathname.endsWith("/home/account")) {
      await route.fulfill({ json: { id: accountId, role: "ADMIN", name: "Quản lý" } });
    } else if (url.pathname.endsWith("/1/services")) {
      await route.fulfill({ json: services("1", "SESSION-OLD", "Cà phê", 1, { id: "91", status: "PENDING" }) });
    } else if (url.pathname.endsWith("/bills/91")) {
      await route.fulfill({ json: bill() });
    } else if (url.pathname.endsWith("/sales/csrf") && !csrfReady) {
      await route.fulfill({ status: 503, json: {} });
    } else if (url.pathname.endsWith("/bills/91/payments")) {
      payments.push(route.request().postDataJSON());
      await route.abort("failed");
    } else return false;
    return true;
  });
  const openBill = async () => {
    await page.getByRole("button", { name: /Bàn 01/ }).click();
    await page.getByRole("button", { name: "Xem hóa đơn hiện tại" }).click();
  };
  await page.goto("/#sale");
  await openBill();
  await page.getByRole("button", { name: "Xác nhận thu tiền" }).click();
  const dialog = page.getByRole("dialog");
  const confirm = dialog.getByRole("button", { name: "Xác nhận", exact: true });
  await confirm.click();
  await expect(dialog.getByRole("alert")).toBeVisible();
  expect(await page.evaluate(() => Object.keys(sessionStorage).filter(key => key.startsWith("lecker_payment_attempt:")))).toEqual([]);
  csrfReady = true;
  await confirm.click();
  await expect(dialog.getByRole("alert")).toContainText("Chưa gửi xác nhận thu tiền");
  expect(payments).toHaveLength(0);
  await page.evaluate(() => sessionStorage.setItem("allow-payment-write", "yes"));
  await dialog.getByLabel("Phương thức").selectOption("BANK_TRANSFER");
  await dialog.getByLabel(/Mã tham chiếu/).fill("REF-ACCOUNT-1");
  await confirm.click();
  await expect(dialog.getByRole("alert")).toContainText("Không kết nối");
  accountId = "2";
  await page.reload();
  await openBill();
  await page.getByRole("button", { name: "Xác nhận thu tiền" }).click();
  await expect(dialog.getByLabel("Phương thức")).toHaveValue("CASH");
  await expect(dialog.getByLabel(/Mã tham chiếu/)).toHaveCount(0);
  expect(payments).toHaveLength(1);
  accountId = "1";
  await page.reload();
  await openBill();
  await page.getByRole("button", { name: "Đối soát thu tiền" }).click();
  await expect(dialog.getByLabel(/Mã tham chiếu/)).toHaveValue("REF-ACCOUNT-1");
});

test("a stale table response cannot show a verified payment as pending", async ({ page }) => {
  let paid = false;
  let paymentCount = 0;
  await setup(page, "ADMIN", async (route, url) => {
    if (url.pathname.endsWith("/1/services")) {
      await route.fulfill({ json: services("1", "SESSION-OLD", "Cà phê", 1, { id: "91", status: paid ? "PAID" : "PENDING" }) });
    } else if (url.pathname.endsWith("/bills/91")) {
      await route.fulfill({ json: bill("PENDING") });
    } else if (url.pathname.endsWith("/bills/91/payments")) {
      paymentCount++;
      await route.fulfill({ json: receipt(route.request().postDataJSON()) });
    } else return false;
    return true;
  });
  await page.clock.install();
  await page.goto("/#sale");
  await page.getByRole("button", { name: /Bàn 01/ }).click();
  const viewBill = page.getByRole("button", { name: "Xem hóa đơn hiện tại" });
  await viewBill.click();
  await page.getByRole("button", { name: "Xác nhận thu tiền" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Xác nhận", exact: true }).click();
  await expect(page.getByText("Hóa đơn đã thanh toán.")).toBeVisible({ timeout: 3000 });
  await expect(page.getByText("Phiên đã có hóa đơn chờ xử lý.")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Đặt lại bàn" })).toHaveCount(0);
  await viewBill.click();
  await expect(page.getByRole("button", { name: "In hóa đơn" })).toBeVisible();
  await expect(page.getByRole("button", { name: /Xác nhận thu tiền|Đối soát thu tiền/ })).toHaveCount(0);
  paid = true;
  await page.clock.runFor(8500);
  await expect(page.getByRole("button", { name: "Đặt lại bàn" })).toBeEnabled();
  expect(paymentCount).toBe(1);
});

test("new session during polling invalidates an open reset confirmation", async ({ page }) => {
  let session = "SESSION-OLD";
  let resets = 0;
  await page.clock.install();
  await setup(page, "ADMIN", async (route, url) => {
    if (url.pathname.endsWith("/1/services")) {
      await route.fulfill({ json: services("1", session, session === "SESSION-OLD" ? "Món cũ" : "Món mới", 1,
        session === "SESSION-OLD" ? { id: "91", status: "PAID" } : null) }); return true;
    }
    if (url.pathname.endsWith("/reset")) resets++;
    return false;
  });
  await page.goto("/#sale");
  await page.getByRole("button", { name: /Bàn 01/ }).click();
  await page.getByRole("button", { name: "Đặt lại bàn" }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  session = "SESSION-NEW";
  await page.clock.runFor(8500);
  await expect(page.getByRole("rowheader", { name: "Món mới" })).toBeVisible();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  expect(resets).toBe(0);
});

test("income shows real zero and validates a custom range before querying", async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 700 });
  let customQueries = 0;
  await setup(page, "ADMIN", async (route, url) => {
    if (!url.pathname.endsWith("/income")) return false;
    if (url.searchParams.has("from")) customQueries++;
    await route.fulfill({ json: { from: "2026-11-11", to: "2026-11-11", totalAmount: "0", billCount: 0,
      averageBillAmount: "0", dailyRevenue: [{ date: "2026-11-11", revenue: "0", billCount: 0 }] } });
    return true;
  });
  await page.goto("/#sale-income");
  await expect(page.getByRole("heading", { name: "Doanh thu hôm nay" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Biểu đồ doanh thu" })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(320);
  await page.getByLabel("Từ ngày").fill("2026-11-12");
  await page.getByLabel("Đến ngày").fill("2026-11-11");
  await page.getByRole("button", { name: "Tính thu nhập" }).click();
  await expect(page.getByRole("alert")).toContainText("Ngày bắt đầu");
  expect(customQueries).toBe(0);
  await page.getByLabel("Từ ngày").fill("2026-11-11");
  await page.getByRole("button", { name: "Tính thu nhập" }).click();
  await expect(page.getByRole("heading", { name: "Doanh thu trong kỳ" })).toBeVisible();
  expect(customQueries).toBe(1);
});
