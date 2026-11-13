import { expect, test } from '@playwright/test';
import { readdirSync, readFileSync } from 'node:fs';

test('tiny oversized PNG/JPEG and malformed headers are rejected before any decode', async ({ page }) => {
  await page.addInitScript(() => {
    window.imageDecodeCalls = 0;
    const decode = window.createImageBitmap.bind(window);
    window.createImageBitmap = (...args) => { window.imageDecodeCalls += 1; return decode(...args); };
  });
  await page.goto('/preview/menu');
  await page.getByRole('button', { name: 'THÊM MÓN MỚI', exact: true }).click();
  const png = Buffer.alloc(33);
  Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]).copy(png);
  png.writeUInt32BE(13, 8); png.write('IHDR', 12); png.writeUInt32BE(100000, 16); png.writeUInt32BE(100000, 20);
  const jpeg = Buffer.from([0xff, 0xd8, 0xff, 0xe1, 0x00, 0x04, 0, 0,
    0xff, 0xc0, 0, 11, 8, 0xff, 0xff, 0xff, 0xff, 1, 1, 0x11, 0, 0xff, 0xd9]);
  const cases = [
    { name: 'oversized-header.png', mimeType: 'image/png', buffer: png },
    { name: 'oversized-header.jpg', mimeType: 'image/jpeg', buffer: jpeg },
    { name: 'truncated.png', mimeType: 'image/png', buffer: png.subarray(0, 23) },
    { name: 'truncated.jpg', mimeType: 'image/jpeg', buffer: Buffer.from([0xff, 0xd8, 0xff, 0xe1, 0xff, 0xff]) },
    { name: 'zero-segment.jpg', mimeType: 'image/jpeg', buffer: Buffer.from([0xff, 0xd8, 0xff, 0xe1, 0, 0]) },
  ];
  for (const file of cases) {
    await page.getByLabel('Ảnh sản phẩm', { exact: true }).setInputFiles(file);
    await expect(page.getByRole('alert')).toContainText('Không đọc được ảnh');
    await expect(page.getByRole('button', { name: 'LƯU MÓN', exact: true })).toBeDisabled();
    expect(await page.evaluate(() => window.imageDecodeCalls)).toBe(0);
  }
  const compressed = await page.evaluate(() => {
    const canvas = document.createElement('canvas'); canvas.width = 5000; canvas.height = 4001;
    return canvas.toDataURL('image/png').split(',')[1];
  });
  const compressedImage = Buffer.from(compressed, 'base64');
  expect(compressedImage.length).toBeLessThan(5 * 1024 * 1024);
  await page.getByLabel('Ảnh sản phẩm', { exact: true }).setInputFiles({ name: 'compressed-20mp.png', mimeType: 'image/png', buffer: compressedImage });
  await expect(page.getByRole('alert')).toContainText('vượt 20 triệu pixel');
  expect(await page.evaluate(() => window.imageDecodeCalls)).toBe(0);
  // A real browser-encoded JPEG with APP metadata must still pass the header scan.
  const valid = await page.evaluate(() => {
    const canvas = document.createElement('canvas'); canvas.width = 2; canvas.height = 3;
    return canvas.toDataURL('image/jpeg').split(',')[1];
  });
  await page.getByLabel('Ảnh sản phẩm', { exact: true }).setInputFiles({ name: 'valid.jpg', mimeType: 'image/jpeg', buffer: Buffer.from(valid, 'base64') });
  await expect(page.getByAltText('Xem trước ảnh món')).toBeVisible();
  expect(await page.evaluate(() => window.imageDecodeCalls)).toBe(1);
});

test('production build neither exposes the menu preview route nor ships its fixtures', async ({ page }) => {
  await page.route('**/api/**', route => route.fulfill({ status: 403, json: {} }));
  await page.goto('http://127.0.0.1:15174/preview/menu');
  await expect(page.getByRole('banner')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Quản lý món', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'THÊM MÓN MỚI', exact: true })).toHaveCount(0);
  const assets = readdirSync('dist/assets');
  expect(assets.filter(name => name.includes('MenuManagement'))).toEqual([]);
  for (const name of assets.filter(name => name.endsWith('.js'))) {
    expect(readFileSync(`dist/assets/${name}`, 'utf8')).not.toContain('Bản xem thử giao diện');
    expect(readFileSync(`dist/assets/${name}`, 'utf8')).not.toContain('CF101');
  }
});
