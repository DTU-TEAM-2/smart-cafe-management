package vn.codegym.smartcafe.auth;

import com.jayway.jsonpath.JsonPath;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.minimum-idle=2", "spring.datasource.hikari.connection-timeout=1000"})
@AutoConfigureMockMvc
@Timeout(25)
class OtpPoolIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource source;
    @Autowired TokenService tokens;
    @Autowired PasswordEncoder passwords;
    @MockitoBean EmailService mail;
    @MockitoSpyBean AuthRepository repository;
    private static final String EMAIL = "pool_fixture@example.test";

    @BeforeEach void setup() throws Exception {
        // Cold MySQL/JDBC setup is separate from the 1s stressed acquisition budget.
        source.unwrap(HikariDataSource.class).setConnectionTimeout(5000);
        cleanup();
        TestDatabase.update(jdbc, "INSERT INTO users(username,password_hash,full_name,email,role,status) VALUES('pool_fixture',?,'Pool fixture',?,'STAFF','ACTIVE')",
                passwords.encode("OldPassword@123"), EMAIL);
        source.unwrap(HikariDataSource.class).setConnectionTimeout(1000);
    }
    @AfterEach void cleanup() {
        TestDatabase.update(jdbc, "DELETE FROM auth_otp_challenges WHERE email = ?", EMAIL);
        TestDatabase.update(jdbc, "DELETE FROM users WHERE username = 'pool_fixture'");
    }

    private String fixture() {
        String id = tokens.generateRandomHex(32);
        TestDatabase.update(jdbc, """
                INSERT INTO auth_otp_challenges(id,user_id,email,purpose,code_hash,expires_at,next_send_at)
                SELECT ?,id,email,'RESET',?,DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 600 SECOND),DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 SECOND)
                FROM users WHERE username='pool_fixture'
                """, id, tokens.hmacSha256(id + "|RESET|123456"));
        return id;
    }

    @ParameterizedTest
    @CsvSource({"1,true,NONE", "2,true,NONE", "3,true,NONE", "2,false,NONE", "3,false,NONE",
            "3,true,RESEND", "3,true,RESET"})
    void wrongVerifiesDoNotStarveThePool(int requests, boolean sameChallenge, String concurrentAction) throws Exception {
        List<String> ids = new ArrayList<>();
        String first = fixture();
        for (int i = 0; i < requests; i++) ids.add(sameChallenge ? first : fixture());
        String other = fixture();
        String grant = null;
        if (concurrentAction.equals("RESET")) {
            MvcResult verified = request("verify-otp", "{\"challengeId\":\""+other+"\",\"otp\":\"123456\"}");
            assertThat(verified.getResponse().getStatus()).isEqualTo(200);
            grant = JsonPath.read(verified.getResponse().getContentAsString(), "$.token");
        }
        HikariDataSource pool = source.unwrap(HikariDataSource.class);
        var readsReady = new CountDownLatch(Math.min(2, requests));
        var releaseReads = new CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger();
        doAnswer(call -> {
            Object row = call.callRealMethod();
            if (ids.contains(call.getArgument(0)) && reads.incrementAndGet() <= Math.min(2, requests)) {
                readsReady.countDown();
                assertThat(releaseReads.await(8, TimeUnit.SECONDS)).isTrue();
            }
            return row;
        }).when(repository).findOtpChallengeById(anyString());
        try (var executor = Executors.newFixedThreadPool(requests + 1)) {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (String id : ids) futures.add(executor.submit(() -> request("verify-otp", "{\"challengeId\":\""+id+"\",\"otp\":\"000000\"}")));
            try {
                assertThat(readsReady.await(8, TimeUnit.SECONDS)).isTrue();
                int heldAfterReads = pool.getHikariPoolMXBean().getActiveConnections();
                Future<MvcResult> action = null;
                if (concurrentAction.equals("RESEND")) action = executor.submit(() -> request("resend-otp", "{\"challengeId\":\""+other+"\"}"));
                if (concurrentAction.equals("RESET")) {
                    String resetGrant = grant;
                    action = executor.submit(() -> request("reset-password", "{\"token\":\""+resetGrant+"\",\"newPassword\":\"NewPassword@123\",\"confirmPassword\":\"NewPassword@123\"}"));
                }
                // Other transactions must finish while wrong verifies remain in flight at the read checkpoint.
                if (action != null) assertThat(action.get(6, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
                releaseReads.countDown();
                List<Integer> statuses = new ArrayList<>();
                for (var future : futures) statuses.add(future.get(12, TimeUnit.SECONDS).getResponse().getStatus());
                assertThat(statuses).as("wrong verify statuses, active connections after SELECT=%s", heldAfterReads).containsOnly(400);
                System.out.printf("POOL_METRICS requests=%d same=%s action=%s max=%d min=%d timeoutMs=%d heldAtRead=%d statuses=%s%n",
                        requests, sameChallenge, concurrentAction, pool.getMaximumPoolSize(), pool.getMinimumIdle(),
                        pool.getConnectionTimeout(), heldAfterReads, statuses);
            } finally { releaseReads.countDown(); }
        }
        assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
        assertThat(pool.getHikariPoolMXBean().getThreadsAwaitingConnection()).isZero();
        for (String id : ids) assertThat(repository.findOtpChallengeById(id).orElseThrow().attempts()).isEqualTo(sameChallenge ? requests : 1);
        // Recovery after load and the persistent attempt cap, without another artificial read barrier.
        reset(repository);
        int attempts = sameChallenge ? requests : 1;
        for (int i = attempts; i < 6; i++) assertThat(request("verify-otp", "{\"challengeId\":\""+first+"\",\"otp\":\"000000\"}").getResponse().getStatus()).isEqualTo(400);
        assertThat(repository.findOtpChallengeById(first).orElseThrow().attempts()).isEqualTo(5);
        assertThat(request("verify-otp", "{\"challengeId\":\""+first+"\",\"otp\":\"123456\"}").getResponse().getStatus()).isEqualTo(400);
        String healthy = fixture();
        assertThat(request("verify-otp", "{\"challengeId\":\""+healthy+"\",\"otp\":\"123456\"}").getResponse().getStatus()).isEqualTo(200);
    }
    private MvcResult request(String route, String body) throws Exception {
        return mvc.perform(post("/api/v1/auth/"+route).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }
}
