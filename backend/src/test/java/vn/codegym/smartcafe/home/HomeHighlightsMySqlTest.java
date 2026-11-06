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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD:}"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:mysql:.*")
@Transactional
class HomeHighlightsMySqlTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @BeforeEach
    void requireEmptyTestDatabase() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).endsWith("_test");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM menu_items", Integer.class)).isZero();
    }

    @Test
    void emptyDatabaseAndProtectedRoutes() throws Exception {
        mvc.perform(get("/api/v1/home/highlights")).andExpect(status().isOk())
                .andExpect(jsonPath("$.newest").isEmpty()).andExpect(jsonPath("$.mostOrdered").isEmpty());
        mvc.perform(post("/api/v1/home/highlights")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/account")).andExpect(status().isForbidden());
    }

    @Test
    void respectsLimitTiesActiveCategoriesAndCancelledOrders() throws Exception {
        jdbc.update("INSERT INTO menu_categories(id, code, name, active) VALUES (1,'OPEN','Cà phê',1),(2,'HIDDEN','Nhóm ẩn',0)");
        addItem(1, 1, "Cà phê sữa", true);
        addItem(2, 1, "Bạc xỉu", true);

        mvc.perform(get("/api/v1/home/highlights")).andExpect(status().isOk())
                .andExpect(jsonPath("$.newest.length()").value(2))
                .andExpect(jsonPath("$.newest[0].name").value("Bạc xỉu"))
                .andExpect(jsonPath("$.mostOrdered").isEmpty());

        for (int id = 3; id <= 6; id++) addItem(id, 1, "Món " + id, true);
        addItem(7, 1, "Món ẩn", false);
        addItem(8, 2, "Món thuộc nhóm ẩn", true);

        jdbc.update("""
                INSERT INTO users(id, username, password_hash, full_name, email, role)
                VALUES (1, 'dt21-fixture', 'test-only', 'Kiểm thử', 'dt21@example.invalid', 'STAFF')
                """);
        jdbc.update("INSERT INTO cafe_tables(id, code, name, qr_public_id) VALUES (1,'T001','Bàn 1',UUID())");
        jdbc.update("INSERT INTO table_sessions(id, table_id, session_code, opened_by_user_id) VALUES (1,1,'TEST',1)");
        jdbc.update("""
                INSERT INTO order_batches(id, order_code, session_id, submitted_by_user_id, submitted_at,
                    editable_until, edit_policy, idempotency_key, request_hash)
                VALUES (1,'TEST-ORDER',1,1,'2026-11-06 00:00:00','2026-11-06 00:00:00','DEADLINE_ONLY','test',REPEAT('0',64))
                """);
        for (int id = 1; id <= 8; id++) {
            int quantity = id == 6 ? 99 : id >= 7 ? 100 : 3;
            jdbc.update("""
                    INSERT INTO order_items(session_id, order_batch_id, menu_item_id, item_code, item_name, unit_price,
                        quantity, status, cancelled_at, cancellation_reason)
                    SELECT 1,1,id,code,name,price,?,IF(id=6,'CANCELLED','SUBMITTED'),
                        IF(id=6,CURRENT_TIMESTAMP,NULL),IF(id=6,'Fixture',NULL) FROM menu_items WHERE id=?
                    """, quantity, id);
        }

        mvc.perform(get("/api/v1/home/highlights")).andExpect(status().isOk())
                .andExpect(jsonPath("$.newest.length()").value(5))
                .andExpect(jsonPath("$.newest[0].price").value("45000"))
                .andExpect(jsonPath("$.mostOrdered.length()").value(5))
                .andExpect(jsonPath("$.mostOrdered[0].name").value("Cà phê sữa"))
                .andExpect(jsonPath("$.mostOrdered[0].orderedQuantity").value("3"));
        for (int index = 0; index < 5; index++) {
            mvc.perform(get("/api/v1/home/highlights"))
                    .andExpect(jsonPath("$.newest[" + index + "].id").value(String.valueOf(6 - index)))
                    .andExpect(jsonPath("$.mostOrdered[" + index + "].id").value(String.valueOf(index + 1)));
        }
    }

    private void addItem(int id, int category, String name, boolean active) {
        jdbc.update("""
                INSERT INTO menu_items(id, category_id, code, name, price, created_at, active)
                VALUES (?, ?, ?, ?, 45000, '2026-11-06 00:00:00', ?)
                """, id, category, "M" + id, name, active);
    }
}
