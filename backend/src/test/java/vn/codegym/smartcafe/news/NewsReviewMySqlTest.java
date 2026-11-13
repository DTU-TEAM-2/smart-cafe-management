package vn.codegym.smartcafe.news;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.LinkedBlockingQueue;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Deliberately no test-managed transaction: requests must commit/rollback through the service proxy.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=${TEST_DB_URL}", "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD:}",
        "smartcafe.auth.token-secret=review-news-secret-only-at-least-32-characters"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:mysql:.*")
class NewsReviewMySqlTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoSpyBean JdbcClient client;
    @LocalServerPort int port;
    @Autowired vn.codegym.smartcafe.config.BearerTokenAuthenticator authenticator;

    @BeforeEach
    void seed() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).endsWith("_test");
        clean();
        jdbc.update("""
                INSERT INTO users(id,username,password_hash,full_name,email,role,status)
                VALUES (901,'review-admin','not-a-login-hash','Review admin','review@example.invalid','ADMIN','ACTIVE'),
                    (902,'review-staff','not-a-login-hash','Review staff','review-staff@example.invalid','STAFF','ACTIVE'),
                    (903,'review-customer','not-a-login-hash','Review customer','review-customer@example.invalid','CUSTOMER','ACTIVE')
                """);
        jdbc.update("""
                INSERT INTO media_assets(id,provider,provider_asset_id,secure_url,mime_type,size_bytes)
                VALUES (901,'FIREBASE','news/00000000-0000-0000-0000-000000000901.jpg','https://example.invalid/review.jpg','image/jpeg',12)
                """);
    }

    @AfterEach
    void clean() {
        reset(client);
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).endsWith("_test");
        jdbc.update("DELETE FROM news_images");
        jdbc.update("DELETE FROM news_posts");
        jdbc.update("DELETE FROM users WHERE id IN (901,902,903)");
        jdbc.update("DELETE FROM media_assets WHERE id=901");
    }

    @Test
    void rejectsInvalidPaginationAndHandlesLargeOffset() throws Exception {
        seedPost();
        for (var parameter : List.of(new String[]{"page", "-1"}, new String[]{"size", "0"},
                new String[]{"size", "101"}, new String[]{"page", "abc"})) {
            mvc.perform(get("/api/v1/news").with(user("review-admin").roles("ADMIN"))
                            .param(parameter[0], parameter[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        mvc.perform(get("/api/v1/news").with(user("review-admin").roles("ADMIN"))
                        .param("page", "2147483647").param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void malformedRequestsUseSanitizedProblemDetails() throws Exception {
        for (String body : List.of("{", "null", "{\"audiences\":[\"STAFF\",\"CUSTOMER\"],\"title\":\"T\",\"body\":\"B\",\"imageMediaIds\":[{}]}")) {
            mvc.perform(post("/api/v1/news").with(user("review-admin").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.instance").value("/api/v1/news"));
        }
    }

    @Test
    void failedImageInsertRollsBackRealCommittedRequest() throws Exception {
        doThrow(new DataIntegrityViolationException("private JDBC details"))
                .when(client).sql(argThat(sql -> sql != null && sql.stripLeading().startsWith("INSERT INTO news_images")));
        mvc.perform(post("/api/v1/news").with(user("review-admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audiences\":[\"STAFF\",\"CUSTOMER\"],\"title\":\"Rollback\",\"body\":\"Body\",\"imageMediaIds\":[901]}"))
                .andExpect(status().isBadRequest()).andExpect(content().string(not(containsString("private JDBC"))));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM news_posts", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM news_images", Integer.class)).isZero();
    }

    @Test
    void concurrentAutoSlugsCommitTwoPosts() throws Exception {
        var barrier = new CyclicBarrier(2);
        var attempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.getAndIncrement() < 2) barrier.await(10, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(client).sql(argThat(sql -> sql != null && sql.stripLeading().startsWith("INSERT INTO news_posts")));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var jobs = List.of(pool.submit(this::createSameTitle), pool.submit(this::createSameTitle));
            for (var job : jobs) assertThat(job.get(20, TimeUnit.SECONDS)).isEqualTo(201);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT slug) FROM news_posts", Integer.class)).isEqualTo(2);
    }

    @Test
    void staleEditorCannotOverwriteNewerPostOrImages() throws Exception {
        seedPost();
        mvc.perform(get("/api/v1/news/901").with(user("review-admin").roles("ADMIN")))
                .andExpect(header().string("ETag", "\"0\""));
        mvc.perform(put("/api/v1/news/901").with(user("review-admin").roles("ADMIN"))
                        .header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audiences\":[\"STAFF\",\"CUSTOMER\"],\"title\":\"New\",\"body\":\"New body\",\"imageMediaIds\":[901]}"))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"1\""));
        mvc.perform(put("/api/v1/news/901").with(user("review-admin").roles("ADMIN"))
                        .header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audiences\":[\"STAFF\",\"CUSTOMER\"],\"title\":\"Stale\",\"body\":\"Stale body\",\"imageMediaIds\":[]}"))
                .andExpect(status().isPreconditionFailed());
        assertThat(jdbc.queryForObject("SELECT title FROM news_posts WHERE id=901", String.class)).isEqualTo("New");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM news_images WHERE news_id=901", Integer.class)).isEqualTo(1);
    }

    @Test
    void missingOrInvalidPreconditionsCannotMutatePost() throws Exception {
        seedPost();
        mvc.perform(put("/api/v1/news/901").with(user("review-admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"audiences\":[\"STAFF\",\"CUSTOMER\"],\"title\":\"New\",\"body\":\"Body\"}"))
                .andExpect(status().is(428));
        for (String etag : List.of("*", "W/\"0\"", "\"-1\"", "\"9223372036854775808\"")) {
            mvc.perform(delete("/api/v1/news/901").with(user("review-admin").roles("ADMIN"))
                            .header("If-Match", etag))
                    .andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT version FROM news_posts WHERE id=901", Long.class)).isZero();
    }

    @Test
    void deleteArchivesAndHidesPostWithoutDestroyingImages() throws Exception {
        seedPost();
        jdbc.update("INSERT INTO news_images(news_id,media_id,sort_order) VALUES (901,901,0)");
        mvc.perform(delete("/api/v1/news/901").with(user("review-admin").roles("ADMIN"))
                        .header("If-Match", "\"0\""))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT status FROM news_posts WHERE id=901", String.class)).isEqualTo("ARCHIVED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM news_images WHERE news_id=901", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_assets WHERE id=901", Integer.class)).isEqualTo(1);
        mvc.perform(get("/api/v1/news").with(user("review-admin").roles("ADMIN")))
                .andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(get("/api/v1/news").param("status", "ARCHIVED").with(user("review-admin").roles("ADMIN")))
                .andExpect(jsonPath("$.content[0].id").value("901"));
    }

    @Test
    void twoRealSocketClientsReceiveOnlyCommittedChanges() throws Exception {
        var first = new SocketClient();
        var second = new SocketClient();
        try (var http = HttpClient.newHttpClient()) {
            var ws1 = connect(http, first);
            var ws2 = connect(http, second);
            try {
                String token = bearer(902, "review-staff");
                ws1.sendText(token, true).get(5, TimeUnit.SECONDS);
                ws2.sendText(token, true).get(5, TimeUnit.SECONDS);
                assertThat(first.messages.poll(5, TimeUnit.SECONDS)).contains("READY");
                assertThat(second.messages.poll(5, TimeUnit.SECONDS)).contains("READY");

                // Fail after the event is registered and after the writes, before transaction commit.
                doThrow(new org.springframework.dao.DataAccessResourceFailureException("private details"))
                        .when(client).sql(argThat(sql -> sql != null && sql.stripLeading()
                                .startsWith("SELECT p.id, p.slug, p.title, p.summary, p.body")));
                mvc.perform(post("/api/v1/news").with(user("review-admin").roles("ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON).content("{\"audiences\":[\"STAFF\",\"CUSTOMER\"],\"title\":\"Failed\",\"body\":\"Body\",\"status\":\"PUBLISHED\"}"))
                        .andExpect(status().isServiceUnavailable());
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM news_posts", Integer.class)).isZero();
                assertThat(first.messages.poll(200, TimeUnit.MILLISECONDS)).isNull();
                assertThat(second.messages.poll(200, TimeUnit.MILLISECONDS)).isNull();
                reset(client);

                assertThat(createPublishedPost()).isEqualTo(201);
                String message1 = first.messages.poll(5, TimeUnit.SECONDS);
                String message2 = second.messages.poll(5, TimeUnit.SECONDS);
                assertThat(message1).isEqualTo("{\"type\":\"NEWS_CHANGED\"}");
                assertThat(message2).isEqualTo(message1);
                // Current role/auth-version is rechecked before sending any event.
                jdbc.update("UPDATE users SET auth_version=1 WHERE id=902");
                assertThat(createPublishedPost()).isEqualTo(201);
                assertThat(first.closed.get(6, TimeUnit.SECONDS)).isEqualTo(1008);
                assertThat(second.closed.get(6, TimeUnit.SECONDS)).isEqualTo(1008);
                assertThat(first.messages.poll()).isNull();
                mvc.perform(get("/api/v1/news").header("Authorization", token)).andExpect(status().isForbidden());
            } finally { ws1.abort(); ws2.abort(); }
        }
    }

    @Test
    void realUpgradeRejectsSourceOverflowWith429AndAcceptsAfterDisconnect() throws Exception {
        var sockets = new java.util.ArrayList<WebSocket>();
        var listeners = new java.util.ArrayList<SocketClient>();
        try (var http = HttpClient.newHttpClient()) {
            try {
                for (int i = 0; i < 16; i++) {
                    var listener = new SocketClient(); listeners.add(listener); sockets.add(connect(http, listener));
                }
                try {
                    connect(http, new SocketClient());
                    throw new AssertionError("A seventeenth source connection must be refused before upgrade");
                } catch (java.util.concurrent.ExecutionException failure) {
                    assertThat(failure.getCause()).isInstanceOf(java.net.http.WebSocketHandshakeException.class);
                    var rejected = (java.net.http.WebSocketHandshakeException) failure.getCause();
                    assertThat(rejected.getResponse().statusCode()).isEqualTo(429);
                    assertThat(rejected.getResponse().headers().firstValue("Retry-After")).contains("10");
                }
                sockets.getFirst().sendClose(WebSocket.NORMAL_CLOSURE, "release one slot").get(5, TimeUnit.SECONDS);
                listeners.getFirst().closed.get(5, TimeUnit.SECONDS);
                var replacementListener = new SocketClient();
                var replacement = connect(http, replacementListener); sockets.add(replacement);
                // The budget is per source before auth; an accepted replacement can authenticate normally.
                replacement.sendText(bearer(902, "review-staff"), true).get(5, TimeUnit.SECONDS);
                assertThat(replacementListener.messages.poll(5, TimeUnit.SECONDS)).contains("READY");
            } finally { sockets.forEach(WebSocket::abort); }
        }
    }

    @Test
    void socketRejectsInvalidTokenAndCrossOriginHandshake() throws Exception {
        try (var http = HttpClient.newHttpClient()) {
            for (String token : List.of("Bearer invalid", bearer(901, "review-admin"), bearer(903, "review-customer"))) {
                var listener = new SocketClient();
                var ws = connect(http, listener);
                try {
                    ws.sendText(token, true).get(5, TimeUnit.SECONDS);
                    assertThat(listener.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
                    assertThat(listener.messages.poll()).isNull();
                } finally { ws.abort(); }
            }
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> http.newWebSocketBuilder()
                    .header("Origin", "https://untrusted.example.invalid")
                    .buildAsync(URI.create("ws://127.0.0.1:" + port + "/api/v1/news/events"), new SocketClient())
                    .get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(java.net.http.WebSocketHandshakeException.class);
        }
    }

    @Test
    void staffSocketDoesNotAnnounceDraftOrCustomerOnlyAndNotifiesAudienceRemoval() throws Exception {
        var listener = new SocketClient();
        try (var http = HttpClient.newHttpClient()) {
            var ws = connect(http, listener);
            try {
                ws.sendText(bearer(902, "review-staff"), true).get(5, TimeUnit.SECONDS);
                assertThat(listener.messages.poll(5, TimeUnit.SECONDS)).contains("READY");
                assertThat(createSameTitle()).isEqualTo(201); // DRAFT, both audiences.
                mvc.perform(post("/api/v1/news").with(user("review-admin").roles("ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"Customer only\",\"body\":\"Body\",\"status\":\"PUBLISHED\",\"audiences\":[\"CUSTOMER\"]}"))
                        .andExpect(status().isCreated());
                assertThat(listener.messages.poll(250, TimeUnit.MILLISECONDS)).isNull();

                mvc.perform(post("/api/v1/news").with(user("review-admin").roles("ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"slug\":\"staff-visible\",\"title\":\"Staff\",\"body\":\"Body\",\"status\":\"PUBLISHED\",\"audiences\":[\"STAFF\"]}"))
                        .andExpect(status().isCreated());
                assertThat(listener.messages.poll(5, TimeUnit.SECONDS)).isEqualTo("{\"type\":\"NEWS_CHANGED\"}");
                long id = jdbc.queryForObject("SELECT id FROM news_posts WHERE slug='staff-visible'", Long.class);
                mvc.perform(put("/api/v1/news/" + id).with(user("review-admin").roles("ADMIN"))
                                .header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"Now customer\",\"body\":\"Body\",\"audiences\":[\"CUSTOMER\"]}"))
                        .andExpect(status().isOk());
                // Staff must refresh to remove a formerly visible article; payload never leaks its ID.
                assertThat(listener.messages.poll(5, TimeUnit.SECONDS)).isEqualTo("{\"type\":\"NEWS_CHANGED\"}");
                mvc.perform(put("/api/v1/news/" + id).with(user("review-admin").roles("ADMIN"))
                                .header("If-Match", "\"1\"").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"Still customer\",\"body\":\"Body\"}"))
                        .andExpect(status().isOk());
                assertThat(listener.messages.poll(250, TimeUnit.MILLISECONDS)).isNull();
                mvc.perform(put("/api/v1/news/" + id).with(user("review-admin").roles("ADMIN"))
                                .header("If-Match", "\"2\"").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"Back to staff\",\"body\":\"Body\",\"audiences\":[\"STAFF\"]}"))
                        .andExpect(status().isOk());
                assertThat(listener.messages.poll(5, TimeUnit.SECONDS)).isEqualTo("{\"type\":\"NEWS_CHANGED\"}");
                mvc.perform(delete("/api/v1/news/" + id).with(user("review-admin").roles("ADMIN"))
                                .header("If-Match", "\"3\""))
                        .andExpect(status().isNoContent());
                assertThat(listener.messages.poll(5, TimeUnit.SECONDS)).isEqualTo("{\"type\":\"NEWS_CHANGED\"}");
            } finally { ws.abort(); }
        }
    }

    @Test
    void publicationTransitionsAndUtcTimestampsAreConsistent() throws Exception {
        var result = mvc.perform(post("/api/v1/news").with(user("review-admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audiences\":[\"STAFF\",\"CUSTOMER\"],\"title\":\"UTC\",\"body\":\"Body\",\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("ETag", "\"0\""))
                .andReturn();
        assertThat(jdbc.queryForObject("SELECT ABS(TIMESTAMPDIFF(SECOND,created_at,UTC_TIMESTAMP(6))) FROM news_posts", Long.class))
                .isLessThan(10);
        long id = jdbc.queryForObject("SELECT id FROM news_posts", Long.class);
        mvc.perform(put("/api/v1/news/" + id).with(user("review-admin").roles("ADMIN"))
                        .header("If-Match", result.getResponse().getHeader("ETag"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audiences\":[\"STAFF\",\"CUSTOMER\"],\"title\":\"Draft\",\"body\":\"Body\",\"status\":\"DRAFT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.publishedAt").isEmpty());
    }

    @Test
    void concurrentEditorsHaveOneWinnerAndConsistentImageLinks() throws Exception {
        seedPost();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> updateWithTitle("First", "[901]"));
            var second = pool.submit(() -> updateWithTitle("Second", "[]"));
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 412);
        }
        String title = jdbc.queryForObject("SELECT title FROM news_posts WHERE id=901", String.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM news_images WHERE news_id=901", Integer.class))
                .isEqualTo("First".equals(title) ? 1 : 0);
        assertThat(jdbc.queryForObject("SELECT version FROM news_posts WHERE id=901", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT audience_staff FROM news_posts WHERE id=901", Integer.class))
                .isEqualTo("First".equals(title) ? 1 : 0);
        assertThat(jdbc.queryForObject("SELECT audience_customer FROM news_posts WHERE id=901", Integer.class))
                .isEqualTo("First".equals(title) ? 0 : 1);
    }

    @Test
    void fractionalOrOutOfRangeMediaIdsAreRejectedWithoutLinkingOtherAssets() throws Exception {
        for (String ids : List.of("[901.5]", "[9.01e2]", "[9223372036854775808]", "[null]", "[0]", "[-1]")) {
            mvc.perform(post("/api/v1/news").with(user("review-admin").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"audiences\":[\"STAFF\",\"CUSTOMER\"],\"title\":\"IDs\",\"body\":\"Body\",\"imageMediaIds\":" + ids + "}"))
                    .andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM news_posts", Integer.class)).isZero();
    }

    @Test
    void unicodeTitlesUseMysqlCharacterLimits() throws Exception {
        mvc.perform(post("/api/v1/news").with(user("review-admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audiences\":[\"STAFF\",\"CUSTOMER\"],\"title\":\"" + "🍵".repeat(200) + "\",\"body\":\"Body\"}"))
                .andExpect(status().isCreated());
    }

    private int updateWithTitle(String title, String imageIds) throws Exception {
        String audience = "First".equals(title) ? "STAFF" : "CUSTOMER";
        return mvc.perform(put("/api/v1/news/901").with(user("review-admin").roles("ADMIN"))
                        .header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audiences\":[\"" + audience + "\"],\"title\":\"" + title + "\",\"body\":\"Body\",\"imageMediaIds\":" + imageIds + "}"))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void failedImageReplacementRollsBackAudienceAndPost() throws Exception {
        seedPost();
        jdbc.update("UPDATE news_posts SET audience_staff=1 WHERE id=901");
        jdbc.update("INSERT INTO news_images(news_id,media_id,sort_order) VALUES (901,901,0)");
        doThrow(new DataIntegrityViolationException("private JDBC details"))
                .when(client).sql(argThat(sql -> sql != null && sql.stripLeading().startsWith("INSERT INTO news_images")));
        mvc.perform(put("/api/v1/news/901").with(user("review-admin").roles("ADMIN"))
                        .header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Should rollback\",\"body\":\"New body\",\"audiences\":[\"CUSTOMER\"],\"imageMediaIds\":[901]}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT title FROM news_posts WHERE id=901", String.class)).isEqualTo("Original");
        assertThat(jdbc.queryForObject("SELECT version FROM news_posts WHERE id=901", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT audience_staff FROM news_posts WHERE id=901", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT audience_customer FROM news_posts WHERE id=901", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM news_images WHERE news_id=901", Integer.class)).isEqualTo(1);
    }

    private WebSocket connect(HttpClient http, SocketClient listener) throws Exception {
        return http.newWebSocketBuilder().buildAsync(URI.create("ws://127.0.0.1:" + port + "/api/v1/news/events"), listener)
                .get(5, TimeUnit.SECONDS);
    }

    private String bearer() throws Exception {
        return bearer(901, "review-admin");
    }

    private String bearer(int id, String username) throws Exception {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                (id + ":" + username + ":IGNORED:" + Instant.now().plusSeconds(120).toEpochMilli()).getBytes(StandardCharsets.UTF_8));
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("review-news-secret-only-at-least-32-characters".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "Bearer " + payload + "." + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void staffNotificationIdentityExcludesAdminCustomerAndRevokedTokens() throws Exception {
        assertThat(authenticator.isCurrentStaff(authenticator.authenticate(bearer(902, "review-staff")))).isTrue();
        assertThat(authenticator.isCurrentStaff(authenticator.authenticate(bearer(901, "review-admin")))).isFalse();
        assertThat(authenticator.isCurrentStaff(authenticator.authenticate(bearer(903, "review-customer")))).isFalse();
        var staff = authenticator.authenticate(bearer(902, "review-staff"));
        jdbc.update("UPDATE users SET auth_version=1 WHERE id=902");
        assertThat(authenticator.isCurrentStaff(staff)).isFalse();
    }

    private static final class SocketClient implements WebSocket.Listener {
        final LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final java.util.concurrent.CompletableFuture<Integer> closed = new java.util.concurrent.CompletableFuture<>();
        private final StringBuilder text = new StringBuilder();
        @Override public void onOpen(WebSocket ws) { ws.request(1); }
        @Override public java.util.concurrent.CompletionStage<?> onText(WebSocket ws, CharSequence fragment, boolean last) {
            text.append(fragment);
            if (last) { messages.add(text.toString()); text.setLength(0); }
            ws.request(1);
            return null;
        }
        @Override public java.util.concurrent.CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
            closed.complete(code);
            return null;
        }
        @Override public void onError(WebSocket ws, Throwable error) { closed.completeExceptionally(error); }
    }

    private int createPublishedPost() throws Exception {
        return mvc.perform(post("/api/v1/news").with(user("review-admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audiences\":[\"STAFF\",\"CUSTOMER\"],\"title\":\"Published\",\"body\":\"Body\",\"status\":\"PUBLISHED\"}"))
                .andReturn().getResponse().getStatus();
    }

    private int createSameTitle() throws Exception {
        return mvc.perform(post("/api/v1/news").with(user("review-admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"audiences\":[\"STAFF\",\"CUSTOMER\"],\"title\":\"Same title\",\"body\":\"Body\"}"))
                .andReturn().getResponse().getStatus();
    }

    private void seedPost() {
        jdbc.update("INSERT INTO news_posts(id,slug,title,body,author_user_id) VALUES (901,'review-post','Original','Body',901)");
    }

    @Test
    void conflictResponsesDistinguishSlugFromBusyWritesAndRetainData() throws Exception {
        seedPost();
        mvc.perform(post("/api/v1/news").with(user("review-admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Duplicate\",\"body\":\"Body\",\"slug\":\"review-post\",\"audiences\":[\"STAFF\"]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NEWS_SLUG_TAKEN"));
        doThrow(new org.springframework.dao.CannotAcquireLockException("private SQL details"))
                .when(client).sql(argThat(sql -> sql != null && sql.stripLeading().startsWith("UPDATE news_posts")));
        mvc.perform(put("/api/v1/news/901").with(user("review-admin").roles("ADMIN"))
                        .header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Busy update\",\"body\":\"Body\",\"audiences\":[\"STAFF\"]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NEWS_WRITE_BUSY"))
                .andExpect(content().string(not(containsString("private SQL"))));
        assertThat(jdbc.queryForObject("SELECT title FROM news_posts WHERE id=901", String.class)).isEqualTo("Original");
        assertThat(jdbc.queryForObject("SELECT version FROM news_posts WHERE id=901", Long.class)).isZero();
        reset(client);
        doThrow(new org.springframework.dao.DuplicateKeyException("private SQL uq_news_slug"))
                .when(client).sql(argThat(sql -> sql != null && sql.stripLeading().startsWith("INSERT INTO news_posts")));
        mvc.perform(post("/api/v1/news").with(user("review-admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Race\",\"body\":\"Body\",\"slug\":\"free-slug\",\"audiences\":[\"STAFF\"]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NEWS_SLUG_TAKEN"))
                .andExpect(content().string(not(containsString("private SQL"))));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM news_posts", Integer.class)).isEqualTo(1);
    }
}
