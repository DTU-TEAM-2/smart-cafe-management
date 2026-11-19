import test from "node:test";
import assert from "node:assert/strict";
import { imageUrl } from "../src/feedback/feedbackApi.js";
test("Feedback images accept HTTPS metadata only without credentials", () => {
  for (const value of [null, "", "data:image/png;base64,abc", "javascript:alert(1)", "gs://private/image", "https://user:password@example.invalid/image", "http://example.invalid/image", "/local/path"])
    assert.equal(imageUrl(value), null);
  assert.equal(imageUrl("https://example.invalid/image.png"), "https://example.invalid/image.png");
});
