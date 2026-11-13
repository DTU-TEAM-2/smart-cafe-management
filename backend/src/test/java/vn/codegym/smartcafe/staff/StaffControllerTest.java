package vn.codegym.smartcafe.staff;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD:}",
        "smartcafe.auth.token-secret=dt268-test-only-signing-key-at-least-32-characters"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:mysql:.*")
@Transactional
class StaffControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private Long testAdminId;
    private Long testStaffId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM staff_profiles");
        jdbc.update("DELETE FROM users");

        // Insert Admin test user
        jdbc.update("""
                INSERT INTO users(username, password_hash, full_name, email, phone, role, status)
                VALUES ('test_admin', 'hash', 'Test Admin', 'admin@test.vn', '0901111111', 'ADMIN', 'ACTIVE')
                """);
        testAdminId = jdbc.queryForObject("SELECT id FROM users WHERE username = 'test_admin'", Long.class);
        jdbc.update("""
                INSERT INTO staff_profiles(user_id, address, gender, salary)
                VALUES (?, 'Da Nang', 'MALE', 15000000)
                """, testAdminId);

        // Insert Staff test user
        jdbc.update("""
                INSERT INTO users(username, password_hash, full_name, email, phone, role, status)
                VALUES ('test_staff', 'hash', 'Test Staff Member', 'staff@test.vn', '0902222222', 'STAFF', 'ACTIVE')
                """);
        testStaffId = jdbc.queryForObject("SELECT id FROM users WHERE username = 'test_staff'", Long.class);
        jdbc.update("""
                INSERT INTO staff_profiles(user_id, address, gender, salary)
                VALUES (?, 'Hue', 'FEMALE', 6000000)
                """, testStaffId);
    }

    @Test
    void anonymousOrStaffAccessIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/staff"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/staff").with(user("test_staff").roles("STAFF")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessStaffListWithPagination() throws Exception {
        // Insert 12 more mock staff users
        for (int i = 1; i <= 12; i++) {
            String u = String.format("staff_extra%02d", i);
            jdbc.update("""
                    INSERT INTO users(username, password_hash, full_name, email, phone, role, status)
                    VALUES (?, 'hash', ?, ?, '0903000000', 'STAFF', 'ACTIVE')
                    """, u, "Extra Staff " + i, u + "@test.vn");
            Long uid = jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, u);
            jdbc.update("""
                    INSERT INTO staff_profiles(user_id, address, gender, salary)
                    VALUES (?, 'Da Nang', 'MALE', 7000000)
                    """, uid);
        }

        mvc.perform(get("/api/v1/staff?page=0&size=10").with(user("test_admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(10)))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(14)))
                .andExpect(jsonPath("$.totalPages", greaterThanOrEqualTo(2)));
    }

    @Test
    void searchStaffWithOrCondition() throws Exception {
        // Search by username
        mvc.perform(get("/api/v1/staff?keyword=test_staff").with(user("test_admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].username", is("test_staff")));

        // Search by phone
        mvc.perform(get("/api/v1/staff?keyword=0902222222").with(user("test_admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].phone", is("0902222222")));

        // Search not found
        mvc.perform(get("/api/v1/staff?keyword=not_found_random_123").with(user("test_admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", empty()));
    }

    @Test
    void createStaffValidatesRequiredFieldsAndRules() throws Exception {
        // Valid creation
        String validJson = """
                {
                    "username": "staff_valid1",
                    "fullName": "Nguyen Van Valid",
                    "email": "valid1@smartcafe.vn",
                    "phone": "0912345678",
                    "address": "123 Le Duan, Da Nang",
                    "gender": "MALE",
                    "birthday": "1998-05-20",
                    "role": "STAFF",
                    "salary": 6500000
                }
                """;
        mvc.perform(post("/api/v1/staff").with(user("test_admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username", is("staff_valid1")))
                .andExpect(jsonPath("$.salary", is(6500000)));

        // Username <= 6 chars -> 400
        String shortUserJson = validJson.replace("staff_valid1", "user1");
        mvc.perform(post("/api/v1/staff").with(user("test_admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shortUserJson))
                .andExpect(status().isBadRequest());

        // Username starts with digit -> 400
        String digitUserJson = validJson.replace("staff_valid1", "1staffuser");
        mvc.perform(post("/api/v1/staff").with(user("test_admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(digitUserJson))
                .andExpect(status().isBadRequest());

        // Username is admin or root -> 400
        String adminUserJson = validJson.replace("staff_valid1", "admin");
        mvc.perform(post("/api/v1/staff").with(user("test_admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminUserJson))
                .andExpect(status().isBadRequest());

        // Salary not multiple of 100k -> 400
        String invalidSalaryJson = validJson.replace("6500000", "6500050");
        mvc.perform(post("/api/v1/staff").with(user("test_admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidSalaryJson))
                .andExpect(status().isBadRequest());

        // Salary <= 0 -> 400
        String zeroSalaryJson = validJson.replace("6500000", "0");
        mvc.perform(post("/api/v1/staff").with(user("test_admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(zeroSalaryJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStaffPreservesUsernameAndUpdatesProfile() throws Exception {
        String updateJson = """
                {
                    "fullName": "Nguyen Updated Name",
                    "email": "updated@smartcafe.vn",
                    "phone": "0988776655",
                    "address": "456 Nguyen Van Linh",
                    "gender": "FEMALE",
                    "birthday": "2000-01-01",
                    "role": "STAFF",
                    "salary": 8000000
                }
                """;

        mvc.perform(put("/api/v1/staff/" + testStaffId).with(user("test_admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("test_staff"))) // Username immutable!
                .andExpect(jsonPath("$.fullName", is("Nguyen Updated Name")))
                .andExpect(jsonPath("$.salary", is(8000000)));
    }

    @Test
    void adminCannotDeleteSelfButCanDeleteOtherStaff() throws Exception {
        // Admin deletes self -> 409 Conflict
        mvc.perform(delete("/api/v1/staff/" + testAdminId).with(user("test_admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isConflict());

        // Admin deletes other staff -> 204 No Content
        mvc.perform(delete("/api/v1/staff/" + testStaffId).with(user("test_admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());

        // Status is updated to INACTIVE
        String status = jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, testStaffId);
        org.assertj.core.api.Assertions.assertThat(status).isEqualTo("INACTIVE");
    }

    @Test
    void customerUsersAreIsolatedFromStaffEndpoints() throws Exception {
        jdbc.update("""
                INSERT INTO users(username, password_hash, full_name, email, phone, role, status)
                VALUES ('test_cust_isolation', 'hash', 'Test Customer', 'cust@test.vn', '0909000000', 'CUSTOMER', 'ACTIVE')
                """);
        Long custId = jdbc.queryForObject("SELECT id FROM users WHERE username = 'test_cust_isolation'", Long.class);

        // GET /api/v1/staff/{customerId} must return 404
        mvc.perform(get("/api/v1/staff/" + custId).with(user("test_admin").roles("ADMIN")))
                .andExpect(status().isNotFound());

        // Search must not return customer
        mvc.perform(get("/api/v1/staff?keyword=test_cust_isolation").with(user("test_admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", empty()));
    }

    @Test
    void largePageNumberDoesNotCauseIntegerOverflow() throws Exception {
        mvc.perform(get("/api/v1/staff?page=21474837&size=100").with(user("test_admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", empty()));
    }

    @Test
    void adminCannotSelfDemoteToStaff() throws Exception {
        String demoteJson = """
                {
                    "fullName": "Test Admin",
                    "email": "admin@test.vn",
                    "phone": "0901111111",
                    "address": "Da Nang",
                    "gender": "MALE",
                    "role": "STAFF",
                    "salary": 15000000
                }
                """;
        mvc.perform(put("/api/v1/staff/" + testAdminId).with(user("test_admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(demoteJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotCreateMoreThanTwoAdmins() throws Exception {
        // System currently has 1 admin (test_admin)
        // Create 2nd admin -> OK
        String admin2Json = """
                {
                    "username": "staff_admin2",
                    "fullName": "Admin Number Two",
                    "email": "admin2@smartcafe.vn",
                    "phone": "0912345679",
                    "address": "Da Nang",
                    "role": "ADMIN",
                    "salary": 20000000
                }
                """;
        mvc.perform(post("/api/v1/staff").with(user("test_admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(admin2Json))
                .andExpect(status().isCreated());

        // Create 3rd admin -> 400 Bad Request
        String admin3Json = """
                {
                    "username": "staff_admin3",
                    "fullName": "Admin Number Three",
                    "email": "admin3@smartcafe.vn",
                    "phone": "0912345680",
                    "address": "Da Nang",
                    "role": "ADMIN",
                    "salary": 20000000
                }
                """;
        mvc.perform(post("/api/v1/staff").with(user("test_admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(admin3Json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createdStaffHasMustChangePasswordEnabled() throws Exception {
        String json = """
                {
                    "username": "staff_must_change",
                    "fullName": "Nguyen Van Change",
                    "email": "change@smartcafe.vn",
                    "phone": "0912999888",
                    "address": "Da Nang",
                    "role": "STAFF",
                    "salary": 7000000
                }
                """;
        mvc.perform(post("/api/v1/staff").with(user("test_admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());

        Integer mustChange = jdbc.queryForObject("SELECT must_change_password FROM users WHERE username = 'staff_must_change'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(mustChange).isEqualTo(1);
    }

    @Test
    void salaryExceeding18DigitsIsRejected() throws Exception {
        String json = """
                {
                    "username": "staff_big_salary",
                    "fullName": "Nguyen Big Sal",
                    "email": "bigsal@smartcafe.vn",
                    "phone": "0912999889",
                    "address": "Da Nang",
                    "role": "STAFF",
                    "salary": 1000000000000000000
                }
                """;
        mvc.perform(post("/api/v1/staff").with(user("test_admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCannotDemoteAnotherAdmin() throws Exception {
        // Create 2nd admin
        jdbc.update("""
                INSERT INTO users(username, password_hash, full_name, email, phone, role, status)
                VALUES ('admin_b', 'hash', 'Admin B', 'admin_b@test.vn', '0901111112', 'ADMIN', 'ACTIVE')
                """);
        Long adminBId = jdbc.queryForObject("SELECT id FROM users WHERE username = 'admin_b'", Long.class);
        jdbc.update("INSERT INTO staff_profiles(user_id, address, gender, salary) VALUES (?, 'Da Nang', 'MALE', 15000000)", adminBId);

        // Admin A tries to demote Admin B to STAFF
        String demoteJson = """
                {
                    "fullName": "Admin B",
                    "email": "admin_b@test.vn",
                    "phone": "0901111112",
                    "address": "Da Nang",
                    "role": "STAFF",
                    "salary": 15000000
                }
                """;
        mvc.perform(put("/api/v1/staff/" + adminBId).with(user("test_admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(demoteJson))
                .andExpect(status().isBadRequest());
    }
}
