package vn.codegym.smartcafe.feedbackmanagement;

import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.codegym.smartcafe.auth.TokenService;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=${TEST_DB_URL}", "spring.datasource.username=${TEST_DB_USERNAME}", "spring.datasource.password=${TEST_DB_PASSWORD:}"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:mysql:.*")
@Transactional
class FeedbackReadMySqlTest {
    private static final String API = "/api/v1/admin/feedbacks";
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired TokenService tokens;

    @BeforeEach void fixture() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).endsWith("_test");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feedback", Integer.class)).isZero();
        for (int n = 1; n <= 3; n++) jdbc.update("INSERT INTO users(id,username,password_hash,full_name,email,role,status) VALUES(?,?,?,?,?,?,?)",
                95000+n, "feedback-reader-"+n, "test-only", "Reader test "+n, "reader-"+n+"@example.invalid", n == 1 ? "ADMIN" : n == 2 ? "STAFF" : "CUSTOMER", "ACTIVE");
        insert(95101, "2026-11-18T16:59:59.999999Z", "Trước ngày");
        insert(95102, "2026-11-18T17:00:00Z", "Đầu ngày");
        insert(95103, "2026-11-19T05:00:00Z", "Giữa ngày 🍵");
        insert(95104, "2026-11-19T16:59:59.999999Z", "Cuối ngày");
        insert(95105, "2026-11-19T17:00:00Z", "Ngày sau");
    }
    void insert(long id, String time, String content) {
        jdbc.update("INSERT INTO feedback(id,author_name,author_email,content,rating,created_at) VALUES(?,?,?,?,?,?)", id, "Khách "+id, "guest@example.invalid", content, 5, Timestamp.from(Instant.parse(time)));
    }
    String auth(int n) { return "Bearer " + tokens.generateToken(95000L+n, "feedback-reader-"+n, "ADMIN", 0); }

    @Test void listReadsFiveColumnsWithoutMutatingStatus() throws Exception {
        mvc.perform(get(API).header("Authorization", auth(1))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.totalElements").value(5)).andExpect(jsonPath("$.content[0].id").value("95105"))
                .andExpect(jsonPath("$.content[0].creator").value("Khách 95105")).andExpect(jsonPath("$.content[0].email").value("guest@example.invalid"))
                .andExpect(jsonPath("$.content[0].date").value("2026-11-20")).andExpect(jsonPath("$.content[0].images").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feedback WHERE status='UNREAD'", Integer.class)).isEqualTo(5);
    }
    @Test void cafeDateUsesInclusiveStartExclusiveNextMidnight() throws Exception {
        mvc.perform(get(API).param("date", "2026-11-19").param("sort", "oldest").header("Authorization", auth(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].id").value("95102")).andExpect(jsonPath("$.content[2].id").value("95104"));
        mvc.perform(get(API).param("date", "2026-11-21").header("Authorization", auth(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.totalElements").value(0));
    }
    @Test void paginationSortAndTieBreakUseExactIds() throws Exception {
        insert(9007199254740993L, "2026-11-19T17:00:00Z", "ID lớn");
        mvc.perform(get(API).param("size", "1").header("Authorization", auth(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value("9007199254740993"));
        mvc.perform(get(API).param("page", "1").param("size", "1").header("Authorization", auth(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value("95105"));
        mvc.perform(get(API).param("page", "2147483647").param("size", "100").header("Authorization", auth(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }
    @Test void detailIsFullUtf8AndMissingIs404() throws Exception {
        jdbc.update("UPDATE feedback SET content=? WHERE id=95103", "Nội dung 🍵 ".repeat(100));
        mvc.perform(get(API+"/95103").header("Authorization", auth(1))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Nội dung 🍵 ".repeat(100))).andExpect(jsonPath("$.rating").value(5)).andExpect(jsonPath("$.images").isEmpty());
        mvc.perform(get(API+"/9223372036854775807").header("Authorization", auth(1))).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json")).andExpect(jsonPath("$.code").value("FEEDBACK_NOT_FOUND"));
    }
    @Test void metadataHasStableOrderAndExcludesDeletedOrUnsafeUrls() throws Exception {
        for (int n=1;n<=4;n++) {
            jdbc.update("INSERT INTO media_assets(id,provider,provider_asset_id,secure_url,mime_type,size_bytes,deleted_at) VALUES(?, 'FIREBASE',?,?, 'image/png',10,?)",
                    95200+n, "feedback-reader-image-"+n, n==2 ? "javascript:alert(1)" : "https://example.invalid/test-"+n+".png", n==4 ? Timestamp.from(Instant.now()) : null);
            jdbc.update("INSERT INTO feedback_images(feedback_id,media_id,sort_order) VALUES(95103,?,?)", 95200+n, 4-n);
        }
        mvc.perform(get(API+"/95103").header("Authorization", auth(1))).andExpect(status().isOk()).andExpect(jsonPath("$.images.length()").value(3))
                .andExpect(jsonPath("$.images[0].id").value("95203")).andExpect(jsonPath("$.images[1].url").isEmpty());
    }
    @Test void realBearerAndCurrentDbRoleAreRequired() throws Exception {
        mvc.perform(get(API)).andExpect(status().isUnauthorized());
        mvc.perform(get(API).header("Authorization", "Bearer made-up")).andExpect(status().isUnauthorized());
        for (int n=2;n<=3;n++) mvc.perform(get(API).header("Authorization", auth(n))).andExpect(status().isForbidden());
        String admin = auth(1);
        jdbc.update("UPDATE users SET auth_version=auth_version+1 WHERE id=95001");
        mvc.perform(get(API).header("Authorization", admin)).andExpect(status().isUnauthorized());
    }
    @Test void malformedQueriesAre400AndWriteRoutesAreNotImplemented() throws Exception {
        for (String[] pair : new String[][] {{"date","2026-02-30"},{"date","wrong"},{"date","0999-01-01"},{"page","-1"},{"size","0"},{"size","101"},{"sort","id;DROP TABLE feedback"}})
            mvc.perform(get(API).param(pair[0],pair[1]).header("Authorization", auth(1))).andExpect(status().isBadRequest());
        mvc.perform(get(API+"/not-an-id").header("Authorization", auth(1))).andExpect(status().isBadRequest());
        mvc.perform(post(API).header("Authorization", auth(1))).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete(API+"/95103").header("Authorization", auth(1))).andExpect(status().isMethodNotAllowed());
    }
}
