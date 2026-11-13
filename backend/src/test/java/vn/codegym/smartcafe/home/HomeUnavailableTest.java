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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:1/unavailable_test",
        "spring.datasource.username=unused",
        "spring.datasource.password=",
        "smartcafe.auth.token-secret=unavailable-test-secret-only-at-least-32-characters"
})
@AutoConfigureMockMvc
class HomeUnavailableTest {
    @Autowired MockMvc mvc;
    @MockitoBean HomeHighlightsService service;
    @MockitoBean HeaderService headerService;

    @Test
    void transactionFailureReturnsSanitized503() throws Exception {
        given(service.getHighlights()).willThrow(new CannotCreateTransactionException("private JDBC details"));
        mvc.perform(get("/api/v1/home/highlights"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Hiện chưa tải được danh sách món."))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private JDBC"))));
    }

    @Test
    void irrelevantBearerDoesNotMakePublicHighlightsDependOnUserDatabase() throws Exception {
        String payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("1:test-admin:ADMIN:" + java.time.Instant.now().plusSeconds(60).toEpochMilli())
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
                "unavailable-test-secret-only-at-least-32-characters".getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        String bearer = "Bearer " + payload + "." + java.util.HexFormat.of().formatHex(
                mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        // A genuinely signed, unsolicited token must not trigger DB authentication for a public API.
        mvc.perform(get("/api/v1/home/highlights").header("Authorization", bearer))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedHeaderFailureAlsoReturnsSanitized503() throws Exception {
        given(headerService.account("test-staff")).willThrow(new CannotCreateTransactionException("private JDBC details"));
        mvc.perform(get("/api/v1/home/account").with(user("test-staff").roles("STAFF")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private JDBC"))));
    }
}
