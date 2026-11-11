package vn.codegym.smartcafe.menu;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

/** Real loopback HTTP/Tomcat/MySQL; the signing key and decoder exist only in test classpath. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.address=127.0.0.1", "spring.servlet.multipart.max-file-size=5MB", "spring.servlet.multipart.max-request-size=6MB"})
@ContextConfiguration(initializers = MenuIntegrationTest.TestDatabaseGuard.class)
@Import(MenuHttpReportTest.TestJwtConfiguration.class)
class MenuHttpReportTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class TestJwtConfiguration {
        @Bean KeyPair reportKeyPair() throws Exception {
            var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); return generator.generateKeyPair();
        }
        @Bean @Primary JwtDecoder reportJwtDecoder(KeyPair pair) {
            var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) pair.getPublic()).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("https://report-test.invalid"));
            return decoder;
        }
        @Bean JwtEncoder reportJwtEncoder(KeyPair pair) {
            var key = new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey((RSAPrivateKey) pair.getPrivate()).keyID("report-test-only").build();
            return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        }
        @Bean(destroyMethod = "close") MenuProviderStub reportProvider() throws Exception { return new MenuProviderStub(); }
        @Bean @Primary @Qualifier("menuImageClient") RestClient reportImageClient(MenuProviderStub provider) { return provider.client(); }
    }

    @LocalServerPort int port;
    @Autowired JwtEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired MenuProviderStub provider;
    @MockitoBean MenuImageConfiguration.Cloudinary imageSettings;
    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final List<Map<String, Object>> results = new ArrayList<>();
    private Map<String, Object> current;
    private String admin, staff, csrfHeader, csrfToken, group, other, item, second, suffix;
    private long groupVersion, itemVersion;

    private String token(String role) {
        Instant now = Instant.now();
        return encoder.encode(JwtEncoderParameters.from(JwtClaimsSet.builder().issuer("https://report-test.invalid")
                .subject("report-test-only").audience(List.of("menu-report" )).issuedAt(now).expiresAt(now.plusSeconds(600))
                .claim("roles", List.of(role)).build())).getTokenValue();
    }
    private String category(String code, String name, long version) {
        return json.writeValueAsString(Map.of("code", code, "name", name, "version", version));
    }
    private String itemBody(String code, String name, Object price, String category, long version) {
        return json.writeValueAsString(Map.of("code", code, "name", name, "price", price, "categoryCode", category,
                "imageUrl", "/images/coffee.jpg", "description", "Postman API report fixture", "available", true, "version", version));
    }
    private String goodItem() { return itemBody(item, "Caramel Latte Postman", 45000, group, 0); }
    private record Response(int status, JsonNode body) { }

    private Response send(String method, String path, String body, String bearer, boolean csrf, int expected) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(20));
        if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
        if (csrf) builder.header(csrfHeader, csrfToken);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return execute(builder.build(), method, path, expected);
    }
    @SuppressWarnings("unchecked")
    private Response execute(HttpRequest request, String method, String path, int expected) throws Exception {
        var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ((List<Map<String, Object>>) current.get("observations")).add(Map.of("request", method + " " + path,
                "expected", expected == -1 ? "200 hoặc 409" : Integer.toString(expected), "actual", Integer.toString(response.statusCode())));
        if (expected != -1) assertThat(response.statusCode()).as(method + " " + path).isEqualTo(expected);
        JsonNode body = response.body().isBlank() ? json.readTree("{}") : json.readTree(response.body());
        return new Response(response.statusCode(), body);
    }
    private Response get(String path, int expected) throws Exception { return send("GET", path, null, admin, false, expected); }
    private Response post(String path, String body, int expected) throws Exception { return send("POST", path, body, admin, true, expected); }
    private Response put(String path, String body, int expected) throws Exception { return send("PUT", path, body, admin, true, expected); }
    private Response delete(String path, int expected) throws Exception { return send("DELETE", path, null, admin, true, expected); }
    private void code(Response response, String expected) { assertThat(response.body().path("code").asText()).isEqualTo(expected); }
    private void csrf() throws Exception {
        var response = get("/api/v1/menu/csrf", 200);
        csrfHeader = response.body().path("headerName").asText(); csrfToken = response.body().path("token").asText();
        assertThat(csrfHeader).isNotBlank(); assertThat(csrfToken).isNotBlank();
    }
    @FunctionalInterface interface Checked { void run() throws Exception; }
    private void run(int number, String title, Checked action) {
        current = new LinkedHashMap<>(); current.put("id", "TC-%02d".formatted(number)); current.put("title", title);
        current.put("observations", Collections.synchronizedList(new ArrayList<Map<String, Object>>()));
        long start = System.nanoTime();
        try { action.run(); current.put("result", "PASS"); current.put("note", "HTTP và kiểm tra dữ liệu đạt kỳ vọng."); }
        catch (Exception | AssertionError error) { current.put("result", "FAIL"); current.put("note", "Assertion/request failed; inspect local Surefire output."); }
        current.put("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)); results.add(current);
    }
    private void providerConfigured(boolean configured) {
        when(imageSettings.cloudName()).thenReturn("menu-test");
        when(imageSettings.apiKey()).thenReturn(configured ? "test-only-key" : "");
        when(imageSettings.apiSecret()).thenReturn(configured ? "test-only-secret" : "");
    }
    private byte[] png() throws Exception {
        var stream = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", stream); return stream.toByteArray();
    }
    private Response upload(byte[] file, int expected) throws Exception {
        String boundary = "menu-report-boundary";
        var body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"test.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(file); body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/menu/images"))
                .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + admin).header(csrfHeader, csrfToken)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary).POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        return execute(request, "POST", "/api/v1/menu/images", expected);
    }

    @Test void recordRealHttpResults() throws Exception {
        suffix = Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.ROOT);
        group = "PMCF_" + suffix; other = "PMTE_" + suffix; item = "PMITEM_" + suffix; second = "PMITEM2_" + suffix;
        admin = token("ADMIN"); staff = token("STAFF");
        providerConfigured(false);
        try {
            run(1, "Health công khai", () -> assertThat(send("GET", "/actuator/health", null, null, false, 200).body().path("status").asText()).isEqualTo("UP"));
            run(2, "Menu không token bị từ chối", () -> { for (String path : List.of("items", "categories", "csrf")) send("GET", "/api/v1/menu/" + path, null, null, false, 401); });
            run(3, "Token sai / header role không cấp quyền", () -> {
                send("GET", "/api/v1/menu/items", null, "invalid-token", false, 401);
                execute(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/menu/items")).header("X-Role", "ADMIN").GET().build(), "GET", "/api/v1/menu/items (X-Role)", 401);
            });
            run(4, "STAFF không được đọc API quản trị", () -> { for (String path : List.of("items", "categories", "csrf")) send("GET", "/api/v1/menu/" + path, null, staff, false, 403); });
            run(5, "STAFF không được tạo nhóm", () -> { csrf(); send("POST", "/api/v1/menu/categories", category("PMSTAFF_" + suffix, "Staff rejected", 0), staff, true, 403); });
            run(6, "ADMIN lấy CSRF / cookie session", this::csrf);
            run(7, "Thiếu hoặc sai CSRF", () -> {
                send("POST", "/api/v1/menu/categories", category(group, "Coffee Postman", 0), admin, false, 403);
                String saved = csrfToken; csrfToken = "invalid-csrf";
                try { post("/api/v1/menu/categories", category(group, "Coffee Postman", 0), 403); } finally { csrfToken = saved; }
            });
            run(8, "Tạo hai nhóm và chuẩn hóa mã", () -> {
                var body = post("/api/v1/menu/categories", category(" " + group.toLowerCase(Locale.ROOT) + " ", "  Cà phê Postman  ", 0), 201).body();
                assertThat(body.path("code").asText()).isEqualTo(group); assertThat(body.path("version").asLong()).isZero(); assertThat(body.path("itemCount").asLong()).isZero();
                post("/api/v1/menu/categories", category(other, "Trà Postman", 0), 201);
            });
            run(9, "Từ chối trùng mã nhóm", () -> code(post("/api/v1/menu/categories", category(group.toLowerCase(Locale.ROOT), "Duplicate", 0), 409), "MENU_CONFLICT"));
            run(10, "Validation mã / tên nhóm", () -> {
                for (String bad : List.of("INVALID!", "A".repeat(33))) code(post("/api/v1/menu/categories", category(bad, "Valid group", 0), 400), "VALIDATION_ERROR");
                for (String bad : List.of("", "   ", "A".repeat(121))) code(post("/api/v1/menu/categories", category("PMinvalid" + suffix, bad, 0), 400), "VALIDATION_ERROR");
            });
            run(11, "Version mới phải bằng 0", () -> {
                code(post("/api/v1/menu/categories", category("PMNEW_" + suffix, "New group", 1), 400), "INVALID_VERSION");
                for (String value : List.of("null", "-1")) code(post("/api/v1/menu/categories", category("PMNEW_" + suffix, "New group", 0).replace("\"version\":0", "\"version\":" + value), 400), "VALIDATION_ERROR");
                code(post("/api/v1/menu/categories", json.writeValueAsString(Map.of("code", "PMNEW_" + suffix, "name", "New group")), 400), "VALIDATION_ERROR");
            });
            run(12, "JSON sai / trường không cho phép", () -> {
                code(post("/api/v1/menu/categories", category(group, "Group", 0).replace("}", ",\"role\":\"ADMIN\"}"), 400), "INVALID_REQUEST");
                code(post("/api/v1/menu/categories", "{bad", 400), "INVALID_REQUEST");
            });
            run(13, "Chi tiết và tìm nhóm", () -> {
                assertThat(get("/api/v1/menu/categories/" + group, 200).body().path("code").asText()).isEqualTo(group);
                assertThat(get("/api/v1/menu/categories?keyword=" + group + "&page=0&size=10", 200).body().path("totalElements").asLong()).isEqualTo(1);
            });
            run(14, "Cập nhật nhóm tăng version", () -> {
                groupVersion = put("/api/v1/menu/categories/" + group, category(group, "Coffee Postman", 0), 200).body().path("version").asLong(); assertThat(groupVersion).isEqualTo(1);
            });
            run(15, "Version cũ không cập nhật/xóa nhóm", () -> {
                code(put("/api/v1/menu/categories/" + group, category(group, "Wrong change", 0), 409), "STALE_MENU");
                code(delete("/api/v1/menu/categories/" + group + "?version=0", 409), "STALE_MENU");
                assertThat(get("/api/v1/menu/categories/" + group, 200).body().path("name").asText()).isEqualTo("Coffee Postman");
            });
            run(16, "Mã nhóm bất biến", () -> code(put("/api/v1/menu/categories/" + group, category(other, "Other", groupVersion), 400), "IMMUTABLE_CODE"));
            run(17, "Tạo món và đọc dữ liệu chuẩn", () -> {
                var body = post("/api/v1/menu/items", goodItem(), 201).body();
                assertThat(body.path("categoryName").asText()).isEqualTo("Coffee Postman"); assertThat(body.path("price").asLong()).isEqualTo(45000); assertThat(body.path("version").asLong()).isZero();
                post("/api/v1/menu/items", itemBody(second, "Milk Coffee Postman", 25000, group, 0), 201);
            });
            run(18, "Từ chối trùng mã món", () -> code(post("/api/v1/menu/items", goodItem(), 409), "MENU_CONFLICT"));
            run(19, "Validation độ dài mã/tên món", () -> {
                for (String bad : List.of("ABC", "A".repeat(41))) code(post("/api/v1/menu/items", itemBody(bad, "Valid coffee", 45000, group, 0), 400), "VALIDATION_ERROR");
                for (String bad : List.of("ABCDE", "A".repeat(161))) code(post("/api/v1/menu/items", itemBody("PMBAD_" + suffix, bad, 45000, group, 0), 400), "VALIDATION_ERROR");
            });
            run(20, "Từ chối giá sai / phần lẻ", () -> {
                for (Object price : List.of(5000, 0, -1, new java.math.BigDecimal("5000.5"), 1000000000000L)) {
                    var response = post("/api/v1/menu/items", itemBody("PMBAD_" + suffix, "Valid coffee", price, group, 0), 400);
                    assertThat(response.body().path("fieldErrors").has("price")).isTrue();
                }
            });
            run(21, "Chấp nhận hai biên giá hợp lệ", () -> {
                for (long price : List.of(5001L, 999999999999L)) {
                    String boundary = "PMBOUND" + price + suffix;
                    assertThat(post("/api/v1/menu/items", itemBody(boundary, "Boundary coffee", price, group, 0), 201).body().path("price").asLong()).isEqualTo(price);
                    delete("/api/v1/menu/items/" + boundary + "?version=0", 204);
                }
            });
            run(22, "Không tạo món trong nhóm thiếu", () -> code(post("/api/v1/menu/items", itemBody("PMBAD_" + suffix, "Valid coffee", 45000, "PMNONE_" + suffix, 0), 409), "CATEGORY_INACTIVE"));
            run(23, "Không lưu URL ảnh không tin cậy", () -> {
                for (String url : List.of("https://evil.invalid/a.jpg", "//evil.invalid/a.jpg", "/images/../a.jpg", "data:image/png;base64,abc")) code(post("/api/v1/menu/items", itemBody("PMBAD_" + suffix, "Valid coffee", 45000, group, 0).replace("/images/coffee.jpg", url), 400), "INVALID_IMAGE_URL");
            });
            run(24, "Đọc chi tiết món", () -> {
                var body = get("/api/v1/menu/items/" + item, 200).body(); assertThat(body.path("code").asText()).isEqualTo(item); assertThat(body.path("price").asLong()).isEqualTo(45000); assertThat(body.path("available").asBoolean()).isTrue();
            });
            run(25, "Tìm mã chính xác", () -> {
                assertThat(get("/api/v1/menu/items?field=code&keyword=" + item, 200).body().path("totalElements").asLong()).isEqualTo(1);
                assertThat(get("/api/v1/menu/items?field=code&keyword=" + item.substring(0, item.length() - 1), 200).body().path("totalElements").asLong()).isZero();
            });
            run(26, "Tìm tên / nhóm không phân biệt hoa", () -> {
                assertThat(get("/api/v1/menu/items?field=name&keyword=caramel%20latte%20postman&categoryCode=" + group, 200).body().path("totalElements").asLong()).isEqualTo(1);
                assertThat(get("/api/v1/menu/items?field=category&keyword=coffee%20postman&categoryCode=" + group, 200).body().path("totalElements").asLong()).isEqualTo(2);
            });
            run(27, "Tìm giá chính xác", () -> {
                assertThat(get("/api/v1/menu/items?field=price&keyword=45000&categoryCode=" + group, 200).body().path("totalElements").asLong()).isEqualTo(1);
                assertThat(get("/api/v1/menu/items?field=price&keyword=45001&categoryCode=" + group, 200).body().path("totalElements").asLong()).isZero();
            });
            run(28, "Lọc nhóm / phân trang không lặp", () -> {
                var a = get("/api/v1/menu/items?categoryCode=" + group + "&page=0&size=1", 200).body();
                var b = get("/api/v1/menu/items?categoryCode=" + group + "&page=1&size=1", 200).body();
                assertThat(a.path("totalElements").asLong()).isEqualTo(2); assertThat(a.path("content").get(0).path("code").asText()).isNotEqualTo(b.path("content").get(0).path("code").asText());
            });
            run(29, "Wildcard / SQL input được tìm literal", () -> {
                for (String term : List.of("%25", "%27%20OR%201%3D1%20--")) assertThat(get("/api/v1/menu/items?categoryCode=" + group + "&keyword=" + term, 200).body().path("totalElements").asLong()).isZero();
                assertThat(get("/api/v1/menu/items?categoryCode=" + group + "&keyword=_", 200).body().path("totalElements").asLong()).isZero();
            });
            run(30, "Tham số phân trang / tìm kiếm sai", () -> {
                for (String query : List.of("field=sql", "size=0", "size=101", "page=-1", "field=price&keyword=abc")) get("/api/v1/menu/items?" + query, 400);
            });
            run(31, "Cập nhật món tăng version", () -> {
                var body = put("/api/v1/menu/items/" + item, itemBody(item, "Updated Latte Postman", 50000, group, 0), 200).body();
                itemVersion = body.path("version").asLong(); assertThat(itemVersion).isEqualTo(1); assertThat(body.path("price").asLong()).isEqualTo(50000);
                assertThat(get("/api/v1/menu/items/" + item, 200).body().path("name").asText()).isEqualTo("Updated Latte Postman");
            });
            run(32, "Version món cũ không ghi đè/xóa", () -> {
                code(put("/api/v1/menu/items/" + item, goodItem(), 409), "STALE_MENU"); code(delete("/api/v1/menu/items/" + item + "?version=0", 409), "STALE_MENU");
                assertThat(get("/api/v1/menu/items/" + item, 200).body().path("price").asLong()).isEqualTo(50000);
            });
            run(33, "Không đổi mã hoặc nhóm món", () -> {
                code(put("/api/v1/menu/items/" + item, itemBody(second, "Valid coffee", 45000, group, itemVersion), 400), "IMMUTABLE_CODE");
                code(put("/api/v1/menu/items/" + item, itemBody(item, "Valid coffee", 45000, other, itemVersion), 409), "STALE_MENU");
            });
            run(34, "Hai PUT đồng thời chỉ một writer thắng", () -> {
                String race = "PMRACE_" + suffix;
                post("/api/v1/menu/items", itemBody(race, "Race coffee", 45000, group, 0), 201);
                var gate = new CountDownLatch(1);
                try (var pool = Executors.newFixedThreadPool(2)) {
                    var a = pool.submit(() -> { gate.await(); return send("PUT", "/api/v1/menu/items/" + race, itemBody(race, "First coffee", 45000, group, 0), admin, true, -1).status(); });
                    var b = pool.submit(() -> { gate.await(); return send("PUT", "/api/v1/menu/items/" + race, itemBody(race, "Second coffee", 45000, group, 0), admin, true, -1).status(); });
                    gate.countDown(); assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
                }
                assertThat(get("/api/v1/menu/items/" + race, 200).body().path("version").asLong()).isEqualTo(1);
                delete("/api/v1/menu/items/" + race + "?version=1", 204);
            });
            run(35, "Xóa món và hậu kiểm danh sách", () -> {
                delete("/api/v1/menu/items/" + item + "?version=" + itemVersion, 204); code(get("/api/v1/menu/items/" + item, 404), "MENU_NOT_FOUND");
                assertThat(get("/api/v1/menu/items?field=code&keyword=" + item, 200).body().path("totalElements").asLong()).isZero();
            });
            run(36, "Không xóa lại / tái sử dụng mã món", () -> {
                delete("/api/v1/menu/items/" + item + "?version=2", 404); code(post("/api/v1/menu/items", goodItem(), 409), "MENU_CONFLICT");
            });
            run(37, "DELETE phải có version hợp lệ", () -> {
                delete("/api/v1/menu/items/" + second, 400); delete("/api/v1/menu/items/" + second + "?version=-1", 400); get("/api/v1/menu/items/" + second, 200);
            });
            run(38, "Xóa nhóm đồng thời ẩn món còn lại", () -> {
                delete("/api/v1/menu/categories/" + group + "?version=" + groupVersion, 204);
                get("/api/v1/menu/categories/" + group, 404); get("/api/v1/menu/items/" + second, 404);
                assertThat(get("/api/v1/menu/items?categoryCode=" + group, 200).body().path("totalElements").asLong()).isZero();
            });
            run(39, "Nhóm đã xóa không nhận món / tái dùng mã", () -> {
                code(post("/api/v1/menu/items", itemBody("PMLATE_" + suffix, "Late coffee", 45000, group, 0), 409), "CATEGORY_INACTIVE");
                code(post("/api/v1/menu/categories", category(group, "Again", 0), 409), "MENU_CONFLICT");
            });
            run(40, "DB read-only: xóa logic giữ hàng dữ liệu", () -> {
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM menu_categories WHERE code=? AND active=FALSE AND version=2", Long.class, group)).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM menu_items WHERE code=? AND active=FALSE AND version=1", Long.class, second)).isEqualTo(1);
                current.put("observations", List.of(Map.of("request", "DB SELECT (read-only)", "expected", "row giữ lại", "actual", "active=false; version đúng")));
            });
            run(41, "Upload và lưu ảnh — provider HTTP giả lập", () -> {
                current.put("mode", "PROVIDER_STUB");
                providerConfigured(true); provider.respondWith(200);
                int before = provider.calls();
                try {
                    var response = upload(png(), 200);
                    assertThat(response.body().path("imageUrl").asText()).isEqualTo(MenuProviderStub.DELIVERY_URL);
                    assertThat(provider.calls()).isEqualTo(before + 1); assertThat(provider.invalidRequest()).isNull();
                    String imageItem = "PMIMAGE_" + suffix;
                    var saved = post("/api/v1/menu/items", itemBody(imageItem, "Image coffee Postman", 45000, other, 0)
                            .replace("/images/coffee.jpg", MenuProviderStub.DELIVERY_URL), 201);
                    assertThat(saved.body().path("imageUrl").asText()).isEqualTo(MenuProviderStub.DELIVERY_URL);
                    assertThat(get("/api/v1/menu/items/" + imageItem, 200).body().path("imageUrl").asText()).isEqualTo(MenuProviderStub.DELIVERY_URL);
                    delete("/api/v1/menu/items/" + imageItem + "?version=0", 204);
                } finally { providerConfigured(false); }
            });
            run(42, "Thiếu cấu hình ảnh trả 503", () -> code(upload(png(), 503), "IMAGE_NOT_CONFIGURED"));
            run(43, "Từ chối tệp rỗng / giả ảnh", () -> { code(upload(new byte[0], 400), "INVALID_IMAGE"); code(upload("not an image".getBytes(StandardCharsets.UTF_8), 400), "INVALID_IMAGE"); });
            run(44, "Upload quá 5 MiB trả 413", () -> upload(new byte[(int) MenuImages.MAX_SIZE + 1], 413));
            run(45, "Ảnh vượt 20 triệu pixel bị từ chối", () -> {
                byte[] bytes = png(); ByteBuffer.wrap(bytes).putInt(16, 50000).putInt(20, 50000);
                var crc = new CRC32(); crc.update(bytes, 12, 17); ByteBuffer.wrap(bytes).putInt(29, (int) crc.getValue()); code(upload(bytes, 400), "INVALID_IMAGE");
            });
            run(46, "Upstream 503 → API 502 — provider giả lập", () -> {
                current.put("mode", "PROVIDER_STUB");
                providerConfigured(true); provider.respondWith(503);
                int before = provider.calls();
                try {
                    var response = upload(png(), 502);
                    code(response, "IMAGE_PROVIDER_ERROR");
                    assertThat(response.body().toString()).doesNotContain(MenuProviderStub.PRIVATE_ERROR, "test-only-secret");
                    assertThat(provider.calls()).isEqualTo(before + 1); assertThat(provider.invalidRequest()).isNull();
                } finally { providerConfigured(false); }
            });
        } finally {
            if (jdbc.queryForObject("SELECT DATABASE()", String.class).endsWith("_test")) {
                jdbc.update("DELETE FROM menu_items WHERE category_code IN (?,?)", group, other);
                jdbc.update("DELETE FROM menu_categories WHERE code IN (?,?)", group, other);
            }
            Path output = Path.of("target/api-report/dt2-9-api-results.json"); Files.createDirectories(output.getParent());
            var report = new LinkedHashMap<String, Object>(); report.put("generatedAt", Instant.now().toString());
            report.put("transport", "HTTP thật · Spring Boot / Tomcat · MySQL " + jdbc.queryForObject("SELECT VERSION()", String.class));
            report.put("authentication", "JWT RSA ký bằng khóa tạm trong test; không phải login/IdP thật; không thay production security.");
            report.put("provider", "TC-41/46 dùng HTTP provider giả lập trên loopback, không gọi Cloudinary thật và không cần key thật.");
            report.put("cases", results); Files.writeString(output, json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        }
        assertThat(results).filteredOn(row -> row.get("result").equals("FAIL")).as("HTTP report failures").isEmpty();
    }
}
