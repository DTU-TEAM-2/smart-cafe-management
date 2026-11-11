package vn.codegym.smartcafe.menu;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MenuImagesTest {
    private byte[] png() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
    }
    private MenuImages service(RestClient client) { return new MenuImages(new MenuImageConfiguration.Cloudinary("menu-test", "test-key", "test-only-secret"), client); }
    private MockMultipartFile file(byte[] bytes) { return new MockMultipartFile("file", "untrusted.jpg", "image/jpeg", bytes); }

    @Test void uploadsDecodedImageToFixedProviderAndReturnsSafeUrl() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.cloudinary.com/v1_1/menu-test/image/upload"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Basic ")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("filename=\"menu.png\"")))
                .andRespond(withSuccess("{\"secure_url\":\"https://res.cloudinary.com/menu-test/image/upload/v1/coffee.png\"}", MediaType.APPLICATION_JSON));
        assertThat(service(builder.build()).upload(file(png())).imageUrl()).isEqualTo("https://res.cloudinary.com/menu-test/image/upload/v1/coffee.png");
        server.verify();
    }
    @Test void rejectsEmptyAndNonImagesRegardlessOfDeclaredMime() {
        var images = service(RestClient.create());
        for (byte[] bytes : new byte[][] {new byte[0], "<script>bad</script>".getBytes(java.nio.charset.StandardCharsets.UTF_8)}) {
            assertThatThrownBy(() -> images.upload(file(bytes))).isInstanceOfSatisfying(MenuException.class, error -> assertThat(error.code()).isEqualTo("INVALID_IMAGE"));
        }
    }
    @Test void rejectsBytesOverLimitBeforeParsingOrProviderCalls() {
        assertThatThrownBy(() -> service(RestClient.create()).upload(file(new byte[(int) MenuImages.MAX_SIZE + 1])))
                .isInstanceOfSatisfying(MenuException.class, error -> assertThat(error.status()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }
    @Test void rejectsPixelBombBeforeDecoding() throws Exception {
        byte[] bytes = png();
        ByteBuffer.wrap(bytes).putInt(16, 50000).putInt(20, 50000);
        var crc = new CRC32(); crc.update(bytes, 12, 17);
        ByteBuffer.wrap(bytes).putInt(29, (int) crc.getValue());
        assertThatThrownBy(() -> service(RestClient.create()).upload(file(bytes))).isInstanceOfSatisfying(MenuException.class, error -> assertThat(error.code()).isEqualTo("INVALID_IMAGE"));
    }
    @Test void missingProviderConfigurationHasExplicitUnavailableError() throws Exception {
        var images = new MenuImages(new MenuImageConfiguration.Cloudinary("", "", ""), RestClient.create());
        byte[] bytes = png();
        assertThatThrownBy(() -> images.upload(file(bytes))).isInstanceOfSatisfying(MenuException.class, error -> assertThat(error.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
    @Test void providerErrorsNeverReturnUpstreamSecrets() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("private provider body test-only-secret"));
        byte[] bytes = png();
        assertThatThrownBy(() -> service(builder.build()).upload(file(bytes))).isInstanceOfSatisfying(MenuException.class, error -> {
            assertThat(error.code()).isEqualTo("IMAGE_PROVIDER_ERROR");
            assertThat(error.getMessage()).doesNotContain("private", "test-only-secret");
        });
        server.verify();
    }
    @Test void refusesUntrustedDeliveryUrl() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withSuccess("{\"secure_url\":\"https://evil.invalid/coffee.jpg\"}", MediaType.APPLICATION_JSON));
        byte[] bytes = png();
        assertThatThrownBy(() -> service(builder.build()).upload(file(bytes))).isInstanceOfSatisfying(MenuException.class, error -> assertThat(error.status()).isEqualTo(HttpStatus.BAD_GATEWAY));
        server.verify();
    }
    @Test void onlySafeLocalOrOwnCloudDeliveryUrlsCanBeSaved() {
        var images = service(RestClient.create());
        images.requireSafeUrl("/images/coffee.jpg");
        images.requireSafeUrl("https://res.cloudinary.com/menu-test/image/upload/v1/a.png");
        for (String url : new String[] {"//evil.invalid/a.png", "/images/%2e%2e/a.jpg", "https://res.cloudinary.com/another/image/upload/a.png", "https://res.cloudinary.com/menu-test/image/upload/%2e%2e/a.png"}) {
            assertThatThrownBy(() -> images.requireSafeUrl(url)).isInstanceOf(MenuException.class);
        }
    }
}
