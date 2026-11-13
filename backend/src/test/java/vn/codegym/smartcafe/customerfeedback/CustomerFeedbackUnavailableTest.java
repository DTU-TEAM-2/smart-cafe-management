package vn.codegym.smartcafe.customerfeedback;

import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
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
    @MockitoBean Clock clock;

    @BeforeEach
    void fixQuotaWindow() {
        given(clock.instant()).willReturn(Instant.ofEpochSecond(65));
    }

    @Test
    void quotaRunsOnFeedbackPostBeforeBodyParsingAndIgnoresClientForwardingHeader() throws Exception {
        for (int i = 0; i < 21; i++) {
            var result = mvc.perform(post("/api/v1/customer/feedback").with(csrf())
                    .with(request -> { request.setRemoteAddr("192.0.2.50"); return request; })
                    .header("X-Forwarded-For", "198.51.100." + i)
                    .contentType("application/json").content("{"));
            if (i < 20) {
                result.andExpect(status().isBadRequest());
            } else {
                result.andExpect(status().isTooManyRequests())
                        .andExpect(header().exists("Retry-After"))
                        .andExpect(jsonPath("$.status").value(429));
            }
        }
        org.mockito.Mockito.verifyNoInteractions(service);
    }

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
