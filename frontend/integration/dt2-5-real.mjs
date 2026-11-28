// Isolated browser -> real Spring Boot -> MySQL acceptance. No API mocking.
import { chromium } from "@playwright/test";
import { createHmac } from "node:crypto";
import { mkdir, writeFile, mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";
import assert from "node:assert/strict";

const base = process.env.FEEDBACK_TEST_BASE || "http://127.0.0.1:15175";
const api = process.env.FEEDBACK_TEST_API || "http://127.0.0.1:18085";
if (!process.env.FEEDBACK_TEST_SECRET || !process.env.TEST_DB_URL?.match(/\/[^/?;]+_test(?:\?|$)/)) throw new Error("Explicit isolated *_test URL and runtime test secret required.");
for (const url of [base, api]) if (!["127.0.0.1", "localhost"].includes(new URL(url).hostname)) throw new Error("This acceptance runner is restricted to loopback.");
function token(id, role) {
  const payload = Buffer.from(`${id}:feedback-http-${role.toLowerCase()}:${role}:0:${Date.now() + 1800000}`).toString("base64url");
  return `${payload}.${createHmac("sha256", process.env.FEEDBACK_TEST_SECRET).update(payload).digest("hex")}`;
}
const adminToken = token(96001, "ADMIN");
const staffToken = token(96002, "STAFF");
const output = path.resolve("../docs/handover/evidence/dt2-5/real");
await mkdir(output, { recursive: true });
const browser = await chromium.launch();
let checks = 0;
try {
  for (const [name, viewport] of [["desktop", { width: 1440, height: 1100 }], ["mobile", { width: 390, height: 844 }]]) {
    const page = await browser.newPage({ viewport });
    await page.addInitScript(({ token }) => {
      sessionStorage.setItem("smart_cafe_token", token);
      sessionStorage.setItem("smart_cafe_user", JSON.stringify({ id: "96001", username: "feedback-http-admin", fullName: "Quản lý HTTP test", role: "ADMIN" }));
    }, { token: adminToken });
    await page.goto(`${base}/#admin-feedback`);
    await page.getByRole("button", { name: "Xem phản hồi 96001", exact: true }).waitFor(); checks++;
    assert.equal(await page.locator(".feedback-table th[scope=col]").count(), 5); checks++;
    await page.getByRole("button", { name: "Xem phản hồi 96001", exact: true }).click();
    await page.locator(".feedback-detail").getByText("Khách kiểm thử HTTP", { exact: true }).waitFor(); checks++;
    await page.screenshot({ path: path.join(output, `01-detail-${name}.png`), fullPage: true });
    await page.getByLabel("Search by Date").fill("2026-11-19");
    await page.getByRole("button", { name: "Search", exact: true }).click();
    await page.getByRole("button", { name: "Xem phản hồi 96001", exact: true }).waitFor();
    assert.equal(await page.locator(".feedback-table tbody tr").count(), 1); checks++;
    await page.screenshot({ path: path.join(output, `02-date-${name}.png`), fullPage: true });
    await page.getByLabel("Search by Date").fill("2026-11-21");
    await page.getByRole("button", { name: "Search", exact: true }).click();
    await page.getByText("Not found!", { exact: true }).waitFor(); checks++;
    assert.equal(await page.locator(".feedback-detail").getByText("Khách kiểm thử HTTP").count(), 0); checks++;
    await page.screenshot({ path: path.join(output, `03-not-found-${name}.png`), fullPage: true });
    await page.getByRole("button", { name: "Show All" }).click();
    await page.getByRole("button", { name: "Xem phản hồi 96002", exact: true }).waitFor();
    assert.equal(await page.locator(".feedback-table tbody tr").count(), 2); checks++;
    await page.close();
  }
  const temp = await mkdtemp(path.join(tmpdir(), "lecker-dt25-"));
  try {
    const envFile = path.join(temp, "runtime-environment.json");
    await writeFile(envFile, JSON.stringify({ name: "Runtime isolated test only", values: [
      { key: "baseUrl", value: api, enabled: true }, { key: "adminToken", value: adminToken, enabled: true }, { key: "staffToken", value: staffToken, enabled: true },
    ] }));
    const npx = process.platform === "win32" ? "npx.cmd" : "npx";
    const run = spawnSync(npx, ["--yes", "newman@6.2.1", "run", "../docs/qa/postman/dt2-5-feedback-management.postman_collection.json", "-e", envFile, "--reporters", "cli"], { stdio: "inherit", shell: process.platform === "win32" });
    if (run.status !== 0) throw new Error("Newman failed");
  } finally { await rm(temp, { recursive: true, force: true }); }
  await writeFile(path.join(output, "results.json"), JSON.stringify({ browserChecks: checks, browsers: ["Chromium desktop", "Chromium mobile viewport"], backend: "Spring Boot real HTTP", database: "isolated MySQL *_test fixture", newman: "9 requests, passed", firebase: "not configured/not exercised", apiMocks: false }, null, 2));
  console.log(`Browser real API checks: ${checks} PASS; Newman: PASS. No tokens exported.`);
} finally { await browser.close(); }
