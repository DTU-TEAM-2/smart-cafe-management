import { expect, test } from '@playwright/test';

async function signIn(page, role) {
  const account = { id: '1', username: 'fixture', fullName: 'Fixture', role };
  await page.addInitScript(() => sessionStorage.setItem('smart_cafe_token', 'fixture-menu-token'));
  await page.route('**/api/v1/auth/me', route => route.fulfill({ json: account }));
  await page.route('**/api/v1/home/account', route => route.fulfill({ json: account }));
  await page.route('**/api/v1/home/notifications', route => route.fulfill({ json: { recipientId: '1', items: [] } }));
}

test('production route rejects staff before fetching menu data', async ({ page }) => {
  await signIn(page, 'STAFF');
  const menuRequests = [];
  page.on('request', request => {
    if (new URL(request.url()).pathname.startsWith('/api/v1/menu/')) menuRequests.push(request.url());
  });
  await page.goto('http://127.0.0.1:15174/#admin-menu');
  await expect(page.getByRole('alert')).toContainText('Chỉ quản trị viên');
  expect(menuRequests).toEqual([]);
});

test('production menu keeps exact prices and server versions across search and save', async ({ page }) => {
  await signIn(page, 'ADMIN');
  let saved = false;
  const code = 'CF01';
  const price = '999999999999999999';
  const item = () => `{"code":"CF01","name":"Cà phê sữa","categoryCode":"CF","categoryName":"Cà phê","price":${price},"available":true,"version":${saved ? 8 : 7}}`;
  await page.route('**/api/v1/menu/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    expect(request.headers().authorization).toBe('Bearer fixture-menu-token');
    const path = url.pathname;
    if (path.endsWith('/categories')) {
      return route.fulfill({ json: { content: [{ code: 'CF', name: 'Cà phê', itemCount: 1, version: 0 }], totalElements: 1 } });
    }
    if (path.endsWith('/items')) {
      const expected = url.searchParams.get('keyword');
      const visible = !expected || expected === 'Cà phê';
      if (expected) expect(url.searchParams.get('field')).toBe('name');
      return route.fulfill({ contentType: 'application/json', body: `{"content":[${visible ? item() : ''}],"totalElements":${visible ? 1 : 0}}` });
    }
    if (path.endsWith('/csrf')) return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'fixture-csrf' } });
    if (path.endsWith(`/items/${code}`)) {
      if (request.method() === 'PUT') {
        expect(request.headers()['x-csrf-token']).toBe('fixture-csrf');
        expect(request.postDataJSON()).toEqual({ code, name: 'Cà phê sữa', categoryCode: 'CF', price, available: true, imageMediaId: null, version: '7' });
        saved = true;
      }
      return route.fulfill({ contentType: 'application/json', body: item() });
    }
    return route.fulfill({ status: 404, json: {} });
  });

  await page.goto('http://127.0.0.1:15174/#admin');
  await page.getByRole('link', { name: 'Mở quản lý thực đơn' }).click();
  await expect(page.getByRole('cell', { name: '999.999.999.999.999.999đ' })).toBeVisible();
  await page.getByRole('searchbox', { name: 'Tìm món' }).fill('Cà phê');
  const search = page.waitForRequest(request => new URL(request.url()).searchParams.get('keyword') === 'Cà phê');
  await page.getByRole('button', { name: 'TÌM KIẾM', exact: true }).click();
  await search;
  await page.getByRole('button', { name: 'Sửa Cà phê sữa' }).click();
  await expect(page.getByLabel('Đơn giá (đ)', { exact: false })).toHaveValue(price);
  await page.getByRole('button', { name: 'LƯU MÓN', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Đã lưu món.' })).toBeVisible();
  expect(saved).toBe(true);
});

