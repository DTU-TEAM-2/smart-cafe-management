package vn.codegym.smartcafe.feedbackmanagement;

import java.net.URI;
import java.sql.Timestamp;
import java.time.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.codegym.smartcafe.customerfeedback.FirebaseFeedbackStorage;

@Service
public class FeedbackReadService {
    private static final ZoneId CAFE_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private final JdbcTemplate jdbc;
    private final FirebaseFeedbackStorage storage;

    public FeedbackReadService(JdbcTemplate jdbc, FirebaseFeedbackStorage storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    public record Summary(String id, String creator, String email, String createdAt, String date, String content, int rating) { }
    public record Image(String id, String url, String mimeType) { }
    public record Detail(String id, String creator, String email, String createdAt, String date, String content, int rating, List<Image> images) { }
    public record Page(List<Summary> content, long totalElements, int page, int size) { }
    public record ImageContent(String mimeType, byte[] bytes) { }

    @Transactional(readOnly = true)
    public Page list(LocalDate date, int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100 || !List.of("newest", "oldest").contains(sort)
                || date != null && (date.getYear() < 1000 || date.getYear() > 9998))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày, trang hoặc thứ tự sắp xếp không hợp lệ.");
        String where = date == null ? "" : " WHERE created_at>=? AND created_at<?";
        Object[] range = date == null ? new Object[0] : new Object[] {
                Timestamp.from(date.atStartOfDay(CAFE_ZONE).toInstant()),
                Timestamp.from(date.plusDays(1).atStartOfDay(CAFE_ZONE).toInstant()) };
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM feedback" + where, Long.class, range);
        var args = new java.util.ArrayList<Object>(List.of(range));
        args.add(size); args.add((long) page * size);
        String direction = sort.equals("oldest") ? " ASC" : " DESC";
        var rows = jdbc.query("SELECT id,author_name,author_email,created_at,LEFT(content,200) content,rating FROM feedback" + where
                        + " ORDER BY created_at" + direction + ",id" + direction + " LIMIT ? OFFSET ?",
                (r, n) -> {
                    Instant created = r.getTimestamp("created_at").toInstant();
                    return new Summary(r.getString("id"), r.getString("author_name"), r.getString("author_email"),
                            created.toString(), created.atZone(CAFE_ZONE).toLocalDate().toString(), r.getString("content"), r.getInt("rating"));
                }, args.toArray());
        return new Page(rows, total, page, size);
    }

    @Transactional(readOnly = true)
    public Detail detail(long id) {
        var rows = jdbc.query("SELECT id,author_name,author_email,created_at,content,rating FROM feedback WHERE id=?", (r, n) -> {
            Instant created = r.getTimestamp("created_at").toInstant();
            return new Summary(r.getString("id"), r.getString("author_name"), r.getString("author_email"),
                    created.toString(), created.atZone(CAFE_ZONE).toLocalDate().toString(), r.getString("content"), r.getInt("rating"));
        }, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phản hồi.");
        var row = rows.getFirst();
        var images = jdbc.query("""
                SELECT m.id,m.secure_url,m.mime_type FROM feedback_images f JOIN media_assets m ON m.id=f.media_id
                WHERE f.feedback_id=? AND m.deleted_at IS NULL ORDER BY f.sort_order,m.id
                """, (r, n) -> new Image(r.getString("id"), displayUrl(r.getString("secure_url")), r.getString("mime_type")), id);
        return new Detail(row.id(), row.creator(), row.email(), row.createdAt(), row.date(), row.content(), row.rating(), images);
    }

    static String displayUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getRawUserInfo() == null ? value : null;
        } catch (IllegalArgumentException | NullPointerException error) { return null; }
    }

    @Transactional(readOnly = true)
    public ImageContent imageContent(long feedbackId, long mediaId) {
        var feedbackRows = jdbc.query("SELECT id FROM feedback WHERE id=?", (r, n) -> r.getLong("id"), feedbackId);
        if (feedbackRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phản hồi.");
        }

        var mediaRows = jdbc.query("""
                SELECT m.id, m.provider, m.provider_asset_id, m.mime_type, m.size_bytes
                FROM feedback_images f
                JOIN media_assets m ON m.id = f.media_id
                WHERE f.feedback_id = ? AND f.media_id = ? AND m.deleted_at IS NULL
                """, (r, n) -> new AssetMeta(
                        r.getLong("id"),
                        r.getString("provider"),
                        r.getString("provider_asset_id"),
                        r.getString("mime_type"),
                        r.getLong("size_bytes")
                ), feedbackId, mediaId);

        if (mediaRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh hoặc ảnh đã bị xóa.");
        }

        var asset = mediaRows.getFirst();
        if (!"FIREBASE".equalsIgnoreCase(asset.provider())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nhà cung cấp lưu trữ ảnh không được hỗ trợ.");
        }

        String assetName = asset.providerAssetId();
        if (assetName == null || !assetName.startsWith("feedback/") || assetName.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Định danh ảnh không hợp lệ.");
        }

        byte[] bytes = storage.read(assetName);
        if (bytes == null || bytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dữ liệu ảnh trống hoặc không tồn tại.");
        }
        if (bytes.length > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Kích thước ảnh vượt quá giới hạn cho phép.");
        }

        String mimeType = asset.mimeType();
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        return new ImageContent(mimeType, bytes);
    }

    private record AssetMeta(long id, String provider, String providerAssetId, String mimeType, long sizeBytes) { }
}
