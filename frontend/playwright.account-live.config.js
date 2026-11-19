import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e-live", workers: 1, timeout: 60000, retries: 0,
  outputDir: "../backend/target/dt2-28-browser/artifacts",
  reporter: [["line"], ["json", { outputFile: "../backend/target/dt2-28-browser/playwright.json" }]],
  use: { ...devices["Desktop Chrome"], baseURL: "http://127.0.0.1:15175", viewport: { width: 1440, height: 900 }, trace: "off", screenshot: "off" },
  webServer: { command: "npm run dev -- --host 127.0.0.1 --port 15175 --strictPort", url: "http://127.0.0.1:15175", reuseExistingServer: false },
});