test('category confirmation preserves items on cancel and rejects a stale delete', async ({ page }) => {
  await signIn(page, 'ADMIN');
  let deletes = 0;
  const category = { code: 'CF', name: 'Cà phê', itemCount: 2, version: 4 };
  await page.route('**/api/v1/menu/**', route => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.pathname.endsWith('/items')) return route.fulfill({ json: { content: [], totalElements: 0 } });
    if (url.pathname.endsWith('/categories')) return route.fulfill({ json: { content: [category], totalElements: 1 } });
    if (url.pathname.endsWith('/csrf')) return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'fixture-csrf' } });
    if (request.method() === 'DELETE') {
      deletes += 1;
      expect(url.searchParams.get('version')).toBe('4');
      expect(request.headers()['x-csrf-token']).toBe('fixture-csrf');
      return route.fulfill({ status: 409, json: { code: 'STALE_MENU' } });
    }
    return route.fulfill({ json: category });
  });

  await page.goto('http://127.0.0.1:15174/#admin-menu');
  await page.getByRole('button', { name: 'Nhóm món', exact: true }).click();
  await page.getByRole('button', { name: 'Xóa Cà phê' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toContainText('2 món đang hoạt động');
  await dialog.getByRole('button', { name: 'HỦY', exact: true }).click();
  expect(deletes).toBe(0);
  await page.getByRole('button', { name: 'Xóa Cà phê' }).click();
  await dialog.getByRole('button', { name: 'XÁC NHẬN XÓA' }).click();
  await expect(dialog.getByRole('alert')).toContainText('Dữ liệu đã thay đổi');
  expect(deletes).toBe(1);
  await dialog.getByRole('button', { name: 'HỦY', exact: true }).click();
  await expect(page.getByRole('cell', { name: 'Cà phê', exact: true })).toBeVisible();
});

test('upload failure retains draft and a rejected save reuses the uploaded media on explicit retry', async ({ page }) => {
  await signIn(page, 'ADMIN');
  let uploads = 0;
  let writes = 0;
  await page.route('**/api/v1/menu/**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path.endsWith('/categories')) return route.fulfill({ json: { content: [{ code: 'CF', name: 'Coffee', version: 0 }], totalElements: 1 } });
    if (path.endsWith('/csrf')) return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'fixture-csrf' } });
    if (path.endsWith('/images')) {
      uploads += 1;
      expect(request.headers()['x-csrf-token']).toBe('fixture-csrf');
      expect(request.headers()['content-type']).toContain('multipart/form-data');
      return uploads === 1
        ? route.fulfill({ status: 503, json: { code: 'IMAGE_NOT_CONFIGURED', detail: 'Dịch vụ ảnh chưa được cấu hình.' } })
        : route.fulfill({ json: { mediaId: 42, imageUrl: 'https://res.cloudinary.com/menu-test/image/upload/new.png' } });
    }
    if (path.endsWith('/items') && request.method() === 'POST') {
      writes += 1;
      expect(request.postDataJSON()).toEqual({ code: 'TEST42', name: 'Coffee test', version: 0,
        categoryCode: 'CF', price: '45000', available: true, imageMediaId: '42' });
      if (writes === 1) return route.fulfill({ status: 409, json: { code: 'MENU_CONFLICT' } });
      return route.fulfill({ status: 201, json: {} });
    }
    return route.fulfill({ json: { content: [], totalElements: 0 } });
  });
  await page.goto('http://127.0.0.1:15174/#admin-menu');
  await page.getByRole('button', { name: 'THÊM MÓN MỚI', exact: true }).click();
  await page.getByLabel('Mã món *').fill('TEST42');
  await page.getByLabel('Tên món *').fill('Coffee test');
  await page.getByLabel('Danh mục *').selectOption('CF');
  await page.getByLabel('Đơn giá (đ) *').fill('45000');
  const image = await page.evaluate(() => {
    const canvas = document.createElement('canvas'); canvas.width = 2; canvas.height = 2;
    return canvas.toDataURL('image/png').split(',')[1];
  });
  await page.getByLabel('Ảnh sản phẩm', { exact: true }).setInputFiles({ name: 'menu.png', mimeType: 'image/png', buffer: Buffer.from(image, 'base64') });
  const save = page.getByRole('button', { name: 'LƯU MÓN', exact: true });
  await expect(save).toBeEnabled();
  await save.click();
  await expect(page.getByRole('alert')).toContainText('Dịch vụ ảnh');
  expect(writes).toBe(0);
  await expect(page.getByLabel('Tên món *')).toHaveValue('Coffee test');
  await save.click();
  await expect(page.getByRole('alert')).toContainText('Dữ liệu đã thay đổi');
  expect(uploads).toBe(2);
  await save.click();
  await expect(page.getByRole('status').filter({ hasText: 'Đã lưu món.' })).toBeVisible();
  expect(uploads).toBe(2);
  expect(writes).toBe(2);
});

