package vn.codegym.smartcafe.customersession;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}", "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD:}"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:mysql:.*")
@Transactional
class CustomerTableMySqlTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @Test
    void resolvesExactActiveTableAndRejectsInvalidInactiveOrUnknownQrWithoutWrites() throws Exception {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).endsWith("_test");
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM table_sessions", Long.class);
        long clientsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM session_clients", Long.class);
        jdbc.update("""
                INSERT INTO cafe_tables(id,code,name,status,qr_public_id) VALUES
                (81001,'QR_TEST_A','Bàn QR A','ACTIVE','7d57e0b0-dfca-4ed3-846a-5b77e4450001'),
                (81002,'QR_TEST_B','Bàn QR B','ACTIVE','7d57e0b0-dfca-4ed3-846a-5b77e4450002'),
                (81003,'QR_TEST_STOP','Bàn tạm ngừng','OUT_OF_SERVICE','7d57e0b0-dfca-4ed3-846a-5b77e4450003'),
                (81004,'QR_TEST_ARCHIVE','Bàn lưu trữ','ARCHIVED','7d57e0b0-dfca-4ed3-846a-5b77e4450004')
                """);
        String root = "/api/v1/customer/tables/qr/";
        for (int repeat = 0; repeat < 2; repeat++) {
            mvc.perform(get(root + "7d57e0b0-dfca-4ed3-846a-5b77e4450001"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.tableId").value("81001"))
                    .andExpect(jsonPath("$.tableName").value("Bàn QR A"));
        }
        mvc.perform(get(root + "7d57e0b0-dfca-4ed3-846a-5b77e4450002"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tableId").value("81002"));
        mvc.perform(get(root + "7d57e0b0-dfca-4ed3-846a-5b77e4450003"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Bàn hiện không phục vụ."));
        mvc.perform(get(root + "7d57e0b0-dfca-4ed3-846a-5b77e4450004")).andExpect(status().isNotFound());
        mvc.perform(get(root + "7d57e0b0-dfca-4ed3-846a-5b77e4459999")).andExpect(status().isNotFound());
        mvc.perform(get(root + "bad-qr")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM table_sessions", Long.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM session_clients", Long.class)).isEqualTo(clientsBefore);
    }
}
