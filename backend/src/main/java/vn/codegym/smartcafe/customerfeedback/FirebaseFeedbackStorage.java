package vn.codegym.smartcafe.customerfeedback;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.StorageClient;
import java.io.IOException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FirebaseFeedbackStorage {
    private final String bucketName;
    private final boolean enabled;
    private FirebaseApp app;
    public FirebaseFeedbackStorage(@Value("${feedback.firebase.bucket:}") String bucketName,
            @Value("${feedback.firebase.enabled:false}") boolean enabled) {
        this.bucketName = bucketName; this.enabled = enabled;
    }
    public record Uploaded(String name, String url, String mimeType, long size, int width, int height) { }

    public Uploaded upload(FeedbackImages.Image image) {
        try {
            var bucket = StorageClient.getInstance(app()).bucket(bucketName);
            String name = "feedback/" + UUID.randomUUID() + "." + image.extension();
            bucket.create(name, image.bytes(), image.contentType());
            // Objects stay private. DT2-5 must read with server credentials, not make the bucket public.
            return new Uploaded(name, "https://storage.googleapis.com/" + bucketName + "/" + name,
                    image.contentType(), image.bytes().length, image.width(), image.height());
        } catch (RuntimeException error) { throw new IllegalStateException("Dịch vụ ảnh phản hồi chưa sẵn sàng."); }
    }

    public void delete(Uploaded image) {
        var blob = StorageClient.getInstance(app()).bucket(bucketName).get(image.name());
        if (blob != null) blob.delete();
    }

    private synchronized FirebaseApp app() {
        if (!enabled || bucketName.isBlank() || !bucketName.matches("[a-z0-9][a-z0-9._-]{2,220}"))
            throw new IllegalStateException("Dịch vụ ảnh phản hồi chưa được cấu hình.");
        if (app == null) {
            try {
                var options = FirebaseOptions.builder().setCredentials(GoogleCredentials.getApplicationDefault()).setStorageBucket(bucketName).build();
                app = FirebaseApp.initializeApp(options, "feedback-" + UUID.randomUUID());
            } catch (IOException error) { throw new IllegalStateException("Dịch vụ ảnh phản hồi chưa được cấu hình."); }
        }
        return app;
    }
}
