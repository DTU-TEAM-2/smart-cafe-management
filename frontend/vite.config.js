import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "VITE_");
  const proxy = { "/api": { target: env.VITE_API_PROXY_TARGET || "http://127.0.0.1:8080", ws: true } };
  if (env.VITE_AUTH_PROXY_TARGET) {
    // Optional separate DT2-2 instance during local integration; no browser-side cross-origin token transport.
    const api = proxy["/api"];
    delete proxy["/api"];
    proxy["^/api/v1/auth/(?:login|me|logout)(?:$|\\?)"] = { target: env.VITE_AUTH_PROXY_TARGET };
    proxy["/api"] = api;
  }
  return { plugins: [react()], server: { proxy }, preview: { proxy } };
});
