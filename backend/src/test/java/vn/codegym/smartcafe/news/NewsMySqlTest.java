package vn.codegym.smartcafe.news;

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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD:}",
        "smartcafe.auth.token-secret=news-test-signing-secret-only-2026-very-secure-key"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:mysql:.*")
@Transactional
class NewsMySqlTest {
    private static final String SECRET = "news-test-signing-secret-only-2026-very-secure-key";

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @BeforeEach
    void seedIsolatedDatabase() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).endsWith("_test");
        jdbc.execute("DELETE FROM news_images");
        jdbc.execute("DELETE FROM news_posts");

        // Seed users
        jdbc.update("DELETE FROM users WHERE id IN (101, 102, 103, 104)");
        jdbc.update("""
                INSERT INTO users(id, username, password_hash, full_name, email, role, status)
                VALUES (101, 'news-admin', 'hash', 'Quản trị viên News', 'news-admin@example.invalid', 'ADMIN', 'ACTIVE'),
                       (102, 'news-staff', 'hash', 'Nhân viên News', 'news-staff@example.invalid', 'STAFF', 'ACTIVE'),
                       (103, 'news-customer', 'hash', 'Khách hàng News', 'news-customer@example.invalid', 'CUSTOMER', 'ACTIVE'),
                       (104, 'news-inactive', 'hash', 'Nhân viên khóa', 'news-inactive@example.invalid', 'STAFF', 'INACTIVE')
                """);

        // Seed media assets
        jdbc.update("DELETE FROM media_assets WHERE id IN (201, 202, 203)");
        jdbc.update("""
                INSERT INTO media_assets(id, provider, provider_asset_id, secure_url, mime_type, size_bytes, created_at, deleted_at)
                VALUES (201, 'FIREBASE', 'news/00000000-0000-0000-0000-000000000201.jpg', 'https://example.invalid/banner1.jpg', 'image/jpeg', 1024, CURRENT_TIMESTAMP(6), NULL),
                       (202, 'FIREBASE', 'news/00000000-0000-0000-0000-000000000202.png', 'https://example.invalid/banner2.png', 'image/png', 2048, CURRENT_TIMESTAMP(6), NULL),
                       (203, 'CLOUDINARY', 'asset-203', 'https://example.invalid/deleted.jpg', 'image/jpeg', 512, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """);
    }

    @Test
    void emptyDatabaseReturnsEmptyPage() throws Exception {
        mvc.perform(get("/api/v1/news")
                        .header("Authorization", bearer(101, "news-admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void listWithPaginationAndSorting() throws Exception {
        // Seed 5 posts
        for (int i = 1; i <= 5; i++) {
            jdbc.update("""
                    INSERT INTO news_posts(id, slug, title, summary, body, status, author_user_id, published_at, created_at)
                    VALUES (?, ?, ?, ?, ?, 'PUBLISHED', 101, DATE_ADD('2026-11-01 10:00:00', INTERVAL ? DAY), DATE_ADD('2026-11-01 08:00:00', INTERVAL ? DAY))
                    """, i, "tin-tuc-" + i, "Tin tức số " + i, "Tóm tắt " + i, "Nội dung bài viết số " + i, i, i);
        }
        jdbc.update("INSERT INTO news_images(news_id, media_id, sort_order) VALUES (1, 201, 0), (1, 202, 1)");

        // Page 0, size 2
        mvc.perform(get("/api/v1/news")
                        .header("Authorization", bearer(101, "news-admin", "ADMIN"))
                        .param("page", "0")
                        .param("size", "2")
                        .param("sort", "publishedAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value("5"))
                .andExpect(jsonPath("$.content[1].id").value("4"))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.last").value(false));

        // Page 1, size 2
        mvc.perform(get("/api/v1/news")
                        .header("Authorization", bearer(101, "news-admin", "ADMIN"))
                        .param("page", "1")
                        .param("size", "2")
                        .param("sort", "publishedAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value("3"))
                .andExpect(jsonPath("$.content[1].id").value("2"))
                .andExpect(jsonPath("$.last").value(false));

        // Page 2, size 2 (last page)
        mvc.perform(get("/api/v1/news")
                        .header("Authorization", bearer(101, "news-admin", "ADMIN"))
                        .param("page", "2")
                        .param("size", "2")
                        .param("sort", "publishedAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value("1"))
                .andExpect(jsonPath("$.content[0].thumbnailUrl").value("/api/v1/news/images/201"))
                .andExpect(jsonPath("$.last").value(true));

        // Ascending sort
        mvc.perform(get("/api/v1/news")
                        .header("Authorization", bearer(101, "news-admin", "ADMIN"))
                        .param("page", "0")
                        .param("size", "2")
                        .param("sort", "publishedAt,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("1"))
                .andExpect(jsonPath("$.content[1].id").value("2"));
    }

    @Test
    void listFilterByStatus() throws Exception {
        jdbc.update("""
                INSERT INTO news_posts(id, slug, title, summary, body, status, author_user_id, published_at)
                VALUES (1, 'post-draft', 'Bài nháp', 'Tóm tắt nháp', 'Nội dung bài nháp', 'DRAFT', 101, NULL),
                       (2, 'post-pub', 'Bài đã xuất bản', 'Tóm tắt pub', 'Nội dung bài pub', 'PUBLISHED', 101, '2026-11-10 10:00:00'),
                       (3, 'post-arch', 'Bài lưu trữ', 'Tóm tắt arch', 'Nội dung bài arch', 'ARCHIVED', 101, NULL)
                """);

        mvc.perform(get("/api/v1/news")
                        .header("Authorization", bearer(101, "news-admin", "ADMIN"))
                        .param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].slug").value("post-draft"));

        mvc.perform(get("/api/v1/news")
                        .header("Authorization", bearer(101, "news-admin", "ADMIN"))
                        .param("status", "PUBLISHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].slug").value("post-pub"));

        mvc.perform(get("/api/v1/news")
                        .header("Authorization", bearer(101, "news-admin", "ADMIN"))
                        .param("status", "INVALID_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").exists());
    }

    @Test
    void listWithInvalidSortReturnsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/news")
                        .header("Authorization", bearer(101, "news-admin", "ADMIN"))
                        .param("sort", "unallowed_column,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Sort không hợp lệ")));
    }

    @Test
    void createNewsSuccessWithImageLinkAndLocationHeader() throws Exception {
        String payload = """
                {
                    "audiences": ["STAFF", "CUSTOMER"], "title": "Khai trương cơ sở mới Smart Cafe",
                    "summary": "Tưng bừng khai trương với nhiều ưu đãi",
                    "body": "Chào đón tất cả quý khách đến trải nghiệm không gian mới.",
                    "slug": "khai-truong-co-so-moi",
                    "status": "PUBLISHED",
                    "imageMediaIds": [201, 202]
                }
                """;

        var result = mvc.perform(post("/api/v1/news")
                        .header("Authorization", bearer(101, "news-admin", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesRegex("^/api/v1/news/\\d+$")))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.slug").value("khai-truong-co-so-moi"))
                .andExpect(jsonPath("$.title").value("Khai trương cơ sở mới Smart Cafe"))
                .andExpect(jsonPath("$.summary").value("Tưng bừng khai trương với nhiều ưu đãi"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty())
                .andExpect(jsonPath("$.authorId").value("101"))
                .andExpect(jsonPath("$.authorName").value("Quản trị viên News"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.images.length()").value(2))
                .andExpect(jsonPath("$.images[0].mediaId").value("201"))
                .andExpect(jsonPath("$.images[0].sortOrder").value(0))
                .andExpect(jsonPath("$.images[0].secureUrl").value("/api/v1/news/images/201"))
                .andExpect(jsonPath("$.images[1].mediaId").value("202"))
                .andExpect(jsonPath("$.images[1].sortOrder").value(1))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String createdId = responseBody.replaceAll(".*\"id\":\"(\\d+)\".*", "$1");

        // Read back
        mvc.perform(get("/api/v1/news/" + createdId)
                        .header("Authorization", bearer(101, "news-admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdId))
                .andExpect(jsonPath("$.title").value("Khai trương cơ sở mới Smart Cafe"));
    }

    @Test
    void createNewsAutoGeneratesUniqueSlugWhenOmitted() throws Exception {
        String payload1 = """
                {
                    "audiences": ["STAFF", "CUSTOMER"], "title": "Món mới hấp dẫn tháng 11",
                    "body": "Giới thiệu các món đồ uống mùa đông."
                }
                """;

        var result1 = mvc.perform(post("/api/v1/news")
                        .header("Authorization", bearer(101, "news-admin", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value(startsWith("mon-moi-hap-dan-thang-11")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.publishedAt").value(nullValue()))
                .andExpect(jsonPath("$.authorId").value("101"))
                .andReturn();

        // Create second post with identical title -> slug must resolve conflict with suffix
        String payload2 = """
                {
                    "audiences": ["STAFF", "CUSTOMER"], "title": "Món mới hấp dẫn tháng 11",
                    "body": "Đợt phát hành bài viết thứ hai."
                }
                """;

        mvc.perform(post("/api/v1/news")
                        .header("Authorization", bearer(101, "news-admin", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value(startsWith("mon-moi-hap-dan-thang-11-")));
    }

    @Test
    void createNewsDuplicateCustomSlugReturnsConflict409() throws Exception {
        String payload1 = """
                {
                    "audiences": ["STAFF", "CUSTOMER"], "title": "Khuyến mãi 20-11",
                    "slug": "khuyen-mai-20-11",
                    "body": "Nội dung bài viết 1"
                }
                """;
        mvc.perform(post("/api/v1/news")
                        .header("Authorization", bearer(101, "news-admin", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload1))
                .andExpect(status().isCreated());

        String payload2 = """
                {
                    "audiences": ["STAFF", "CUSTOMER"], "title": "Tiêu đề khác",
                    "slug": "khuyen-mai-20-11",
                    "body": "Nội dung bài viết 2"
                }
                """;
        mvc.perform(post("/api/v1/news")
                        .header("Authorization", bearer(101, "news-admin", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NEWS_SLUG_TAKEN"))
                .andExpect(jsonPath("$.detail").value(containsString("Đường dẫn bài viết")));
    }

    @Test
    void createNewsValidationFailures() throws Exception {
        // Blank title
        mvc.perform(post("/api/v1/news")
                        .header("Authorization", bearer(101, "news-admin", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audiences\":[\"STAFF\",\"CUSTOMER\"],\"title\":\"   \",\"body\":\"Nội dung hợp lệ\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Tiêu đề tin tức không được để trống")));

        // Title > 200 chars
        mvc.perform(post("/api/v1/news")
                        .header("Authorization", bearer(101, "news-admin", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audiences\":[\"STAFF\",\"CUSTOMER\"],\"title\":\"" + "A".repeat(201) + "\",\"body\":\"Nội dung hợp lệ\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("tối đa 200 ký tự")));

        // Blank body
        mvc.perform(post("/api/v1/news")
                        .header("Authorization", bearer(101, "news-admin", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audiences\":[\"STAFF\",\"CUSTOMER\"],\"title\":\"Tiêu đề\",\"body\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Nội dung tin tức không được để trống")));

        // Summary > 500 chars
        mvc.perform(post("/api/v1/news")
                        .header("Authorization", bearer(101, "news-admin", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audiences\":[\"STAFF\",\"CUSTOMER\"],\"title\":\"Tiêu đề\",\"summary\":\"" + "S".repeat(501) + "\",\"body\":\"Nội dung\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("tối đa 500 ký tự")));

        // Invalid slug characters
        mvc.perform(post("/api/v1/news")
                        .header("Authorization", bearer(101, "news-admin", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audiences\":[\"STAFF\",\"CUSTOMER\"],\"title\":\"Tiêu đề\",\"slug\":\"INVALID_SLUG!\",\"body\":\"Nội dung\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Slug không hợp lệ")));

        // Duplicate media IDs in same request
        mvc.perform(post("/api/v1/news")
                        .header("Authorization", bearer(101, "news-admin", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audiences\":[\"STAFF\",\"CUSTOMER\"],\"title\":\"Tiêu đề\",\"body\":\"Nội dung\",\"imageMediaIds\":[201, 201]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("trùng lặp")));
    }

    @Test
    void rejectsInvalidMediaBeforeWriting() throws Exception {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM news_posts", Integer.class)).isZero();

        // 203 is soft-deleted, 999 does not exist
        String payload = """
                {
                    "audiences": ["STAFF", "CUSTOMER"], "title": "Bài viết thất bại",
                    "body": "Nội dung bài viết",
                    "imageMediaIds": [201, 999]
                }
                """;

        mvc.perform(post("/api/v1/news")
                        .header("Authorization", bearer(101, "news-admin", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("do News tải lên")));

        // Pre-validation check, not evidence of rollback after an INSERT
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM news_posts", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM news_images", Integer.class)).isZero();
    }

    @Test
    void updateNewsSuccessAndReadBack() throws Exception {
        jdbc.update("""
                INSERT INTO news_posts(id, slug, title, summary, body, status, author_user_id, published_at, version)
                VALUES (10, 'post-original', 'Tiêu đề cũ', 'Tóm tắt cũ', 'Nội dung cũ', 'DRAFT', 101, NULL, 0)
                """);
        jdbc.update("INSERT INTO news_images(news_id, media_id, sort_order) VALUES (10, 201, 0)");

        String updatePayload = """
                {
                    "audiences": ["STAFF", "CUSTOMER"], "title": "Tiêu đề đã được cập nhật",
                    "summary": "Tóm tắt cập nhật mới",
                    "body": "Nội dung cập nhật mới hoàn toàn.",
                    "slug": "post-updated",
                    "status": "PUBLISHED",
                    "imageMediaIds": [202]
                }
                """;

        mvc.perform(put("/api/v1/news/10").header("If-Match", "\"0\"")
                        .header("Authorization", bearer(101, "news-admin", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("10"))
                .andExpect(jsonPath("$.slug").value("post-updated"))
                .andExpect(jsonPath("$.title").value("Tiêu đề đã được cập nhật"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty())
                .andExpect(jsonPath("$.authorId").value("101"))
                .andExpect(jsonPath("$.updatedByUserId").value("101"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.images.length()").value(1))
                .andExpect(jsonPath("$.images[0].mediaId").value("202"));

        // Confirm database state
        assertThat(jdbc.queryForObject("SELECT title FROM news_posts WHERE id = 10", String.class))
                .isEqualTo("Tiêu đề đã được cập nhật");
        assertThat(jdbc.queryForObject("SELECT updated_by_user_id FROM news_posts WHERE id = 10", Long.class))
                .isEqualTo(101L);
        assertThat(jdbc.queryForObject("SELECT media_id FROM news_images WHERE news_id = 10", Long.class))
                .isEqualTo(202L);
    }

    @Test
    void updateNewsNotFoundAndValidationErrors() throws Exception {
        mvc.perform(put("/api/v1/news/99999").header("If-Match", "\"0\"")
                        .header("Authorization", bearer(101, "news-admin", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audiences\":[\"STAFF\",\"CUSTOMER\"],\"title\":\"Tiêu đề\",\"body\":\"Nội dung\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(containsString("Không tìm thấy bài viết tin tức")));

        jdbc.update("""
                INSERT INTO news_posts(id, slug, title, summary, body, status, author_user_id)
                VALUES (11, 'post-11', 'Bài 11', 'Tóm tắt', 'Nội dung', 'DRAFT', 101),
                       (12, 'post-12', 'Bài 12', 'Tóm tắt', 'Nội dung', 'DRAFT', 101)
                """);

        // Conflict slug with post 11 when updating post 12
        mvc.perform(put("/api/v1/news/12").header("If-Match", "\"0\"")
                        .header("Authorization", bearer(101, "news-admin", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audiences\":[\"STAFF\",\"CUSTOMER\"],\"title\":\"Bài 12 mới\",\"slug\":\"post-11\",\"body\":\"Nội dung\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NEWS_SLUG_TAKEN"))
                .andExpect(jsonPath("$.detail").value(containsString("Đường dẫn bài viết")));
    }

    @Test
    void archiveNewsSuccessAndPreservesMediaAssets() throws Exception {
        jdbc.update("""
                INSERT INTO news_posts(id, slug, title, summary, body, status, author_user_id)
                VALUES (20, 'post-to-delete', 'Bài sắp xóa', 'Tóm tắt', 'Nội dung', 'DRAFT', 101)
                """);
        jdbc.update("INSERT INTO news_images(news_id, media_id, sort_order) VALUES (20, 201, 0)");

        // Admin can delete
        mvc.perform(delete("/api/v1/news/20").header("If-Match", "\"0\"")
                        .header("Authorization", bearer(101, "news-admin", "ADMIN")))
                .andExpect(status().isNoContent());

        // Archive preserves both article history and image links
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM news_posts WHERE id = 20 AND status='ARCHIVED'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM news_images WHERE news_id = 20", Integer.class)).isEqualTo(1);

        // CRITICAL: verify media asset 201 is NOT deleted or corrupted
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_assets WHERE id = 201 AND deleted_at IS NULL", Integer.class))
                .isEqualTo(1);

        // Admin can still inspect archived history
        mvc.perform(get("/api/v1/news/20")
                        .header("Authorization", bearer(101, "news-admin", "ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    void deleteNewsNotFound() throws Exception {
        mvc.perform(delete("/api/v1/news/88888").header("If-Match", "\"0\"")
                        .header("Authorization", bearer(101, "news-admin", "ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void permissionMatrixDenyByDefault() throws Exception {
        // Missing token -> 403 Forbidden on all endpoints
        mvc.perform(get("/api/v1/news")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/news/1")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/news").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/news/1").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/news/1")).andExpect(status().isForbidden());

        // Invalid / Tampered / Expired token -> 403 Forbidden
        mvc.perform(get("/api/v1/news").header("Authorization", "Bearer invalid-token")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/news").header("Authorization", signed(101, "news-admin", Instant.now().minusSeconds(10).toEpochMilli())))
                .andExpect(status().isForbidden());

        // Inactive user token -> 403 Forbidden
        mvc.perform(get("/api/v1/news").header("Authorization", bearer(104, "news-inactive", "STAFF")))
                .andExpect(status().isForbidden());

        // CUSTOMER can read its audience feed but cannot manage articles.
        mvc.perform(get("/api/v1/news").header("Authorization", bearer(103, "news-customer", "CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(post("/api/v1/news").header("Authorization", bearer(103, "news-customer", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"audiences\":[\"STAFF\",\"CUSTOMER\"],\"title\":\"T\",\"body\":\"B\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/news/1").header("Authorization", bearer(103, "news-customer", "CUSTOMER")))
                .andExpect(status().isForbidden());

        // STAFF reads its audience feed but never manages articles (Q&A 4/19).
        jdbc.update("""
                INSERT INTO news_posts(id, slug, title, summary, body, status, author_user_id)
                VALUES (30, 'post-staff-test', 'Bài staff', 'Tóm tắt', 'Nội dung', 'DRAFT', 101)
                """);
        mvc.perform(delete("/api/v1/news/30").header("Authorization", bearer(102, "news-staff", "STAFF")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/news").header("Authorization", bearer(102, "news-staff", "STAFF")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(post("/api/v1/news").header("Authorization", bearer(102, "news-staff", "STAFF"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"audiences\":[\"STAFF\",\"CUSTOMER\"],\"title\":\"T\",\"body\":\"B\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/news/30").header("Authorization", bearer(102, "news-staff", "STAFF"))
                        .header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audiences\":[\"STAFF\",\"CUSTOMER\"],\"title\":\"T\",\"body\":\"B\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientCannotSpoofServerManagedFields() throws Exception {
        // Client attempts to spoof authorId=999, version=100, publishedAt in 1990
        String spoofPayload = """
                {
                    "audiences": ["STAFF", "CUSTOMER"], "title": "Bài viết thử nghiệm spoofing",
                    "body": "Nội dung bài viết",
                    "authorId": 999,
                    "author_user_id": 999,
                    "version": 100,
                    "publishedAt": "1990-01-01T00:00:00Z"
                }
                """;

        var result = mvc.perform(post("/api/v1/news")
                        .header("Authorization", bearer(101, "news-admin", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(spoofPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorId").value("101")) // Server uses the authenticated admin
                .andExpect(jsonPath("$.version").value(0))        // Server starts version at 0
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String newsId = responseBody.replaceAll(".*\"id\":\"(\\d+)\".*", "$1");

        // Verify in database: author_user_id is strictly 101
        Long actualAuthorId = jdbc.queryForObject("SELECT author_user_id FROM news_posts WHERE id = ?", Long.class, Long.parseLong(newsId));
        assertThat(actualAuthorId).isEqualTo(101L);

        Long actualVersion = jdbc.queryForObject("SELECT version FROM news_posts WHERE id = ?", Long.class, Long.parseLong(newsId));
        assertThat(actualVersion).isZero();
    }

    private String bearer(int id, String username, String role) throws Exception {
        return signed(id, username, Instant.now().plusSeconds(3600).toEpochMilli());
    }

    private String signed(int id, String username, long expiry) throws Exception {
        // Matches the existing authentication module's four-field bearer contract:
        // base64url(id:username:role:expiryEpochMillis).hex(hmac-sha256)
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                (id + ":" + username + ":CUSTOMER:" + expiry).getBytes(StandardCharsets.UTF_8));
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "Bearer " + payload + "." + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