async function menuFixture(page, handler) {
  await signIn(page, 'ADMIN');
  const item = { code: 'CF01', name: 'Coffee fixture', categoryCode: 'CF', categoryName: 'Coffee',
    price: '45000', available: true, imageMediaId: null, version: '7' };
  await page.route('**/api/v1/menu/**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (await handler?.(route, path, item)) return;
    if (path.endsWith('/csrf')) return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'fixture-csrf' } });
    if (path.endsWith('/categories')) return route.fulfill({ json: { content: [{ code: 'CF', name: 'Coffee', version: 0 }], totalElements: 1 } });
    if (path.endsWith('/items')) return route.fulfill({ json: { content: [item], totalElements: 1 } });
    return route.fulfill({ json: item });
  });
  await page.goto('http://127.0.0.1:15174/#admin-menu');
  await page.getByRole('button', { name: 'Sửa Coffee fixture' }).click();
}

test('menu mutation 401 expires the current session and clears the draft', async ({ page }) => {
  await menuFixture(page, async route => {
    if (route.request().method() !== 'PUT') return false;
    await route.fulfill({ status: 401, json: {} }); return true;
  });
  await page.getByRole('button', { name: 'LƯU MÓN', exact: true }).click();
  await expect(page.getByText('Chỉ quản trị viên đã đăng nhập mới có thể quản lý thực đơn.')).toBeVisible();
  await expect(page.getByLabel('Tên món *')).toHaveCount(0);
  expect(await page.evaluate(() => sessionStorage.getItem('smart_cafe_token'))).toBeNull();
});

