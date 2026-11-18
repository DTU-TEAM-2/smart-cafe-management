package vn.codegym.smartcafe.customerfeedback;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class FeedbackImages {
    private static final long MAX_PIXELS = 4_000_000;
    public record Image(byte[] bytes, String contentType, String extension, int width, int height) { }

    public List<Image> validate(List<MultipartFile> files) {
        if (files == null || files.isEmpty() || files.size() > 3) throw new IllegalArgumentException("Vui lòng chọn từ 1 đến 3 ảnh.");
        return files.stream().map(this::validate).toList();
    }

    private Image validate(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 5L * 1024 * 1024) throw new IllegalArgumentException("Mỗi ảnh phải có dữ liệu và không quá 5 MB.");
        try {
            byte[] bytes = file.getBytes();
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new IllegalArgumentException("Ảnh không hợp lệ. Chỉ hỗ trợ JPEG và PNG.");
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (!List.of("jpeg", "png").contains(format)) throw new IllegalArgumentException("Chỉ hỗ trợ ảnh JPEG và PNG.");
                    int rawWidth = reader.getWidth(0), rawHeight = reader.getHeight(0);
                    if (rawWidth < 1 || rawHeight < 1 || rawWidth > 4096 || rawHeight > 4096) {
                        throw new IllegalArgumentException("Kích thước ảnh tối đa 4096 × 4096 pixel.");
                    }
                    if ((long) rawWidth * rawHeight > MAX_PIXELS) {
                        throw new IllegalArgumentException("Mỗi ảnh không được vượt quá 4 triệu điểm ảnh.");
                    }
                    var raster = reader.read(0);
                    if (raster == null) throw new IllegalArgumentException("Ảnh không đọc được.");

                    int orientation = readOrientation(bytes, format);
                    BufferedImage cleanImage = raster;
                    if (orientation == 3 || orientation == 6 || orientation == 8) {
                        cleanImage = rotate(cleanImage, orientation);
                    }
                    int width = cleanImage.getWidth();
                    int height = cleanImage.getHeight();
                    if ((long) width * height > MAX_PIXELS) {
                        throw new IllegalArgumentException("Mỗi ảnh không được vượt quá 4 triệu điểm ảnh.");
                    }

                    if (format.equals("jpeg") && cleanImage.getTransparency() != java.awt.Transparency.OPAQUE) {
                        BufferedImage rgbImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                        var g = rgbImage.createGraphics();
                        try {
                            g.setColor(java.awt.Color.WHITE);
                            g.fillRect(0, 0, width, height);
                            g.drawImage(cleanImage, 0, 0, null);
                        } finally {
                            g.dispose();
                        }
                        cleanImage = rgbImage;
                    }

                    var out = new ByteArrayOutputStream();
                    boolean written = ImageIO.write(cleanImage, format.equals("jpeg") ? "jpeg" : "png", out);
                    if (!written) throw new IllegalArgumentException("Không thể xử lý định dạng ảnh.");
                    byte[] cleanBytes = out.toByteArray();
                    if (cleanBytes.length == 0 || cleanBytes.length > 5L * 1024 * 1024) {
                        throw new IllegalArgumentException("Kích thước ảnh sau xử lý vượt quá 5 MB.");
                    }
                    return new Image(cleanBytes, "image/" + format, format.equals("jpeg") ? "jpg" : "png", width, height);
                } finally { reader.dispose(); }
            }
        } catch (IOException error) { throw new IllegalArgumentException("Ảnh không đọc được."); }
    }

    private static BufferedImage rotate(BufferedImage src, int orientation) {
        int w = src.getWidth();
        int h = src.getHeight();
        int targetW = (orientation == 6 || orientation == 8) ? h : w;
        int targetH = (orientation == 6 || orientation == 8) ? w : h;
        int type = src.getType() == 0 ? BufferedImage.TYPE_INT_RGB : src.getType();
        BufferedImage dest = new BufferedImage(targetW, targetH, type);
        var g2d = dest.createGraphics();
        try {
            if (orientation == 6) {
                g2d.translate(targetW, 0);
                g2d.rotate(Math.PI / 2);
            } else if (orientation == 8) {
                g2d.translate(0, targetH);
                g2d.rotate(-Math.PI / 2);
            } else if (orientation == 3) {
                g2d.translate(targetW, targetH);
                g2d.rotate(Math.PI);
            }
            g2d.drawImage(src, 0, 0, null);
        } finally {
            g2d.dispose();
        }
        return dest;
    }

    private static int readOrientation(byte[] bytes, String format) {
        if (!"jpeg".equals(format) || bytes == null || bytes.length < 12) {
            return 1;
        }
        int offset = 0;
        if ((bytes[offset] & 0xFF) != 0xFF || (bytes[offset + 1] & 0xFF) != 0xD8) {
            return 1;
        }
        offset += 2;
        while (offset + 4 <= bytes.length) {
            if ((bytes[offset] & 0xFF) != 0xFF) {
                break;
            }
            int marker = bytes[offset + 1] & 0xFF;
            if (marker == 0xDA || marker == 0xD9) {
                break;
            }
            int length = ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
            if (length < 2 || offset + 2 + length > bytes.length) {
                break;
            }
            if (marker == 0xE1 && length >= 8) {
                int exifHeader = offset + 4;
                if (bytes[exifHeader] == 'E' && bytes[exifHeader + 1] == 'x' && bytes[exifHeader + 2] == 'i'
                        && bytes[exifHeader + 3] == 'f' && bytes[exifHeader + 4] == 0 && bytes[exifHeader + 5] == 0) {
                    return parseTiffOrientation(bytes, exifHeader + 6, length - 8);
                }
            }
            offset += 2 + length;
        }
        return 1;
    }

    private static int parseTiffOrientation(byte[] bytes, int tiffStart, int tiffLength) {
        if (tiffLength < 8 || tiffStart + tiffLength > bytes.length) {
            return 1;
        }
        boolean littleEndian;
        if (bytes[tiffStart] == 'I' && bytes[tiffStart + 1] == 'I') {
            littleEndian = true;
        } else if (bytes[tiffStart] == 'M' && bytes[tiffStart + 1] == 'M') {
            littleEndian = false;
        } else {
            return 1;
        }
        int magic = readShort(bytes, tiffStart + 2, littleEndian);
        if (magic != 0x2A) {
            return 1;
        }
        int ifdOffset = readInt(bytes, tiffStart + 4, littleEndian);
        if (ifdOffset < 8 || ifdOffset >= tiffLength) {
            return 1;
        }
        int dirStart = tiffStart + ifdOffset;
        if (dirStart + 2 > bytes.length) {
            return 1;
        }
        int numEntries = readShort(bytes, dirStart, littleEndian);
        int entryOffset = dirStart + 2;
        for (int i = 0; i < numEntries && entryOffset + 12 <= bytes.length && entryOffset + 12 <= tiffStart + tiffLength; i++) {
            int tag = readShort(bytes, entryOffset, littleEndian);
            if (tag == 0x0112) {
                int orientation = readShort(bytes, entryOffset + 8, littleEndian);
                if (orientation >= 1 && orientation <= 8) {
                    return orientation;
                }
                return 1;
            }
            entryOffset += 12;
        }
        return 1;
    }

    private static int readShort(byte[] bytes, int offset, boolean littleEndian) {
        int b0 = bytes[offset] & 0xFF;
        int b1 = bytes[offset + 1] & 0xFF;
        return littleEndian ? (b1 << 8) | b0 : (b0 << 8) | b1;
    }

    private static int readInt(byte[] bytes, int offset, boolean littleEndian) {
        int b0 = bytes[offset] & 0xFF;
        int b1 = bytes[offset + 1] & 0xFF;
        int b2 = bytes[offset + 2] & 0xFF;
        int b3 = bytes[offset + 3] & 0xFF;
        return littleEndian ? (b3 << 24) | (b2 << 16) | (b1 << 8) | b0 : (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }
}
