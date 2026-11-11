package vn.codegym.smartcafe.billing;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD:}",
        "smartcafe.auth.token-secret=test-secret-at-least-32-bytes-long-for-testing!"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:mysql:.*")
@Transactional
class BillMySqlIntegrationTest {
    private static final String SECRET = "test-secret-at-least-32-bytes-long-for-testing!";

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            // Fixed clock at 2026-11-11T10:00:00Z (17:00 in Asia/Ho_Chi_Minh)
            return Clock.fixed(Instant.parse("2026-11-11T10:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    private String bearer(int id, String username) throws Exception {
        long expiry = Instant.now().plusSeconds(3600).toEpochMilli();
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                (id + ":" + username + ":ANY:" + expiry).getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "Bearer " + payload + "." + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    @BeforeEach
    void setupDatabase() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).endsWith("_test");
        // Insert base users
        jdbc.update("""
                INSERT INTO users(id, username, password_hash, full_name, email, role, status)
                VALUES (1, 'admin_u', 'test-only', 'Quản trị viên', 'admin@example.invalid', 'ADMIN', 'ACTIVE'),
                       (2, 'staff_u', 'test-only', 'Nhân viên Nam', 'staff@example.invalid', 'STAFF', 'ACTIVE'),
                       (3, 'cust_u', 'test-only', 'Khách hàng A', 'cust@example.invalid', 'CUSTOMER', 'ACTIVE')
                """);
        jdbc.update("""
                INSERT INTO cafe_tables(id, code, name, qr_public_id)
                VALUES (1, 'T001', 'Bàn 1', UUID()),
                       (2, 'T002', 'Bàn 2', UUID())
                """);
        jdbc.update("""
                INSERT INTO table_sessions(id, table_id, session_code, opened_by_user_id)
                VALUES (1, 1, 'SESS-001', 2),
                       (2, 2, 'SESS-002', 2)
                """);
        jdbc.update("""
                INSERT INTO menu_categories(id, code, name, active)
                VALUES (1, 'CF', 'Cà phê', 1)
                """);
        jdbc.update("""
                INSERT INTO menu_items(id, category_id, code, name, price, active)
                VALUES (1, 1, 'CF-01', 'Cà phê sữa', 35000, 1),
                       (2, 1, 'CF-02', 'Bạc xỉu', 40000, 1),
                       (3, 1, 'CF-03', 'Espresso', 30000, 1)
                """);
        jdbc.update("""
                INSERT INTO order_batches(id, order_code, session_id, submitted_by_user_id, submitted_at,
                    editable_until, edit_policy, idempotency_key, request_hash)
                VALUES (1, 'ORD-001', 1, 2, '2026-11-10 10:00:00', '2026-11-10 10:05:00', 'DEADLINE_ONLY', 'k1', REPEAT('0', 64)),
                       (2, 'ORD-002', 2, 2, '2026-11-11 08:00:00', '2026-11-11 08:05:00', 'DEADLINE_ONLY', 'k2', REPEAT('1', 64))
                """);
        jdbc.update("""
                INSERT INTO order_items(id, session_id, order_batch_id, menu_item_id, item_code, item_name, unit_price, quantity, status)
                VALUES (1, 1, 1, 1, 'CF-01', 'Cà phê sữa', 35000, 2, 'SUBMITTED'),
                       (2, 1, 1, 2, 'CF-02', 'Bạc xỉu', 40000, 1, 'SUBMITTED'),
                       (3, 2, 2, 3, 'CF-03', 'Espresso', 30000, 3, 'SUBMITTED')
                """);
        // Bill 1: Created on Nov 10, Paid on Nov 11 (Different created_at and paid_at)
        jdbc.update("""
                INSERT INTO bills(id, bill_code, session_id, created_by_user_id, creator_name_snapshot,
                    table_code_snapshot, table_name_snapshot, total_amount, status, created_at, paid_at,
                    idempotency_key, request_hash)
                VALUES (1, 'BILL-20261110-001', 1, 2, 'Nhân viên Nam', 'T001', 'Bàn 1', 110000, 'PAID',
                    '2026-11-10 10:30:00', '2026-11-11 09:00:00', 'bkey-1', REPEAT('a', 64))
                """);
        jdbc.update("""
                INSERT INTO bill_items(id, bill_id, session_id, order_item_id, item_code, item_name, unit_price, quantity)
                VALUES (1, 1, 1, 1, 'CF-01', 'Cà phê sữa', 35000, 2),
                       (2, 1, 1, 2, 'CF-02', 'Bạc xỉu', 40000, 1)
                """);
        // Bill 2: PENDING on Nov 11
        jdbc.update("""
                INSERT INTO bills(id, bill_code, session_id, created_by_user_id, creator_name_snapshot,
                    table_code_snapshot, table_name_snapshot, total_amount, status, created_at, paid_at,
                    idempotency_key, request_hash)
                VALUES (2, 'BILL-20261111-002', 2, 2, 'Nhân viên Nam', 'T002', 'Bàn 2', 90000, 'PENDING',
                    '2026-11-11 08:30:00', NULL, 'bkey-2', REPEAT('b', 64))
                """);
        jdbc.update("""
                INSERT INTO bill_items(id, bill_id, session_id, order_item_id, item_code, item_name, unit_price, quantity)
                VALUES (3, 2, 2, 3, 'CF-03', 'Espresso', 30000, 3)
                """);
    }

