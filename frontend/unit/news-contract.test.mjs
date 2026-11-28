import assert from "node:assert/strict";
import test from "node:test";
import { hasNewsVersion, isNewsDetail, isNewsPage } from "../src/news/newsApi.js";

const news = {
  id: "9007199254740993", title: "Tin từ LECKER", slug: "tin-tu-lecker", body: "Nội dung",
  status: "PUBLISHED", audiences: ["STAFF"], version: 4, images: [],
};
const image = { mediaId: "42", secureUrl: "/api/v1/news/images/42", sortOrder: 0 };

test("News pages must contain exactly the expected unique articles", () => {
  const size = 6;
  for (const totalElements of [0, 1, 5, 6, 7, 12, 13]) {
    const totalPages = Math.ceil(totalElements / size);
    for (let page = 0; page <= totalPages; page++) {
      const content = Array.from({ length: Math.max(0, Math.min(size, totalElements - page * size)) },
        (_, index) => ({ ...news, id: String(index + 1) }));
      const response = { content, page, size, totalElements, totalPages, last: page + 1 >= totalPages };
      assert.equal(isNewsPage(response, page, size), true);
      assert.equal(isNewsPage({ ...response, last: !response.last }, page, size), false);
      if (content.length) assert.equal(isNewsPage({ ...response, content: content.slice(1) }, page, size), false);
      if (content.length > 1) {
        assert.equal(isNewsPage({ ...response, content: [content[1], ...content.slice(1)] }, page, size), false);
      }
    }
  }
});

test("Admin expects a strong canonical ETag for the body version", () => {
  assert.equal(hasNewsVersion(news, '"4"'), true);
  for (const tag of ['"5"', '"04"', 'W/"4"', '"4", "5"', null]) assert.equal(hasNewsVersion(news, tag), false);
  assert.equal(hasNewsVersion({ ...news, version: 2 ** 53 }, '"9007199254740992"'), false);
});

test("News detail checks private media, explicit version and supported audiences", () => {
  assert.equal(isNewsDetail({ ...news, images: [image], audiences: [] }), true);
  assert.equal(isNewsDetail({ ...news, title: "\u00a0", body: "\u00a0" }), true);
  for (const value of [
    { version: undefined }, { audiences: ["STAFF", "STAFF"] }, { publishedAt: "không phải ngày" },
    { title: "\u3000" }, { body: "\u3000" }, { images: [{ mediaId: image.mediaId }] },
    { images: [image, { ...image, sortOrder: 1 }] }, { images: [{ ...image, sortOrder: -1 }] },
    ...["/api/v1/news/images/43", "https://example.invalid/a.jpg", "//example.invalid/a.jpg", "javascript:alert(1)", "data:image/png,abc"]
      .map(secureUrl => ({ images: [{ ...image, secureUrl }] })),
  ]) assert.equal(isNewsDetail({ ...news, ...value }), false, JSON.stringify(value));
});
