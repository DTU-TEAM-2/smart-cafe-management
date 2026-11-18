import test from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, rmdirSync, unlinkSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";
import { validateGitFlow } from "./git-flow.mjs";

test("tasks integrate into develop, including existing names and source naming examples", () => {
  for (const head of ["feature/cf-01_git-flow", "feature/#123_add-search-filter", "feature/dt2-10-staff-management", "bugfix/dt2-1_fix-header"]) {
    assert.equal(validateGitFlow({ base: "develop", head }).ok, true, head);
  }
});
test("release and hotfix cannot skip main promotion and go directly into develop", () => {
  for (const head of ["release/1.4.0", "hotfix/1.4.1_fix-bill"]) {
    assert.equal(validateGitFlow({ base: "develop", head }).ok, false, head);
  }
});
test("features and development fixes cannot go directly into main", () => {
  for (const head of ["feature/new", "bugfix/new", "develop", "chore/new"]) assert.equal(validateGitFlow({ base: "main", head }).ok, false);
});
test("main accepts only release/hotfix", () => {
  for (const head of ["release/1.4.0", "hotfix/1.4.1_fix-bill"]) assert.equal(validateGitFlow({ base: "main", head }).ok, true);
});
test("release stabilization allows fixes, not features or release-to-release merges", () => {
  for (const head of ["bugfix/issue_fix", "hotfix/1.4.1_fix"]) assert.equal(validateGitFlow({ base: "release/1.4.0", head }).ok, true);
  for (const head of ["feature/issue_new", "develop", "release/1.5.0", "main"]) assert.equal(validateGitFlow({ base: "release/1.4.0", head }).ok, false);
});
test("main sync back to develop requires the same repository, not a fork or missing identity", () => {
  assert.equal(validateGitFlow({ base: "develop", head: "main", baseRepo: "org/cafe", headRepo: "org/cafe" }).ok, true);
  for (const headRepo of [undefined, "fork/cafe"]) assert.equal(validateGitFlow({ base: "develop", head: "main", baseRepo: "org/cafe", headRepo }).ok, false);
});
test("missing, malformed and unsupported refs fail closed", () => {
  for (const args of [{}, { base: "main", head: "main" }, { base: "develop", head: "feature/" },
    { base: "develop", head: "feature/Name With Spaces" }, { base: "custom", head: "feature/task" }, { base: "develop", head: "temp" }]) {
    assert.equal(validateGitFlow(args).ok, false, JSON.stringify(args));
  }
});

test("CI command fails without a PR, rejects a feature to main and reports a valid task to develop", () => {
  const folder = mkdtempSync(join(tmpdir(), "cafe-git-flow-"));
  const event = join(folder, "event.json");
  const summary = join(folder, "summary.md");
  const script = fileURLToPath(new URL("./git-flow.mjs", import.meta.url));
  try {
    const env = { ...process.env, GITHUB_EVENT_PATH: "", GITHUB_STEP_SUMMARY: summary };
    assert.notEqual(spawnSync(process.execPath, [script], { env }).status, 0);
    for (const base of ["main", "develop"]) {
      writeFileSync(event, JSON.stringify({ pull_request: {
        base: { ref: base, repo: { full_name: "org/cafe" } }, head: { ref: "feature/cf-01_git-flow", repo: { full_name: "org/cafe" } },
      } }));
      const result = spawnSync(process.execPath, [script], { env: { ...env, GITHUB_EVENT_PATH: event }, encoding: "utf8" });
      assert.equal(result.status, base === "develop" ? 0 : 1);
    }
    for (const head of ["release/1.4.0", "hotfix/1.4.1_fix-bill"]) {
      writeFileSync(event, JSON.stringify({ pull_request: {
        base: { ref: "develop", repo: { full_name: "org/cafe" } }, head: { ref: head, repo: { full_name: "org/cafe" } },
      } }));
      assert.equal(spawnSync(process.execPath, [script], { env: { ...env, GITHUB_EVENT_PATH: event } }).status, 1);
    }
    assert.match(readFileSync(summary, "utf8"), /FAIL/);
    assert.match(readFileSync(summary, "utf8"), /PASS/);
  } finally {
    for (const path of [event, summary]) { try { unlinkSync(path); } catch (error) { if (error.code !== "ENOENT") throw error; } }
    rmdirSync(folder);
  }
});
