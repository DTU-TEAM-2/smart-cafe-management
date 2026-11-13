package vn.codegym.smartcafe.customerfeedback;

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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}", "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD:}"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:mysql:.*")
@Transactional
class CustomerFeedbackMySqlTest {
    private static final String URL = "/api/v1/customer/feedback";
    private static final String VALID = """
            {"name":" Khách kiểm thử ","email":"customer@example.invalid","content":" Đồ uống ngon! ","rating":5}
            """;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void isolatedDatabase() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).endsWith("_test");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feedback", Integer.class)).isZero();
    }

    @Test
    void guestFeedbackPersistsVietnameseTextWithoutInventingSessionOrCustomer() throws Exception {
        mvc.perform(post(URL).with(csrf()).contentType("application/json").content(VALID))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.message").value("Cảm ơn bạn đã gửi phản hồi!"))
                .andExpect(jsonPath("$.email").doesNotExist());
        var row = jdbc.queryForMap("SELECT * FROM feedback");
        assertThat(row.get("author_name")).isEqualTo("Khách kiểm thử");
        assertThat(row.get("author_email")).isEqualTo("customer@example.invalid");
        assertThat(row.get("content")).isEqualTo("Đồ uống ngon!");
        assertThat(((Number) row.get("rating")).intValue()).isEqualTo(5);
        assertThat(row.get("status")).isEqualTo("UNREAD");
        assertThat(row.get("session_id")).isNull();
        assertThat(row.get("customer_user_id")).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feedback_images", Integer.class)).isZero();
    }

    @Test
    void missingFieldsInvalidEmailAndRatingNeverWrite() throws Exception {
        for (String body : new String[]{"{}", VALID.replace("customer@example.invalid", "not-an-email"),
                VALID.replace("\"rating\":5", "\"rating\":0"), VALID.replace("\"rating\":5", "\"rating\":6"),
                VALID.replace(" Đồ uống ngon! ", "   "), VALID.replace("\"rating\":5", "\"rating\":3.5")}) {
            mvc.perform(post(URL).with(csrf()).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.fieldErrors").isArray());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feedback", Integer.class)).isZero();
    }

    @Test
    void rejectsSchemaLimitsIncludingUtf8TextBytesAndMalformedJson() throws Exception {
        for (String body : new String[]{VALID.replace(" Khách kiểm thử ", "a".repeat(161)),
                VALID.replace(" Đồ uống ngon! ", "\u20ac".repeat(21846)), "{", VALID.replace("\"rating\":5", "\"rating\":{}")}) {
            mvc.perform(post(URL).with(csrf()).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feedback", Integer.class)).isZero();
    }

    @Test
    void realCsrfSessionWorksAndMissingOrInvalidCsrfCannotWrite() throws Exception {
        var result = mvc.perform(get(URL + "/csrf")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN")).andReturn();
        // Use the actual generated CSRF token via the request attribute, not a test bypass.
        var token = (org.springframework.security.web.csrf.CsrfToken) result.getRequest().getAttribute(
                org.springframework.security.web.csrf.CsrfToken.class.getName());
        mvc.perform(post(URL).session((org.springframework.mock.web.MockHttpSession) result.getRequest().getSession())
                        .header(token.getHeaderName(), token.getToken()).contentType("application/json").content(VALID))
                .andExpect(status().isCreated());
        mvc.perform(post(URL).contentType("application/json").content(VALID)).andExpect(status().isForbidden());
        mvc.perform(post(URL).with(csrf().useInvalidToken()).contentType("application/json").content(VALID))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feedback", Integer.class)).isEqualTo(1);
    }

    @Test
    void submissionDoesNotExposePublicReadUpdateDeleteOrUpload() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isForbidden());
        mvc.perform(get(URL + "/1")).andExpect(status().isForbidden());
        mvc.perform(delete(URL + "/1").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(put(URL + "/1").with(csrf()).contentType("application/json").content(VALID)).andExpect(status().isForbidden());
        mvc.perform(post(URL + "/1/images").with(csrf())).andExpect(status().isForbidden());
    }
}
