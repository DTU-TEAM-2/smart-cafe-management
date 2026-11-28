import { afterEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteMenu, loadCategories, readMenu, reconcileMenuSave, saveMenu } from '../src/menu/menuApi.js';
import { stripJavaText as stripMenuText } from '../src/javaText.js';

const originalFetch = globalThis.fetch;
const originalParse = JSON.parse;
afterEach(() => { globalThis.fetch = originalFetch; JSON.parse = originalParse; });
const json = data => new Response(JSON.stringify(data), { headers: { 'Content-Type': 'application/json' } });

test('menu normalization matches Java strip and preserves non-Java whitespace', async () => {
  const whitespace = '\t\n\v\f\r\u001c\u001d\u001e\u001f \u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2008\u2009\u200a\u2028\u2029\u205f\u3000';
  assert.equal(stripMenuText(`${whitespace}Coffee${whitespace}`), 'Coffee');
  for (const char of ['\u00a0', '\u2007', '\u202f', '\ufeff', '\u0085', '\u200b']) {
    assert.equal(stripMenuText(`${char}Coffee${char}`), `${char}Coffee${char}`);
  }
  assert.equal(stripMenuText(`Coffee\u001cfixture`), 'Coffee\u001cfixture');
  for (const uncertain of [false, true]) {
    let pending;
    let writes = 0;
    const expected = { code: 'CF', name: 'Coffee fixture', version: '0' };
    globalThis.fetch = async (url, options) => {
      if (url.endsWith('/csrf')) return json({ headerName: 'X-CSRF-TOKEN', token: 'test' });
      if (options.method === 'GET') { assert.ok(url.endsWith('/categories/CF')); return json(expected); }
      writes++;
      assert.deepEqual(JSON.parse(options.body), { ...expected, version: 0 });
      return new Response(JSON.stringify(uncertain ? {} : expected), { status: 201 });
    };
    const saved = saveMenu('categories', { code: '\u001ccf\u001c', name: '\u001cCoffee fixture\u001c' }, false, 'Bearer test');
    if (uncertain) {
      await assert.rejects(saved, error => { pending = error.pendingSave; return Boolean(pending); });
      assert.deepEqual(await reconcileMenuSave(pending, 'Bearer test'), expected);
    } else assert.deepEqual(await saved, expected);
    assert.equal(writes, 1);
  }
});
const groups = (start, end) => Array.from({ length: end - start }, (_, index) => ({
  code: `C${String(start + index).padStart(3, '0')}`, name: 'Coffee', version: '0', itemCount: 0,
}));

for (const editing of [false, true]) {
  for (const kind of ['categories', 'items']) {
    test(`${kind} ${editing ? 'update' : 'create'} validates success before accepting the write`, async () => {
      const value = { code: 'CF01', name: 'Coffee fixture', categoryCode: 'CF', price: '999999999999999999',
        available: false, imageMediaId: '9007199254740993', version: '9007199254740993' };
      const expected = kind === 'items' ? { ...value } : { code: value.code, name: value.name };
      expected.version = editing ? '9007199254740994' : '0';
      const bad = [{}, null, { ...expected, code: 'OTHER' }, { ...expected, version: '1' },
        { ...expected, name: 'Other name' }, { ...expected, version: 'invalid' }];
      if (kind === 'items') bad.push({ ...expected, categoryCode: 'OTHER' }, { ...expected, price: '999999999999999998' },
        { ...expected, imageMediaId: '9007199254740992' }, { ...expected, available: true });
      for (const field of Object.keys(expected)) {
        const missing = { ...expected };
        delete missing[field];
        bad.push(missing);
      }
      for (const body of [...bad, 'no-content', 'wrong-status', expected]) {
        let writes = 0;
        let reads = 0;
        globalThis.fetch = async (url, options) => {
          if (url.endsWith('/csrf')) return json({ headerName: 'X-CSRF-TOKEN', token: 'test' });
          if (options.method === 'GET') { reads++; return json(expected); }
          writes++;
          if (body === 'no-content') return new Response(null, { status: 204 });
          return new Response(JSON.stringify(body === 'wrong-status' ? expected : body),
            { status: body === 'wrong-status' ? (editing ? 201 : 200) : (editing ? 200 : 201) });
        };
        if (body === expected) assert.deepEqual(await saveMenu(kind, value, editing, 'Bearer test'), expected);
        else {
          let pending;
          await assert.rejects(saveMenu(kind, value, editing, 'Bearer test'), error => {
            pending = error.pendingSave;
            return error.uncertain && Boolean(pending);
          });
          assert.deepEqual(await reconcileMenuSave(pending, 'Bearer test'), expected);
          assert.equal(reads, 1);
        }
        assert.equal(writes, 1);
      }
    });
  }
}

