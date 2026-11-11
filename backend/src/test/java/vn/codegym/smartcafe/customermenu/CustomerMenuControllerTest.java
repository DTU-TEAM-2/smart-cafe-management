package vn.codegym.smartcafe.customermenu;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:1/unavailable_test",
        "spring.datasource.username=unused",
        "spring.datasource.password="
})
@AutoConfigureMockMvc
class CustomerMenuControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CustomerMenuService service;

    @Test
    void listCategoriesReturnsOk() throws Exception {
        given(service.listCategories()).willReturn(List.of(
                new CustomerMenuModels.Category("1", "COFFEE", "Cà phê", 4),
                new CustomerMenuModels.Category("2", "EMPTY", "Danh mục trống", 0)));

        mvc.perform(get("/api/v1/customer/menu/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("COFFEE"))
                .andExpect(jsonPath("$[0].activeItemCount").value(4))
                .andExpect(jsonPath("$[1].activeItemCount").value(0));
    }

    @Test
    void listItemsAndEmptyCategoryReturnOk() throws Exception {
        var item = new CustomerMenuModels.Item(
                "10", "CF01", "Caramel Latte", "45000", "https://example.invalid/latte.jpg",
                true, "1", "COFFEE", "Cà phê");
        given(service.listItems(1L, "", false, 0, 10))
                .willReturn(new CustomerMenuModels.ItemPage(List.of(item), 1, 0, 10));
        given(service.listItems(2L, "", false, 0, 10))
                .willReturn(new CustomerMenuModels.ItemPage(List.of(), 0, 0, 10));

        mvc.perform(get("/api/v1/customer/menu/items").param("categoryId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Caramel Latte"))
                .andExpect(jsonPath("$.content[0].price").value("45000"))
                .andExpect(jsonPath("$.content[0].available").value(true));

        mvc.perform(get("/api/v1/customer/menu/items").param("categoryId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void missingCategoryOrItemReturnsVietnamese404() throws Exception {
        given(service.listItems(999L, "", false, 0, 10))
                .willThrow(new CustomerMenuException(HttpStatus.NOT_FOUND, "Không tìm thấy nhóm món."));
        given(service.getItem(999L))
                .willThrow(new CustomerMenuException(HttpStatus.NOT_FOUND, "Không tìm thấy món."));

        mvc.perform(get("/api/v1/customer/menu/items").param("categoryId", "999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Không tìm thấy nhóm món."));

        mvc.perform(get("/api/v1/customer/menu/items/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Không tìm thấy món."));
    }

    @Test
    void typeMismatchAndMutationAreRejected() throws Exception {
        mvc.perform(get("/api/v1/customer/menu/items/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Tham số truy vấn thực đơn không hợp lệ."));

        mvc.perform(post("/api/v1/customer/menu/items"))
                .andExpect(status().isForbidden());
    }

    @Test
    void databaseFailureReturnsSanitized503() throws Exception {
        given(service.listCategories())
                .willThrow(new CannotCreateTransactionException("jdbc:mysql://secret-host/smart_cafe"));

        mvc.perform(get("/api/v1/customer/menu/categories"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Hiện chưa tải được thực đơn."))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret-host"))));
    }

    @Test
    void serviceValidatesPagingAndIdsBeforeQueryingDatabase() {
        var standalone = new CustomerMenuService(Mockito.mock(JdbcClient.class));

        assertThatThrownBy(() -> standalone.listItems(0L, "", false, 0, 10))
                .isInstanceOf(CustomerMenuException.class)
                .hasMessage("Tham số truy vấn thực đơn không hợp lệ.");
        assertThatThrownBy(() -> standalone.listItems(null, "", false, -1, 10))
                .isInstanceOf(CustomerMenuException.class);
        assertThatThrownBy(() -> standalone.listItems(null, "", false, 0, 101))
                .isInstanceOf(CustomerMenuException.class);
        assertThatThrownBy(() -> standalone.listItems(null, "a".repeat(161), false, 0, 10))
                .isInstanceOf(CustomerMenuException.class);
        assertThatThrownBy(() -> standalone.getItem(0L))
                .isInstanceOf(CustomerMenuException.class)
                .hasMessage("Mã món không hợp lệ.");
    }
}
