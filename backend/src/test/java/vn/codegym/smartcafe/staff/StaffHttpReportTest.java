package vn.codegym.smartcafe.staff;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "server.address=127.0.0.1",
                "spring.datasource.url=${TEST_DB_URL}",
                "spring.datasource.username=${TEST_DB_USERNAME:root}",
                "spring.datasource.password=${TEST_DB_PASSWORD:123456}",
                "smartcafe.auth.token-secret=dt210-staff-http-test-signing-secret-only-2026"
        })
@ContextConfiguration(initializers = StaffHttpReportTest.TestDatabaseGuard.class)
class StaffHttpReportTest {

    static class TestDatabaseGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            String url = context.getEnvironment().getProperty("spring.datasource.url", "");
            if (!url.matches("jdbc:mysql://[^/]+/[A-Za-z0-9_]+_test(?:\\?.*)?")) {
                throw new IllegalStateException("A dedicated MySQL *_test URL is required before integration tests run");
            }
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final List<Map<String, Object>> results = new ArrayList<>();
    private Map<String, Object> current;
    private String adminToken;
    private String staffToken;
    private String csrfHeader;
    private String csrfToken;
    private String suffix;
    private Long adminUserId;
    private Long staffUserId;

    private String token(String username, String role) throws Exception {
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, username);
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                (id + ":" + username + ":" + role + ":" + Instant.now().plusSeconds(600).toEpochMilli())
                        .getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("dt210-staff-http-test-signing-secret-only-2026".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return payload + "." + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private record Response(int status, JsonNode body) {}

    @SuppressWarnings("unchecked")
    private Response send(String method, String path, String body, String bearer, boolean sendCsrf, int expected) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15));
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        if (sendCsrf && csrfHeader != null && csrfToken != null) {
            builder.header(csrfHeader, csrfToken);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));

        var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (current != null && current.containsKey("observations")) {
            ((List<Map<String, Object>>) current.get("observations")).add(Map.of(
                    "request", method + " " + path,
                    "expected", Integer.toString(expected),
                    "actual", Integer.toString(response.statusCode())));
        }
        if (expected != -1) {
            assertThat(response.statusCode()).as(method + " " + path).isEqualTo(expected);
        }
        JsonNode jsonBody = response.body().isBlank() ? json.readTree("{}") : json.readTree(response.body());
        return new Response(response.statusCode(), jsonBody);
    }

    @FunctionalInterface
    interface Checked {
        void run() throws Exception;
    }

    private void run(int number, String title, Checked action) {
        current = new LinkedHashMap<>();
        current.put("id", String.format("TC-%02d", number));
        current.put("title", title);
        current.put("observations", Collections.synchronizedList(new ArrayList<Map<String, Object>>()));
        long start = System.nanoTime();
        try {
            action.run();
            current.put("result", "PASS");
            current.put("note", "Mã HTTP và phản hồi JSON đạt kỳ vọng.");
        } catch (Exception | AssertionError error) {
            current.put("result", "FAIL");
            current.put("note", "Thất bại: " + error.getMessage());
        }
        current.put("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        results.add(current);
    }

    @Test
    void executeAllStaffApiTestCases() throws Exception {
        suffix = Long.toString(System.currentTimeMillis(), 36).toLowerCase(Locale.ROOT);
        String adminUser = "adm_" + suffix;
        String staffUser = "stf_" + suffix;

        // Cleanup any leftovers in dedicated test database
        jdbc.update("DELETE FROM staff_profiles");
        jdbc.update("DELETE FROM users");

        // 1. Seed Admin
        jdbc.update("""
                INSERT INTO users(username, password_hash, full_name, email, phone, role, status, auth_version)
                VALUES (?, '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890', 'Quản trị viên Hệ thống', ?, '0901111111', 'ADMIN', 'ACTIVE', 0)
                """, adminUser, adminUser + "@cafe.vn");
        adminUserId = jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, adminUser);
        jdbc.update("INSERT INTO staff_profiles(user_id, address, gender, salary) VALUES (?, 'Đà Nẵng', 'MALE', 20000000)", adminUserId);

        // 2. Seed Staff
        jdbc.update("""
                INSERT INTO users(username, password_hash, full_name, email, phone, role, status, auth_version)
                VALUES (?, '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890', 'Nhân viên Phục vụ', ?, '0908888888', 'STAFF', 'ACTIVE', 0)
                """, staffUser, staffUser + "@cafe.vn");
        staffUserId = jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, staffUser);
        jdbc.update("INSERT INTO staff_profiles(user_id, address, gender, salary) VALUES (?, 'Huế', 'FEMALE', 7500000)", staffUserId);

        // Seed a Customer user to verify scope isolation
        String custUser = "cust_" + suffix;
        jdbc.update("""
                INSERT INTO users(username, password_hash, full_name, email, phone, role, status, auth_version)
                VALUES (?, '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890', 'Khách hàng thử nghiệm', ?, '0907777777', 'CUSTOMER', 'ACTIVE', 0)
                """, custUser, custUser + "@cafe.vn");
        Long custUserId = jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, custUser);

        // 3. Seed 12 more staff users for pagination testing
        for (int i = 1; i <= 12; i++) {
            String u = String.format("extra_%s_%02d", suffix, i);
            jdbc.update("""
                    INSERT INTO users(username, password_hash, full_name, email, phone, role, status, auth_version)
                    VALUES (?, 'hash', ?, ?, '0905000000', 'STAFF', 'ACTIVE', 0)
                    """, u, "Nhân viên Bổ sung " + i, u + "@cafe.vn");
            Long uid = jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, u);
            jdbc.update("INSERT INTO staff_profiles(user_id, address, gender, salary) VALUES (?, 'Đà Nẵng', 'MALE', 6000000)", uid);
        }

        adminToken = token(adminUser, "ADMIN");
        staffToken = token(staffUser, "STAFF");

        String createdUsername = "nv_moi_" + suffix;
        long[] createdIdHolder = new long[1];

        try {
            // TC-01: Admin get staff list
            run(1, "Admin truy cập danh sách nhân viên", () -> {
                var res = send("GET", "/api/v1/staff", null, adminToken, false, 200);
                assertThat(res.body().path("content").isArray()).isTrue();
            });

            // TC-02: Staff forbidden
            run(2, "Nhân viên (Role STAFF) bị từ chối truy cập danh sách", () -> {
                send("GET", "/api/v1/staff", null, staffToken, false, 403);
            });

            // TC-03: Anonymous forbidden
            run(3, "Khách chưa đăng nhập (Anonymous) bị từ chối truy cập", () -> {
                send("GET", "/api/v1/staff", null, null, false, 403);
            });

            // TC-04: Pagination
            run(4, "Phân trang danh sách nhân viên mặc định 10 bản ghi", () -> {
                var res = send("GET", "/api/v1/staff?page=0&size=10", null, adminToken, false, 200);
                assertThat(res.body().path("content").size()).isEqualTo(10);
                assertThat(res.body().path("totalElements").asLong()).isGreaterThanOrEqualTo(14);
            });

            // TC-05: Search by username
            run(5, "Tìm kiếm nhân viên theo Username (Điều kiện OR)", () -> {
                var res = send("GET", "/api/v1/staff?keyword=" + staffUser, null, adminToken, false, 200);
                assertThat(res.body().path("content").size()).isEqualTo(1);
                assertThat(res.body().path("content").get(0).path("username").asText()).isEqualTo(staffUser);
            });

            // TC-06: Search by full name
            run(6, "Tìm kiếm nhân viên theo Họ tên (Điều kiện OR)", () -> {
                var res = send("GET", "/api/v1/staff?keyword=Ph%E1%BB%A5c%20v%E1%BB%A5", null, adminToken, false, 200);
                assertThat(res.body().path("content").size()).isGreaterThanOrEqualTo(1);
            });

            // TC-07: Search by phone
            run(7, "Tìm kiếm nhân viên theo Số điện thoại (Điều kiện OR)", () -> {
                var res = send("GET", "/api/v1/staff?keyword=0908888888", null, adminToken, false, 200);
                assertThat(res.body().path("content").size()).isEqualTo(1);
                assertThat(res.body().path("content").get(0).path("phone").asText()).isEqualTo("0908888888");
            });

            // TC-08: Search not found
            run(8, "Tìm kiếm với từ khóa không tồn tại trả về danh sách rỗng", () -> {
                var res = send("GET", "/api/v1/staff?keyword=non_existing_random_xyz", null, adminToken, false, 200);
                assertThat(res.body().path("content").size()).isZero();
            });

            // TC-09: CSRF token retrieval
            run(9, "Admin lấy CSRF Token để bảo mật thao tác thay đổi dữ liệu", () -> {
                var res = send("GET", "/api/v1/staff/csrf", null, adminToken, false, 200);
                csrfHeader = res.body().path("headerName").asText();
                csrfToken = res.body().path("token").asText();
                assertThat(csrfHeader).isNotBlank();
                assertThat(csrfToken).isNotBlank();
            });

            // TC-10: Create valid staff
            run(10, "Admin thêm nhân viên mới hợp lệ", () -> {
                String payload = """
                        {
                            "username": "%s",
                            "fullName": "Nguyen Van Test",
                            "email": "%s@cafe.vn",
                            "phone": "0912345678",
                            "address": "123 Le Duan, Da Nang",
                            "gender": "MALE",
                            "birthday": "1998-05-20",
                            "role": "STAFF",
                            "salary": 6500000
                        }
                        """.formatted(createdUsername, createdUsername);
                var res = send("POST", "/api/v1/staff", payload, adminToken, true, 201);
                assertThat(res.body().path("username").asText()).isEqualTo(createdUsername);
                assertThat(res.body().path("salary").asLong()).isEqualTo(6500000);
                createdIdHolder[0] = res.body().path("id").asLong();
            });

            // TC-11: Create staff username < 6 chars
            run(11, "Thêm nhân viên với Username ngắn hơn 6 ký tự bị từ chối", () -> {
                String payload = """
                        {
                            "username": "abc",
                            "fullName": "Nguyen Short",
                            "email": "short@cafe.vn",
                            "phone": "0987111222",
                            "address": "123 Da Nang",
                            "salary": 5000000
                        }
                        """;
                send("POST", "/api/v1/staff", payload, adminToken, true, 400);
            });

            // TC-12: Create staff username starting with digit
            run(12, "Thêm nhân viên với Username bắt đầu bằng chữ số bị từ chối", () -> {
                String payload = """
                        {
                            "username": "9staffname",
                            "fullName": "Nguyen Digit",
                            "email": "digit@cafe.vn",
                            "phone": "0987111222",
                            "address": "123 Da Nang",
                            "salary": 5000000
                        }
                        """;
                send("POST", "/api/v1/staff", payload, adminToken, true, 400);
            });

            // TC-13: Create staff username is admin or root
            run(13, "Thêm nhân viên với Username trùng tên cấm (admin/root) bị từ chối", () -> {
                String payload = """
                        {
                            "username": "admin",
                            "fullName": "Nguyen Admin",
                            "email": "admin99@cafe.vn",
                            "phone": "0987111222",
                            "address": "123 Da Nang",
                            "salary": 5000000
                        }
                        """;
                send("POST", "/api/v1/staff", payload, adminToken, true, 400);
            });

            // TC-14: Create staff salary not multiple of 100,000 VND
            run(14, "Thêm nhân viên với Lương không phải bội số 100,000 VND bị từ chối", () -> {
                String payload = """
                        {
                            "username": "staff_odd_sal",
                            "fullName": "Nguyen Odd Salary",
                            "email": "oddsal@cafe.vn",
                            "phone": "0987111222",
                            "address": "123 Da Nang",
                            "salary": 5000050
                        }
                        """;
                send("POST", "/api/v1/staff", payload, adminToken, true, 400);
            });

            // TC-15: Duplicate username
            run(15, "Thêm nhân viên với Username đã tồn tại bị báo lỗi xung đột (Conflict)", () -> {
                String payload = """
                        {
                            "username": "%s",
                            "fullName": "Nguyen Duplicate",
                            "email": "dup@cafe.vn",
                            "phone": "0987654321",
                            "address": "123 Le Duan, Da Nang",
                            "gender": "MALE",
                            "salary": 6000000
                        }
                        """.formatted(createdUsername);
                send("POST", "/api/v1/staff", payload, adminToken, true, 409);
            });

            // TC-16: Get staff detail by ID
            run(16, "Admin xem chi tiết thông tin nhân viên theo ID", () -> {
                var res = send("GET", "/api/v1/staff/" + createdIdHolder[0], null, adminToken, false, 200);
                assertThat(res.body().path("username").asText()).isEqualTo(createdUsername);
            });

            // TC-17: Update staff info
            run(17, "Admin cập nhật thông tin nhân viên thành công", () -> {
                String updatePayload = """
                        {
                            "fullName": "Nguyen Van Test Updated",
                            "email": "%s_upd@cafe.vn",
                            "phone": "0999999999",
                            "address": "456 Tran Phu, Da Nang",
                            "gender": "MALE",
                            "birthday": "1997-01-01",
                            "salary": 8000000,
                            "status": "ACTIVE"
                        }
                        """.formatted(createdUsername);
                var res = send("PUT", "/api/v1/staff/" + createdIdHolder[0], updatePayload, adminToken, true, 200);
                assertThat(res.body().path("fullName").asText()).isEqualTo("Nguyen Van Test Updated");
                assertThat(res.body().path("salary").asLong()).isEqualTo(8000000);
            });

            // TC-18: Admin cannot delete own account
            run(18, "Admin tự xóa tài khoản của chính mình bị hệ thống từ chối", () -> {
                send("DELETE", "/api/v1/staff/" + adminUserId, null, adminToken, true, 409);
            });

            // TC-19: Soft delete staff
            run(19, "Admin xóa nhân viên thành công (Xóa mềm INACTIVE, bảo toàn toàn vẹn dữ liệu)", () -> {
                send("DELETE", "/api/v1/staff/" + createdIdHolder[0], null, adminToken, true, 204);
                // Verify status in DB is INACTIVE
                String status = jdbc.queryForObject("SELECT status FROM users WHERE id=?", String.class, createdIdHolder[0]);
                assertThat(status).isEqualTo("INACTIVE");
                // Verify soft-deleted staff is filtered out from detail (404) and search list (empty)
                send("GET", "/api/v1/staff/" + createdIdHolder[0], null, adminToken, false, 404);
                var searchRes = send("GET", "/api/v1/staff?keyword=" + createdUsername, null, adminToken, false, 200);
                assertThat(searchRes.body().path("content").size()).isZero();
            });

            // TC-20: Customer ID is excluded from staff scope (returns 404)
            run(20, "Tài khoản khách hàng (CUSTOMER) bị cô lập khỏi API nhân sự (404 Not Found)", () -> {
                send("GET", "/api/v1/staff/" + custUserId, null, adminToken, false, 404);
                var custSearch = send("GET", "/api/v1/staff?keyword=" + custUser, null, adminToken, false, 200);
                assertThat(custSearch.body().path("content").size()).isZero();
            });

            // TC-21: Large page number does not overflow offset (returns 200 with empty list)
            run(21, "Phân trang với chỉ số page lớn không gây lỗi tràn số 503 (trả về danh sách rỗng 200 OK)", () -> {
                var bigPage = send("GET", "/api/v1/staff?page=21474837&size=100", null, adminToken, false, 200);
                assertThat(bigPage.body().path("content").size()).isZero();
            });

            // TC-22: Admin cannot demote self
            run(22, "Quản trị viên tự hạ quyền thành STAFF bị hệ thống từ chối (400 Bad Request)", () -> {
                String demotePayload = """
                        {
                            "fullName": "Quản trị viên Hệ thống",
                            "email": "%s@cafe.vn",
                            "phone": "0901111111",
                            "address": "Đà Nẵng",
                            "role": "STAFF",
                            "salary": 20000000
                        }
                        """.formatted(adminUser);
                send("PUT", "/api/v1/staff/" + adminUserId, demotePayload, adminToken, true, 400);
            });

            // TC-23: Maximum 2 Admins limit
            run(23, "Giới hạn tối đa 2 Quản trị viên: từ chối tạo thêm Quản trị viên thứ 3", () -> {
                String secondAdminPayload = """
                        {
                            "username": "adm2_%s",
                            "fullName": "Quản trị viên thứ hai",
                            "email": "adm2_%s@cafe.vn",
                            "phone": "0909999991",
                            "address": "Đà Nẵng",
                            "role": "ADMIN",
                            "salary": 22000000
                        }
                        """.formatted(suffix, suffix);
                send("POST", "/api/v1/staff", secondAdminPayload, adminToken, true, 201);

                String thirdAdminPayload = """
                        {
                            "username": "adm3_%s",
                            "fullName": "Quản trị viên thứ ba",
                            "email": "adm3_%s@cafe.vn",
                            "phone": "0909999992",
                            "address": "Đà Nẵng",
                            "role": "ADMIN",
                            "salary": 22000000
                        }
                        """.formatted(suffix, suffix);
                send("POST", "/api/v1/staff", thirdAdminPayload, adminToken, true, 400);
            });

        } finally {
            // Write results to JSON
            Path output = Path.of("target/api-report/dt2-10-api-results.json");
            Files.createDirectories(output.getParent());
            var report = new LinkedHashMap<String, Object>();
            report.put("generatedAt", Instant.now().toString());
            report.put("transport", "HTTP thật · Spring Boot / Tomcat · MySQL " + jdbc.queryForObject("SELECT VERSION()", String.class) + " · BCrypt");
            report.put("authentication", "Bearer HMAC test-only theo contract auth chung; role/status/auth_version đọc từ users của schema test.");
            report.put("cases", results);
            Files.writeString(output, json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        }

        assertThat(results).filteredOn(row -> "FAIL".equals(row.get("result"))).as("Staff HTTP report failures").isEmpty();
    }
}
