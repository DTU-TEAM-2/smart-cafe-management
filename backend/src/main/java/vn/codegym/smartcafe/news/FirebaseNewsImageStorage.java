package vn.codegym.smartcafe.news;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Bucket;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.StorageClient;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.channels.Channels;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Private Firebase Storage bucket; never changes ACLs or creates public download tokens. */
@Component
@Profile("!firebase-emulator")
class FirebaseNewsImageStorage implements NewsImageStorage {
    private static final Logger LOG = LoggerFactory.getLogger(FirebaseNewsImageStorage.class);
    private final String bucketName;
    private final boolean cloudEnabled;
    private FirebaseApp app;
    private Bucket bucket;

    FirebaseNewsImageStorage(@Value("${smartcafe.news.firebase.bucket:}") String bucketName,
                            @Value("${smartcafe.news.firebase.cloud-enabled:false}") boolean cloudEnabled) {
        this.bucketName = bucketName;
        this.cloudEnabled = cloudEnabled;
    }

    private synchronized Bucket bucket() {
        if (bucket != null) return bucket;
        // No ADC lookup or live request under the owner's free-only configuration.
        if (!cloudEnabled || bucketName.isBlank()) throw unavailable();
        try {
            var options = FirebaseOptions.builder().setCredentials(GoogleCredentials.getApplicationDefault())
                    .setStorageBucket(bucketName).build();
            app = FirebaseApp.initializeApp(options, "news-storage-" + UUID.randomUUID());
            bucket = StorageClient.getInstance(app).bucket();
            return bucket;
        } catch (IOException | RuntimeException failure) {
            if (app != null) { app.delete(); app = null; }
            throw unavailable();
        }
    }

    @Override
    public StoredImage upload(byte[] bytes, String mimeType) {
        var storage = bucket();
        String name = "news/" + UUID.randomUUID() + ("image/png".equals(mimeType) ? ".png" : ".jpg");
        try {
            storage.create(name, bytes, mimeType);
            return new StoredImage(name, "https://storage.googleapis.com/" + storage.getName() + "/" + name);
        } catch (RuntimeException failure) {
            // A timed-out upload may already have created the object. Best-effort compensation.
            try { var blob = storage.get(name); if (blob != null) blob.delete(); }
            catch (RuntimeException cleanupFailure) { LOG.warn("Could not confirm cleanup of failed news image {}", name); }
            throw unavailable();
        }
    }

    @Override
    public byte[] read(String assetId) {
        validateAssetId(assetId);
        try {
            var blob = bucket().get(assetId);
            if (blob == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh.");
            if (blob.getSize() > NewsImageService.MAX_BYTES) throw unavailable();
            try (var input = Channels.newInputStream(blob.reader())) {
                byte[] bytes = input.readNBytes(NewsImageService.MAX_BYTES + 1);
                if (bytes.length > NewsImageService.MAX_BYTES) throw unavailable();
                return bytes;
            }
        } catch (ResponseStatusException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw unavailable();
        }
    }

    @Override
    public void delete(String assetId) {
        validateAssetId(assetId);
        var blob = bucket().get(assetId);
        if (blob != null) blob.delete();
    }

    private void validateAssetId(String assetId) {
        if (!assetId.matches("news/[a-f0-9-]{36}\\.(png|jpg)")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh tin tức.");
        }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Dịch vụ ảnh Firebase chưa sẵn sàng. Vui lòng thử lại sau.");
    }

    @PreDestroy
    synchronized void shutdown() {
        if (app != null) app.delete();
    }
}