test('menu upload 401 expires session while csrf 403 preserves it', async ({ page }) => {
  let forbidden = true;
  await menuFixture(page, async (route, path) => {
    if (path.endsWith('/csrf') && forbidden) {
      await route.fulfill({ status: 403, json: { code: 'CSRF_INVALID' } }); return true;
    }
    if (path.endsWith('/images')) { await route.fulfill({ status: 401, json: {} }); return true; }
    return false;
  });
  await page.getByRole('button', { name: 'LƯU MÓN', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('CSRF');
  expect(await page.evaluate(() => sessionStorage.getItem('smart_cafe_token'))).toBe('fixture-menu-token');
  forbidden = false;
  const image = await page.evaluate(() => {
    const canvas = document.createElement('canvas'); canvas.width = 2; canvas.height = 2;
    return canvas.toDataURL('image/png').split(',')[1];
  });
  await page.getByLabel('Ảnh sản phẩm', { exact: true }).setInputFiles({ name: 'menu.png', mimeType: 'image/png', buffer: Buffer.from(image, 'base64') });
  await page.getByRole('button', { name: 'LƯU MÓN', exact: true }).click();
  await expect(page.getByText('Chỉ quản trị viên đã đăng nhập mới có thể quản lý thực đơn.')).toBeVisible();
  expect(await page.evaluate(() => sessionStorage.getItem('smart_cafe_token'))).toBeNull();
});

test('a delayed old-session 401 cannot clear a replacement login', async ({ page }) => {
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  let started;
  const sent = new Promise(resolve => { started = resolve; });
  await menuFixture(page, async route => {
    if (route.request().method() !== 'PUT') return false;
    started(); await gate;
    await route.fulfill({ status: 401, json: {} }).catch(() => {}); return true;
  });
  await page.getByRole('button', { name: 'LƯU MÓN', exact: true }).click();
  await sent;
  await page.evaluate(() => {
    sessionStorage.setItem('smart_cafe_token', 'replacement-token');
    window.dispatchEvent(new CustomEvent('smart-cafe-auth-change'));
  });
  release();
  await expect(page.getByRole('button', { name: 'Sửa Coffee fixture' })).toBeVisible();
  expect(await page.evaluate(() => sessionStorage.getItem('smart_cafe_token'))).toBe('replacement-token');
});

for (const outcome of ['dropped', 'invalid-json', 'changed-by-another-admin']) {
  test(`uncertain save reconciles without a second write: ${outcome}`, async ({ page }) => {
    let writes = 0;
    await menuFixture(page, async (route, path, item) => {
      if (route.request().method() !== 'PUT') return false;
      writes += 1;
      Object.assign(item, route.request().postDataJSON(), { version: outcome === 'changed-by-another-admin' ? '9' : '8' });
      if (outcome === 'changed-by-another-admin') item.name = 'Other admin coffee';
      if (outcome === 'invalid-json') await route.fulfill({ status: 200, contentType: 'application/json', body: '{' });
      else await route.abort('failed');
      return true;
    });
    await page.getByRole('button', { name: 'LƯU MÓN', exact: true }).click();
    await expect(page.getByRole('alert')).toContainText('Chưa xác nhận được kết quả lưu');
    await expect(page.getByRole('button', { name: 'LƯU MÓN', exact: true })).toBeDisabled();
    await expect(page.getByLabel('Tên món *')).toBeDisabled();
    await page.getByRole('button', { name: 'KIỂM TRA KẾT QUẢ LƯU' }).click();
    if (outcome === 'changed-by-another-admin') {
      await expect(page.getByRole('alert')).toContainText('không tự ghi đè');
      await expect(page.getByRole('button', { name: 'LƯU MÓN', exact: true })).toBeDisabled();
    } else await expect(page.getByRole('status').filter({ hasText: 'đã được lưu trên máy chủ' })).toBeVisible();
    expect(writes).toBe(1);
  });
}

test('direct menu navigation preserves password-expiry warning and link', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('smart_cafe_user', JSON.stringify({ role: 'ADMIN',
    _passwordStatus: { mustChangePassword: true, passwordChangedAt: '2026-01-01T00:00:00Z', passwordExpiresAt: '2026-02-01T00:00:00Z' } })));
  await menuFixture(page);
  await expect(page.getByRole('complementary', { name: 'Cảnh báo mật khẩu' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Đổi mật khẩu' })).toHaveAttribute('href', '#account-password');
});

test('a create with dropped response is read back without repeating POST', async ({ page }) => {
  let saved;
  let writes = 0;
  let reads = 0;
  await menuFixture(page, async (route, path) => {
    if (route.request().method() === 'POST' && path.endsWith('/items')) {
      writes += 1;
      saved = { ...route.request().postDataJSON(), version: '0' };
      await route.abort('failed'); return true;
    }
    if (path.endsWith('/items/NEW1')) {
      reads += 1;
      if (reads === 1) await route.fulfill({ status: 503, json: {} });
      else await route.fulfill({ json: saved });
      return true;
    }
    return false;
  });
  await page.getByRole('button', { name: 'HỦY', exact: true }).click();
  await page.getByRole('button', { name: 'THÊM MÓN MỚI', exact: true }).click();
  await page.getByLabel('Mã món *').fill('NEW1');
  await page.getByLabel('Tên món *').fill('New coffee');
  await page.getByLabel('Danh mục *').selectOption('CF');
  await page.getByLabel('Đơn giá (đ) *').fill('45000');
  await page.getByRole('button', { name: 'LƯU MÓN', exact: true }).click();
  const check = page.getByRole('button', { name: 'KIỂM TRA KẾT QUẢ LƯU' });
  await check.click();
  await expect(page.getByRole('button', { name: 'LƯU MÓN', exact: true })).toBeDisabled();
  await expect(check).toBeEnabled();
  await check.click();
  await expect(page.getByRole('status').filter({ hasText: 'đã được lưu trên máy chủ' })).toBeVisible();
  expect(writes).toBe(1);
  expect(reads).toBe(2);
});

for (const [kind, outcome] of [['items', 'changed'], ['categories', 'changed'], ['items', 'missing'],
  ['items', 'unavailable'], ['items', 'unchecked']]) {
  test(`canceling an uncertain ${kind} save reloads both lists: ${outcome}`, async ({ page }) => {
    await signIn(page, 'ADMIN');
    const category = { code: 'CF', name: 'Coffee group', version: '4', itemCount: 1 };
    const item = { code: 'CF01', name: 'Coffee fixture', categoryCode: 'CF', categoryName: category.name,
      price: '45000', available: true, imageMediaId: null, version: '7' };
    const entity = kind === 'items' ? item : category;
    const reads = { items: 0, categories: 0 };
    let writes = 0;
    await page.route('**/api/v1/menu/**', async route => {
      const request = route.request();
      const path = new URL(request.url()).pathname;
      if (path.endsWith('/csrf')) return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'fixture-csrf' } });
      if (request.method() === 'PUT') {
        writes += 1;
        Object.assign(entity, request.postDataJSON(), { name: 'Latest server name', version: '10' });
        item.price = '51000';
        category.name = 'Latest server group';
        return route.abort('failed');
      }
      for (const listKind of ['items', 'categories']) {
        if (path.endsWith(`/${listKind}`)) {
          reads[listKind] += 1;
          const content = listKind === 'items' ? [item] : [category];
          return route.fulfill({ json: { content: writes && outcome === 'missing' && listKind === kind ? [] : content,
            totalElements: writes && outcome === 'missing' && listKind === kind ? 0 : 1 } });
        }
      }
      if (writes && outcome === 'missing') return route.fulfill({ status: 404, json: {} });
      if (writes && outcome === 'unavailable') return route.fulfill({ status: 503, json: {} });
      return route.fulfill({ json: path.includes('/categories/') ? category : item });
    });
    await page.goto('http://127.0.0.1:15174/#admin-menu');
    if (kind === 'categories') await page.getByRole('button', { name: 'Nhóm món', exact: true }).click();
    await page.getByRole('button', { name: `Sửa ${entity.name}` }).click();
    const before = { ...reads };
    await page.getByRole('button', { name: kind === 'items' ? 'LƯU MÓN' : 'LƯU NHÓM MÓN', exact: true }).click();
    await expect(page.getByRole('alert')).toContainText('Chưa xác nhận được kết quả lưu');
    if (outcome !== 'unchecked') {
      await page.getByRole('button', { name: 'KIỂM TRA KẾT QUẢ LƯU' }).click();
      await expect(page.getByRole('alert')).toContainText(outcome === 'missing' ? 'Chưa tìm thấy bản ghi'
        : outcome === 'unavailable' ? 'Không thể xử lý yêu cầu' : 'không tự ghi đè');
    }
    await page.getByRole('button', { name: 'HỦY', exact: true }).click();
    await expect.poll(() => reads.categories).toBeGreaterThan(before.categories);
    if (outcome === 'missing') await expect(page.getByRole('status').filter({ hasText: 'Không tìm thấy món' })).toBeVisible();
    else {
      await expect(page.getByRole('button', { name: `Sửa ${entity.name}` })).toBeVisible();
      if (kind === 'items') {
        await expect(page.getByRole('cell', { name: '51.000đ', exact: true })).toBeVisible();
        await expect(page.getByRole('combobox', { name: 'Lọc nhóm món' })).toContainText(category.name);
      } else {
        await page.getByRole('button', { name: 'Món', exact: true }).click();
        await expect(page.getByRole('button', { name: `Sửa ${item.name}` })).toBeVisible();
      }
    }
    await expect.poll(() => reads.items).toBeGreaterThan(before.items);
    expect(writes).toBe(1);
  });
}

