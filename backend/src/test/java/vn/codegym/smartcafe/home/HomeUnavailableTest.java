package vn.codegym.smartcafe.home;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:1/unavailable_test",
        "spring.datasource.username=unused",
        "spring.datasource.password="
})
@AutoConfigureMockMvc
class HomeUnavailableTest {
    @Autowired MockMvc mvc;
    @MockitoBean HomeHighlightsService service;

    @Test
    void transactionFailureReturnsSanitized503() throws Exception {
        given(service.getHighlights()).willThrow(new CannotCreateTransactionException("private JDBC details"));
        mvc.perform(get("/api/v1/home/highlights"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Hiện chưa tải được danh sách món."))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private JDBC"))));
    }
}
