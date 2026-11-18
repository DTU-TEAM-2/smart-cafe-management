package vn.codegym.smartcafe.database;

import com.jayway.jsonpath.JsonPath;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import tools.jackson.databind.json.JsonMapper;
import vn.codegym.smartcafe.auth.TokenService;
import static org.assertj.core.api.Assertions.*;

/** Real Tomcat, real Login/bearer and native verified Flyway/MySQL. No repository or HTTP doubles. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.profiles.active=verified-db", "server.address=127.0.0.1",
        "spring.datasource.hikari.maximum-pool-size=2", "spring.datasource.hikari.minimum-idle=0"})
@Import(ProfileMySqlTest.TimeConfiguration.class)
@DirtiesContext
class ProfileMySqlTest {
    private static final TestSchema SCHEMA = provision();
    private static final AtomicReference<Instant> NOW = new AtomicReference<>(Instant.now());
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static TestSchema provision() {
        var schema = new TestSchema(null);
        try {
            var flyway = Flyway.configure().dataSource(schema.source).locations("classpath:db/smart-cafe")
                    .initSql("SET SESSION time_zone='+00:00'").baselineOnMigrate(false).validateOnMigrate(true).load();
            assertThat(DatabaseRehearsal.execute(flyway, "provision")).isEqualTo(1);
            return schema;
        } catch (Exception ex) { schema.close(); throw new IllegalStateException(ex); }
    }

    @TestConfiguration
    static class TimeConfiguration {
        @Bean @Primary ControlledEncoder profileTestEncoder() { return new ControlledEncoder(); }
        @Bean @Primary Clock profileTestClock() {
            return new Clock() {
                public ZoneId getZone() { return ZoneOffset.UTC; }
                public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
                public Instant instant() { return NOW.get(); }
            };
        }
    }

    @DynamicPropertySource static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SCHEMA.source::getUrl);
        registry.add("spring.datasource.username", SCHEMA.source::getUsername);
        registry.add("spring.datasource.password", SCHEMA.source::getPassword);
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired HikariDataSource dataSource;
    @Autowired PasswordEncoder encoder;
    @Autowired TokenService tokens;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private String password;
    private String nextPassword;

    @BeforeEach void fixture() {
        SCHEMA.assertIsolated();
        SCHEMA.update("DELETE FROM auth_otp_challenges");
        SCHEMA.update("DELETE FROM staff_profiles");
        SCHEMA.update("DELETE FROM users");
        SCHEMA.update("DELETE FROM media_assets");
        NOW.set(Instant.now().truncatedTo(ChronoUnit.MICROS));
        password = "Aa1!" + UUID.randomUUID();
        nextPassword = "Bb2!" + UUID.randomUUID();
        String hash = encoder.encode(password);
        for (int i = 1; i <= 6; i++) {
            SCHEMA.update("""
                    INSERT INTO users(id,username,password_hash,full_name,email,phone,role,status,password_changed_at)
                    VALUES(?,?,?,?,?,?,?,?,?)
                    """, i, "profile_" + i, hash, "Synthetic " + i, "profile_" + i + "@example.test", "090000000" + i,
                    i == 3 ? "CUSTOMER" : i == 2 ? "STAFF" : "ADMIN", i == 4 ? "INACTIVE" : "ACTIVE", Timestamp.from(NOW.get()));
            if (i != 5) SCHEMA.update("INSERT INTO staff_profiles(user_id,address,salary) VALUES(?,'Synthetic address',10000000)", i);
        }
    }

    @AfterAll static void cleanup() { SCHEMA.close(); }

    @Test void adminAndStaffReadOnlyTheirOwnPreciseProfileAndHeaderUsesSameProjection() throws Exception {
        for (int id : new int[]{1, 2}) {
            String token = login(id);
            var profile = call("GET", "/profiles/me?userId=6&role=ADMIN", token, null);
            assertThat(profile.statusCode()).isEqualTo(200);
            assertThat(value(profile, "$.id")).isEqualTo(Integer.toString(id));
            assertThat(value(profile, "$.salary")).isEqualTo("10000000");
            assertThat(value(profile, "$.birthday")).isNull();
            assertThat(value(profile, "$.gender")).isNull();
            assertThat(value(profile, "$.canUpdateProfile")).isEqualTo(true);
            assertThat(value(profile, "$.version")).isEqualTo("0");
            assertThat(profile.headers().firstValue("Cache-Control")).contains("no-store");
            assertThat(profile.body()).doesNotContain("passwordHash", "password_hash", "authVersion", password);
            var home = call("GET", "/home/account", token, null);
            assertThat(value(home, "$.name")).isEqualTo(value(profile, "$.name"));
        }
        SCHEMA.update("UPDATE users SET id=9007199254740993 WHERE id=5");
        SCHEMA.update("INSERT INTO staff_profiles(user_id,address,salary) VALUES(9007199254740993,'Synthetic',999999999999900000)");
        var large = call("GET", "/profiles/me", login(5), null);
        assertThat(value(large, "$.id")).isEqualTo("9007199254740993");
        assertThat(value(large, "$.salary")).isEqualTo("999999999999900000");
    }

    @Test void missingPersonnelIsNullableAndDeletedAvatarIsHidden() throws Exception {
        var missing = call("GET", "/profiles/me", login(5), null);
        assertThat(missing.statusCode()).isEqualTo(200);
        assertThat(value(missing, "$.address")).isNull();
        assertThat(value(missing, "$.salary")).isNull();
        SCHEMA.update("INSERT INTO media_assets(id,provider,provider_asset_id,secure_url,mime_type,size_bytes) VALUES(1,'CLOUDINARY','profile-fixture','https://example.test/avatar.png','image/png',10)");
        SCHEMA.update("UPDATE users SET avatar_media_id=1 WHERE id=1");
        String token = login(1);
        assertThat(value(call("GET", "/profiles/me", token, null), "$.avatarUrl")).isEqualTo("https://example.test/avatar.png");
        SCHEMA.update("UPDATE media_assets SET deleted_at=CURRENT_TIMESTAMP(6) WHERE id=1");
        assertThat(value(call("GET", "/profiles/me", token, null), "$.avatarUrl")).isNull();
    }

    @Test void authenticationAndCurrentDbAuthorityRejectAllInvalidIdentities() throws Exception {
        for (String token : new String[]{"", "tampered", expired()}) {
            assertThat(call("GET", "/profiles/me", token, null).statusCode()).isEqualTo(401);
            assertThat(call("PATCH", "/profiles/me", token, Map.of("version", "0", "name", "Denied")).statusCode()).isEqualTo(401);
            assertThat(call("POST", "/profiles/me/password", token, passwordRequest(password, nextPassword, nextPassword)).statusCode()).isEqualTo(401);
        }
        String token = login(1);
        SCHEMA.update("UPDATE users SET auth_version=auth_version+1 WHERE id=1");
        assertThat(call("GET", "/profiles/me", token, null).statusCode()).isEqualTo(401);
        token = login(1);
        SCHEMA.update("UPDATE users SET status='INACTIVE' WHERE id=1");
        assertThat(call("GET", "/profiles/me", token, null).statusCode()).isEqualTo(401);
        String customer = login(3);
        for (String method : new String[]{"GET", "PATCH", "POST"}) {
            var result = call(method, "/profiles/me" + (method.equals("POST") ? "/password" : ""), customer,
                    method.equals("GET") ? null : Map.of("userId", 1));
            assertThat(result.statusCode()).isEqualTo(403);
            assertThat(result.body()).doesNotContain("salary", "Synthetic");
        }
        var customerHeader = call("GET", "/home/account", customer, null);
        assertThat(value(customerHeader, "$.salary")).isNull();
    }

    @ParameterizedTest @ValueSource(ints = {1, 2})
    void permittedUpdateIsAtomicAndVisibleToProfileAndHeader(int id) throws Exception {
        String token = login(id);
        var beforeOther = jdbc.queryForMap("SELECT full_name,phone,version FROM users WHERE id=6");
        var result = call("PATCH", "/profiles/me", token, Map.of("version", "0", "name", "Nguyễn Ánh", "address", "Đà Nẵng", "phone", "+84 900 000 000"));
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(value(result, "$.version")).isEqualTo("1");
        assertThat(value(result, "$.name")).isEqualTo("Nguyễn Ánh");
        assertThat(jdbc.queryForObject("SELECT address FROM staff_profiles WHERE user_id=?", String.class, id)).isEqualTo("Đà Nẵng");
        assertThat(value(call("GET", "/home/account", token, null), "$.phone")).isEqualTo("+84 900 000 000");
        assertThat(jdbc.queryForMap("SELECT full_name,phone,version FROM users WHERE id=6")).isEqualTo(beforeOther);
        var omitted = call("PATCH", "/profiles/me", token, Map.of("version", "1", "name", "O'Brien; SELECT 1 --"));
        assertThat(omitted.statusCode()).isEqualTo(200);
        assertThat(value(omitted, "$.address")).isEqualTo("Đà Nẵng");
        assertThat(value(omitted, "$.name")).isEqualTo("O'Brien; SELECT 1 --");
    }

    @ParameterizedTest @ValueSource(strings = {"role", "salary", "status", "userId", "username", "passwordHash", "authVersion", "email", "unknown"})
    void staffAndAdminMassAssignmentIsRejectedWithoutWrites(String field) throws Exception {
        for (int id : new int[]{1, 2}) {
            var before = userState(id);
            var result = call("PATCH", "/profiles/me", login(id), Map.of("version", "0", "name", "Unexpected", field, "6"));
            assertThat(result.statusCode()).isEqualTo(400);
            assertThat(value(result, "$.code")).isEqualTo("INVALID_PROFILE_REQUEST");
            assertThat(userState(id)).isEqualTo(before);
        }
    }

    @Test void nullMalformedAndOversizedRequestsAre400WithoutWrites() throws Exception {
        String token = login(1);
        var before = userState(1);
        for (String body : new String[]{"null", "[]", "{", "{\"version\":\"0\",\"phone\":null}", "{\"version\":0,\"name\":\"A\"}",
                "{\"version\":\"0\",\"name\":\"   \"}", JSON.writeValueAsString(Map.of("version", "0", "address", "a".repeat(256)))}) {
            assertThat(raw("PATCH", "/profiles/me", token, body).statusCode()).isEqualTo(400);
        }
        assertThat(userState(1)).isEqualTo(before);
    }

    @Test void staleVersionConflictsAndMissingPersonnelRollsBackUserWrite() throws Exception {
        String token = login(1);
        assertThat(call("PATCH", "/profiles/me", token, Map.of("version", "0", "name", "Changed")).statusCode()).isEqualTo(200);
        var stale = call("PATCH", "/profiles/me", token, Map.of("version", "0", "phone", "123"));
        assertThat(stale.statusCode()).isEqualTo(409);
        assertThat(value(stale, "$.code")).isEqualTo("PROFILE_CONFLICT");
        var before = userState(5);
        var missing = call("PATCH", "/profiles/me", login(5), Map.of("version", "0", "name", "Must rollback", "address", "A"));
        assertThat(missing.statusCode()).isEqualTo(409);
        assertThat(value(missing, "$.code")).isEqualTo("PROFILE_INCOMPLETE");
        assertThat(userState(5)).isEqualTo(before);
    }

    @Test void concurrentProfileEditorsCannotBothWin() throws Exception {
        String token = login(1);
        List<Integer> statuses = parallel(() -> call("PATCH", "/profiles/me", token, Map.of("version", "0", "name", UUID.randomUUID().toString())).statusCode(),
                () -> call("PATCH", "/profiles/me", token, Map.of("version", "0", "name", UUID.randomUUID().toString())).statusCode());
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("SELECT version FROM users WHERE id=1", Long.class)).isEqualTo(1L);
    }

    @Test void passwordFailureNeverMutatesAuthenticationOrOtherProfileState() throws Exception {
        String token = login(2);
        var before = userState(2);
        for (var data : List.of(passwordRequest("wrong", nextPassword, nextPassword), passwordRequest(password, nextPassword, "different"),
                passwordRequest(password, "weak", "weak"), passwordRequest(password, password, password),
                passwordRequest(password, "Aa1" + "é".repeat(35), "Aa1" + "é".repeat(35)))) {
            var result = call("POST", "/profiles/me/password", token, data);
            assertThat(result.statusCode()).isEqualTo(400);
            assertThat(result.body()).doesNotContain(password, "password_hash", nextPassword);
            assertThat(userState(2)).isEqualTo(before);
        }
    }

    @Test void passwordChangeRevokesAllOldTokensAndGrantsAndNewTokenContinuesProfile() throws Exception {
        SCHEMA.update("UPDATE users SET must_change_password=1,password_changed_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 31 DAY) WHERE id=2");
        String token = login(2), otherSession = login(2);
        String grant = resetGrant();
        var other = userState(1);
        var result = call("POST", "/profiles/me/password", token, passwordRequest(password, nextPassword, nextPassword));
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(value(result, "$.mustChangePassword")).isEqualTo(false);
        String replacement = (String) value(result, "$.token");
        assertThat(call("GET", "/profiles/me", token, null).statusCode()).isEqualTo(401);
        assertThat(call("GET", "/profiles/me", otherSession, null).statusCode()).isEqualTo(401);
        assertThat(call("GET", "/profiles/me", replacement, null).statusCode()).isEqualTo(200);
        assertThat(call("POST", "/auth/login", "", Map.of("username", "profile_2", "password", password)).statusCode()).isEqualTo(401);
        assertThat(call("POST", "/auth/login", "", Map.of("username", "profile_2", "password", nextPassword)).statusCode()).isEqualTo(200);
        assertThat(value(call("POST", "/auth/verify-reset-token", "", Map.of("token", grant)), "$.valid")).isEqualTo(false);
        // Auth's existing InvalidTokenException contract is 401 for a revoked grant.
        assertThat(call("POST", "/auth/reset-password", "", Map.of("token", grant, "newPassword", password, "confirmPassword", password)).statusCode()).isEqualTo(401);
        var state = userState(2);
        assertThat(encoder.matches(nextPassword, (String) state.get("password_hash"))).isTrue();
        assertThat(state.get("auth_version")).isEqualTo(1L);
        assertThat(state.get("version")).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT must_change_password FROM users WHERE id=2", Boolean.class)).isFalse();
        assertThat(userState(1)).isEqualTo(other);
    }

    @Test void concurrentPasswordChangesAndResetHaveExactlyOneWinner() throws Exception {
        String token = login(2);
        var request = passwordRequest(password, nextPassword, nextPassword);
        var statuses = parallel(() -> call("POST", "/profiles/me/password", token, request).statusCode(),
                () -> call("POST", "/profiles/me/password", token, request).statusCode());
        assertThat(statuses).containsExactlyInAnyOrder(200, 401);
        assertThat(jdbc.queryForObject("SELECT auth_version FROM users WHERE id=2", Long.class)).isEqualTo(1L);
        password = nextPassword;
        nextPassword = "Cc3!" + UUID.randomUUID();
        String fresh = login(2), grant = resetGrant();
        var race = parallel(() -> call("POST", "/profiles/me/password", fresh, passwordRequest(password, nextPassword, nextPassword)).statusCode(),
                () -> call("POST", "/auth/reset-password", "", Map.of("token", grant, "newPassword", nextPassword, "confirmPassword", nextPassword)).statusCode());
        assertThat(race).containsExactlyInAnyOrder(200, 401);
        assertThat(jdbc.queryForObject("SELECT auth_version FROM users WHERE id=2", Long.class)).isEqualTo(2L);
    }

    @Test void loginAndProfileAgreeJustBeforeAtAndAfter30DaysAndExplicitFlag() throws Exception {
        var changed = NOW.get().minus(Duration.ofDays(30));
        SCHEMA.update("UPDATE users SET password_changed_at=?,must_change_password=0 WHERE id=2", Timestamp.from(changed));
        for (long nanos : new long[]{-1000, 0, 1000, Duration.ofDays(1).toNanos()}) {
            NOW.set(changed.plus(Duration.ofDays(30)).plusNanos(nanos));
            var login = call("POST", "/auth/login", "", Map.of("username", "profile_2", "password", password));
            assertThat(value(login, "$.mustChangePassword")).isEqualTo(nanos >= 0);
            var profile = call("GET", "/profiles/me", (String) value(login, "$.token"), null);
            assertThat(value(profile, "$.mustChangePassword")).isEqualTo(nanos >= 0);
            assertThat(Instant.parse((String) value(profile, "$.passwordExpiresAt"))).isEqualTo(changed.plus(Duration.ofDays(30)));
        }
        SCHEMA.update("UPDATE users SET password_changed_at=?,must_change_password=1 WHERE id=2", Timestamp.from(NOW.get()));
        assertThat(value(call("POST", "/auth/login", "", Map.of("username", "profile_2", "password", password)), "$.mustChangePassword")).isEqualTo(true);
    }

    @ParameterizedTest @ValueSource(strings = {"wrong-current", "reuse-check", "encode"})
    void slowPasswordWorkDoesNotHoldConnectionOrBlockOwnGetAndPatch(String phase) throws Exception {
        String token = login(2);
        String current = phase.equals("wrong-current") ? "WrongAa1" : password;
        var controlled = (ControlledEncoder) encoder;
        controlled.arm(phase.equals("wrong-current") ? current : nextPassword, phase.equals("encode"));
        var pool = Executors.newFixedThreadPool(2);
        try {
            var changing = pool.submit(() -> call("POST", "/profiles/me/password", token,
                    passwordRequest(current, nextPassword, nextPassword)));
            assertThat(controlled.entered.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(controlled.transactionActive).as("BCrypt must run outside a DB transaction").isFalse();
            assertThat(dataSource.getHikariPoolMXBean().getActiveConnections()).isZero();
            var browsing = pool.submit(() -> {
                assertThat(call("GET", "/profiles/me", token, null).statusCode()).isEqualTo(200);
                return call("PATCH", "/profiles/me", token, Map.of("version", "0", "name", "While hashing"));
            });
            assertThat(browsing.get(3, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
            controlled.release.countDown();
            assertThat(changing.get(10, TimeUnit.SECONDS).statusCode())
                    .isEqualTo(phase.equals("wrong-current") ? 400 : 200);
            assertThat(jdbc.queryForObject("SELECT version FROM users WHERE id=2", Long.class))
                    .isEqualTo(phase.equals("wrong-current") ? 1L : 2L);
        } finally {
            controlled.release.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
            controlled.blockedValue = null;
        }
    }

    @ParameterizedTest @ValueSource(strings = {"hash", "auth-version", "inactive"})
    void passwordSnapshotIsRevalidatedAfterHashingBeforeAnyWriteOrToken(String change) throws Exception {
        String token = login(2);
        String interveningHash = encoder.encode("InterveningCc3");
        var controlled = (ControlledEncoder) encoder;
        controlled.arm(nextPassword, true);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var changing = pool.submit(() -> call("POST", "/profiles/me/password", token,
                    passwordRequest(password, nextPassword, nextPassword)));
            assertThat(controlled.entered.await(10, TimeUnit.SECONDS)).isTrue();
            var intervening = pool.submit(() -> {
                switch (change) {
                    case "hash" -> jdbc.update("UPDATE users SET password_hash=? WHERE id=2", interveningHash);
                    case "auth-version" -> jdbc.update("UPDATE users SET auth_version=auth_version+1 WHERE id=2");
                    default -> jdbc.update("UPDATE users SET status='INACTIVE' WHERE id=2");
                }
            });
            intervening.get(3, TimeUnit.SECONDS);
            var state = userState(2);
            controlled.release.countDown();
            var result = changing.get(10, TimeUnit.SECONDS);
            assertThat(result.statusCode()).isEqualTo(change.equals("hash") ? 409 : 401);
            assertThat(result.body()).doesNotContain("\"token\"", nextPassword, interveningHash);
            assertThat(userState(2)).isEqualTo(state);
        } finally {
            controlled.release.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
            controlled.blockedValue = null;
        }
    }

    /** Only delays the selected real BCrypt operation; repositories, HTTP and MySQL stay real. */
    static class ControlledEncoder implements PasswordEncoder {
        final PasswordEncoder delegate = new BCryptPasswordEncoder();
        volatile String blockedValue;
        volatile boolean blockEncode;
        volatile boolean transactionActive;
        volatile CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        void arm(String value, boolean encode) {
            entered = new CountDownLatch(1);
            release = new CountDownLatch(1);
            transactionActive = false;
            blockEncode = encode;
            blockedValue = value;
        }
        private void delay(CharSequence value, boolean encode) {
            if (blockedValue == null || blockEncode != encode || !blockedValue.contentEquals(value)) return;
            transactionActive = TransactionSynchronizationManager.isActualTransactionActive();
            entered.countDown();
            try {
                if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Encoder gate timed out");
            } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
        }
        public String encode(CharSequence value) { delay(value, true); return delegate.encode(value); }
        public boolean matches(CharSequence value, String hash) { delay(value, false); return delegate.matches(value, hash); }
    }

    private Map<String, Object> userState(int id) { return jdbc.queryForMap("SELECT password_hash,password_changed_at,must_change_password,auth_version,version,full_name,phone FROM users WHERE id=?", id); }
    private String login(int id) throws Exception {
        var result = call("POST", "/auth/login", "", Map.of("username", "profile_" + id, "password", password));
        assertThat(result.statusCode()).isEqualTo(200);
        return (String) value(result, "$.token");
    }
    private Map<String, String> passwordRequest(String current, String next, String confirmation) {
        return Map.of("currentPassword", current, "newPassword", next, "confirmNewPassword", confirmation);
    }
    private String expired() {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(("1:profile_1:ADMIN:0:" + Instant.now().minusSeconds(60).toEpochMilli()).getBytes(StandardCharsets.UTF_8));
        return payload + "." + tokens.hmacSha256(payload);
    }
    private String resetGrant() {
        long version = jdbc.queryForObject("SELECT auth_version FROM users WHERE id=2", Long.class);
        String id = tokens.generateRandomHex(32);
        SCHEMA.update("INSERT INTO auth_otp_challenges(id,user_id,email,purpose,code_hash,expires_at,next_send_at,used_at,delivery_status) VALUES(?,2,'profile_2@example.test','RESET',?,DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 10 MINUTE),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'SENT')", id, tokens.hmacSha256("synthetic"));
        return tokens.generateResetGrantToken(id, 2L, version, 600);
    }
    private Object value(HttpResponse<String> response, String path) { return JsonPath.read(response.body(), path); }
    private HttpResponse<String> call(String method, String path, String token, Object body) throws Exception {
        return raw(method, path, token, body == null ? null : JSON.writeValueAsString(body));
    }
    private HttpResponse<String> raw(String method, String path, String token, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json");
        if (token != null && !token.isEmpty()) request.header("Authorization", "Bearer " + token);
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private List<Integer> parallel(Callable<Integer> first, Callable<Integer> second) throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { start.await(); return first.call(); });
            var b = pool.submit(() -> { start.await(); return second.call(); });
            start.countDown();
            return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        }
    }
}
