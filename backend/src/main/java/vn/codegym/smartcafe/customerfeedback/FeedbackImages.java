package vn.codegym.smartcafe.customerfeedback;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
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
                    String format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                    if (!List.of("jpeg", "png").contains(format)) throw new IllegalArgumentException("Chỉ hỗ trợ ảnh JPEG và PNG.");
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width < 1 || height < 1 || width > 4096 || height > 4096) throw new IllegalArgumentException("Kích thước ảnh tối đa 4096 × 4096 pixel.");
                    if ((long) width * height > MAX_PIXELS) {
                        throw new IllegalArgumentException("Mỗi ảnh không được vượt quá 4 triệu điểm ảnh.");
                    }
                    if (reader.read(0) == null) throw new IllegalArgumentException("Ảnh không đọc được.");
                    return new Image(bytes, "image/" + format, format.equals("jpeg") ? "jpg" : "png", width, height);
                } finally { reader.dispose(); }
            }
        } catch (IOException error) { throw new IllegalArgumentException("Ảnh không đọc được."); }
    }
}
