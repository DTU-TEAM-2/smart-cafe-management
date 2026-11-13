import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

test.beforeEach(async ({ page }) => {
  await page.goto('/preview/menu');
  await expect(page.getByRole('heading', { name: 'Quản lý món', exact: true })).toBeVisible();
});

test('search, filter and empty states use local fixtures without API calls', async ({ page }) => {
  const apiCalls = [];
  page.on('request', request => { if (request.url().includes('/api/')) apiCalls.push(request.url()); });
  await expect(page.getByRole('cell', { name: 'Caramel Latte', exact: true })).toBeVisible();
  await page.getByRole('searchbox').fill('bac xiu');
  await expect(page.getByRole('cell', { name: 'Bạc xỉu', exact: true })).toBeVisible();
  await expect(page.getByRole('cell', { name: 'Tiramisu', exact: true })).toHaveCount(0);
  await page.getByRole('searchbox').fill('khong co');
  await expect(page.getByRole('heading', { name: 'Không tìm thấy món' })).toBeVisible();
  await page.getByRole('button', { name: 'Xóa bộ lọc' }).click();
  await page.getByRole('combobox', { name: 'Lọc nhóm món' }).selectOption('TE');
  await expect(page.getByRole('cell', { name: 'Trà đào', exact: true })).toBeVisible();
  expect(apiCalls).toEqual([]);
});

