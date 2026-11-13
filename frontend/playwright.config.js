import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  use: { baseURL: "http://127.0.0.1:15173" },
  projects: [
    { name: "desktop", use: { ...devices["Desktop Chrome"], viewport: { width: 1440, height: 1300 } } },
    { name: "mobile", use: { ...devices["iPhone 13"], defaultBrowserType: "chromium" } },
  ],
  webServer: [{
    command: "npm run dev -- --host 127.0.0.1 --port 15173 --strictPort",
    url: "http://127.0.0.1:15173",
    reuseExistingServer: false,
  }, {
    command: "npm run build && npm exec -- vite preview --host 127.0.0.1 --port 15174 --strictPort",
    url: "http://127.0.0.1:15174",
    reuseExistingServer: false,
  }],
});
