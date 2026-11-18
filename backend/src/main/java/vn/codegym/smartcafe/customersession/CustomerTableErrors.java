package vn.codegym.smartcafe.customersession;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CustomerTableController.class)
class CustomerTableErrors {
    @ExceptionHandler(CustomerTableException.class)
    ProblemDetail invalid(CustomerTableException error) {
        return problem(error.status(), error.getMessage());
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ProblemDetail unavailable(RuntimeException error) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Hiện chưa kiểm tra được mã QR. Vui lòng thử lại.");
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        var result = ProblemDetail.forStatusAndDetail(status, detail);
        result.setTitle(status == HttpStatus.SERVICE_UNAVAILABLE ? "Dịch vụ tạm thời gián đoạn" : "Không thể truy cập bàn");
        return result;
    }
}
