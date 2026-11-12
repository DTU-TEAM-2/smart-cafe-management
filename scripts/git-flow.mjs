import { appendFileSync, readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";

const task = /^(feature|bugfix|hotfix|release)\/[a-z0-9#][a-z0-9#._/-]*$/;

export function validateGitFlow({ base, head, baseRepo, headRepo }) {
  if (!base || !head || base === head) return { ok: false, reason: "Missing refs or identical source/target." };
  if (head === "main" && base === "develop") {
    return baseRepo && headRepo === baseRepo
      ? { ok: true, reason: "Synchronize an approved release/hotfix from main to develop; use a merge commit." }
      : { ok: false, reason: "main -> develop sync must originate in the same repository." };
  }
  if (!task.test(head)) return { ok: false, reason: "Source must be feature/*, bugfix/*, release/* or hotfix/*." };
  const type = head.split("/")[0];
  if (base === "develop") return { ok: true, reason: "Integrate the reviewed task or synchronize a release/hotfix." };
  if (base === "main" && ["release", "hotfix"].includes(type)) {
    return { ok: true, reason: "Promote release/hotfix; merge commit and follow-up sync to develop required." };
  }
  if (/^release\/[a-z0-9][a-z0-9._-]*$/.test(base) && ["bugfix", "hotfix"].includes(type)) {
    return { ok: true, reason: "Stabilize the specified release; reviewer verifies scope and starting ref." };
  }
  return { ok: false, reason: "Feature/bugfix target develop; main accepts release/hotfix; release accepts bugfix/hotfix." };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const eventPath = process.env.GITHUB_EVENT_PATH;
  const pr = eventPath ? JSON.parse(readFileSync(eventPath, "utf8")).pull_request : null;
  if (!pr) throw new Error("Git Flow check requires a pull_request event payload.");
  const result = validateGitFlow({ base: pr.base.ref, head: pr.head.ref,
    baseRepo: pr.base.repo?.full_name, headRepo: pr.head.repo?.full_name });
  console.log(`${result.ok ? "PASS" : "FAIL"}: ${result.reason}`);
  if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY,
    `## Git Flow\n\n${result.ok ? "PASS" : "FAIL"}: ${result.reason}\n\nThis check validates source/target policy only. It does not replace code review, two independent approvals, branch-origin or release-scope verification.\n`);
  if (!result.ok) process.exitCode = 1;
}
