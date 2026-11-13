const PNG_SIGNATURE = [137, 80, 78, 71, 13, 10, 26, 10];
const JPEG_FRAMES = new Set([0xc0, 0xc1, 0xc2, 0xc3, 0xc5, 0xc6, 0xc7, 0xc9, 0xca, 0xcb, 0xcd, 0xce, 0xcf]);

function invalid() { throw new Error('Invalid or unsupported image header'); }
function dimensions(width, height) {
  if (width < 1 || height < 1 || width * height > 20_000_000) invalid();
  return { width, height };
}

// Header-only preflight: never allocate a bitmap before enforcing the pixel limit.
// PNG IHDR and JPEG SOF describe raster dimensions, not EXIF thumbnail dimensions.
export function imageDimensions(buffer, mimeType) {
  const bytes = new Uint8Array(buffer);
  const view = new DataView(buffer);
  if (mimeType === 'image/png') {
    if (bytes.length < 33 || !PNG_SIGNATURE.every((byte, index) => bytes[index] === byte)
        || view.getUint32(8) !== 13 || view.getUint32(12) !== 0x49484452) invalid();
    return dimensions(view.getUint32(16), view.getUint32(20));
  }
  if (mimeType !== 'image/jpeg' || bytes.length < 4 || bytes[0] !== 0xff || bytes[1] !== 0xd8) invalid();
  let offset = 2;
  let frame;
  while (offset < bytes.length) {
    if (bytes[offset++] !== 0xff) invalid();
    while (offset < bytes.length && bytes[offset] === 0xff) offset += 1;
    if (offset >= bytes.length) invalid();
    const marker = bytes[offset++];
    if (marker === 0x00 || marker === 0xd8 || marker === 0xd9) invalid();
    if (marker === 0x01 || (marker >= 0xd0 && marker <= 0xd7)) continue;
    if (offset + 2 > bytes.length) invalid();
    const length = view.getUint16(offset);
    if (length < 2 || offset + length > bytes.length) invalid();
    if (JPEG_FRAMES.has(marker)) {
      if (frame || length < 8 || bytes[offset + 7] === 0 || length !== 8 + 3 * bytes[offset + 7]) invalid();
      frame = dimensions(view.getUint16(offset + 5), view.getUint16(offset + 3));
    }
    if (marker === 0xda) {
      if (!frame || length < 6) invalid();
      return frame;
    }
    offset += length;
  }
  invalid();
}