for (const outcome of ['dropped', 'provider-error']) {
  test(`unknown image upload requires an explicit choice without re-upload: ${outcome}`, async ({ page }) => {
    let uploads = 0;
    let writes = 0;
    await menuFixture(page, async (route, path, item) => {
      if (path.endsWith('/images')) {
        uploads += 1;
        if (outcome === 'dropped') await route.abort('failed');
        else await route.fulfill({ status: 502, json: { code: 'IMAGE_PROVIDER_ERROR' } });
        return true;
      }
      if (route.request().method() === 'PUT') {
        writes += 1;
        expect(route.request().postDataJSON().imageMediaId).toBeNull();
        await route.fulfill({ json: { ...item, version: '8' } }); return true;
      }
      return false;
    });
    const image = await page.evaluate(() => {
      const canvas = document.createElement('canvas'); canvas.width = 2; canvas.height = 2;
      return canvas.toDataURL('image/png').split(',')[1];
    });
    await page.getByLabel('Ảnh sản phẩm', { exact: true }).setInputFiles({ name: 'menu.png', mimeType: 'image/png', buffer: Buffer.from(image, 'base64') });
    const save = page.getByRole('button', { name: 'LƯU MÓN', exact: true });
    await save.click();
    await expect(page.getByRole('alert')).toContainText('không gửi lại');
    await expect(save).toBeDisabled();
    expect(uploads).toBe(1);
    expect(writes).toBe(0);
    await expect(page.getByLabel('Tên món *')).toHaveValue('Coffee fixture');
    await page.getByRole('button', { name: 'TIẾP TỤC KHÔNG DÙNG ẢNH MỚI' }).click();
    await save.click();
    await expect(page.getByRole('status').filter({ hasText: 'Đã lưu món.' })).toBeVisible();
    expect(uploads).toBe(1);
    expect(writes).toBe(1);
  });
}