    // --- 1. Security & Permissions Tests ---

    @Test
    void unauthenticatedAccessIsBlocked() throws Exception {
        mvc.perform(get("/api/v1/bills")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/bills/1")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/statistics/revenue")).andExpect(status().isForbidden());
    }

    @Test
    void staffRoleIsForbiddenFromManagerBills() throws Exception {
        String staffAuth = bearer(2, "staff_u");
        mvc.perform(get("/api/v1/bills").header("Authorization", staffAuth)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/bills/1").header("Authorization", staffAuth)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/statistics/revenue").header("Authorization", staffAuth)).andExpect(status().isForbidden());
    }

    @Test
    void customerRoleIsForbidden() throws Exception {
        String custAuth = bearer(3, "cust_u");
        mvc.perform(get("/api/v1/bills").header("Authorization", custAuth)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/bills/1").header("Authorization", custAuth)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/statistics/revenue").header("Authorization", custAuth)).andExpect(status().isForbidden());
    }

    // --- 2. Bill List & Search Tests ---

    @Test
    void listBillsWithDefaultPaginationAndSort() throws Exception {
        String adminAuth = bearer(1, "admin_u");
        mvc.perform(get("/api/v1/bills").header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content.length()").value(2))
                // Default sort createdAt,desc -> Bill 2 first, then Bill 1
                .andExpect(jsonPath("$.content[0].billCode").value("BILL-20261111-002"))
                .andExpect(jsonPath("$.content[0].tableCode").value("T002"))
                .andExpect(jsonPath("$.content[0].tableName").value("Bàn 2"))
                .andExpect(jsonPath("$.content[0].creatorName").value("Nhân viên Nam"))
                .andExpect(jsonPath("$.content[0].totalAmount").value("90000"))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[1].billCode").value("BILL-20261110-001"))
                .andExpect(jsonPath("$.content[1].status").value("PAID"));
    }

    @Test
    void searchBillByCodeExactAndPartial() throws Exception {
        String adminAuth = bearer(1, "admin_u");
        mvc.perform(get("/api/v1/bills").header("Authorization", adminAuth).param("code", "BILL-20261110-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].billCode").value("BILL-20261110-001"));

        mvc.perform(get("/api/v1/bills").header("Authorization", adminAuth).param("code", "002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].billCode").value("BILL-20261111-002"));

        mvc.perform(get("/api/v1/bills").header("Authorization", adminAuth).param("code", "NONEXISTENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty());

        mvc.perform(get("/api/v1/bills").header("Authorization", adminAuth).param("code", "   "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wildcardCharactersInBillSearchAreLiteral() throws Exception {
        for (String keyword : new String[] {"%", "_", "!"}) {
            mvc.perform(get("/api/v1/bills").header("Authorization", bearer(1, "admin_u"))
                    .param("code", keyword))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    @Test
    void searchBillByCreatedDate() throws Exception {
        String adminAuth = bearer(1, "admin_u");
        // Bill 1 was created on 2026-11-10
        mvc.perform(get("/api/v1/bills").header("Authorization", adminAuth)
                .param("from", "2026-11-10")
                .param("to", "2026-11-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].billCode").value("BILL-20261110-001"));

        // Bill 2 was created on 2026-11-11
        mvc.perform(get("/api/v1/bills").header("Authorization", adminAuth)
                .param("from", "2026-11-11")
                .param("to", "2026-11-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].billCode").value("BILL-20261111-002"));

        // Invalid date range from > to
        mvc.perform(get("/api/v1/bills").header("Authorization", adminAuth)
                .param("from", "2026-11-12")
                .param("to", "2026-11-10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void filterBillByStatus() throws Exception {
        String adminAuth = bearer(1, "admin_u");
        mvc.perform(get("/api/v1/bills").header("Authorization", adminAuth).param("status", "PAID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("PAID"));

        mvc.perform(get("/api/v1/bills").header("Authorization", adminAuth).param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));

        mvc.perform(get("/api/v1/bills").header("Authorization", adminAuth).param("status", "INVALID_STATUS"))
                .andExpect(status().isBadRequest());
    }

    // --- 3. Bill Detail & Snapshot Immutability Tests ---

    @Test
    void getBillDetailReturnsCompleteSnapshot() throws Exception {
        String adminAuth = bearer(1, "admin_u");
        mvc.perform(get("/api/v1/bills/1").header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.billCode").value("BILL-20261110-001"))
                .andExpect(jsonPath("$.sessionCode").value("SESS-001"))
                .andExpect(jsonPath("$.creatorName").value("Nhân viên Nam"))
                .andExpect(jsonPath("$.tableCode").value("T001"))
                .andExpect(jsonPath("$.tableName").value("Bàn 1"))
                .andExpect(jsonPath("$.totalAmount").value("110000"))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].itemName").value("Cà phê sữa"))
                .andExpect(jsonPath("$.items[0].unitPrice").value("35000"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].lineTotal").value("70000"))
                .andExpect(jsonPath("$.items[1].itemName").value("Bạc xỉu"))
                .andExpect(jsonPath("$.items[1].quantity").value(1))
                .andExpect(jsonPath("$.items[1].lineTotal").value("40000"));
    }

    @Test
    void billDetailRetainsHistoricalSnapshotEvenIfCurrentMenuOrTableChanges() throws Exception {
        String adminAuth = bearer(1, "admin_u");
        // Change current table name and menu price in database
        jdbc.update("UPDATE cafe_tables SET name='Bàn VIP Mới' WHERE id=1");
        jdbc.update("UPDATE menu_items SET name='Cà phê sữa Sài Gòn', price=99000 WHERE id=1");

        // Bill detail MUST return original snapshot
        mvc.perform(get("/api/v1/bills/1").header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableName").value("Bàn 1")) // Preserved snapshot!
                .andExpect(jsonPath("$.items[0].itemName").value("Cà phê sữa")) // Preserved snapshot!
                .andExpect(jsonPath("$.items[0].unitPrice").value("35000")); // Preserved snapshot!
    }

    @Test
    void billDetailReturnsNotFoundForNonExistentId() throws Exception {
        String adminAuth = bearer(1, "admin_u");
        mvc.perform(get("/api/v1/bills/99999").header("Authorization", adminAuth))
                .andExpect(status().isNotFound());
    }

    // --- 4. Statistics & Revenue Reconciliation Tests ---

    @Test
    void statisticsCalculatesRevenueFromPaidAtNotCreatedAt() throws Exception {
        String adminAuth = bearer(1, "admin_u");
        // Bill 1 was created on Nov 10, but paid on Nov 11 at 09:00:00 (UTC 09:00:00 -> 16:00:00 VN)
        // Bill 2 was created on Nov 11, but is PENDING -> excluded from revenue!

        // Query Today (Nov 11): should include Bill 1 (paid_at is Nov 11 VN time), total = 110,000
        mvc.perform(get("/api/v1/statistics/revenue").header("Authorization", adminAuth).param("period", "today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalRevenue").value("110000"))
                .andExpect(jsonPath("$.summary.totalBills").value(1))
                .andExpect(jsonPath("$.summary.averageBillAmount").value("110000"))
                .andExpect(jsonPath("$.dailyRevenue.length()").value(1))
                .andExpect(jsonPath("$.dailyRevenue[0].date").value("2026-11-11"))
                .andExpect(jsonPath("$.dailyRevenue[0].revenue").value("110000"))
                .andExpect(jsonPath("$.dailyRevenue[0].billCount").value(1));

        // Query Nov 10 (when Bill 1 was created): revenue should be 0 because paid_at was Nov 11!
        mvc.perform(get("/api/v1/statistics/revenue").header("Authorization", adminAuth)
                .param("from", "2026-11-10")
                .param("to", "2026-11-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalRevenue").value("0"))
                .andExpect(jsonPath("$.summary.totalBills").value(0))
                .andExpect(jsonPath("$.summary.averageBillAmount").value("0"))
                .andExpect(jsonPath("$.dailyRevenue[0].revenue").value("0"));
    }

    @Test
    void statisticsFillsMissingDaysWithZero() throws Exception {
        String adminAuth = bearer(1, "admin_u");
        // Query Nov 08 to Nov 11 (4 days)
        mvc.perform(get("/api/v1/statistics/revenue").header("Authorization", adminAuth)
                .param("from", "2026-11-08")
                .param("to", "2026-11-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyRevenue.length()").value(4))
                .andExpect(jsonPath("$.dailyRevenue[0].date").value("2026-11-08"))
                .andExpect(jsonPath("$.dailyRevenue[0].revenue").value("0"))
                .andExpect(jsonPath("$.dailyRevenue[1].date").value("2026-11-09"))
                .andExpect(jsonPath("$.dailyRevenue[1].revenue").value("0"))
                .andExpect(jsonPath("$.dailyRevenue[2].date").value("2026-11-10"))
                .andExpect(jsonPath("$.dailyRevenue[2].revenue").value("0"))
                .andExpect(jsonPath("$.dailyRevenue[3].date").value("2026-11-11"))
                .andExpect(jsonPath("$.dailyRevenue[3].revenue").value("110000"));
    }
}
