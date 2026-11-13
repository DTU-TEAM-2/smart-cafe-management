import { test, expect } from '@playwright/test';

test.beforeEach(async ({ page }) => {
  await page.goto('/preview/menu');
  await expect(page.getByRole('heading', { name: 'Quản lý món', exact: true })).toBeVisible();
});

async function newItem(page, code = 'CF104', name = 'Mocha Latte', price = '48000') {
  await page.getByRole('button', { name: 'THÊM MÓN MỚI', exact: true }).click();
  await page.getByLabel('Mã món *').fill(code);
  await page.getByLabel('Tên món *').fill(name);
  await page.getByLabel('Danh mục *').selectOption('CF');
  await page.getByLabel('Đơn giá (đ) *').fill(price);
}

async function png(page, color = 'red') {
  const content = await page.evaluate(color => {
    const canvas = document.createElement('canvas'); canvas.width = 2; canvas.height = 2;
    const context = canvas.getContext('2d'); context.fillStyle = color; context.fillRect(0, 0, 2, 2);
    return canvas.toDataURL('image/png').split(',')[1];
  }, color);
  return { name: `${color}.png`, mimeType: 'image/png', buffer: Buffer.from(content, 'base64') };
}

test('search covers four criteria, empty keyword, invalid price and show all resets filters', async ({ page }) => {
  await page.getByRole('button', { name: 'TÌM KIẾM', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Vui lòng nhập từ khóa');
  const field = page.getByRole('combobox', { name: 'Tiêu chí tìm kiếm' });
  const keyword = page.getByRole('searchbox');
  await field.selectOption('code'); await keyword.fill('cf101');
  await expect(page.getByRole('cell', { name: 'Caramel Latte', exact: true })).toBeVisible();
  await keyword.fill('CF10');
  await expect(page.getByRole('heading', { name: 'Không tìm thấy món' })).toBeVisible();
  await field.selectOption('name'); await keyword.fill('bac xiu');
  await expect(page.getByRole('cell', { name: 'Bạc xỉu', exact: true })).toBeVisible();
  await field.selectOption('price'); await keyword.fill('45000');
  await expect(page.locator('tbody tr')).toHaveCount(1);
  await expect(page.getByRole('cell', { name: 'Caramel Latte', exact: true })).toBeVisible();
  await keyword.fill('45000.5');
  await page.getByRole('button', { name: 'TÌM KIẾM', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Giá tìm kiếm' })).toBeVisible();
  await field.selectOption('category'); await keyword.fill('ca phe');
  await expect(page.locator('tbody tr')).toHaveCount(2);
  await page.getByRole('combobox', { name: 'Lọc nhóm món' }).selectOption('TE');
  await expect(page.locator('tbody tr')).toHaveCount(0);
  await page.getByRole('button', { name: 'HIỂN THỊ TẤT CẢ' }).click();
  await expect(keyword).toHaveValue('');
  await expect(page.getByRole('combobox', { name: 'Lọc nhóm món' })).toHaveValue('');
  await expect(page.locator('tbody tr')).toHaveCount(4);
});

test('selection actions notify when empty and cannot act on a row hidden by filtering', async ({ page }) => {
  await page.getByRole('button', { name: 'SỬA ĐÃ CHỌN' }).click();
  await expect(page.getByRole('status')).toContainText('Vui lòng chọn món trước khi sửa');
  await page.getByRole('button', { name: 'XÓA ĐÃ CHỌN' }).click();
  await expect(page.getByRole('status')).toContainText('Vui lòng chọn món trước khi xóa');
  await page.getByRole('radio', { name: 'Chọn Caramel Latte', exact: true }).check();
  await page.getByRole('button', { name: 'SỬA ĐÃ CHỌN' }).click();
  await expect(page.getByLabel('Mã món *')).toHaveValue('CF101');
  await expect(page.getByLabel('Danh mục *')).toBeDisabled();
  await page.getByRole('button', { name: 'HỦY', exact: true }).click();
  await page.getByRole('searchbox').fill('Tiramisu');
  await page.getByRole('button', { name: 'XÓA ĐÃ CHỌN' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByRole('status')).toContainText('Vui lòng chọn món');
});

test('populated category deletion cancels without changes then hides all children and reserves code', async ({ page }, testInfo) => {
  await page.getByRole('button', { name: 'Nhóm món', exact: true }).click();
  await page.getByRole('button', { name: 'Xóa Cà phê', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('2 món đang hoạt động');
  await page.screenshot({ path: testInfo.outputPath('menu-category-delete.png'), fullPage: true, scale: 'css' });
  await page.getByRole('button', { name: 'HỦY', exact: true }).click();
  await page.getByRole('button', { name: 'Món', exact: true }).click();
  await expect(page.locator('tbody tr')).toHaveCount(4);
  await page.getByRole('button', { name: 'Nhóm món', exact: true }).click();
  await page.getByRole('button', { name: 'Xóa Cà phê', exact: true }).click();
  await page.getByRole('button', { name: 'XÁC NHẬN XÓA' }).click();
  await expect(page.getByRole('cell', { name: 'Cà phê', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'THÊM NHÓM MÓN MỚI', exact: true }).click();
  await page.getByLabel('Mã nhóm món *').fill(' cf ');
  await page.getByLabel('Tên nhóm món *').fill('Nhóm thay thế');
  await page.getByRole('button', { name: 'LƯU NHÓM MÓN' }).click();
  await expect(page.getByRole('alert')).toContainText('Mã đã tồn tại hoặc đã bị xóa');
  await page.getByRole('button', { name: 'HỦY', exact: true }).click();
  await page.getByRole('button', { name: 'Món', exact: true }).click();
  await expect(page.locator('tbody tr')).toHaveCount(2);
  await page.getByRole('button', { name: 'THÊM MÓN MỚI', exact: true }).click();
  await expect(page.getByLabel('Danh mục *').getByRole('option', { name: 'Cà phê', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'HỦY', exact: true }).click();
  await page.reload();
  await expect(page.locator('tbody tr')).toHaveCount(4);
});

test('validation rejects missing and malformed values and retains exact 18 digit VND', async ({ page }) => {
  await page.getByRole('button', { name: 'THÊM MÓN MỚI', exact: true }).click();
  await page.getByRole('button', { name: 'LƯU MÓN', exact: true }).click();
  await expect(page.getByLabel('Mã món *')).toHaveAttribute('aria-invalid', 'true');
  await expect(page.getByLabel('Mã món *')).toBeFocused();
  await page.getByLabel('Mã món *').fill('cf04');
  await page.getByLabel('Tên món *').fill('     a');
  await page.getByLabel('Danh mục *').selectOption('CF');
  await page.getByLabel('Đơn giá (đ) *').fill('45000');
  await page.getByRole('button', { name: 'LƯU MÓN', exact: true }).click();
  await expect(page.getByLabel('Tên món *')).toHaveAttribute('aria-invalid', 'true');
  await page.getByLabel('Tên món *').fill('Caramel Latte');
  for (const price of ['5000', '5000.5', '1e5', '1000000000000000000']) {
    await page.getByLabel('Đơn giá (đ) *').fill(price);
    await page.getByRole('button', { name: 'LƯU MÓN', exact: true }).click();
    await expect(page.getByLabel('Đơn giá (đ) *')).toHaveAttribute('aria-invalid', 'true');
    await expect(page.getByLabel('Đơn giá (đ) *')).toBeFocused();
  }
  await page.getByLabel('Đơn giá (đ) *').fill('999999999999999999');
  await page.getByRole('button', { name: 'LƯU MÓN', exact: true }).click();
  await expect(page.getByRole('cell', { name: '999.999.999.999.999.999đ', exact: true })).toBeVisible();
  await page.getByRole('row').filter({ has: page.getByRole('rowheader', { name: 'CF04', exact: true }) }).getByRole('button', { name: 'Sửa Caramel Latte', exact: true }).click();
  await expect(page.getByLabel('Đơn giá (đ) *')).toHaveValue('999999999999999999');
});

test('invalid image contents cannot be saved and a valid local image survives editing', async ({ page }) => {
  await newItem(page);
  const fileInput = page.getByLabel('Ảnh sản phẩm', { exact: true });
  await fileInput.setInputFiles({ name: 'fake.png', mimeType: 'image/png', buffer: Buffer.from('not a PNG') });
  await expect(page.getByRole('alert')).toContainText('Không đọc được ảnh');
  await expect(page.getByRole('button', { name: 'LƯU MÓN', exact: true })).toBeDisabled();
  const image = await png(page);
  await fileInput.setInputFiles({ ...image, name: 'disguised.jpg', mimeType: 'image/jpeg' });
  await expect(page.getByRole('alert')).toContainText('Không đọc được ảnh');
  await fileInput.setInputFiles({ name: 'fake.webp', mimeType: 'image/webp', buffer: Buffer.from('WEBP') });
  await expect(page.getByRole('alert')).toContainText('Chọn ảnh JPG hoặc PNG');
  await fileInput.setInputFiles({ name: 'too-large.png', mimeType: 'image/png', buffer: Buffer.alloc(5 * 1024 * 1024 + 1) });
  await expect(page.getByRole('alert')).toContainText('tối đa 5 MB');
  const excessivePixels = await page.evaluate(() => {
    const canvas = document.createElement('canvas'); canvas.width = 5000; canvas.height = 4001;
    return canvas.toDataURL('image/png').split(',')[1];
  });
  await fileInput.setInputFiles({ name: 'too-many-pixels.png', mimeType: 'image/png', buffer: Buffer.from(excessivePixels, 'base64') });
  await expect(page.getByRole('alert')).toContainText('vượt 20 triệu pixel');
  await fileInput.setInputFiles(image);
  await expect(page.getByAltText('Xem trước ảnh món')).toBeVisible();
  await page.getByRole('button', { name: 'LƯU MÓN', exact: true }).click();
  await page.getByRole('button', { name: 'Sửa Mocha Latte', exact: true }).click();
  await expect(page.getByAltText('Xem trước ảnh món')).toHaveAttribute('src', `data:image/png;base64,${image.buffer.toString('base64')}`);
});

test('a slower previous image read cannot overwrite the latest selected file', async ({ page }) => {
  await newItem(page);
  await page.evaluate(() => {
    const decode = window.createImageBitmap.bind(window);
    window.pendingImageReads = [];
    window.createImageBitmap = (...args) => decode(...args).then(bitmap => new Promise(resolve => { window.pendingImageReads.push(() => resolve(bitmap)); }));
  });
  const fileInput = page.getByLabel('Ảnh sản phẩm', { exact: true });
  await fileInput.setInputFiles(await png(page, 'red'));
  await expect.poll(() => page.evaluate(() => window.pendingImageReads.length)).toBe(1);
  const latest = await png(page, 'blue');
  await fileInput.setInputFiles(latest);
  await expect.poll(() => page.evaluate(() => window.pendingImageReads.length)).toBe(2);
  await page.evaluate(() => window.pendingImageReads[1]());
  const expected = `data:image/png;base64,${latest.buffer.toString('base64')}`;
  await expect(page.getByAltText('Xem trước ảnh món')).toHaveAttribute('src', expected);
  await page.evaluate(() => window.pendingImageReads[0]());
  await expect(page.getByAltText('Xem trước ảnh món')).toHaveAttribute('src', expected);
  await page.getByRole('button', { name: 'Bỏ ảnh', exact: true }).click();
  await expect(page.getByAltText('Xem trước ảnh món')).toHaveCount(0);
});