for (const kind of ['items', 'categories']) {
  for (const outcome of ['missing', 'changed', 'unchanged', 'unavailable', 'cancel']) {
    test(`unknown ${kind} delete reconciles without another DELETE: ${outcome}`, async ({ page }) => {
      await signIn(page, 'ADMIN');
      const row = kind === 'items' ? { code: 'CF01', name: 'Coffee fixture', categoryCode: 'CF', categoryName: 'Coffee', price: '45000', version: '4', available: true }
        : { code: 'CF', name: 'Coffee group', itemCount: 1, version: '4' };
      let deletes = 0;
      let lists = 0;
      await page.route('**/api/v1/menu/**', async route => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        if (path.endsWith('/csrf')) return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'test' } });
        if (request.method() === 'DELETE') { deletes += 1; return route.abort('failed'); }
        if (path.endsWith(`/${kind}`)) {
          lists += 1;
          return route.fulfill({ json: { content: deletes && ['missing', 'cancel'].includes(outcome) ? [] : [row], totalElements: deletes && ['missing', 'cancel'].includes(outcome) ? 0 : 1 } });
        }
        if (path.endsWith(`/${kind}/${row.code}`)) {
          if (deletes && outcome === 'missing') return route.fulfill({ status: 404, json: {} });
          if (deletes && outcome === 'unavailable') return route.fulfill({ status: 503, json: {} });
          return route.fulfill({ json: { ...row, version: deletes && outcome === 'changed' ? '5' : row.version } });
        }
        return route.fulfill({ json: { content: kind === 'items' ? [{ code: 'CF', name: 'Coffee', version: '0' }] : [], totalElements: kind === 'items' ? 1 : 0 } });
      });
      await page.goto('http://127.0.0.1:15174/#admin-menu');
      if (kind === 'categories') await page.getByRole('button', { name: 'Nhóm món', exact: true }).click();
      await page.getByRole('button', { name: `Xóa ${row.name}`, exact: true }).click();
      const before = lists;
      const dialog = page.getByRole('dialog');
      await dialog.getByRole('button', { name: 'XÁC NHẬN XÓA' }).click();
      await expect(dialog.getByRole('alert')).toContainText('Chưa xác nhận được kết quả xóa');
      await expect(dialog.getByRole('button', { name: 'XÁC NHẬN XÓA' })).toBeDisabled();
      if (outcome === 'cancel') await page.keyboard.press('Escape');
      else {
        await dialog.getByRole('button', { name: 'KIỂM TRA KẾT QUẢ XÓA' }).click();
        if (outcome !== 'missing') {
          await expect(dialog.getByRole('alert')).toContainText(outcome === 'changed' ? 'Dữ liệu đã thay đổi'
            : outcome === 'unavailable' ? 'Không thể xử lý yêu cầu' : 'vẫn đang hoạt động');
          await expect(dialog.getByRole('button', { name: 'XÁC NHẬN XÓA' })).toBeDisabled();
          await dialog.getByRole('button', { name: 'HỦY', exact: true }).click();
        }
      }
      await expect(dialog).toHaveCount(0);
      await expect.poll(() => lists).toBeGreaterThan(before);
      expect(deletes).toBe(1);
    });
  }
}

