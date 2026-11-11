package vn.codegym.smartcafe.customermenu;

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
class CustomerMenuMySqlTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @BeforeEach
    void requireEmptyTestDatabase() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).endsWith("_test");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM menu_items", Integer.class)).isZero();
    }

    @Test
    void returnsCategoriesItemsAvailabilityAndHandlesEmptyAndMissing() throws Exception {
        mvc.perform(get("/api/v1/customer/menu/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        jdbc.update("INSERT INTO menu_categories(id, code, name, active) VALUES (1,'COFFEE','Cà phê',1),(2,'TEA','Trà',1),(3,'HIDDEN','Nhóm ẩn',0)");
        jdbc.update("""
                INSERT INTO media_assets(id, provider, provider_asset_id, secure_url, mime_type, size_bytes, deleted_at)
                VALUES (1,'CLOUDINARY','latte','https://example.invalid/latte.jpg','image/jpeg',1024,NULL),
                       (2,'CLOUDINARY','deleted','https://example.invalid/deleted.jpg','image/jpeg',1024,CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO menu_items(id, category_id, code, name, price, image_media_id, active, available)
                VALUES (1,1,'CF01','Caramel Latte',45000,1,1,1),
                       (2,1,'CF02','Bạc xỉu 50%',40000,2,1,0),
                       (3,1,'CF03','Món ngừng kinh doanh',35000,NULL,0,1),
                       (4,3,'HD01','Món thuộc nhóm ẩn',50000,NULL,1,1)
                """);

        mvc.perform(get("/api/v1/customer/menu/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Cà phê"))
                .andExpect(jsonPath("$[0].activeItemCount").value(2))
                .andExpect(jsonPath("$[1].name").value("Trà"))
                .andExpect(jsonPath("$[1].activeItemCount").value(0));

        mvc.perform(get("/api/v1/customer/menu/items").param("categoryId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Bạc xỉu 50%"))
                .andExpect(jsonPath("$.content[0].available").value(false))
                .andExpect(jsonPath("$.content[0].imageUrl").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.content[1].name").value("Caramel Latte"))
                .andExpect(jsonPath("$.content[1].price").value("45000"))
                .andExpect(jsonPath("$.content[1].imageUrl").value("https://example.invalid/latte.jpg"))
                .andExpect(jsonPath("$.content[1].available").value(true));

        mvc.perform(get("/api/v1/customer/menu/items").param("categoryId", "1").param("availableOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].code").value("CF01"));

        mvc.perform(get("/api/v1/customer/menu/items").param("keyword", "50%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].code").value("CF02"));

        mvc.perform(get("/api/v1/customer/menu/items").param("categoryId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty());

        mvc.perform(get("/api/v1/customer/menu/items").param("categoryId", "3"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Không tìm thấy nhóm món."));

        mvc.perform(get("/api/v1/customer/menu/items/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CF01"));

        mvc.perform(get("/api/v1/customer/menu/items/3"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Không tìm thấy món."));

        mvc.perform(get("/api/v1/customer/menu/items/4"))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/customer/menu/items"))
                .andExpect(status().isForbidden());
    }
}
