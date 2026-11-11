package vn.codegym.smartcafe.menu;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = MenuIntegrationTest.TestDatabaseGuard.class)
class MenuIntegrationTest {
    static class TestDatabaseGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override public void initialize(ConfigurableApplicationContext context) {
            String url = context.getEnvironment().getProperty("spring.datasource.url", "");
            if (!url.matches("jdbc:mysql://[^/]+/[A-Za-z0-9_]+_test(?:\\?.*)?")) {
                throw new IllegalStateException("A dedicated MySQL *_test URL is required before Flyway runs");
            }
        }
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MenuCatalog catalog;
    @Autowired TransactionTemplate transaction;

    @BeforeEach
    void cleanOnlyDedicatedTestSchema() {
        String database = jdbc.queryForObject("SELECT DATABASE()", String.class);
        if (database == null || !database.matches("[A-Za-z0-9_]+_test")) {
            throw new IllegalStateException("TEST_DB_URL must point to a dedicated *_test schema");
        }
        jdbc.update("DELETE FROM menu_items");
        jdbc.update("DELETE FROM menu_categories");
    }

    private String category(String code, String name, long version) {
        return "{\"code\":\"%s\",\"name\":\"%s\",\"version\":%d}".formatted(code, name, version);
    }
    private String item(String code, String name, long price, String group, long version) {
        return """
                {"code":"%s","name":"%s","price":%d,"categoryCode":"%s","imageUrl":"/images/coffee.jpg",
                 "description":"Espresso","available":true,"version":%d}
                """.formatted(code, name, price, group, version);
    }
    private void fixtures() {
        jdbc.update("INSERT INTO menu_categories(code, name) VALUES ('CF','Cà phê'),('TE','Trà')");
        jdbc.update("""
                INSERT INTO menu_items(code,name,price,category_code,image_url) VALUES
                ('CF01','Black coffee',15000,'CF','/images/coffee.jpg'),
                ('CF02','Milk coffee',25000,'CF','/images/milk.jpg')
                """);
    }

    @Test void healthIsPublicButCatalogRequiresAuthentication() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.components").doesNotExist());
        for (String path : List.of("/items", "/categories", "/csrf")) {
            mvc.perform(get("/api/v1/menu" + path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/v1/menu/items").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/menu/items").header("X-Role", "ADMIN")).andExpect(status().isUnauthorized());
    }

