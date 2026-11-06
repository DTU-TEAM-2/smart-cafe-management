import test from "node:test";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { checkInheritedInventory, checkInheritedSkills, routeSkills } from "./skills.mjs";

test("backend-only work does not load UI/design skills", async () => {
  const skills = await routeSkills("backend");
  assert.ok(skills.some((skill) => skill.id === "cafe-spring-api"));
  assert.ok(skills.every((skill) => !/react|accessibility|design|ui-ux/.test(skill.id)));
});

test("React interactive work includes accessibility without backend guidance", async () => {
  const ids = (await routeSkills("frontend")).map((skill) => skill.id);
  assert.ok(ids.includes("vercel-react-best-practices"));
  assert.ok(ids.includes("fixing-accessibility"));
  assert.ok(!ids.includes("cafe-spring-api"));
});

test("correctness review is separate from optional complexity review", async () => {
  assert.ok((await routeSkills("review")).some((skill) => skill.id === "cafe-review"));
  assert.ok((await routeSkills("simplicity")).every((skill) => skill.id !== "cafe-review"));
});

test("unknown domains fail explicitly instead of silently routing unrelated skills", async () => {
  await assert.rejects(routeSkills("ocr-training"), /Unknown domain/);
  await assert.rejects(routeSkills("backend", "constructor"), /Unknown domain/);
});

test("multi-domain work preserves order and deduplicates shared skills", async () => {
  const ids = (await routeSkills("backend", "database", "quality")).map((skill) => skill.id);
  assert.deepEqual(ids, ["cafe-spring-api", "cafe-database", "cafe-quality"]);
  await assert.rejects(routeSkills("backend", "database", "frontend"), /More than three skills/);
});

test("an unclear task starts with requirements before implementation skills", async () => {
  await assert.rejects(routeSkills(), /Use requirements/);
  assert.deepEqual((await routeSkills("requirements")).map((skill) => skill.id), ["cafe-requirements"]);
});

test("an inherited skill edit or deletion fails checksum verification", async (t) => {
  const directory = await mkdtemp(join(tmpdir(), "cafe-skills-"));
  t.after(() => rm(directory, { recursive: true, force: true }));
  const path = ".agents/skills/example/SKILL.md";
  const skill = join(directory, path);
  const manifest = join(directory, "docs/engineering/inherited-skills.json");
  await mkdir(dirname(skill), { recursive: true });
  await mkdir(dirname(manifest), { recursive: true });
  const original = "Inherited skill\r\n";
  const sha256 = createHash("sha256").update(original).digest("hex");
  await writeFile(skill, original);
  await writeFile(manifest, JSON.stringify([{ path, sha256 }]));
  assert.equal(await checkInheritedSkills(directory), 1);
  await writeFile(skill, "Inherited skill\n");
  assert.equal(await checkInheritedSkills(directory), 1);
  await writeFile(skill, "Changed skill\n");
  await assert.rejects(checkInheritedSkills(directory), /checksum mismatch/);
  await rm(skill);
  await assert.rejects(checkInheritedSkills(directory), { code: "ENOENT" });
});

test("a tracked inherited file and its manifest entry must both exist", () => {
  const path = ".agents/skills/example/SKILL.md";
  assert.throws(() => checkInheritedInventory([path], []), /Missing from inherited manifest/);
  assert.throws(() => checkInheritedInventory([], [{ path }]), /Outside tracked inherited inventory/);
});

test("removing both a file and its entry is detected against the PR base", () => {
  const kept = { path: ".agents/skills/example/SKILL.md" };
  const removed = { path: ".agents/skills/example/guide.md" };
  const added = { path: ".agents/skills/example/reference.md" };
  const base = [kept, removed];
  assert.throws(() => checkInheritedInventory([kept.path], [kept], base), /Removed: .*guide\.md/);
  const entries = [kept, added];
  const paths = entries.map(({ path }) => path);
  assert.deepEqual(checkInheritedInventory(paths, entries, base, true), [
    `Added: ${added.path}`, `Removed: ${removed.path}`,
  ]);
});
