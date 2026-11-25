export function imageDimensions(bytes) {
  const view = new DataView(bytes);
  if (view.byteLength >= 24 && view.getUint32(0) === 0x89504e47 && view.getUint32(4) === 0x0d0a1a0a
    && view.getUint32(8) === 13 && view.getUint32(12) === 0x49484452) {
    return { width: view.getUint32(16), height: view.getUint32(20), type: "image/png" };
  }
  if (view.byteLength >= 4 && view.getUint16(0) === 0xffd8) {
    let offset = 2;
    while (offset < view.byteLength) {
      if (view.getUint8(offset++) !== 0xff) break;
      while (offset < view.byteLength && view.getUint8(offset) === 0xff) offset++;
      if (offset >= view.byteLength) break;
      const marker = view.getUint8(offset++);
      if (marker === 0xda || marker === 0xd9) break;
      if (marker === 0x01 || marker >= 0xd0 && marker <= 0xd7) continue;
      if (offset + 2 > view.byteLength) break;
      const length = view.getUint16(offset);
      if (length < 2 || offset + length > view.byteLength) break;
      if ([0xc0, 0xc1, 0xc2, 0xc3, 0xc5, 0xc6, 0xc7, 0xc9, 0xca, 0xcb, 0xcd, 0xce, 0xcf].includes(marker) && length >= 7) {
        return { width: view.getUint16(offset + 5), height: view.getUint16(offset + 3), type: "image/jpeg" };
      }
      offset += length;
    }
  }
  throw new Error("Ảnh không đọc được. Chỉ hỗ trợ JPEG và PNG hợp lệ.");
}

export async function validateFeedbackImage(file) {
  if (!["image/jpeg", "image/png"].includes(file.type)) throw new Error("Chỉ hỗ trợ ảnh JPEG và PNG.");
  if (!file.size || file.size > 5 * 1024 * 1024) throw new Error("Mỗi ảnh phải có dữ liệu và không quá 5 MB.");
  const { width, height, type } = imageDimensions(await file.arrayBuffer());
  if (type !== file.type) throw new Error("Định dạng ảnh không khớp nội dung tệp.");
  if (width < 1 || height < 1 || width > 4096 || height > 4096) throw new Error("Kích thước ảnh tối đa 4096 × 4096 pixel.");
  if (width * height > 4_000_000) throw new Error("Mỗi ảnh không được vượt quá 4 triệu điểm ảnh. Vui lòng giảm kích thước ảnh trước khi chọn.");
  // Inspect dimensions before allocating a raster, matching the backend budget.
  const url = URL.createObjectURL(file);
  try {
    await new Promise((resolve, reject) => {
      const image = new Image();
      image.onload = resolve;
      image.onerror = () => reject(new Error("Ảnh không đọc được."));
      image.src = url;
    });
  } finally { URL.revokeObjectURL(url); }
}