test('a group missing from options does not prevent editing its item from fresh detail', async ({ page }) => {
  let written = false;
  await menuFixture(page, async (route, path, item) => {
    if (path.endsWith('/categories')) {
      const content = [{ code: 'CF', name: 'Coffee', version: '0' }];
      await route.fulfill({ json: { content, totalElements: content.length } }); return true;
    }
    if (path.endsWith('/items/TE01')) {
      const tea = { ...item, code: 'TE01', name: 'New tea fixture', categoryCode: 'TE', categoryName: 'New tea group' };
      if (route.request().method() === 'PUT') {
        expect(route.request().postDataJSON().categoryCode).toBe('TE');
        written = true;
      }
      await route.fulfill({ json: tea }); return true;
    }
    if (path.endsWith('/items') && new URL(route.request().url()).searchParams.get('keyword') === 'TE01') {
      await route.fulfill({ json: { content: [{ ...item, code: 'TE01', name: 'New tea fixture', categoryCode: 'TE' }], totalElements: 1 } }); return true;
    }
    return false;
  });
  await page.getByRole('button', { name: 'HỦY', exact: true }).click();
  await page.getByRole('searchbox', { name: 'Tìm món' }).fill('TE01');
  await page.getByRole('button', { name: 'TÌM KIẾM', exact: true }).click();
  await page.getByRole('button', { name: 'Sửa New tea fixture' }).click();
  await expect(page.getByLabel('Danh mục *')).toHaveValue('TE');
  await page.getByRole('button', { name: 'LƯU MÓN', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Đã lưu món.' })).toBeVisible();
  expect(written).toBe(true);
});

test('opening create refreshes groups added by another admin after mount', async ({ page }) => {
  let groupAdded = false;
  await menuFixture(page, async (route, path) => {
    if (!path.endsWith('/categories')) return false;
    const content = [{ code: 'CF', name: 'Coffee', version: '0' }];
    if (groupAdded) content.push({ code: 'TE', name: 'New tea group', version: '0' });
    await route.fulfill({ json: { content, totalElements: content.length } }); return true;
  });
  await page.getByRole('button', { name: 'HỦY', exact: true }).click();
  groupAdded = true;
  await page.getByRole('button', { name: 'THÊM MÓN MỚI', exact: true }).click();
  await page.getByLabel('Danh mục *').selectOption('TE');
  await expect(page.getByLabel('Danh mục *')).toHaveValue('TE');
});
