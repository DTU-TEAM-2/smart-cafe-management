import test from "node:test";
import assert from "node:assert/strict";
import { routeSkills } from "./skills.mjs";

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
});
