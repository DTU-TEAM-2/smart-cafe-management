package vn.codegym.smartcafe.staff;

import java.math.BigDecimal;
import java.util.List;
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

    @Autowired(required = false)
    private vn.codegym.smartcafe.auth.OtpService otpService;

    @Autowired(required = false)
    private vn.codegym.smartcafe.auth.TokenService tokenService;

    private Long testAdminId;
    private Long testStaffId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM staff_profiles");
        jdbc.update("DELETE FROM auth_otp_challenges");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM media_assets");

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
    void staffAndAvatarIdsRemainExactAtTheJsonBoundary() throws Exception {
        long neighbor = 9007199254740992L;
        long target = 9007199254740993L;
        jdbc.update("""
                INSERT INTO users(id, username, password_hash, full_name, email, role, status)
                VALUES (?, 'large_staff_a', 'hash', 'Large A', 'large-a@example.invalid', 'STAFF', 'ACTIVE'),
                       (?, 'large_staff_b', 'hash', 'Large B', 'large-b@example.invalid', 'STAFF', 'ACTIVE')
                """, neighbor, target);
        jdbc.update("INSERT INTO staff_profiles(user_id,address,salary) VALUES (?,'Da Nang',6000000), (?,'Da Nang',6000000)", neighbor, target);
        jdbc.update("""
                INSERT INTO media_assets(id, provider, provider_asset_id, secure_url, mime_type, size_bytes)
                VALUES (?, 'CLOUDINARY', 'large-a', 'https://example.invalid/a.png', 'image/png', 10),
                       (?, 'CLOUDINARY', 'large-b', 'https://example.invalid/b.png', 'image/png', 10)
                """, neighbor, target);

        var admin = user("test_admin").roles("ADMIN");
        mvc.perform(get("/api/v1/staff").param("keyword", "large_staff_").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(Long.toString(neighbor), Long.toString(target))));
        mvc.perform(put("/api/v1/staff/{id}", target).with(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"Updated Large B","email":"large-b@example.invalid",
                         "role":"STAFF","avatarMediaId":"9007199254740993","version":"0", "address":"Da Nang","phone":"0901234567","gender":"FEMALE","salary":6000000}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(Long.toString(target))))
                .andExpect(jsonPath("$.avatarMediaId", is(Long.toString(target))));
        mvc.perform(get("/api/v1/staff/{id}", neighbor).with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(Long.toString(neighbor))))
                .andExpect(jsonPath("$.fullName", is("Large A")));
        mvc.perform(delete("/api/v1/staff/{id}", target).with(admin).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/staff/{id}", neighbor).with(admin))
                .andExpect(status().isOk());
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
                .andExpect(jsonPath("$.salary", is("6500000")));

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
                    "version": "0",
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
                .andExpect(jsonPath("$.salary", is("8000000")));
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
                    "version": "0",
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

    @Test
    void adminCannotDeleteOtherAdmin() throws Exception {
        // Create 2nd admin
        jdbc.update("""
                INSERT INTO users(username, password_hash, full_name, email, phone, role, status)
                VALUES ('admin_c_del', 'hash', 'Admin C', 'admin_c_del@test.vn', '0901111113', 'ADMIN', 'ACTIVE')
                """);
        Long adminCId = jdbc.queryForObject("SELECT id FROM users WHERE username = 'admin_c_del'", Long.class);
        jdbc.update("INSERT INTO staff_profiles(user_id, address, gender, salary) VALUES (?, 'Da Nang', 'MALE', 15000000)", adminCId);

        // Admin A tries to delete Admin C -> 400 Bad Request
        mvc.perform(delete("/api/v1/staff/" + adminCId).with(user("test_admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isBadRequest());

        // Status is still ACTIVE
        String status = jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, adminCId);
        org.assertj.core.api.Assertions.assertThat(status).isEqualTo("ACTIVE");
    }

    @Test
    void searchTreatsPercentUnderscoreAndExclamationAsLiterals() throws Exception {
        // Insert a staff member with special characters in username, fullName and phone
        jdbc.update("""
                INSERT INTO users(username, password_hash, full_name, email, phone, role, status)
                VALUES ('staff_percent_%', 'hash', 'Special ! Staff', 'percent@test.vn', '0903333333', 'STAFF', 'ACTIVE')
                """);
        Long specialId = jdbc.queryForObject("SELECT id FROM users WHERE username = 'staff_percent_%'", Long.class);
        jdbc.update("INSERT INTO staff_profiles(user_id, address, gender, salary) VALUES (?, 'Can Tho', 'MALE', 7000000)", specialId);

        // 1. Search literal '%' - must match only 'staff_percent_%', NOT all staff
        mvc.perform(get("/api/v1/staff?keyword=%").with(user("test_admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].username", is("staff_percent_%")));

        // 2. Search literal '!' - must match only 'Special ! Staff'
        mvc.perform(get("/api/v1/staff?keyword=!").with(user("test_admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].fullName", is("Special ! Staff")));

        // 3. Search literal '_' - matches usernames containing literal '_'
        // ('test_admin', 'test_staff', 'staff_percent_%' -> 3 records)
        mvc.perform(get("/api/v1/staff?keyword=_").with(user("test_admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(3)));

        // 4. Search combined pattern '%_'
        mvc.perform(get("/api/v1/staff?keyword=%_").with(user("test_admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    @Test
    void avatarMediaIdValidationForCreateAndUpdate() throws Exception {
        // Seed active media asset
        jdbc.update("""
                INSERT INTO media_assets(id, provider, provider_asset_id, secure_url, mime_type, size_bytes)
                VALUES (501, 'CLOUDINARY', 'pub_test_501', 'https://example.com/active.png', 'image/png', 1024)
                """);
        // Seed soft-deleted media asset
        jdbc.update("""
                INSERT INTO media_assets(id, provider, provider_asset_id, secure_url, mime_type, size_bytes, deleted_at)
                VALUES (502, 'CLOUDINARY', 'pub_test_502', 'https://example.com/deleted.png', 'image/png', 1024, CURRENT_TIMESTAMP(6))
                """);

        // 1. CREATE with non-existent avatarMediaId -> 400 Bad Request
        String nonExistentJson = """
                {
                    "username": "staff_avatar_none",
                    "fullName": "Staff Avatar None",
                    "email": "avatar_none@smartcafe.vn",
                    "phone": "0911000001",
                    "address": "123 Da Nang",
                    "gender": "MALE",
                    "salary": 6500000,
                    "avatarMediaId": 999999
                }
                """;
        mvc.perform(post("/api/v1/staff").with(user("test_admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nonExistentJson))
                .andExpect(status().isBadRequest());

        // 2. CREATE with soft-deleted avatarMediaId -> 400 Bad Request
        String softDeletedJson = nonExistentJson
                .replace("staff_avatar_none", "staff_avatar_del")
                .replace("avatar_none@smartcafe.vn", "avatar_del@smartcafe.vn")
                .replace("999999", "502");
        mvc.perform(post("/api/v1/staff").with(user("test_admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(softDeletedJson))
                .andExpect(status().isBadRequest());

        // 3. CREATE with active avatarMediaId -> 201 Created
        String activeJson = nonExistentJson
                .replace("staff_avatar_none", "staff_avatar_ok")
                .replace("avatar_none@smartcafe.vn", "avatar_ok@smartcafe.vn")
                .replace("999999", "501");
        for (String invalidId : List.of("501.9", "5.01e2", "\"\"", "\"9223372036854775808\"")) {
            mvc.perform(post("/api/v1/staff").with(user("test_admin").roles("ADMIN")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(activeJson.replace("\"avatarMediaId\": 501", "\"avatarMediaId\": " + invalidId)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/staff").with(user("test_admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activeJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.avatarUrl", is("https://example.com/active.png")));

        // 4. UPDATE with non-existent avatarMediaId -> 400 Bad Request
        String updateNonExistentJson = """
                {
                    "version": "0",
                    "fullName": "Staff Updated Name",
                    "email": "staff@test.vn",
                    "phone": "0902222222",
                    "address": "Hue City",
                    "gender": "FEMALE",
                    "salary": 7000000,
                    "avatarMediaId": 999999
                }
                """;
        mvc.perform(put("/api/v1/staff/" + testStaffId).with(user("test_admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateNonExistentJson))
                .andExpect(status().isBadRequest());

        // 5. UPDATE with soft-deleted avatarMediaId -> 400 Bad Request
        String updateSoftDeletedJson = updateNonExistentJson.replace("999999", "502");
        mvc.perform(put("/api/v1/staff/" + testStaffId).with(user("test_admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateSoftDeletedJson))
                .andExpect(status().isBadRequest());

        // 6. UPDATE with active avatarMediaId -> 200 OK
        String updateActiveJson = updateNonExistentJson.replace("999999", "501");
        mvc.perform(put("/api/v1/staff/" + testStaffId).with(user("test_admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateActiveJson.replace("\"avatarMediaId\": 501", "\"avatarMediaId\": 501.9")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/staff/" + testStaffId).with(user("test_admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateActiveJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl", is("https://example.com/active.png")));

        // 7. UPDATE with null avatarMediaId -> 200 OK
        String currentVersion = com.jayway.jsonpath.JsonPath.read(
                mvc.perform(get("/api/v1/staff/" + testStaffId).with(user("test_admin").roles("ADMIN")))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.version");
        String updateNullJson = updateNonExistentJson.replace("\"avatarMediaId\": 999999", "\"avatarMediaId\": null")
                .replace("\"version\": \"0\"", "\"version\": \"" + currentVersion + "\"");
        mvc.perform(put("/api/v1/staff/" + testStaffId).with(user("test_admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateNullJson))
                .andExpect(status().isOk());
    }

    @Test
    void updateRequiresExpectedVersionAndRejectsStaleVersionWithoutOverwriting() throws Exception {
        String body = """
                {"fullName":"Nguyen Version Test","email":"staff@test.vn","phone":"0902222222",
                 "address":"Da Nang","role":"STAFF","salary":7000000,"version":"0"}
                """;
        for (String invalid : new String[]{"null", "\"-1\"", "\"01\"", "\"9223372036854775808\""}) {
            mvc.perform(put("/api/v1/staff/" + testStaffId).with(user("test_admin").roles("ADMIN")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body.replace("\"0\"", invalid)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(put("/api/v1/staff/" + testStaffId).with(user("test_admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body.replace(",\"version\":\"0\"", "")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/staff/" + testStaffId).with(user("test_admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version", is("1")));
        mvc.perform(put("/api/v1/staff/" + testStaffId).with(user("test_admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body.replace("Nguyen Version Test", "Stale Overwrite")))
                .andExpect(status().isConflict());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT full_name FROM users WHERE id=?", String.class, testStaffId)).isEqualTo("Nguyen Version Test");
    }

    @Test
    void roleTransitionIncrementsAuthVersionAsWellAsProfileVersion() throws Exception {
        long before = jdbc.queryForObject("SELECT auth_version FROM users WHERE id=?", Long.class, testStaffId);
        String body = """
                {"fullName":"Nguyen Promoted Staff","email":"staff@test.vn","phone":"0902222222",
                 "address":"Da Nang","role":"ADMIN","salary":7000000,"version":"0"}
                """;
        mvc.perform(put("/api/v1/staff/" + testStaffId).with(user("test_admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version", is("1")));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT auth_version FROM users WHERE id=?", Long.class, testStaffId)).isEqualTo(before + 1);
    }

    @Test
    void emailChangeIncrementsAuthVersionAndRevokesPendingOtpChallenges() throws Exception {
        // Insert active OTP challenge for test staff
        jdbc.update("""
                INSERT INTO auth_otp_challenges(id, user_id, email, purpose, code_hash, expires_at, next_send_at)
                VALUES ('ch-test-email-revocation', ?, 'staff@test.vn', 'RESET', 'dummy_hash_12345',
                        DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 10 MINUTE), DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 1 MINUTE))
                """, testStaffId);

        long beforeAuthVersion = jdbc.queryForObject("SELECT auth_version FROM users WHERE id=?", Long.class, testStaffId);

        // Update staff email
        String body = """
                {"fullName":"Nguyen Email Test","email":"staff-updated@test.vn","phone":"0902222222",
                 "address":"Da Nang","role":"STAFF","salary":6000000,"version":"0"}
                """;
        mvc.perform(put("/api/v1/staff/" + testStaffId).with(user("test_admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("staff-updated@test.vn")))
                .andExpect(jsonPath("$.version", is("1")));

        // Verify auth_version incremented
        long afterAuthVersion = jdbc.queryForObject("SELECT auth_version FROM users WHERE id=?", Long.class, testStaffId);
        org.assertj.core.api.Assertions.assertThat(afterAuthVersion).isEqualTo(beforeAuthVersion + 1);

        // Verify pending OTP challenge was invalidated (used_at is not null)
        java.sql.Timestamp usedAt = jdbc.queryForObject(
                "SELECT used_at FROM auth_otp_challenges WHERE id = 'ch-test-email-revocation'", java.sql.Timestamp.class);
        org.assertj.core.api.Assertions.assertThat(usedAt).isNotNull();

        // Updating regular field without changing email does NOT bump auth_version further
        String updateNameBody = """
                {"fullName":"Nguyen Email Test Modified Name","email":"staff-updated@test.vn","phone":"0902222222",
                 "address":"Da Nang","role":"STAFF","salary":6000000,"version":"1"}
                """;
        mvc.perform(put("/api/v1/staff/" + testStaffId).with(user("test_admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(updateNameBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version", is("2")));

        long finalAuthVersion = jdbc.queryForObject("SELECT auth_version FROM users WHERE id=?", Long.class, testStaffId);
        org.assertj.core.api.Assertions.assertThat(finalAuthVersion).isEqualTo(afterAuthVersion);
    }

    @Test
    void emailChangeRaceInterleavingPreventsStaleOtpAndGrant() throws Exception {
        // 1. Pre-existing challenge created before email update
        String oldChallengeId = "ch-pre-existing-old-email";
        jdbc.update("""
                INSERT INTO auth_otp_challenges(id, user_id, email, purpose, code_hash, attempts, sends, expires_at, next_send_at, used_at, delivery_status, created_at)
                VALUES (?, ?, 'staff@test.vn', 'RESET', 'dummy_hash_old_email', 0, 1,
                        DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 10 MINUTE), DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 1 MINUTE),
                        NULL, 'SENT', CURRENT_TIMESTAMP)
                """, oldChallengeId, testStaffId);

        // 2. Staff PUT updates email from staff@test.vn to staff-new-race@test.vn
        String body = """
                {"fullName":"Nguyen Email Race","email":"staff-new-race@test.vn","phone":"0902222222",
                 "address":"Da Nang","role":"STAFF","salary":6000000,"version":"0"}
                """;
        mvc.perform(put("/api/v1/staff/" + testStaffId).with(user("test_admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("staff-new-race@test.vn")));

        // 3. Verify challenge in DB was immediately revoked (used_at is NOT NULL)
        java.sql.Timestamp usedAt = jdbc.queryForObject(
                "SELECT used_at FROM auth_otp_challenges WHERE id = ?", java.sql.Timestamp.class, oldChallengeId);
        org.assertj.core.api.Assertions.assertThat(usedAt).isNotNull();

        // 4. Verify auth_version incremented
        long currentAuthVersion = jdbc.queryForObject("SELECT auth_version FROM users WHERE id=?", Long.class, testStaffId);
        org.assertj.core.api.Assertions.assertThat(currentAuthVersion).isEqualTo(1L);

        // 5. If otpService is present: verify that old challenge cannot be verified or resent
        if (otpService != null) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> otpService.verifyOtp(oldChallengeId, "123456"))
                    .isInstanceOf(vn.codegym.smartcafe.auth.OtpException.class);

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> otpService.resendOtp(oldChallengeId))
                    .isInstanceOf(vn.codegym.smartcafe.auth.OtpException.class);

            // Initiating forgot-password for old email creates dummy challenge (user_id IS NULL)
            var forgotResp = otpService.initiatePasswordResetOtp("staff@test.vn");
            org.assertj.core.api.Assertions.assertThat(forgotResp.challengeId()).isNotEmpty();
            Long challengeUserId = jdbc.queryForObject(
                    "SELECT user_id FROM auth_otp_challenges WHERE id = ?", Long.class, forgotResp.challengeId());
            org.assertj.core.api.Assertions.assertThat(challengeUserId).isNull();

            // Initiating forgot-password for new email creates real challenge
            var newForgotResp = otpService.initiatePasswordResetOtp("staff-new-race@test.vn");
            org.assertj.core.api.Assertions.assertThat(newForgotResp.challengeId()).isNotEmpty();
            Long newChallengeUserId = jdbc.queryForObject(
                    "SELECT user_id FROM auth_otp_challenges WHERE id = ?", Long.class, newForgotResp.challengeId());
            org.assertj.core.api.Assertions.assertThat(newChallengeUserId).isEqualTo(testStaffId);
        }
    }

    @Test
    void searchSupportsIndependentFiltersWithOrCombination() throws Exception {
        // Insert additional test users
        jdbc.update("""
                INSERT INTO users(username, password_hash, full_name, email, phone, role, status)
                VALUES ('user_search_alpha', 'hash', 'Tran Alpha User', 'alpha@test.vn', '0988111222', 'STAFF', 'ACTIVE')
                """);
        Long alphaId = jdbc.queryForObject("SELECT id FROM users WHERE username = 'user_search_alpha'", Long.class);
        jdbc.update("INSERT INTO staff_profiles(user_id, address, gender, salary) VALUES (?, 'HN', 'MALE', 8000000)", alphaId);

        jdbc.update("""
                INSERT INTO users(username, password_hash, full_name, email, phone, role, status)
                VALUES ('user_search_beta', 'hash', 'Le Beta Member', 'beta@test.vn', '0977333444', 'STAFF', 'ACTIVE')
                """);
        Long betaId = jdbc.queryForObject("SELECT id FROM users WHERE username = 'user_search_beta'", Long.class);
        jdbc.update("INSERT INTO staff_profiles(user_id, address, gender, salary) VALUES (?, 'HCM', 'FEMALE', 9000000)", betaId);

        // Independent search by username
        mvc.perform(get("/api/v1/staff?username=alpha").with(user("test_admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].username", is("user_search_alpha")));

        // Independent search by name
        mvc.perform(get("/api/v1/staff?name=Beta").with(user("test_admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].username", is("user_search_beta")));

        // Independent search by phone
        mvc.perform(get("/api/v1/staff?phone=0988111222").with(user("test_admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].username", is("user_search_alpha")));

        // Combination with OR: matching username "alpha" OR phone "0977333444" -> returns 2 items
        mvc.perform(get("/api/v1/staff?username=alpha&phone=0977333444").with(user("test_admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(2)));

        // Backward compatibility: keyword still works
        mvc.perform(get("/api/v1/staff?keyword=alpha").with(user("test_admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].username", is("user_search_alpha")));
    }

    @Test
    void salaryIsFormattedAsJsonStringPreservingHighPrecision() throws Exception {
        BigDecimal largeSalary = new BigDecimal("999999999999900000"); // > Number.MAX_SAFE_INTEGER
        String createBody = """
                {
                    "username": "user_large_salary",
                    "fullName": "High Earner Staff",
                    "email": "earner@test.vn",
                    "phone": "0912345678",
                    "address": "Da Nang",
                    "role": "STAFF",
                    "salary": 999999999999900000
                }
                """;

        mvc.perform(post("/api/v1/staff").with(user("test_admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.salary", is("999999999999900000")));

        Long createdId = jdbc.queryForObject("SELECT id FROM users WHERE username = 'user_large_salary'", Long.class);

        // GET detail returns salary as String
        mvc.perform(get("/api/v1/staff/" + createdId).with(user("test_admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salary", is("999999999999900000")));

        // GET summary list also returns salary as String
        mvc.perform(get("/api/v1/staff?username=user_large_salary").with(user("test_admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].salary", is("999999999999900000")));
    }
}