test('create, reject duplicate, edit, cancel and confirm deletion, then reset on reload', async ({ page }) => {
  await page.getByRole('button', { name: 'THÊM MÓN MỚI', exact: true }).click();
  await page.getByLabel('Mã món *').fill('CF101');
  await page.getByLabel('Tên món *').fill('Mocha Latte');
  await page.getByLabel('Danh mục *').selectOption('CF');
  await page.getByLabel('Đơn giá (đ) *').fill('48000');
  await page.getByRole('button', { name: 'LƯU MÓN', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Mã đã tồn tại');
  await page.getByLabel('Mã món *').fill('CF104');
  await page.getByRole('button', { name: 'LƯU MÓN', exact: true }).click();
  await page.getByRole('button', { name: 'Sửa Mocha Latte', exact: true }).click();
  await expect(page.getByLabel('Mã món *')).toHaveAttribute('readonly', '');
  await page.getByLabel('Tên món *').fill('Mocha mới');
  await page.getByRole('button', { name: 'LƯU MÓN', exact: true }).click();
  await page.getByRole('button', { name: 'Xóa Mocha mới', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Xóa Mocha mới', exact: true })).toBeFocused();
  await page.getByRole('button', { name: 'Xóa Mocha mới', exact: true }).click();
  await page.getByRole('button', { name: 'XÁC NHẬN XÓA' }).click();
  await expect(page.getByRole('cell', { name: 'Mocha mới', exact: true })).toHaveCount(0);
  await page.reload();
  await expect(page.getByRole('row')).toHaveCount(5);
});

test('category creation, rename propagation and cancel deletion of a populated category', async ({ page }, testInfo) => {
  await page.getByRole('button', { name: 'Nhóm món', exact: true }).click();
  await page.getByRole('button', { name: 'Xóa Cà phê', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('2 món đang hoạt động thuộc nhóm cũng sẽ bị ẩn');
  await page.getByRole('button', { name: 'HỦY', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Xóa Cà phê', exact: true })).toBeFocused();
  await page.getByRole('button', { name: 'Sửa Cà phê', exact: true }).click();
  await page.screenshot({ path: testInfo.outputPath('menu-category-editor.png'), fullPage: true, scale: 'css' });
  await page.getByLabel('Tên nhóm món *').fill('Coffee');
  await page.getByRole('button', { name: 'LƯU NHÓM MÓN' }).click();
  await page.getByRole('button', { name: 'Món', exact: true }).click();
  await expect(page.getByRole('cell', { name: 'Coffee', exact: true })).toHaveCount(2);
  await page.getByRole('button', { name: 'Nhóm món', exact: true }).click();
  await page.getByRole('button', { name: 'THÊM NHÓM MÓN MỚI', exact: true }).click();
  await page.getByLabel('Mã nhóm món *').fill('JU');
  await page.getByLabel('Tên nhóm món *').fill('Nước ép');
  await page.getByRole('button', { name: 'LƯU NHÓM MÓN' }).click();
  await page.getByRole('button', { name: 'Xóa Nước ép', exact: true }).click();
  await page.getByRole('button', { name: 'XÁC NHẬN XÓA' }).click();
  await expect(page.getByRole('cell', { name: 'Nước ép', exact: true })).toHaveCount(0);
});

test('image validation and local preview', async ({ page }) => {
  await page.getByRole('button', { name: 'THÊM MÓN MỚI', exact: true }).click();
  await page.getByLabel('Ảnh sản phẩm', { exact: true }).setInputFiles({ name: 'bad.txt', mimeType: 'text/plain', buffer: Buffer.from('bad') });
  await expect(page.getByRole('alert')).toContainText('Chọn ảnh JPG');
  const png = await page.evaluate(() => { const canvas = document.createElement('canvas'); canvas.width = 2; canvas.height = 2; return canvas.toDataURL('image/png').split(',')[1]; });
  await page.getByLabel('Ảnh sản phẩm', { exact: true }).setInputFiles({ name: 'pixel.png', mimeType: 'image/png', buffer: Buffer.from(png, 'base64') });
  await expect(page.getByAltText('Xem trước ảnh món')).toBeVisible();
  await page.getByRole('button', { name: 'Bỏ ảnh' }).click();
  await expect(page.getByAltText('Xem trước ảnh món')).toHaveCount(0);
});

test('pagination remains valid when the last row on the last page is deleted', async ({ page }) => {
  await page.getByRole('button', { name: 'Nhóm món', exact: true }).click();
  for (let index = 0; index < 8; index += 1) {
    await page.getByRole('button', { name: 'THÊM NHÓM MÓN MỚI', exact: true }).click();
    await page.getByLabel('Mã nhóm món *').fill(`TEST${index}`);
    await page.getByLabel('Tên nhóm món *').fill(`Nhóm thử ${index}`);
    await page.getByRole('button', { name: 'LƯU NHÓM MÓN' }).click();
  }
  await page.getByRole('button', { name: 'Trang sau' }).click();
  await expect(page.getByRole('button', { name: 'Trang sau' })).toBeDisabled();
  await page.getByRole('button', { name: 'Xóa Nhóm thử 7', exact: true }).click();
  await page.getByRole('button', { name: 'XÁC NHẬN XÓA' }).click();
  await expect(page.getByRole('row')).toHaveCount(11);
  await expect(page.getByRole('button', { name: 'Trang trước' })).toBeDisabled();
});

test('keyboard, accessibility and responsive containment', async ({ page }, testInfo) => {
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({ path: testInfo.outputPath('menu-list.png'), fullPage: true, scale: 'css' });
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.getByRole('button', { name: 'Nhóm món', exact: true }).click();
  await page.screenshot({ path: testInfo.outputPath('menu-categories.png'), fullPage: true, scale: 'css' });
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await page.getByRole('button', { name: 'Món', exact: true }).click();
  await page.getByRole('button', { name: 'THÊM MÓN MỚI', exact: true }).click();
  await expect(page.getByLabel('Mã món *')).toBeFocused();
  await page.screenshot({ path: testInfo.outputPath('menu-editor.png'), fullPage: true, scale: 'css' });
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await page.getByRole('button', { name: 'HỦY', exact: true }).click();
  await page.getByRole('button', { name: 'Sửa Caramel Latte', exact: true }).click();
  await page.screenshot({ path: testInfo.outputPath('menu-edit.png'), fullPage: true, scale: 'css' });
  await page.getByRole('button', { name: 'HỦY', exact: true }).click();
  await page.getByRole('button', { name: 'Xóa Caramel Latte', exact: true }).click();
  await expect(page.getByRole('button', { name: 'HỦY', exact: true })).toBeFocused();
  await page.screenshot({ path: testInfo.outputPath('menu-delete.png'), fullPage: true, scale: 'css' });
  await page.keyboard.press('Shift+Tab');
  await expect(page.getByRole('button', { name: 'XÁC NHẬN XÓA' })).toBeFocused();
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
});