test('DELETE accepts only the contracted 204 and marks other successes uncertain', async () => {
  for (const status of [200, 201, 204]) {
    let writes = 0;
    globalThis.fetch = async url => {
      if (url.endsWith('/csrf')) return json({ headerName: 'X-CSRF-TOKEN', token: 'test' });
      writes++;
      return new Response(status === 204 ? null : '{}', { status });
    };
    const result = deleteMenu('items', { code: 'CF01', version: '7' }, 'Bearer test');
    if (status === 204) assert.equal(await result, null);
    else await assert.rejects(result, error => error.uncertain === true);
    assert.equal(writes, 1);
  }
});

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

for (const code of ['RETRY_MENU', 'MENU_CONFLICT', 'UNRECOGNIZED_UPLOAD_ERROR']) {
  test(`upload 409 ${code} preserves partial side-effect state instead of retrying`, async () => {
    let uploads = 0;
    globalThis.fetch = async url => {
      if (url.endsWith('/csrf')) return json({ headerName: 'X-CSRF-TOKEN', token: 'test' });
      assert.ok(url.endsWith('/images'));
      uploads += 1;
      return new Response(JSON.stringify({ code }), { status: 409 });
    };
    const cache = new WeakMap();
    const value = { code: 'CF01', imageFile: new File(['test'], 'menu.png') };
    for (let attempt = 0; attempt < 2; attempt += 1) {
      await assert.rejects(saveMenu('items', value, true, 'Bearer test', undefined, cache), error => error.pendingUpload === true);
    }
    assert.equal(uploads, 1);
  });
}

test('a CSRF failure occurs before any upload and leaves the File retryable', async () => {
  let csrf = 0;
  let uploads = 0;
  const file = new File(['test'], 'menu.png');
  const cache = new WeakMap();
  globalThis.fetch = async url => {
    if (url.endsWith('/csrf')) {
      csrf += 1;
      return new Response(JSON.stringify({ code: 'DATABASE_UNAVAILABLE' }), { status: 503 });
    }
    uploads += 1;
    throw new Error('Unexpected upload');
  };
  for (let attempt = 0; attempt < 2; attempt += 1) {
    await assert.rejects(saveMenu('items', { imageFile: file }, true, 'Bearer test', undefined, cache), error => !error.pendingUpload);
  }
  assert.equal(csrf, 2);
  assert.equal(uploads, 0);
  assert.equal(cache.has(file), false);
});

test('without reviver source context, unsafe wire numbers fail instead of rounding', async () => {
  JSON.parse = (text, reviver) => originalParse(text, function (key, value) {
    return reviver ? reviver.call(this, key, value) : value;
  });
  globalThis.fetch = async () => new Response('{"price":999999999999999999,"version":0,"imageMediaId":null}');
  await assert.rejects(readMenu('items', 'CF01', 'Bearer test'), /không đọc được số nguyên chính xác/);
  globalThis.fetch = async () => new Response('{"price":45000,"version":9007199254740991,"imageMediaId":null}');
  assert.deepEqual(await readMenu('items', 'CF01', 'Bearer test'), { price: '45000', version: '9007199254740991', imageMediaId: null });
  globalThis.fetch = async () => new Response('{"price":"999999999999999999","version":"9223372036854775807","imageMediaId":null}');
  assert.deepEqual(await readMenu('items', 'CF01', 'Bearer test'), { price: '999999999999999999', version: '9223372036854775807', imageMediaId: null });
});
