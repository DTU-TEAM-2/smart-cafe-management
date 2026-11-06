import { readFile, readdir } from "node:fs/promises";
import { resolve, dirname } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");

export async function loadRegistry() {
  return JSON.parse(await readFile(resolve(root, ".agents/registry.json"), "utf8"));
}

export async function routeSkills(domain) {
  const registry = await loadRegistry();
  const ids = registry.routes[domain];
  if (!ids) {
    throw new Error(`Unknown domain: ${domain}. Available: ${Object.keys(registry.routes).join(", ")}`);
  }
  return ids.map((id) => registry.skills.find((skill) => skill.id === id));
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
  const [command = "list", domain] = process.argv.slice(2);
  if (command === "check") return checkRegistry();
  const skills = command === "list" ? (await loadRegistry()).skills
    : command === "route" ? await routeSkills(domain) : null;
  if (!skills) throw new Error("Usage: node scripts/skills.mjs [list|check|route <domain>]");
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
