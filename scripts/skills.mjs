import { readFile, readdir } from "node:fs/promises";
import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
import { resolve, dirname } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const manifest = "docs/engineering/inherited-skills.json";
const provenance = "docs/engineering/skill-provenance.md";

export async function loadRegistry() {
  return JSON.parse(await readFile(resolve(root, ".agents/registry.json"), "utf8"));
}

export async function routeSkills(...domains) {
  if (!domains.length) throw new Error("Choose a domain. Use requirements if the task is unclear.");
  const registry = await loadRegistry();
  const ids = new Set();
  for (const domain of domains) {
    if (!Object.hasOwn(registry.routes, domain)) {
      throw new Error(`Unknown domain: ${domain}. Available: ${Object.keys(registry.routes).join(", ")}`);
    }
    for (const id of registry.routes[domain]) ids.add(id);
  }
  if (ids.size > 3) throw new Error("More than three skills match. Select narrower domains for this phase.");
  return [...ids].map((id) => registry.skills.find((skill) => skill.id === id));
}

export async function checkInheritedSkills(directory = root) {
  const entries = JSON.parse(await readFile(resolve(directory, manifest), "utf8"));
  if (!Array.isArray(entries) || !entries.length) throw new Error("Inherited skill manifest is empty or invalid");
  const seen = new Set();
  for (const { path, sha256 } of entries) {
    if (typeof path !== "string" || !/^\.agents\/skills\/(?:[\w.-]+\/)+[\w.-]+$/.test(path)
      || path.split("/").some((part) => part === "." || part === "..")
      || seen.has(path) || typeof sha256 !== "string" || !/^[a-f0-9]{64}$/.test(sha256)) {
      throw new Error(`Invalid or duplicate inherited skill entry: ${path}`);
    }
    seen.add(path);
    const content = await readFile(resolve(directory, path));
    if (!matchesInheritedChecksum(content, sha256)) {
      throw new Error(`Inherited skill checksum mismatch: ${path}`);
    }
  }
  return entries.length;
}

export function checkInheritedInventory(paths, entries, baseEntries = entries, provenanceChanged = false) {
  const actual = new Set(paths);
  const current = new Set(entries.map(({ path }) => path));
  const base = new Set(baseEntries.map(({ path }) => path));
  const missing = [...actual].filter((path) => !current.has(path));
  const unexpected = [...current].filter((path) => !actual.has(path));
  if (missing.length || unexpected.length) {
    throw new Error([
      ...missing.map((path) => `Missing from inherited manifest: ${path}`),
      ...unexpected.map((path) => `Outside tracked inherited inventory: ${path}`),
    ].join("\n"));
  }
  const changes = [
    ...[...current].filter((path) => !base.has(path)).sort().map((path) => `Added: ${path}`),
    ...[...base].filter((path) => !current.has(path)).sort().map((path) => `Removed: ${path}`),
  ];
  if (changes.length && !provenanceChanged) {
    throw new Error(["Inherited inventory changed. Document the reason and provenance in the same PR.", ...changes].join("\n"));
  }
  return changes;
}

async function checkTrackedInheritedInventory(baseSha) {
  if (baseSha && !/^[a-f0-9]{40}$/.test(baseSha)) throw new Error("Expected a full base commit SHA");
  const registry = await loadRegistry();
  const folders = registry.skills.filter(({ origin }) => origin !== "project").map(({ folder }) => `.agents/skills/${folder}/`);
  const git = (...args) => execFileSync("git", args, { cwd: root, encoding: "utf8" });
  const paths = git("ls-files", "-z", "--", ".agents/skills")
    .split("\0").filter((path) => folders.some((folder) => path.startsWith(folder)));
  const entries = JSON.parse(await readFile(resolve(root, manifest), "utf8"));
  const baseEntries = baseSha ? JSON.parse(git("show", `${baseSha}:${manifest}`)) : entries;
  const provenanceChanged = baseSha
    ? git("diff", "--name-only", baseSha, "HEAD", "--", provenance).trim() === provenance
    : false;
  const changes = checkInheritedInventory(paths, entries, baseEntries, provenanceChanged);
  if (changes.length) console.log(["Inherited inventory changes for review:", ...changes].join("\n"));
}

function matchesInheritedChecksum(content, expected) {
  const hash = (bytes) => createHash("sha256").update(bytes).digest("hex");
  if (hash(content) === expected) return true;

  // Some source snapshots were hashed on Windows before Git normalized line endings.
  const text = content.toString("utf8");
  if (!Buffer.from(text, "utf8").equals(content) || text.includes("\0")) return false;
  const lf = text.replaceAll("\r\n", "\n");
  return hash(lf) === expected || hash(lf.replaceAll("\n", "\r\n")) === expected;
}

async function checkRegistry() {
  const registry = await loadRegistry();
  const ids = new Set();
  const folders = new Set();
  for (const skill of registry.skills) {
    if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(skill.id) || skill.id.length > 64) {
      throw new Error(`Invalid skill identifier: ${skill.id}`);
    }
    if (ids.has(skill.id) || folders.has(skill.folder)) {
      throw new Error(`Duplicate skill registration: ${skill.id}`);
    }
    if (!/^[a-z0-9-]+$/.test(skill.folder)) throw new Error(`Invalid folder: ${skill.folder}`);
    ids.add(skill.id);
    folders.add(skill.folder);
    const source = (await readFile(resolve(root, ".agents/skills", skill.folder, "SKILL.md"), "utf8"))
      .replaceAll("\r\n", "\n");
    const frontmatter = source.match(/^---\n([\s\S]*?)\n---/u)?.[1];
    if (!frontmatter || !new RegExp(`^name: ${skill.id}$`, "m").test(frontmatter)
        || !/^description:\s*\S/m.test(frontmatter)) {
      throw new Error(`Missing or inconsistent frontmatter: ${skill.folder}`);
    }
  }
  const installed = (await readdir(resolve(root, ".agents/skills"), { withFileTypes: true }))
    .filter((entry) => entry.isDirectory()).map((entry) => entry.name);
  for (const folder of installed) {
    if (!folders.has(folder)) throw new Error(`Unregistered installed skill: ${folder}`);
  }
  for (const [domain, route] of Object.entries(registry.routes)) {
    if (!route.length || route.length > 3 || new Set(route).size !== route.length) {
      throw new Error(`Invalid route: ${domain}`);
    }
    for (const id of route) {
      if (!ids.has(id)) throw new Error(`Route ${domain} references missing skill: ${id}`);
    }
  }
  console.log(`Skill registry OK: ${ids.size} skills, ${Object.keys(registry.routes).length} routes.`);
}

async function main() {
  const [command = "list", ...domains] = process.argv.slice(2);
  if (command === "check") {
    if (domains.length > 1) throw new Error("Usage: node scripts/skills.mjs check [base-sha]");
    await checkRegistry();
    const count = await checkInheritedSkills();
    await checkTrackedInheritedInventory(domains[0]);
    console.log(`Inherited skill inventory and checksums OK: ${count} files.`);
    return;
  }
  const skills = command === "list" ? (await loadRegistry()).skills
    : command === "route" ? await routeSkills(...domains) : null;
  if (!skills) throw new Error("Usage: node scripts/skills.mjs [list|check|route <domain> ...]");
  for (const skill of skills) {
    console.log(`${skill.id} — .agents/skills/${skill.folder}/SKILL.md — ${skill.purpose}`);
  }
  if (command === "route") console.log("Read selected SKILL.md files before this phase; candidates are not automatically applied.");
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
