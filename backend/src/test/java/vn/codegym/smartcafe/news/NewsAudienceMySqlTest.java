package vn.codegym.smartcafe.news;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Real MySQL and service transactions; external object reads alone are mocked.
@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}", "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD:}",
        "smartcafe.auth.token-secret=audience-test-secret-only-at-least-32-characters"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:mysql:.*")
class NewsAudienceMySqlTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean NewsImageStorage storage;

    @BeforeEach
    void seed() {
        clean();
        jdbc.update("""
                INSERT INTO users(id,username,password_hash,full_name,email,role,status) VALUES
                    (1101,'aud-admin','not-a-login-hash','Audience admin','aud-admin@example.invalid','ADMIN','ACTIVE'),
                    (1102,'aud-staff','not-a-login-hash','Audience staff','aud-staff@example.invalid','STAFF','ACTIVE'),
                    (1103,'aud-customer','not-a-login-hash','Audience customer','aud-customer@example.invalid','CUSTOMER','ACTIVE')
                """);
        seedArticle(1101, "PUBLISHED", 1, 0);
        seedArticle(1102, "PUBLISHED", 0, 1);
        seedArticle(1103, "PUBLISHED", 1, 1);
        seedArticle(1104, "DRAFT", 1, 1);
        seedArticle(1105, "ARCHIVED", 1, 1);
        seedArticle(1106, "PUBLISHED", 0, 0);
        for (int id = 1101; id <= 1104; id++) {
            jdbc.update("""
                    INSERT INTO media_assets(id,provider,provider_asset_id,secure_url,mime_type,size_bytes)
                    VALUES (?,'FIREBASE',?,'https://example.invalid/private.png','image/png',1)
                    """, id, asset(id));
        }
        jdbc.update("INSERT INTO news_images(news_id,media_id,sort_order) VALUES (1101,1101,0),(1102,1102,0),(1103,1103,0)");
    }

    @AfterEach
    void clean() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).endsWith("_test");
        jdbc.update("DELETE FROM news_images");
        jdbc.update("DELETE FROM news_posts");
        jdbc.update("DELETE FROM users WHERE id BETWEEN 1101 AND 1103");
        jdbc.update("DELETE FROM media_assets WHERE id BETWEEN 1101 AND 1104");
    }

    @Test
    void listUsesRealIdentityAndFiltersCountsPaginationAndStatus() throws Exception {
        mvc.perform(get("/api/v1/news").header("Authorization", bearer(1102, "aud-staff")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[*].id", containsInAnyOrder("1101", "1103")))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/news").header("Authorization", bearer(1103, "aud-customer")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[*].id", containsInAnyOrder("1102", "1103")))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/news").with(user("aud-staff").roles("ADMIN"))
                        .param("role", "ADMIN").param("audiences", "CUSTOMER").param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/v1/news").with(user("aud-staff").roles("STAFF")).param("status", "DRAFT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/news").with(user("aud-admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(5));
    }

    @Test
    void detailNeverRevealsOtherAudienceDraftArchiveOrLegacyArticle() throws Exception {
        for (int id : new int[]{1102, 1104, 1105, 1106}) {
            mvc.perform(get("/api/v1/news/" + id).with(user("aud-staff").roles("STAFF")))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/news/1101").with(user("aud-customer").roles("CUSTOMER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/news/1103").with(user("aud-customer").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.audiences", containsInAnyOrder("STAFF", "CUSTOMER")));
        for (int id : new int[]{1104, 1105, 1106}) {
            mvc.perform(get("/api/v1/news/" + id).with(user("aud-admin").roles("ADMIN")))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/news/1103")).andExpect(status().isForbidden());
    }

    @Test
    void createAndUpdatePersistAudiencesAndOmittedUpdatePreservesThem() throws Exception {
        var result = mvc.perform(post("/api/v1/news").with(user("aud-admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"aud-created\",\"title\":\"Article\",\"body\":\"Body\",\"status\":\"PUBLISHED\",\"audiences\":[\"STAFF\"]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.audiences[0]").value("STAFF")).andReturn();
        String location = result.getResponse().getHeader("Location");
        long id = jdbc.queryForObject("SELECT id FROM news_posts WHERE slug='aud-created'", Long.class);
        assertThat(jdbc.queryForObject("SELECT audience_staff FROM news_posts WHERE id=?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT audience_customer FROM news_posts WHERE id=?", Integer.class, id)).isZero();
        mvc.perform(put(location).with(user("aud-admin").roles("ADMIN"))
                        .header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"To customer\",\"body\":\"Body\",\"audiences\":[\"CUSTOMER\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.audiences[0]").value("CUSTOMER"));
        mvc.perform(get(location).with(user("aud-staff").roles("STAFF"))).andExpect(status().isNotFound());
        mvc.perform(get(location).with(user("aud-customer").roles("CUSTOMER"))).andExpect(status().isOk());
        mvc.perform(put(location).with(user("aud-admin").roles("ADMIN"))
                        .header("If-Match", "\"1\"").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Keep customer\",\"body\":\"Body\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.audiences[0]").value("CUSTOMER"));
    }

    @Test
    void invalidAudiencesCannotCreateOrClearRecipients() throws Exception {
        for (String audiences : List.of("null", "[]", "[\"ADMIN\"]", "[\"STAFF\",\"STAFF\"]", "[null]", "[\"staff\"]")) {
            mvc.perform(post("/api/v1/news").with(user("aud-admin").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Invalid\",\"body\":\"Body\",\"audiences\":" + audiences + "}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/news").with(user("aud-admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Missing\",\"body\":\"Body\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/news/1101").with(user("aud-admin").roles("ADMIN"))
                        .header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Invalid\",\"body\":\"Body\",\"audiences\":[]}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM news_posts", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT audience_staff FROM news_posts WHERE id=1101", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("UPDATE news_posts SET audience_staff=2 WHERE id=1101"))
                .isInstanceOfSatisfying(DataAccessException.class, failure ->
                        assertThat(failure.getMostSpecificCause()).isInstanceOfSatisfying(java.sql.SQLException.class,
                                sql -> assertThat(sql.getErrorCode()).isEqualTo(3819)));
        assertThat(jdbc.queryForObject("SELECT audience_staff FROM news_posts WHERE id=1101", Integer.class)).isEqualTo(1);
    }

    @Test
    void privateImagesRequireVisibleArticleAndSupportSharedAssets() throws Exception {
        for (int id : new int[]{1102, 1104}) {
            mvc.perform(get("/api/v1/news/images/" + id).with(user("aud-staff").roles("STAFF")))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/news/images/1101").with(user("aud-customer").roles("CUSTOMER")))
                .andExpect(status().isNotFound());
        verifyNoInteractions(storage);
        when(storage.read(asset(1101))).thenReturn(new byte[]{1});
        mvc.perform(get("/api/v1/news/images/1101").with(user("aud-staff").roles("STAFF")))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", "no-store"));
        jdbc.update("INSERT INTO news_images(news_id,media_id,sort_order) VALUES (1102,1101,1)");
        mvc.perform(get("/api/v1/news/images/1101").with(user("aud-customer").roles("CUSTOMER")))
                .andExpect(status().isOk());
        jdbc.update("UPDATE news_posts SET status='ARCHIVED' WHERE id=1102");
        mvc.perform(get("/api/v1/news/images/1101").with(user("aud-customer").roles("CUSTOMER")))
                .andExpect(status().isNotFound());
        jdbc.update("UPDATE media_assets SET deleted_at=UTC_TIMESTAMP(6) WHERE id=1101");
        mvc.perform(get("/api/v1/news/images/1101").with(user("aud-staff").roles("STAFF")))
                .andExpect(status().isNotFound());
    }

    @Test
    void staffAndCustomerRemainUnableToManageArticlesOrUpload() throws Exception {
        for (String role : List.of("STAFF", "CUSTOMER")) {
            String username = role.equals("STAFF") ? "aud-staff" : "aud-customer";
            mvc.perform(post("/api/v1/news").with(user(username).roles(role))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/news/1103").with(user(username).roles(role))
                            .header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/v1/news/1103").with(user(username).roles(role)).header("If-Match", "\"0\""))
                    .andExpect(status().isForbidden());
            mvc.perform(multipart("/api/v1/news/images").with(user(username).roles(role)))
                    .andExpect(status().isForbidden());
        }
    }

    private void seedArticle(int id, String status, int staff, int customer) {
        jdbc.update("""
                INSERT INTO news_posts(id,slug,title,body,status,author_user_id,published_at,audience_staff,audience_customer)
                VALUES (?,?,'Audience article','Body',?,1101,IF(?='PUBLISHED',UTC_TIMESTAMP(6),NULL),?,?)
                """, id, "aud-post-" + id, status, status, staff, customer);
    }

    private String asset(int id) { return "news/00000000-0000-0000-0000-00000000" + id + ".png"; }

    private String bearer(int id, String username) throws Exception {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                (id + ":" + username + ":UNTRUSTED:" + Instant.now().plusSeconds(120).toEpochMilli())
                        .getBytes(StandardCharsets.UTF_8));
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("audience-test-secret-only-at-least-32-characters".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "Bearer " + payload + "." + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
