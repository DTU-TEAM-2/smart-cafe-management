package vn.codegym.smartcafe.menu;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

@Service
class MenuImages {
    static final long MAX_SIZE = 5L * 1024 * 1024;
    private final MenuImageConfiguration.Cloudinary settings;
    private final RestClient client;
    private final MenuMedia media;
    MenuImages(MenuImageConfiguration.Cloudinary settings, @Qualifier("menuImageClient") RestClient client, MenuMedia media) {
        this.settings = settings; this.client = client; this.media = media;
    }
    record Upload(Long mediaId, String imageUrl) { }
    private record ImageData(String format, int width, int height) { }

    Upload upload(MultipartFile file) {
        if (file.isEmpty()) throw invalid();
        if (file.getSize() > MAX_SIZE) throw new MenuException(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGE_TOO_LARGE", "Ảnh tối đa 5 MB.");
        byte[] bytes;
        ImageData image;
        try {
            bytes = file.getBytes();
            image = inspect(bytes);
        } catch (IOException exception) { throw invalid(); }
        if (!configured()) throw new MenuException(HttpStatus.SERVICE_UNAVAILABLE, "IMAGE_NOT_CONFIGURED", "Dịch vụ ảnh chưa được cấu hình.");
        var form = new LinkedMultiValueMap<String, Object>();
        String extension = image.format();
        form.add("file", new ByteArrayResource(bytes) {
            @Override public String getFilename() { return "menu." + extension; }
        });
        form.add("allowed_formats", "jpg,png");
        try {
            // The provider endpoint is fixed; browsers cannot supply a remote upload destination.
            var response = client.post().uri("https://api.cloudinary.com/v1_1/{cloud}/image/upload", settings.cloudName())
                    .headers(headers -> headers.setBasicAuth(settings.apiKey(), settings.apiSecret()))
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() { });
            if (response == null || !(response.get("secure_url") instanceof String url) || !isCloudinaryUrl(url)
                    || !(response.get("asset_id") instanceof String assetId) || !assetId.matches("[A-Za-z0-9_-]{1,255}")) throw providerFailure();
            Long id = media.register(assetId, url, extension.equals("png") ? "image/png" : "image/jpeg", bytes.length, image.width(), image.height());
            return new Upload(id, url);
        } catch (RestClientException exception) { throw providerFailure(); }
    }

    private ImageData inspect(byte[] bytes) throws IOException {
        try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (stream == null) throw invalid();
            var decoders = ImageIO.getImageReaders(stream);
            if (!decoders.hasNext()) throw invalid();
            var decoder = decoders.next();
            try {
                decoder.setInput(stream, true, true);
                String format = decoder.getFormatName();
                if (!format.equalsIgnoreCase("PNG") && !format.equalsIgnoreCase("JPEG")) throw invalid();
                long pixels = (long) decoder.getWidth(0) * decoder.getHeight(0);
                if (pixels < 1 || pixels > 20_000_000 || decoder.read(0) == null) throw invalid();
                return new ImageData(format.equalsIgnoreCase("PNG") ? "png" : "jpg", decoder.getWidth(0), decoder.getHeight(0));
            } finally { decoder.dispose(); }
        }
    }

    private boolean configured() {
        return settings.cloudName() != null && settings.cloudName().matches("[A-Za-z0-9_-]{1,64}")
                && settings.apiKey() != null && !settings.apiKey().isBlank()
                && settings.apiSecret() != null && !settings.apiSecret().isBlank();
    }
    private boolean isCloudinaryUrl(String url) {
        if (url == null || url.length() > 1024 || settings.cloudName() == null || !settings.cloudName().matches("[A-Za-z0-9_-]{1,64}")) return false;
        try {
            URI uri = URI.create(url);
            return "https".equals(uri.getScheme()) && "res.cloudinary.com".equals(uri.getHost())
                    && uri.getUserInfo() == null && uri.getPort() == -1 && uri.getQuery() == null && uri.getFragment() == null
                    && uri.getPath().startsWith("/" + settings.cloudName() + "/image/upload/")
                    && !uri.getPath().contains("..") && !url.contains("\\");
        } catch (IllegalArgumentException exception) { return false; }
    }
    private MenuException invalid() { return new MenuException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", "Chọn ảnh PNG/JPEG hợp lệ, tối đa 20 triệu pixel."); }
    private MenuException providerFailure() { return new MenuException(HttpStatus.BAD_GATEWAY, "IMAGE_PROVIDER_ERROR", "Không tải được ảnh lên dịch vụ ảnh. Vui lòng thử lại."); }
}
