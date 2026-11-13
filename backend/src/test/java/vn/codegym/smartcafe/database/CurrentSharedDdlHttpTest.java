package vn.codegym.smartcafe.database;

import com.jayway.jsonpath.JsonPath;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.codegym.smartcafe.auth.EmailService;

import javax.sql.DataSource;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Current DDL diagnostics and a candidate expansion; neither is an approved20-table target. */
@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@DirtiesContext
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@org.junit.jupiter.api.parallel.Isolated("Checks JDBC UTC against a different JVM time zone")
class CurrentSharedDdlHttpTest {
    private static final TestSchema SCHEMA = currentSchema();
    private static TestSchema currentSchema() {
        TestSchema schema = new TestSchema("db/current-shared-2026-11-13.sql");
        schema.sharedHistory();
        return schema;
    }
    @DynamicPropertySource static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SCHEMA.source::getUrl);
        registry.add("spring.datasource.username", SCHEMA.source::getUsername);
        registry.add("spring.datasource.password", SCHEMA.source::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired DataSource source;
    @MockitoBean EmailService mail;
    private final AtomicReference<String> otp = new AtomicReference<>();
    private String password;

    @BeforeEach void fixtures() {
        SCHEMA.assertIsolated();
        SCHEMA.update("DELETE FROM auth_otp_challenges");
        SCHEMA.update("DELETE FROM auth_otp_limits");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='staff_profiles'", Integer.class) == 1) SCHEMA.update("DELETE FROM staff_profiles");
        SCHEMA.update("DELETE FROM users");
        password = "Aa1!" + UUID.randomUUID();
        for (String role : new String[]{"ADMIN", "STAFF"}) {
            String username = "schema_" + role.toLowerCase();
            SCHEMA.update("INSERT INTO users(username,password_hash,full_name,email,role,status) VALUES(?,?,?,?,?,'ACTIVE')",
                    username, passwords.encode(password), "Synthetic schema fixture", username + "@example.test", role);
        }
        doAnswer(call -> { otp.set(call.getArgument(1)); return null; }).when(mail).sendOtpEmail(anyString(), anyString(), anyString(), anyLong());
    }

    @Test @Order(1) void currentDdlAuthenticatesButHomeAccountIsUnavailable() throws Exception {
        String admin = login("schema_admin", password);
        String staff = login("schema_staff", password);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + staff)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", "Bearer " + staff)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/home/account").header("Authorization", "Bearer " + staff)).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/home/notifications").header("Authorization", "Bearer " + staff)).andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('staff_profiles','media_assets')", Integer.class)).isZero();
        System.out.println("CURRENT_SHARED_DDL Auth/RBAC=PASS HomeAccount=503 EXPECTED_INCOMPATIBLE");
    }

    @Test @Order(2) void candidateExpansionAllowsRecoveryAndHomeWithoutReplacingTheJoins() throws Exception {
        SCHEMA.assertIsolated();
        var before = jdbc.queryForList("SELECT installed_rank,version,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank");
        var sql = new ResourceDatabasePopulator(new FileSystemResource("../docs/database/proposals/2026-11-13-dt2-2/home-dependencies.sql"));
        sql.setSqlScriptEncoding("UTF-8");
        sql.execute(source);
        SCHEMA.assertIsolated();
        SCHEMA.update("INSERT INTO staff_profiles(user_id,address,salary) SELECT id,'Synthetic address',1000000 FROM users WHERE username='schema_staff'");
        String oldBearer = login("schema_staff", password);
        String challenge = JsonPath.read(postJson("forgot-password", "{\"email\":\"schema_staff@example.test\"}", 200), "$.challengeId");
        postJson("verify-otp", "{\"challengeId\":\"" + challenge + "\",\"otp\":\"" + (otp.get().equals("000000") ? "111111" : "000000") + "\"}", 400);
        SCHEMA.assertIsolated();
        SCHEMA.update("UPDATE auth_otp_challenges SET next_send_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 SECOND) WHERE id=?", challenge);
        postJson("resend-otp", "{\"challengeId\":\"" + challenge + "\"}", 200);
        String grant = JsonPath.read(postJson("verify-otp", "{\"challengeId\":\"" + challenge + "\",\"otp\":\"" + otp.get() + "\"}", 200), "$.token");
        String next = "Bb2!" + UUID.randomUUID();
        postJson("reset-password", "{\"token\":\"" + grant + "\",\"newPassword\":\"" + next + "\",\"confirmPassword\":\"" + next + "\"}", 200);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + oldBearer)).andExpect(status().isUnauthorized());
        String current = login("schema_staff", next);
        mvc.perform(get("/api/v1/home/account").header("Authorization", "Bearer " + current))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("schema_staff"))
                .andExpect(jsonPath("$.address").value("Synthetic address"));
        SCHEMA.update("INSERT INTO cafe_tables(table_number,name,status) VALUES(77,'Synthetic table','ACTIVE')");
        Long table = jdbc.queryForObject("SELECT id FROM cafe_tables WHERE table_number=77", Long.class);
        SCHEMA.update("INSERT INTO table_sessions(table_id,session_code,status) VALUES(?,'NOTICE_FIXTURE','OPEN')", table);
        Long session = jdbc.queryForObject("SELECT id FROM table_sessions WHERE session_code='NOTICE_FIXTURE'", Long.class);
        java.time.Instant noticeTime = java.time.Instant.parse("2030-04-05T06:07:08Z");
        SCHEMA.update("INSERT INTO service_requests(table_id,session_id,request_type,status,created_at) VALUES(?,?,'WAITER','PENDING',?)", table, session, java.sql.Timestamp.from(noticeTime));
        java.util.TimeZone previousZone = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
            mvc.perform(get("/api/v1/home/notifications").header("Authorization", "Bearer " + current))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].createdAt").value(noticeTime.toString()));
        } finally { java.util.TimeZone.setDefault(previousZone); }
        assertThat(jdbc.queryForList("SELECT installed_rank,version,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank")).isEqualTo(before);
        System.out.println("CANDIDATE_SHARED_PLUS5 Recovery/NewLogin/HomeAccount/Notifications=PASS APPROVED_TARGET=false");
    }

    private String login(String username, String candidate) throws Exception {
        return JsonPath.read(postJson("login", "{\"username\":\"" + username + "\",\"password\":\"" + candidate + "\"}", 200), "$.token");
    }
    private String postJson(String path, String body, int status) throws Exception {
        return mvc.perform(post("/api/v1/auth/" + path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(status)).andReturn().getResponse().getContentAsString();
    }
    @AfterAll void cleanup() throws Exception {
        source.unwrap(HikariDataSource.class).close();
        SCHEMA.close();
    }
}
