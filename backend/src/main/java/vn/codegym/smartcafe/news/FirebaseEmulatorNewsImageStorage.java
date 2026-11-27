package vn.codegym.smartcafe.news;

import com.google.cloud.NoCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Explicit, credential-free local testing; never contacts a live Firebase project. */
@Component
@Profile("firebase-emulator")
class FirebaseEmulatorNewsImageStorage implements NewsImageStorage {
    private final Storage storage;
    private final String bucketName = "demo-smart-cafe-news.appspot.com";

    FirebaseEmulatorNewsImageStorage(
            @Value("${smartcafe.news.firebase.emulator-host:}") String host,
            @Value("${spring.datasource.url}") String databaseUrl) {
        URI endpoint = URI.create(host);
        if (!"http".equals(endpoint.getScheme()) || !"127.0.0.1".equals(endpoint.getHost())
                || endpoint.getPort() < 1 || endpoint.getPort() > 65535 || endpoint.getRawUserInfo() != null
                || endpoint.getRawQuery() != null || endpoint.getRawFragment() != null
                || !(endpoint.getPath().isEmpty() || "/".equals(endpoint.getPath()))
                || !databaseUrl.matches("^jdbc:mysql://[^/]+/[A-Za-z0-9_]+_test(?:\\?.*)?$")) {
            throw new IllegalArgumentException("Firebase emulator requires an explicit loopback HTTP endpoint and MySQL _test database.");
        }
        storage = StorageOptions.newBuilder().setHost(host).setProjectId("demo-smart-cafe-news")
                .setCredentials(NoCredentials.getInstance()).build().getService();
    }

    @Override
    public StoredImage upload(byte[] bytes, String mimeType) {
        String name = "news/" + UUID.randomUUID() + ("image/png".equals(mimeType) ? ".png" : ".jpg");
        try {
            // The emulator's multipart parser is incompatible with this SDK's metadata part.
            // The supported resumable object endpoint avoids modifying the official emulator.
            try (var writer = storage.writer(BlobInfo.newBuilder(bucketName, name).setContentType(mimeType).build())) {
                var buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) writer.write(buffer);
            }
            // Matches persisted cloud metadata shape; readers still go through the authorized backend.
            return new StoredImage(name, "https://storage.googleapis.com/" + bucketName + "/" + name);
        } catch (IOException | RuntimeException failure) {
            try { storage.delete(BlobId.of(bucketName, name)); } catch (RuntimeException ignored) { }
            throw unavailable();
        }
    }

    @Override
    public byte[] read(String assetId) {
        validate(assetId);
        try {
            var blob = storage.get(BlobId.of(bucketName, assetId));
            if (blob == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh.");
            if (blob.getSize() > NewsImageService.MAX_BYTES) throw unavailable();
            try (var input = Channels.newInputStream(blob.reader())) {
                byte[] bytes = input.readNBytes(NewsImageService.MAX_BYTES + 1);
                if (bytes.length > NewsImageService.MAX_BYTES) throw unavailable();
                return bytes;
            }
        } catch (ResponseStatusException failure) { throw failure; }
        catch (IOException | RuntimeException failure) { throw unavailable(); }
    }

    @Override
    public void delete(String assetId) {
        validate(assetId);
        storage.delete(BlobId.of(bucketName, assetId));
    }

    private void validate(String assetId) {
        if (!assetId.matches("news/[a-f0-9-]{36}\\.(png|jpg)"))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh tin tức.");
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Firebase Emulator local chưa sẵn sàng.");
    }
}
