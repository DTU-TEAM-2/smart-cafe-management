import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./smoke",
  outputDir: "./smoke/test-results",
  reporter: [["list"], ["html", { outputFolder: "./smoke/playwright-report", open: "never" }]],
  use: {
    baseURL: "http://127.0.0.1:15175",
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
  },
  webServer: {
    command: "npm exec -- vite preview --host 127.0.0.1 --port 15175 --strictPort",
    url: "http://127.0.0.1:15175",
    reuseExistingServer: false,
  },
});
