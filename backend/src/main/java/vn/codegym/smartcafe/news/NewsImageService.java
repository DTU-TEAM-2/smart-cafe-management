package vn.codegym.smartcafe.news;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
class NewsImageService {
    static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final Logger LOG = LoggerFactory.getLogger(NewsImageService.class);
    private final JdbcClient jdbc;
    private final NewsImageStorage storage;
    private final NewsReaderAccess readers;

    NewsImageService(JdbcClient jdbc, NewsImageStorage storage, NewsReaderAccess readers) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.readers = readers;
    }

    @Transactional
    public UploadedImage upload(MultipartFile file) {
        var image = validateImage(file);
        var stored = storage.upload(image.bytes(), image.mimeType());
        // A failed INSERT or failed commit must not leave the uploaded object orphaned.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try { storage.delete(stored.assetId()); }
                    catch (RuntimeException failure) {
                        LOG.warn("Could not remove uncommitted news image asset {}", stored.assetId());
                    }
                } else if (status == STATUS_UNKNOWN) {
                    LOG.warn("Unknown transaction outcome for news image {}; retain object for reconciliation", stored.assetId());
                }
            }
        });
        var key = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO media_assets(provider,provider_asset_id,secure_url,mime_type,size_bytes,
                    width_px,height_px,created_at)
                VALUES ('FIREBASE',?,?,?,?,?,?,UTC_TIMESTAMP(6))
                """).param(stored.assetId()).param(stored.secureUrl()).param(image.mimeType())
                .param(image.bytes().length).param(image.width()).param(image.height()).update(key, "id");
        if (key.getKey() == null) throw new IllegalStateException("Missing generated media ID");
        String id = key.getKey().toString();
        return new UploadedImage(id, "/api/v1/news/images/" + id, image.mimeType(), image.bytes().length,
                image.width(), image.height());
    }

    @Transactional(readOnly = true)
    public ImageContent read(long id, String username) {
        String access = readers.predicate(username);
        var image = jdbc.sql("""
                SELECT m.provider_asset_id,m.mime_type FROM media_assets m
                WHERE m.id=? AND %s
                """.formatted(NewsService.NEWS_MEDIA_PREDICATE) + (access.isEmpty() ? "" : """
                    AND EXISTS (SELECT 1 FROM news_images ni JOIN news_posts p ON p.id=ni.news_id
                        WHERE ni.media_id=m.id
                    """ + access + ")"))
                .param(id).query((row, index) -> new ImageMetadata(row.getString("provider_asset_id"),
                        row.getString("mime_type"))).optional().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh tin tức."));
        if (!"image/jpeg".equals(image.mimeType()) && !"image/png".equals(image.mimeType())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh tin tức.");
        }
        return new ImageContent(storage.read(image.assetId()), image.mimeType());
    }

    private ValidatedImage validateImage(MultipartFile file) {
        if (file.isEmpty()) throw new IllegalArgumentException("Ảnh không được để trống.");
        if (file.getSize() > MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                "Ảnh tối đa 5 MiB.");
        try (var input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Ảnh tối đa 5 MiB.");
            try (var imageInput = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(imageInput);
                if (!readers.hasNext()) throw unsupported();
                var reader = readers.next();
                try {
                    reader.setInput(imageInput);
                    String format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                    if (!format.equals("png") && !format.equals("jpeg")) throw unsupported();
                    String mimeType = "image/" + format;
                    if (!mimeType.equals(file.getContentType())) throw unsupported();
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width <= 0 || height <= 0 || width > 6000 || height > 6000 || (long) width * height > 12_000_000) {
                        throw new IllegalArgumentException("Ảnh tối đa 6000 px mỗi chiều và 12 triệu pixel.");
                    }
                    // Decode and re-encode; never store SVG/scripts, EXIF or caller-supplied filenames.
                    var decoded = reader.read(0);
                    if (format.equals("jpeg")) decoded = NewsJpegOrientation.apply(decoded, bytes);
                    var output = new ByteArrayOutputStream();
                    if (!ImageIO.write(decoded, format, output)) throw unsupported();
                    if (output.size() > MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Ảnh tối đa 5 MiB.");
                    return new ValidatedImage(output.toByteArray(), mimeType, decoded.getWidth(), decoded.getHeight());
                } finally { reader.dispose(); }
            }
        } catch (IOException failure) {
            throw new IllegalArgumentException("Không thể đọc nội dung ảnh.");
        }
    }

    private ResponseStatusException unsupported() {
        return new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Chỉ chấp nhận nội dung ảnh PNG hoặc JPEG hợp lệ.");
    }

    record UploadedImage(String mediaId, String url, String mimeType, long sizeBytes, int width, int height) {}
    record ImageContent(byte[] bytes, String mimeType) {}
    private record ImageMetadata(String assetId, String mimeType) {}
    private record ValidatedImage(byte[] bytes, String mimeType, int width, int height) {}
}
