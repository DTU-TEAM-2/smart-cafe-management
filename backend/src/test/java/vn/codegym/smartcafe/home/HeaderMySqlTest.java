package vn.codegym.smartcafe.home;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}", "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD:}",
        "smartcafe.auth.token-secret=header-test-signing-secret-only-2026"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:mysql:.*")
@Transactional
class HeaderMySqlTest {
    private static final String SECRET = "header-test-signing-secret-only-2026";
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @BeforeEach
    void seedIsolatedDatabase() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).endsWith("_test");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isZero();
        for (int id = 1; id <= 4; id++) {
            String role = id == 1 ? "ADMIN" : id == 3 ? "CUSTOMER" : "STAFF";
            jdbc.update("""
                    INSERT INTO users(id,username,password_hash,full_name,email,role,status)
                    VALUES (?,?,?,?,?,?,?)
                    """, id, "header-" + id, "not-a-login-hash", "Tài khoản " + id, "header-" + id + "@example.invalid", role, id == 4 ? "INACTIVE" : "ACTIVE");
        }
        jdbc.update("INSERT INTO staff_profiles(user_id,address,salary) VALUES (1,'Đà Nẵng',10000000),(2,'Đà Nẵng',8000000)");
    }

    @Test
    void accountAlwaysUsesAuthenticatedIdentityAndNeverExposesPassword() throws Exception {
        mvc.perform(get("/api/v1/home/account").header("Authorization", bearer(2)).param("userId", "1"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value("2")).andExpect(jsonPath("$.name").value("Tài khoản 2"))
                .andExpect(jsonPath("$.salary").value("8000000"))
                .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/v1/home/account").header("Authorization", bearer(3)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("3"))
                .andExpect(jsonPath("$.salary").value(nullValue()));
    }

    @Test
    void staffAndAdminGetUniquePendingOrderAndWaiterEventsFromSharedTables() throws Exception {
        seedEvents();
        for (int id = 1; id <= 2; id++) {
            mvc.perform(get("/api/v1/home/notifications").header("Authorization", bearer(id)).param("recipientId", "999"))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.recipientId").value(String.valueOf(id)))
                    .andExpect(jsonPath("$.items.length()").value(2));
        }
        // Processing/cancelling an order or acknowledging service removes it from the pending snapshot.
        jdbc.update("UPDATE order_items SET status='PREPARING', preparation_started_at=CURRENT_TIMESTAMP WHERE id=1");
        jdbc.update("UPDATE service_requests SET status='ACKNOWLEDGED', acknowledged_at=CURRENT_TIMESTAMP(6), acknowledged_by_user_id=2 WHERE id=1");
        mvc.perform(get("/api/v1/home/notifications").header("Authorization", bearer(2)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void guestCustomerDisabledAndChangedRoleCannotReadStaffFeed() throws Exception {
        mvc.perform(get("/api/v1/home/account")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/home/notifications")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/home/notifications").header("Authorization", bearer(3))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/home/account").header("Authorization", bearer(4))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/home/notifications").header("Authorization", "Bearer made-up-staff-token")).andExpect(status().isForbidden());
        jdbc.update("UPDATE users SET role='CUSTOMER' WHERE id=2");
        mvc.perform(get("/api/v1/home/notifications").header("Authorization", bearer(2))).andExpect(status().isForbidden());
    }

    @Test
    void expiredTamperedWrongIdentityAndRevokedTokensAreRejected() throws Exception {
        mvc.perform(get("/api/v1/home/account").header("Authorization", signed(2, "header-2", java.time.Instant.now().minusSeconds(5).toEpochMilli())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/home/account").header("Authorization", signed(1, "header-2", java.time.Instant.now().plusSeconds(60).toEpochMilli())))
                .andExpect(status().isForbidden());
        String token = bearer(2);
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("0") ? "1" : "0");
        mvc.perform(get("/api/v1/home/account").header("Authorization", tampered)).andExpect(status().isForbidden());
        jdbc.update("UPDATE users SET auth_version=1 WHERE id=2");
        mvc.perform(get("/api/v1/home/account").header("Authorization", token)).andExpect(status().isForbidden());
    }

    private String bearer(int id) throws Exception {
        return signed(id, "header-" + id, java.time.Instant.now().plusSeconds(3600).toEpochMilli());
    }

    private String signed(int id, String username, long expiry) throws Exception {
        // Matches the existing authentication module's four-field bearer contract.
        // The payload always claims ADMIN to ensure the server uses the current database role instead.
        String payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                (id + ":" + username + ":ADMIN:" + expiry).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        return "Bearer " + payload + "." + java.util.HexFormat.of().formatHex(mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private void seedEvents() {
        jdbc.update("INSERT INTO menu_categories(id,code,name) VALUES (1,'TEST','Mẫu')");
        jdbc.update("INSERT INTO menu_items(id,category_id,code,name,price) VALUES (1,1,'TEST','Bạc xỉu',35000)");
        jdbc.update("INSERT INTO cafe_tables(id,code,name,qr_public_id) VALUES (1,'TEST','Bàn mẫu',UUID())");
        jdbc.update("INSERT INTO table_sessions(id,table_id,session_code,opened_by_user_id) VALUES (1,1,'TEST',2)");
        jdbc.update("""
                INSERT INTO session_clients(id,session_id,access_token_hash,expires_at)
                VALUES (1,1,REPEAT('a',64),CURRENT_TIMESTAMP + INTERVAL 1 HOUR)
                """);
        jdbc.update("""
                INSERT INTO order_batches(id,order_code,session_id,submitted_by_user_id,editable_until,edit_policy,idempotency_key,request_hash)
                VALUES (1,'TEST',1,2,CURRENT_TIMESTAMP + INTERVAL 3 MINUTE,'DEADLINE_ONLY','test',REPEAT('0',64))
                """);
        jdbc.update("""
                INSERT INTO order_items(id,session_id,order_batch_id,menu_item_id,item_code,item_name,unit_price,quantity)
                VALUES (1,1,1,1,'TEST','Bạc xỉu',35000,1)
                """);
        jdbc.update("""
                INSERT INTO service_requests(id,session_id,requested_by_client_id,request_type,idempotency_key)
                VALUES (1,1,1,'WAITER','test')
                """);
    }
}
