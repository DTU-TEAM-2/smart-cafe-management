package vn.codegym.smartcafe.staff;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class StaffImageServiceTest {

    private byte[] png() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "avatar.png", "image/png", bytes);
    }

    @Test
    void uploadsDecodedImageToCloudinaryAndSavesMediaAsset() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://api.cloudinary.com/v1_1/test-cloud/image/upload"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Basic ")))
                .andRespond(withSuccess(
                        "{\"asset_id\":\"staff-asset-123\",\"secure_url\":\"https://res.cloudinary.com/test-cloud/image/upload/v1/avatar.png\"}",
                        MediaType.APPLICATION_JSON));

        var jdbc = mock(JdbcTemplate.class);
        doAnswer(invocation -> {
            KeyHolder kh = invocation.getArgument(1);
            kh.getKeyList().add(java.util.Map.of("GENERATED_KEY", 101L));
            return 1;
        }).when(jdbc).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        var service = new StaffImageService("test-cloud", "test-key", "test-secret", builder.build(), jdbc);
        var res = service.upload(file(png()));

        assertThat(res.mediaId()).isEqualTo(101L);
        assertThat(res.secureUrl()).isEqualTo("https://res.cloudinary.com/test-cloud/image/upload/v1/avatar.png");
        server.verify();
    }

    @Test
    void rejectsEmptyAndNonImagesRegardlessOfDeclaredMime() {
        var jdbc = mock(JdbcTemplate.class);
        var service = new StaffImageService("test-cloud", "test-key", "test-secret", RestClient.create(), jdbc);

        // Empty file
        assertThatThrownBy(() -> service.upload(file(new byte[0])))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        // Invalid non-image bytes
        assertThatThrownBy(() -> service.upload(file("<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void rejectsBytesOverLimitBeforeParsingOrProviderCalls() {
        var jdbc = mock(JdbcTemplate.class);
        var service = new StaffImageService("test-cloud", "test-key", "test-secret", RestClient.create(), jdbc);

        assertThatThrownBy(() -> service.upload(file(new byte[(int) StaffImageService.MAX_SIZE + 1])))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }

    @Test
    void rejectsPixelBombBeforeDecoding() throws Exception {
        byte[] bytes = png();
        ByteBuffer.wrap(bytes).putInt(16, 50000).putInt(20, 50000);
        var crc = new CRC32();
        crc.update(bytes, 12, 17);
        ByteBuffer.wrap(bytes).putInt(29, (int) crc.getValue());

        var jdbc = mock(JdbcTemplate.class);
        var service = new StaffImageService("test-cloud", "test-key", "test-secret", RestClient.create(), jdbc);

        assertThatThrownBy(() -> service.upload(file(bytes)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void missingCloudinaryConfigurationReturns503() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var service = new StaffImageService("", "", "", RestClient.create(), jdbc);

        assertThatThrownBy(() -> service.upload(file(png())))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void cloudinaryUpstreamErrorReturns502() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://api.cloudinary.com/v1_1/test-cloud/image/upload"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        var jdbc = mock(JdbcTemplate.class);
        var service = new StaffImageService("test-cloud", "test-key", "test-secret", builder.build(), jdbc);

        assertThatThrownBy(() -> service.upload(file(png())))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
        server.verify();
    }

    @Test
    void databaseInsertFailureCleansUpCloudinaryOrphanAsset() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();

        // 1. Upload to Cloudinary succeeds with distinct asset_id and public_id
        server.expect(requestTo("https://api.cloudinary.com/v1_1/test-cloud/image/upload"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"asset_id\":\"orphan-asset-456\",\"public_id\":\"orphan-pub-789\",\"secure_url\":\"https://res.cloudinary.com/test-cloud/image/upload/v1/avatar.png\"}",
                        MediaType.APPLICATION_JSON));

        // 2. Expect destroy call to clean up orphan asset using public_id (orphan-pub-789)
        server.expect(requestTo("https://api.cloudinary.com/v1_1/test-cloud/image/destroy"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("orphan-pub-789")))
                .andRespond(withSuccess("{\"result\":\"ok\"}", MediaType.APPLICATION_JSON));

        // Mock DB failure
        var jdbc = mock(JdbcTemplate.class);
        doThrow(new DataIntegrityViolationException("Simulated DB connection or constraint failure"))
                .when(jdbc).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        var service = new StaffImageService("test-cloud", "test-key", "test-secret", builder.build(), jdbc);

        assertThatThrownBy(() -> service.upload(file(png())))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));

        server.verify();
    }
}
