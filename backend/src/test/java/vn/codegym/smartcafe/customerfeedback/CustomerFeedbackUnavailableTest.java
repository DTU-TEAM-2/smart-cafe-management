package vn.codegym.smartcafe.customerfeedback;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:mysql://127.0.0.1:1/unavailable_test",
        "spring.datasource.username=unused", "spring.datasource.password="})
@AutoConfigureMockMvc
class CustomerFeedbackUnavailableTest {
    @Autowired MockMvc mvc;
    @MockitoBean CustomerFeedbackService service;

    @Test
    void databaseFailureReturnsVietnameseProblemWithoutEmailSqlOrHost() throws Exception {
        given(service.submit(any())).willThrow(new CannotCreateTransactionException("private-jdbc-host"));
        mvc.perform(post("/api/v1/customer/feedback").with(csrf()).contentType("application/json")
                        .content("{\"name\":\"Khách\",\"email\":\"test@example.invalid\",\"content\":\"Tốt\",\"rating\":5}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Hiện chưa gửi được phản hồi. Vui lòng thử lại."))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-jdbc-host"))));
    }
}
