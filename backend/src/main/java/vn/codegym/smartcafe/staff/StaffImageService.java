package vn.codegym.smartcafe.staff;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.sql.Statement;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StaffImageService {
    static final long MAX_SIZE = 5L * 1024 * 1024;

    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final RestClient client;
    private final JdbcTemplate jdbc;

    @Autowired
    public StaffImageService(
            @Value("${menu.images.cloud-name:${CLOUDINARY_CLOUD_NAME:}}") String cloudName,
            @Value("${menu.images.api-key:${CLOUDINARY_API_KEY:}}") String apiKey,
            @Value("${menu.images.api-secret:${CLOUDINARY_API_SECRET:}}") String apiSecret,
            JdbcTemplate jdbc) {
        this(cloudName, apiKey, apiSecret, RestClient.builder().build(), jdbc);
    }

    StaffImageService(String cloudName, String apiKey, String apiSecret, RestClient client, JdbcTemplate jdbc) {
        this.cloudName = cloudName != null ? cloudName.trim() : "";
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.apiSecret = apiSecret != null ? apiSecret.trim() : "";
        this.client = client != null ? client : RestClient.builder().build();
        this.jdbc = jdbc;
    }

    public boolean isConfigured() {
        return !cloudName.isBlank() && !apiKey.isBlank() && !apiSecret.isBlank();
    }

    private record ImageData(String format, int width, int height) {}

    public StaffModels.ImageUploadResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tệp ảnh không được rỗng.");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Ảnh đại diện tối đa 5 MB.");
        }

        byte[] bytes;
        ImageData imageData;
        try {
            bytes = file.getBytes();
            imageData = inspect(bytes);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không đọc được tệp ảnh.");
        }

        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ Cloudinary chưa được cấu hình.");
        }

        String format = imageData.format();
        var form = new LinkedMultiValueMap<String, Object>();
        form.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "staff." + format;
            }
        });
        form.add("allowed_formats", "jpg,png");

        try {
            var response = client.post()
                    .uri("https://api.cloudinary.com/v1_1/{cloud}/image/upload", cloudName)
                    .headers(headers -> headers.setBasicAuth(apiKey, apiSecret))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response == null || !(response.get("secure_url") instanceof String url)
                    || !isCloudinaryUrl(url)
                    || !(response.get("asset_id") instanceof String assetId)
                    || !assetId.matches("[A-Za-z0-9_-]{1,255}")) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Lỗi phản hồi từ nhà cung cấp Cloudinary.");
            }

            String publicId = response.get("public_id") instanceof String pid && !pid.isBlank() ? pid : assetId;

            try {
                var keys = new GeneratedKeyHolder();
                jdbc.update(conn -> {
                    var ps = conn.prepareStatement("""
                            INSERT INTO media_assets(provider, provider_asset_id, secure_url, mime_type, size_bytes, width_px, height_px)
                            VALUES ('CLOUDINARY', ?, ?, ?, ?, ?, ?)
                            """, Statement.RETURN_GENERATED_KEYS);
                    ps.setString(1, assetId);
                    ps.setString(2, url);
                    ps.setString(3, format.equals("png") ? "image/png" : "image/jpeg");
                    ps.setLong(4, bytes.length);
                    ps.setInt(5, imageData.width());
                    ps.setInt(6, imageData.height());
                    return ps;
                }, keys);

                Number key = keys.getKey();
                if (key == null) {
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu thông tin ảnh vào cơ sở dữ liệu.");
                }
                return new StaffModels.ImageUploadResponse(key.longValue(), url);
            } catch (Exception dbEx) {
                boolean cleaned = cleanupOrphanAsset(publicId);
                if (!cleaned) {
                    System.err.println("WARN: Failed to clean up orphan Cloudinary asset with public_id: " + publicId);
                }
                if (dbEx instanceof ResponseStatusException rse) {
                    throw rse;
                }
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu thông tin ảnh vào cơ sở dữ liệu.", dbEx);
            }
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Không thể kết nối đến máy chủ Cloudinary.");
        }
    }

    boolean cleanupOrphanAsset(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return false;
        }
        try {
            var form = new LinkedMultiValueMap<String, Object>();
            form.add("public_id", publicId);
            var destroyRes = client.post()
                    .uri("https://api.cloudinary.com/v1_1/{cloud}/image/destroy", cloudName)
                    .headers(headers -> headers.setBasicAuth(apiKey, apiSecret))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            return destroyRes != null && "ok".equals(destroyRes.get("result"));
        } catch (Exception ex) {
            System.err.println("WARN: Error while requesting Cloudinary destroy for orphan public_id: " + publicId + ", error: " + ex.getMessage());
            return false;
        }
    }

    private ImageData inspect(byte[] bytes) {
        try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (stream == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chọn ảnh PNG/JPEG hợp lệ, tối đa 20 triệu pixel.");
            }
            var decoders = ImageIO.getImageReaders(stream);
            if (!decoders.hasNext()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chọn ảnh PNG/JPEG hợp lệ, tối đa 20 triệu pixel.");
            }
            var decoder = decoders.next();
            try {
                decoder.setInput(stream, true, true);
                String format = decoder.getFormatName();
                if (!format.equalsIgnoreCase("PNG") && !format.equalsIgnoreCase("JPEG")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ chấp nhận định dạng ảnh PNG hoặc JPEG.");
                }
                long pixels = (long) decoder.getWidth(0) * decoder.getHeight(0);
                if (pixels < 1 || pixels > 20_000_000 || decoder.read(0) == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kích thước ảnh không hợp lệ hoặc vượt quá 20 triệu pixel.");
                }
                return new ImageData(format.equalsIgnoreCase("PNG") ? "png" : "jpg", decoder.getWidth(0), decoder.getHeight(0));
            } finally {
                decoder.dispose();
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chọn ảnh PNG/JPEG hợp lệ, tối đa 20 triệu pixel.");
        }
    }

    private boolean isCloudinaryUrl(String url) {
        if (url == null || url.length() > 1024 || cloudName.isBlank()) return false;
        try {
            URI uri = URI.create(url);
            return "https".equals(uri.getScheme()) && "res.cloudinary.com".equals(uri.getHost())
                    && uri.getUserInfo() == null && uri.getPort() == -1 && uri.getQuery() == null && uri.getFragment() == null
                    && uri.getPath().startsWith("/" + cloudName + "/image/upload/")
                    && !uri.getPath().contains("..") && !url.contains("\\");
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
