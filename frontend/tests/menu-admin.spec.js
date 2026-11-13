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
