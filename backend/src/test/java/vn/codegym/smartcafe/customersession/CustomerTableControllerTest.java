package vn.codegym.smartcafe.customersession;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:1/unavailable_test",
        "spring.datasource.username=unused", "spring.datasource.password="
})
@AutoConfigureMockMvc
class CustomerTableControllerTest {
    private static final String QR = "7d57e0b0-dfca-4ed3-846a-5b77e4450001";
    private static final String URL = "/api/v1/customer/tables/qr/";
    @Autowired MockMvc mvc;
    @MockitoBean CustomerTableService service;

    @Test
    void guestCanResolveTableWithoutReceivingSessionCredentials() throws Exception {
        given(service.resolve(QR)).willReturn(new CustomerTableService.TableContext("12", "T12", "Bàn 12"));
        mvc.perform(get(URL + QR)).andExpect(status().isOk())
                .andExpect(jsonPath("$.tableId").value("12"))
                .andExpect(jsonPath("$.tableName").value("Bàn 12"))
                .andExpect(jsonPath("$.sessionId").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void returnsVietnameseInvalidAndUnknownQrErrors() throws Exception {
        given(service.resolve("bad-qr")).willThrow(new CustomerTableException(HttpStatus.BAD_REQUEST, "Mã QR không hợp lệ."));
        given(service.resolve(QR)).willThrow(new CustomerTableException(HttpStatus.NOT_FOUND, "Không tìm thấy bàn."));
        mvc.perform(get(URL + "bad-qr")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Mã QR không hợp lệ."));
        mvc.perform(get(URL + QR)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Không tìm thấy bàn."));
    }

    @Test
    void databaseFailureDoesNotLeakConnectionDetails() throws Exception {
        given(service.resolve(QR)).willThrow(new CannotCreateTransactionException("private-jdbc-details"));
        mvc.perform(get(URL + QR)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Hiện chưa kiểm tra được mã QR. Vui lòng thử lại."))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-jdbc-details"))));
    }

    @Test
    void qrLookupDoesNotOpenPublicWriteRoutes() throws Exception {
        mvc.perform(post(URL + QR)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/customer/tables/12")).andExpect(status().isForbidden());
    }
}