    @Test @WithMockUser(roles = "STAFF") void staffCannotReadOrMutateCatalog() throws Exception {
        for (String path : List.of("/items", "/categories", "/csrf")) mvc.perform(get("/api/v1/menu" + path)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/menu/categories").with(csrf()).contentType("application/json").content(category("CF", "Coffee", 0))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/menu/items/CF01?version=0").with(csrf())).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(roles = "ADMIN") void mutationsRequireCsrfAndUnknownRoutesStayClosed() throws Exception {
        mvc.perform(get("/api/v1/menu/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
        mvc.perform(post("/api/v1/menu/categories").contentType("application/json").content(category("CF", "Coffee", 0))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/menu/items").with(jwt().authorities(() -> "ROLE_ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/menu/items").with(jwt().authorities(() -> "ROLE_STAFF"))).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(roles = "ADMIN") void categoryCrudNormalizesAndPreservesDeletedCode() throws Exception {
        mvc.perform(post("/api/v1/menu/categories").with(csrf()).contentType("application/json").content(category(" cf ", "  Cà phê  ", 0)))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/v1/menu/categories/CF"))
                .andExpect(jsonPath("$.name").value("Cà phê")).andExpect(jsonPath("$.version").value(0));
        mvc.perform(put("/api/v1/menu/categories/CF").with(csrf()).contentType("application/json").content(category("cf", "Coffee", 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/v1/menu/categories?keyword=Coffee")).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(delete("/api/v1/menu/categories/CF?version=0").with(csrf())).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/menu/categories/CF?version=1").with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/menu/categories/CF")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/menu/categories").with(csrf()).contentType("application/json").content(category("CF", "Again", 0)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MENU_CONFLICT"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM menu_categories WHERE active=FALSE", Long.class)).isEqualTo(1);
    }

    @Test @WithMockUser(roles = "ADMIN") void itemCrudReturnsCanonicalValuesAndRejectsStaleWrites() throws Exception {
        fixtures();
        mvc.perform(post("/api/v1/menu/items").with(csrf()).contentType("application/json").content(item("cf03", "Caramel Latte", 45000, "cf", 0)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("CF03"))
                .andExpect(jsonPath("$.categoryName").value("Cà phê")).andExpect(jsonPath("$.description").value("Espresso"));
        mvc.perform(put("/api/v1/menu/items/CF03").with(csrf()).contentType("application/json").content(item("CF03", "New coffee", 50000, "CF", 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.price").value(50000));
        mvc.perform(put("/api/v1/menu/items/CF03").with(csrf()).contentType("application/json").content(item("CF03", "Other change", 60000, "CF", 0))).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/menu/items/CF03?version=0").with(csrf())).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/menu/items/CF03?version=1").with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/menu/items/CF03")).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT price FROM menu_items WHERE code='CF03'", Long.class)).isEqualTo(50000);
    }

    @Test @WithMockUser(roles = "ADMIN") void searchPaginationAndLiteralWildcards() throws Exception {
        fixtures();
        for (String[] search : new String[][] {{"code", "cf01"}, {"name", "BLACK"}, {"price", "15000"}, {"category", "ca phe"}}) {
            mvc.perform(get("/api/v1/menu/items").param("field", search[0]).param("keyword", search[1]))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].code").value("CF01"));
        }
        mvc.perform(get("/api/v1/menu/items?size=1&page=1")).andExpect(jsonPath("$.content[0].code").value("CF02")).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/menu/items").param("keyword", "%" )).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/menu/items").param("keyword", "' OR 1=1 --")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/menu/items?categoryCode=TE")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/menu/items?page=2147483647&size=100")).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test @WithMockUser(roles = "ADMIN") void validationRejectsUnsafeAndMalformedInput() throws Exception {
        fixtures();
        for (String query : List.of("size=101", "size=0", "page=-1", "field=sql", "field=price&keyword=abc", "field=price&keyword=5000.5")) {
            mvc.perform(get("/api/v1/menu/items?" + query)).andExpect(status().isBadRequest());
        }
        for (long price : List.of(0L, 5000L, 1000000000000L)) {
            mvc.perform(post("/api/v1/menu/items").with(csrf()).contentType("application/json").content(item("CF09", "New coffee", price, "CF", 0)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.price").exists());
        }
        mvc.perform(post("/api/v1/menu/items").with(csrf()).contentType("application/json").content(item("CF09", "     a", 15000, "CF", 0))).andExpect(status().isBadRequest());
        for (String url : List.of("https://evil.invalid/a.jpg", "//evil.invalid/a.jpg", "/images/../a.jpg", "data:image/png;base64,abc")) {
            mvc.perform(post("/api/v1/menu/items").with(csrf()).contentType("application/json").content(item("CF09", "New coffee", 15000, "CF", 0).replace("/images/coffee.jpg", url)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IMAGE_URL"));
        }
        mvc.perform(post("/api/v1/menu/categories").with(csrf()).contentType("application/json").content(category("NEW", "New group", 0).replace("}", ",\"role\":\"ADMIN\"}")))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/menu/items/CF01").with(csrf())).andExpect(status().isBadRequest());
    }

    @Test @WithMockUser(roles = "ADMIN") void categoryAndItemCodesAndGroupsAreImmutable() throws Exception {
        fixtures();
        mvc.perform(put("/api/v1/menu/categories/CF").with(csrf()).contentType("application/json").content(category("TE", "Other group", 0))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/menu/items/CF01").with(csrf()).contentType("application/json").content(item("CF02", "Updated coffee", 15000, "CF", 0))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/menu/items/CF01").with(csrf()).contentType("application/json").content(item("CF01", "Updated coffee", 15000, "TE", 0))).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/menu/items").with(csrf()).contentType("application/json").content(item("CF09", "New coffee", 15000, "MISSING", 0))).andExpect(status().isConflict());
    }

    @Test @WithMockUser(roles = "ADMIN") void deletingGroupIsAtomicAndPreservesRowsAndRejectsLateWrites() throws Exception {
        fixtures();
        mvc.perform(delete("/api/v1/menu/categories/CF?version=9").with(csrf())).andExpect(status().isConflict());
        assertThat(catalog.items("name", "", null, 0, 10).totalElements()).isEqualTo(2);
        mvc.perform(delete("/api/v1/menu/categories/CF?version=0").with(csrf())).andExpect(status().isNoContent());
        assertThat(catalog.items("name", "", null, 0, 10).totalElements()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM menu_items WHERE active=FALSE AND version=1", Long.class)).isEqualTo(2);
        mvc.perform(post("/api/v1/menu/items").with(csrf()).contentType("application/json").content(item("CF09", "New coffee", 15000, "CF", 0))).andExpect(status().isConflict());
    }

    @Test void simultaneousUpdatesHaveOneWinner() throws Exception {
        fixtures();
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var tasks = List.of(pool.submit(() -> raceUpdate(gate, "First coffee")), pool.submit(() -> raceUpdate(gate, "Second coffee")));
            gate.countDown();
            assertThat(List.of(tasks.get(0).get(10, TimeUnit.SECONDS), tasks.get(1).get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder("saved", "conflict");
        }
        assertThat(catalog.item("CF01").version()).isEqualTo(1);
    }
    private String raceUpdate(CountDownLatch gate, String name) throws Exception {
        gate.await();
        try {
            catalog.updateItem("CF01", new MenuModels.ItemInput("CF01", name, new java.math.BigDecimal("20000"), "CF", "/images/coffee.jpg", "", 0L, true));
            return "saved";
        } catch (MenuException error) { return error.status().value() == 409 ? "conflict" : "unexpected"; }
    }

    @Test void deleteGroupLockBlocksNewItemsUntilDeletionCommits() throws Exception {
        fixtures();
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var deletion = pool.submit(() -> transaction.executeWithoutResult(status -> {
                catalog.deleteCategory("CF", 0);
                locked.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var insertion = pool.submit(() -> {
                try { catalog.createItem(new MenuModels.ItemInput("CF09", "Late coffee", new java.math.BigDecimal("15000"), "CF", "/images/coffee.jpg", "", 0L, true)); return "saved"; }
                catch (MenuException error) { return error.code(); }
            });
            try { assertThatThrownBy(() -> insertion.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class); }
            finally { release.countDown(); }
            deletion.get(5, TimeUnit.SECONDS);
            assertThat(insertion.get(5, TimeUnit.SECONDS)).isEqualTo("CATEGORY_INACTIVE");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM menu_items WHERE code='CF09'", Long.class)).isZero();
    }
}
