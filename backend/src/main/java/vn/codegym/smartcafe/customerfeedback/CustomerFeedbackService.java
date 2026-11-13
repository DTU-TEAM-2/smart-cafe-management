package vn.codegym.smartcafe.customerfeedback;

import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerFeedbackService {
    private final JdbcTemplate jdbc;

    public CustomerFeedbackService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public String submit(CustomerFeedbackController.Command command) {
        return insert(command);
    }

    @Transactional
    public String submitWithImages(CustomerFeedbackController.Command command, java.util.List<FirebaseFeedbackStorage.Uploaded> images) {
        String feedbackId = insert(command);
        for (int i = 0; i < images.size(); i++) {
            var image = images.get(i);
            jdbc.update("""
                    INSERT INTO media_assets(provider,provider_asset_id,secure_url,mime_type,size_bytes,width_px,height_px)
                    VALUES('FIREBASE',?,?,?,?,?,?)
                    """, image.name(), image.url(), image.mimeType(), image.size(), image.width(), image.height());
            long mediaId = jdbc.queryForObject("SELECT id FROM media_assets WHERE provider='FIREBASE' AND provider_asset_id=?", Long.class, image.name());
            jdbc.update("INSERT INTO feedback_images(feedback_id,media_id,sort_order) VALUES(?,?,?)", Long.parseLong(feedbackId), mediaId, i);
        }
        return feedbackId;
    }

    private String insert(CustomerFeedbackController.Command command) {
        String content = command.content().strip();
        // MySQL TEXT holds at most 65,535 bytes, including multibyte Vietnamese text.
        if (content.getBytes(StandardCharsets.UTF_8).length > 65_535) {
            throw new IllegalArgumentException("Nội dung phản hồi vượt quá dung lượng cho phép.");
        }
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO feedback(author_name, author_email, content, rating)
                    VALUES (?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, command.name().strip());
            statement.setString(2, command.email().strip());
            statement.setString(3, content);
            statement.setInt(4, command.rating().intValueExact());
            return statement;
        }, keys);
        Number id = keys.getKey();
        if (id == null) throw new IllegalStateException("Không lưu được phản hồi.");
        return id.toString();
    }
}
