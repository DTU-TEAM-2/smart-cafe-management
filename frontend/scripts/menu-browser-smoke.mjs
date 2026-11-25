import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';
import { chromium, expect } from '@playwright/test';

const live = process.env.MENU_BROWSER_LIVE === 'true';
const backend = new URL(process.env.MENU_BACKEND_URL);
if (!['127.0.0.1', 'localhost'].includes(backend.hostname)) throw new Error('Only a local isolated test backend is allowed.');
const category = process.env.MENU_TEST_CATEGORY;
const code = process.env.MENU_TEST_ITEM;
if (!/^BG[A-Z0-9]+$/.test(category) || !/^BI[A-Z0-9]+$/.test(code)) throw new Error('Dedicated browser fixture codes required.');
const server = spawn(process.execPath, ['node_modules/vite/bin/vite.js', '--host', '127.0.0.1', '--port', '0'], {
  env: { ...process.env, VITE_API_PROXY_TARGET: backend.origin, NO_COLOR: '1' }, stdio: ['ignore', 'pipe', 'pipe'],
});
let browser;
try {
  const origin = await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error('Vite did not start')), 15000);
    server.stdout.on('data', data => {
      const match = data.toString().match(/http:\/\/127\.0\.0\.1:\d+/);
      if (match) { clearTimeout(timeout); resolve(match[0]); }
    });
    server.once('error', reject);
    server.once('exit', () => { clearTimeout(timeout); reject(new Error('Vite exited before startup')); });
  });
  browser = await chromium.launch();
  const page = await browser.newPage();
  const evidenceDirectory = resolve(process.env.MENU_EVIDENCE_DIR || '../backend/target/menu-evidence');
  await mkdir(evidenceDirectory, { recursive: true });
  async function capture(name) {
    for (const [device, viewport] of [['desktop', { width: 1440, height: 1000 }], ['mobile', { width: 390, height: 844 }]]) {
      await page.setViewportSize(viewport);
      await page.screenshot({ path: resolve(evidenceDirectory, `${name}-${device}.png`), fullPage: true });
    }
    await page.setViewportSize({ width: 1280, height: 900 });
  }
  const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', 'base64');
  // Only image delivery is stubbed in fixture mode; all application APIs reach Spring/MySQL.
  if (!live) await page.route('https://res.cloudinary.com/menu-test/**', route => route.fulfill({ contentType: 'image/png', body: png }));
  await page.goto(origin + '/#login');
  await page.getByLabel('Tên đăng nhập', { exact: true }).fill(process.env.MENU_TEST_USERNAME);
  await page.getByLabel('Mật khẩu', { exact: true }).fill(process.env.MENU_TEST_PASSWORD);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await page.getByRole('link', { name: 'Mở quản lý thực đơn' }).click();
  await page.getByRole('button', { name: 'Nhóm món', exact: true }).click();
  await page.getByRole('button', { name: 'THÊM NHÓM MÓN MỚI', exact: true }).click();
  await page.getByLabel('Mã nhóm món *').fill(category);
  await page.getByLabel('Tên nhóm món *').fill(category);
  await capture('01-category-editor');
  await page.getByRole('button', { name: 'LƯU NHÓM MÓN', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Đã lưu nhóm món.' })).toBeVisible();
  await capture('02-category-list');
  await page.getByRole('button', { name: 'Món', exact: true }).click();
  await page.getByRole('button', { name: 'THÊM MÓN MỚI', exact: true }).click();
  await page.getByLabel('Mã món *').fill(code);
  await page.getByLabel('Tên món *').fill(code + ' Coffee');
  await page.getByLabel('Danh mục *').selectOption(category);
  await page.getByLabel('Đơn giá (đ) *').fill('45000');
  async function chooseImage(color) {
    const data = await page.evaluate(color => {
      const canvas = document.createElement('canvas'); canvas.width = 2; canvas.height = 2;
      const context = canvas.getContext('2d'); context.fillStyle = color; context.fillRect(0, 0, 2, 2);
      return canvas.toDataURL('image/png').split(',')[1];
    }, color);
    await page.getByLabel('Ảnh sản phẩm', { exact: true }).setInputFiles({ name: 'menu.png', mimeType: 'image/png', buffer: Buffer.from(data, 'base64') });
    await expect(page.getByAltText('Xem trước ảnh món')).toBeVisible();
  }
  async function saveItem() {
    const saved = page.waitForResponse(response => /\/api\/v1\/menu\/items(?:\/[^/?]+)?$/.test(new URL(response.url()).pathname)
      && ['POST', 'PUT'].includes(response.request().method()));
    await page.getByRole('button', { name: 'LƯU MÓN', exact: true }).click();
    const response = await saved;
    expect(response.ok()).toBe(true);
    const item = await response.json();
    expect(item.imageMediaId).toBeTruthy();
    expect(item.imageUrl).toMatch(/^https:\/\/res\.cloudinary\.com\//);
    await expect(page.getByRole('status').filter({ hasText: 'Đã lưu món.' })).toBeVisible();
    return item;
  }
  await chooseImage('red');
  await capture('03-item-create');
  const created = await saveItem();
  await capture('04-item-list');
  async function searchCustomerMenu() {
    await page.goto(origin + '/#customer');
    const loaded = page.waitForResponse(response => {
      const url = new URL(response.url());
      return url.pathname === '/api/v1/customer/menu/items' && url.searchParams.get('keyword') === code;
    });
    await page.getByRole('searchbox').fill(code);
    const response = await loaded;
    expect(response.ok()).toBe(true);
    return response.json();
  }
  async function checkCustomerImage(url) {
    const menu = await searchCustomerMenu();
    expect(menu.content.find(item => item.code === code)?.imageUrl).toBe(url);
    const image = page.getByAltText(code + ' Coffee', { exact: true }).first();
    await expect(image).toHaveAttribute('src', url);
    await expect.poll(() => image.evaluate(element => element.complete && element.naturalWidth > 0)).toBe(true);
  }
  await checkCustomerImage(created.imageUrl);
  await page.goto(origin + '/#admin-menu');
  await page.reload();
  await page.getByRole('searchbox', { name: 'Tìm món' }).fill(code);
  await page.getByRole('button', { name: 'TÌM KIẾM', exact: true }).click();
  await page.getByRole('button', { name: 'Sửa ' + code + ' Coffee', exact: true }).click();
  await expect(page.getByLabel('Đơn giá (đ) *')).toHaveValue('45000');
  await expect(page.getByAltText('Xem trước ảnh món')).toHaveAttribute('src', created.imageUrl);
  await page.getByLabel('Đơn giá (đ) *').fill('48000');
  await chooseImage('blue');
  await capture('05-item-edit');
  const updated = await saveItem();
  expect(updated.imageMediaId).not.toBe(created.imageMediaId);
  if (!live) {
    // Let the real provider stub and MySQL commit, then lose only the HTTP response.
    await page.getByRole('button', { name: 'Sửa ' + code + ' Coffee', exact: true }).click();
    await chooseImage('green');
    let unknownUploads = 0;
    await page.route('**/api/v1/menu/images', async route => {
      unknownUploads += 1;
      const response = await route.fetch();
      expect(response.ok()).toBe(true);
      await route.abort('failed');
    });
    await page.getByRole('button', { name: 'LƯU MÓN', exact: true }).click();
    await expect(page.getByRole('alert')).toContainText('không gửi lại');
    await expect(page.getByRole('button', { name: 'LƯU MÓN', exact: true })).toBeDisabled();
    await page.getByRole('button', { name: 'TIẾP TỤC KHÔNG DÙNG ẢNH MỚI' }).click();
    const retained = await saveItem();
    expect(retained.imageMediaId).toBe(updated.imageMediaId);
    expect(unknownUploads).toBe(1);
    await page.unroute('**/api/v1/menu/images');
  }
  await checkCustomerImage(updated.imageUrl);
  await capture('06-customer-menu');
  await page.screenshot({ path: '../backend/target/menu-browser-customer.png', fullPage: true });
  await page.goto(origin + '/#admin-menu');
  await page.getByRole('button', { name: 'Nhóm món', exact: true }).click();
  await page.getByRole('searchbox', { name: 'Tìm nhóm món' }).fill(category);
  await page.getByRole('button', { name: 'TÌM KIẾM', exact: true }).click();
  await page.getByRole('button', { name: 'Xóa ' + category, exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('1 món');
  await capture('07-category-delete');
  await page.getByRole('button', { name: 'HỦY', exact: true }).click();
  await expect(page.getByRole('cell', { name: category, exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Xóa ' + category, exact: true }).click();
  await page.getByRole('button', { name: 'XÁC NHẬN XÓA', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Đã ẩn nhóm món' })).toBeVisible();
  const archivedMenu = await searchCustomerMenu();
  expect(archivedMenu.content.some(item => item.code === code)).toBe(false);
  await expect(page.getByAltText(code + ' Coffee', { exact: true })).toHaveCount(0);
  console.log('PASS browser CRUD + reload + image replacement + customer menu + category cancel/archive; provider=' + (live ? 'LIVE CLOUDINARY' : 'HTTP FIXTURE (not live Cloudinary)'));
} finally {
  await browser?.close();
  server.kill();
  await Promise.race([once(server, 'exit'), new Promise(resolve => setTimeout(resolve, 3000))]);
}
