package vn.codegym.smartcafe.menu;

import java.sql.Statement;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MenuMedia {
    private final JdbcTemplate jdbc;
    MenuMedia(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Long resolve(Long mediaId) {
        if (mediaId == null) return null;
        if (jdbc.queryForList("SELECT id FROM media_assets WHERE id=? AND deleted_at IS NULL FOR SHARE", Long.class, mediaId).isEmpty()) {
            throw new MenuException(HttpStatus.CONFLICT, "MEDIA_UNAVAILABLE", "Ảnh không tồn tại hoặc đã được xóa.");
        }
        return mediaId;
    }

    @Transactional
    Long register(String assetId, String url, String mimeType, long size, int width, int height) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO media_assets(provider, provider_asset_id, secure_url, mime_type, size_bytes, width_px, height_px)
                    VALUES ('CLOUDINARY', ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, assetId); statement.setString(2, url); statement.setString(3, mimeType);
            statement.setLong(4, size); statement.setInt(5, width); statement.setInt(6, height);
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new MenuException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_NOT_SAVED", "Không lưu được thông tin ảnh.");
        return key.longValue();
    }
}
