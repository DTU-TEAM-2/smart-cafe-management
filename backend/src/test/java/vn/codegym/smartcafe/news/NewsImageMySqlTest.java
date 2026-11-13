package vn.codegym.smartcafe.news;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// MySQL metadata/transactions are real; only the external Firebase boundary is mocked.
@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}", "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD:}"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:mysql:.*")
class NewsImageMySqlTest {
    private static final String ASSET = "news/00000000-0000-0000-0000-000000000001.png";
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean NewsImageStorage storage;
    @MockitoSpyBean JdbcClient client;

    @BeforeEach
    void seed() {
        clean();
        jdbc.update("""
                INSERT INTO users(id,username,password_hash,full_name,email,role,status)
                VALUES (951,'image-admin','not-a-login-hash','Image admin','image@example.invalid','ADMIN','ACTIVE')
                """);
        when(storage.upload(any(), anyString())).thenReturn(new NewsImageStorage.StoredImage(ASSET,
                "https://storage.googleapis.com/test-bucket/" + ASSET));
    }

    @AfterEach
    void clean() {
        reset(client);
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).endsWith("_test");
        jdbc.update("DELETE FROM news_images");
        jdbc.update("DELETE FROM news_posts");
        jdbc.update("DELETE FROM users WHERE id IN (951,952,953)");
        jdbc.update("DELETE FROM media_assets WHERE id BETWEEN 9501 AND 9503");
        jdbc.update("DELETE FROM media_assets WHERE provider_asset_id=?", ASSET);
    }

    @ParameterizedTest
    @ValueSource(strings = {"menu", "avatars", "feedback", "wrong-provider", "malformed-news"})
    void rejectsCrossModuleMediaOnCreateAndUpdateWithoutChangingExistingPost(String namespace) throws Exception {
        String asset = namespace.equals("wrong-provider") ? ASSET
                : namespace.equals("malformed-news") ? "news/avatar.png" : namespace + "/foreign.png";
        jdbc.update("""
                INSERT INTO media_assets(id,provider,provider_asset_id,secure_url,mime_type,size_bytes)
                VALUES (9501,?,?, 'https://example.invalid/private-other-module.png','image/png',1)
                """, namespace.equals("wrong-provider") ? "CLOUDINARY" : "FIREBASE", asset);
        mvc.perform(post("/api/v1/news").with(user("image-admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audiences\":[\"STAFF\"],\"title\":\"Foreign\",\"body\":\"Body\",\"imageMediaIds\":[9501]}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM news_posts", Integer.class)).isZero();

        mvc.perform(post("/api/v1/news").with(user("image-admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audiences\":[\"STAFF\"],\"title\":\"Original\",\"body\":\"Body\"}"))
                .andExpect(status().isCreated());
        long postId = jdbc.queryForObject("SELECT id FROM news_posts", Long.class);
        mvc.perform(put("/api/v1/news/" + postId).with(user("image-admin").roles("ADMIN"))
                        .header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Must not save\",\"body\":\"Body\",\"imageMediaIds\":[9501]}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT title FROM news_posts WHERE id=?", String.class, postId)).isEqualTo("Original");
        assertThat(jdbc.queryForObject("SELECT version FROM news_posts WHERE id=?", Long.class, postId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM news_images", Integer.class)).isZero();
    }

    @Test
    void existingCrossModuleLinksNeverExposeUrlsInListDetailOrPrivateImageEndpoint() throws Exception {
        jdbc.update("""
                INSERT INTO users(id,username,password_hash,full_name,email,role,status)
                VALUES (952,'image-staff','not-a-login-hash','Staff','image-staff@example.invalid','STAFF','ACTIVE'),
                       (953,'image-customer','not-a-login-hash','Customer','image-customer@example.invalid','CUSTOMER','ACTIVE')
                """);
        jdbc.update("""
                INSERT INTO media_assets(id,provider,provider_asset_id,secure_url,mime_type,size_bytes)
                VALUES (9501,'FIREBASE','avatars/foreign.png','https://example.invalid/private-avatar.png','image/png',1),
                       (9502,'CLOUDINARY','menu/foreign.jpg','https://example.invalid/private-menu.jpg','image/jpeg',1),
                       (9503,'FIREBASE','feedback/foreign.png','https://example.invalid/private-feedback.png','image/png',1)
                """);
        mvc.perform(post("/api/v1/news").with(user("image-admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audiences\":[\"STAFF\",\"CUSTOMER\"],\"title\":\"Legacy links\",\"body\":\"Body\",\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isCreated());
        long postId = jdbc.queryForObject("SELECT id FROM news_posts", Long.class);
        for (int id = 9501; id <= 9503; id++) {
            jdbc.update("INSERT INTO news_images(news_id,media_id,sort_order) VALUES (?,?,?)", postId, id, id - 9501);
        }
        for (String role : new String[]{"ADMIN", "STAFF", "CUSTOMER"}) {
            String username = "image-" + role.toLowerCase(java.util.Locale.ROOT);
            mvc.perform(get("/api/v1/news").with(user(username).roles(role)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].thumbnailUrl").doesNotExist());
            mvc.perform(get("/api/v1/news/" + postId).with(user(username).roles(role)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.images").isEmpty());
            for (int id = 9501; id <= 9503; id++) {
                mvc.perform(get("/api/v1/news/images/" + id).with(user(username).roles(role)))
                        .andExpect(status().isNotFound());
            }
        }
        verify(storage, never()).read(anyString());
        // Existing foreign links and media remain intact; a read must not silently migrate/delete them.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM news_images", Integer.class)).isEqualTo(3);
        mvc.perform(multipart("/api/v1/news/images").file(png()).with(user("image-admin").roles("ADMIN")))
                .andExpect(status().isCreated());
        long nativeId = jdbc.queryForObject("SELECT id FROM media_assets WHERE provider_asset_id=?", Long.class, ASSET);
        jdbc.update("INSERT INTO news_images(news_id,media_id,sort_order) VALUES (?,?,3)", postId, nativeId);
        for (String role : new String[]{"ADMIN", "STAFF", "CUSTOMER"}) {
            String username = "image-" + role.toLowerCase(java.util.Locale.ROOT);
            mvc.perform(get("/api/v1/news").with(user(username).roles(role)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].thumbnailUrl").value("/api/v1/news/images/" + nativeId));
            mvc.perform(get("/api/v1/news/" + postId).with(user(username).roles(role)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.images.length()").value(1))
                    .andExpect(jsonPath("$.images[0].secureUrl").value("/api/v1/news/images/" + nativeId));
        }
    }

    @Test
    void uploadMetadataLinkAndAuthenticatedReadBack() throws Exception {
        mvc.perform(multipart("/api/v1/news/images").file(png()).with(user("image-admin").roles("ADMIN")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.mimeType").value("image/png"))
                .andExpect(jsonPath("$.width").value(2)).andExpect(jsonPath("$.height").value(2));
        long id = jdbc.queryForObject("SELECT id FROM media_assets WHERE provider_asset_id=?", Long.class, ASSET);
        assertThat(jdbc.queryForObject("SELECT provider FROM media_assets WHERE id=?", String.class, id)).isEqualTo("FIREBASE");
        mvc.perform(post("/api/v1/news").with(user("image-admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audiences\":[\"STAFF\",\"CUSTOMER\"],\"title\":\"Image\",\"body\":\"Body\",\"imageMediaIds\":[" + id + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.images[0].secureUrl").value("/api/v1/news/images/" + id));
        when(storage.read(ASSET)).thenReturn(png().getBytes());
        mvc.perform(get("/api/v1/news/images/" + id).with(user("image-admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(png().getBytes()));
        jdbc.update("UPDATE media_assets SET deleted_at=UTC_TIMESTAMP(6) WHERE id=?", id);
        mvc.perform(get("/api/v1/news/images/" + id).with(user("image-admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidEmptySpoofedOrOversizedImagesNeverReachFirebase() throws Exception {
        var invalidFiles = new MockMultipartFile[]{
                new MockMultipartFile("file", "empty.png", "image/png", new byte[0]),
                new MockMultipartFile("file", "x.svg", "image/svg+xml", "<svg onload='alert(1)'/>".getBytes()),
                new MockMultipartFile("file", "x.png", "image/jpeg", png().getBytes()),
                new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[NewsImageService.MAX_BYTES + 1])};
        int[] statuses = {400, 415, 415, 413};
        for (int i = 0; i < invalidFiles.length; i++) {
            mvc.perform(multipart("/api/v1/news/images").file(invalidFiles[i]).with(user("image-admin").roles("ADMIN")))
                    .andExpect(status().is(statuses[i]))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        verifyNoInteractions(storage);
    }

    @Test
    void databaseFailureCompensatesUploadedObject() throws Exception {
        doThrow(new DataAccessResourceFailureException("private SQL or secret"))
                .when(client).sql(argThat(sql -> sql != null && sql.stripLeading().startsWith("INSERT INTO media_assets")));
        mvc.perform(multipart("/api/v1/news/images").file(png()).with(user("image-admin").roles("ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));
        verify(storage).delete(ASSET);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_assets WHERE provider_asset_id=?", Integer.class, ASSET)).isZero();
    }

    @Test
    void firebaseFailureDoesNotCreateMediaRecord() throws Exception {
        when(storage.upload(any(), anyString())).thenThrow(new ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ ảnh Firebase chưa sẵn sàng."));
        mvc.perform(multipart("/api/v1/news/images").file(png()).with(user("image-admin").roles("ADMIN")))
                .andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_assets WHERE provider_asset_id=?", Integer.class, ASSET)).isZero();
    }

    @Test
    void missingFileAndUnauthorizedRequestsAreRejected() throws Exception {
        mvc.perform(multipart("/api/v1/news/images").with(user("image-admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(multipart("/api/v1/news/images").file(png())).andExpect(status().isForbidden());
        for (String role : new String[]{"STAFF", "CUSTOMER"}) {
            mvc.perform(multipart("/api/v1/news/images").file(png()).with(user("other").roles(role)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/news/images/1").with(user("other").roles(role)))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(storage);
    }

    private MockMultipartFile png() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        return new MockMultipartFile("file", "ignored-filename.png", "image/png", output.toByteArray());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    void phoneJpegOrientationIsAppliedBeforePrivateMetadataIsRemoved(int orientation) throws Exception {
        int[] colors = {0xff0000, 0x00ff00, 0x0000ff, 0xffff00};
        // Expected display corners (TL, TR, BL, BR) from the EXIF orientation definitions.
        int[][] corners = {{0,1,2,3}, {1,0,3,2}, {3,2,1,0}, {2,3,0,1},
                {0,2,1,3}, {2,0,3,1}, {3,1,2,0}, {1,3,0,2}};
        int width = orientation >= 5 ? 24 : 40, height = orientation >= 5 ? 40 : 24;
        mvc.perform(multipart("/api/v1/news/images").file(jpegWithOrientation(orientation))
                        .with(user("image-admin").roles("ADMIN")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.mimeType").value("image/jpeg"))
                .andExpect(jsonPath("$.width").value(width)).andExpect(jsonPath("$.height").value(height));
        var captured = org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(storage).upload(captured.capture(), eq("image/jpeg"));
        byte[] stored = captured.getValue();
        assertThat(new String(stored, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("Exif\0\0");
        var image = ImageIO.read(new java.io.ByteArrayInputStream(stored));
        assertThat(image.getWidth()).isEqualTo(width); assertThat(image.getHeight()).isEqualTo(height);
        for (int corner = 0; corner < 4; corner++) {
            int expected = colors[corners[orientation - 1][corner]];
            int actual = image.getRGB(corner % 2 == 0 ? width / 4 : width * 3 / 4,
                    corner < 2 ? height / 4 : height * 3 / 4);
            for (int shift : new int[]{0,8,16}) assertThat(Math.abs(((actual >> shift) & 255) - ((expected >> shift) & 255)))
                    .as("orientation %s, corner %s", orientation, corner).isLessThan(55);
        }
        assertThat(jdbc.queryForObject("SELECT width_px FROM media_assets WHERE provider_asset_id=?", Integer.class, ASSET)).isEqualTo(width);
        assertThat(jdbc.queryForObject("SELECT height_px FROM media_assets WHERE provider_asset_id=?", Integer.class, ASSET)).isEqualTo(height);
    }

    private MockMultipartFile jpegWithOrientation(int orientation) throws Exception {
        var image = new BufferedImage(40,24,BufferedImage.TYPE_INT_RGB);
        int[] colors = {0xff0000,0x00ff00,0x0000ff,0xffff00};
        for (int y=0;y<24;y++) for(int x=0;x<40;x++) image.setRGB(x,y,colors[(y>=12?2:0)+(x>=20?1:0)]);
        var raw = new ByteArrayOutputStream(); ImageIO.write(image,"jpeg",raw);
        var output = new ByteArrayOutputStream(); byte[] encoded=raw.toByteArray();
        output.write(encoded,0,2);
        // APP1 Exif with little-endian TIFF IFD0 containing one SHORT orientation tag.
        output.write(new byte[]{(byte)255,(byte)225,0,34,'E','x','i','f',0,0,'I','I',42,0,8,0,0,0,
                1,0,0x12,1,3,0,1,0,0,0,(byte)orientation,0,0,0,0,0,0,0});
        output.write(encoded,2,encoded.length-2);
        return new MockMultipartFile("file","phone.jpg","image/jpeg",output.toByteArray());
    }

    @Test
    void brokenExifOffsetsCannotBreakValidPixelUploadOrRetainMetadata() throws Exception {
        byte[] source = jpegWithOrientation(6).getBytes();
        source[16] = (byte)255; source[17] = (byte)255; source[18] = (byte)255; source[19] = 127;
        mvc.perform(multipart("/api/v1/news/images")
                        .file(new MockMultipartFile("file", "broken-exif.jpg", "image/jpeg", source))
                        .with(user("image-admin").roles("ADMIN")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.width").value(40))
                .andExpect(jsonPath("$.height").value(24));
        var captured = org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(storage).upload(captured.capture(), eq("image/jpeg"));
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(captured.getValue()))).isNotNull();
        assertThat(new String(captured.getValue(), java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("Exif\0\0");
    }
}
