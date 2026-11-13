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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:mysql://127.0.0.1:1/unavailable_test",
        "spring.datasource.username=unused", "spring.datasource.password=",
        "feedback.limits.csrf-per-source-per-minute=2", "feedback.limits.csrf-total-per-minute=3"})
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
    void boundsCookielessCsrfRequestsBeforeCreatingSessionsAndKeepsPostQuotaSeparate() throws Exception {
        for (int i = 0; i < 2; i++) {
            var accepted = mvc.perform(get("/api/v1/customer/feedback/csrf")
                            .with(request -> { request.setRemoteAddr("192.0.2.51"); return request; }))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andReturn();
            assertThat(accepted.getRequest().getSession(false)).isNotNull();
        }

        mvc.perform(get("/api/v1/customer/feedback/csrf")
                        .with(request -> { request.setRemoteAddr("192.0.2.51"); return request; })
                        .header("X-Forwarded-For", "198.51.100.42"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "55"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());

        mvc.perform(get("/api/v1/customer/feedback/csrf")
                        .with(request -> { request.setRemoteAddr("192.0.2.52"); return request; }))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/customer/feedback/csrf")
                        .with(request -> { request.setRemoteAddr("192.0.2.53"); return request; }))
                .andExpect(status().isTooManyRequests())
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());

        mvc.perform(post("/api/v1/customer/feedback").with(csrf())
                        .with(request -> { request.setRemoteAddr("192.0.2.51"); return request; })
                        .contentType("application/json").content("{"))
                .andExpect(status().isBadRequest());
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
