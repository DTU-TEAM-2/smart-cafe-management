package vn.codegym.smartcafe.staff;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.Statement;
import java.util.Map;
import javax.imageio.ImageIO;
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

    public StaffImageService(
            @Value("${menu.images.cloud-name:${CLOUDINARY_CLOUD_NAME:}}") String cloudName,
            @Value("${menu.images.api-key:${CLOUDINARY_API_KEY:}}") String apiKey,
            @Value("${menu.images.api-secret:${CLOUDINARY_API_SECRET:}}") String apiSecret,
            JdbcTemplate jdbc) {
        this.cloudName = cloudName != null ? cloudName.trim() : "";
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.apiSecret = apiSecret != null ? apiSecret.trim() : "";
        this.client = RestClient.builder().build();
        this.jdbc = jdbc;
    }

    public boolean isConfigured() {
        return !cloudName.isBlank() && !apiKey.isBlank() && !apiSecret.isBlank();
    }

    @Transactional
    public StaffModels.ImageUploadResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tệp ảnh không được rỗng.");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Ảnh đại diện tối đa 5 MB.");
        }

        byte[] bytes;
        String format;
        int width;
        int height;
        try {
            bytes = file.getBytes();
            var image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Định dạng ảnh không được hỗ trợ (chỉ nhận PNG, JPG).");
            }
            width = image.getWidth();
            height = image.getHeight();
            String contentType = file.getContentType();
            if (contentType != null && contentType.equalsIgnoreCase("image/png")) {
                format = "png";
            } else {
                format = "jpg";
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không đọc được tệp ảnh.");
        }

        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ Cloudinary chưa được cấu hình biến môi trường.");
        }

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
                    || !(response.get("asset_id") instanceof String assetId)) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Lỗi phản hồi từ nhà cung cấp Cloudinary.");
            }

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
                ps.setInt(5, width);
                ps.setInt(6, height);
                return ps;
            }, keys);

            Number key = keys.getKey();
            if (key == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu thông tin ảnh vào cơ sở dữ liệu.");
            }
            return new StaffModels.ImageUploadResponse(key.longValue(), url);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Không thể kết nối đến máy chủ Cloudinary.");
        }
    }
}
