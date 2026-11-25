import { afterEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { loadCategories, saveMenu } from '../src/menu/menuApi.js';

const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });
const json = data => new Response(JSON.stringify(data), { headers: { 'Content-Type': 'application/json' } });
const groups = (start, end) => Array.from({ length: end - start }, (_, index) => ({
  code: `C${String(start + index).padStart(3, '0')}`, name: 'Coffee', version: '0', itemCount: 0,
}));

test('category paging restarts after 101 groups shrink to 100 between pages', async () => {
  let calls = 0;
  globalThis.fetch = async () => {
    calls += 1;
    return json(calls === 1 ? { content: groups(0, 100), totalElements: 101 }
      : calls === 2 ? { content: [], totalElements: 100 } : { content: groups(1, 101), totalElements: 100 });
  };
  const result = await loadCategories('Bearer test');
  assert.equal(result.length, 100);
  assert.equal(result[0].code, 'C001');
  assert.equal(result.at(-1).code, 'C100');
  assert.equal(calls, 4);
});

test('same-count membership replacement is detected by two complete category passes', async () => {
  let pass = 0;
  globalThis.fetch = async url => {
    const page = Number(new URL(url, 'http://localhost').searchParams.get('page'));
    if (!page) pass += 1;
    const rows = pass === 1 ? groups(0, 101) : groups(1, 102);
    return json({ content: rows.slice(page * 100, (page + 1) * 100), totalElements: 101 });
  };
  const result = await loadCategories('Bearer test');
  assert.equal(result[0].code, 'C001');
  assert.equal(result.at(-1).code, 'C101');
  assert.equal(pass, 3);
});

test('continuously changing category pages stop after three bounded passes', async () => {
  let calls = 0;
  globalThis.fetch = async () => json({ content: [{ ...groups(0, 1)[0], version: String(++calls) }], totalElements: 1 });
  await assert.rejects(loadCategories('Bearer test'), /đang thay đổi/);
  assert.equal(calls, 3);
});

test('unknown upload is remembered for the same File and never retried implicitly', async () => {
  let uploads = 0;
  let writes = 0;
  globalThis.fetch = async (url, options) => {
    if (url.endsWith('/csrf')) return json({ headerName: 'X-CSRF-TOKEN', token: 'test' });
    if (url.endsWith('/images')) { uploads += 1; throw new TypeError('Response lost after provider commit'); }
    if (options.method === 'PUT') writes += 1;
    throw new Error('Unexpected request');
  };
  const file = new File(['test'], 'menu.png', { type: 'image/png' });
  const cache = new WeakMap();
  const value = { code: 'CF01', name: 'Coffee test', categoryCode: 'CF', price: '45000', available: true, version: '0', imageFile: file };
  for (let attempt = 0; attempt < 2; attempt += 1) {
    await assert.rejects(saveMenu('items', value, true, 'Bearer test', undefined, cache), error => error.pendingUpload === true);
  }
  assert.equal(uploads, 1);
  assert.equal(writes, 0);
});
