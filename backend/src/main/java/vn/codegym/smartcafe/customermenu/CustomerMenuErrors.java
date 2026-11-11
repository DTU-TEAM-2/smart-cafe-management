package vn.codegym.smartcafe.customermenu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = CustomerMenuController.class)
class CustomerMenuErrors {
    private static final Logger LOG = LoggerFactory.getLogger(CustomerMenuErrors.class);

    @ExceptionHandler(CustomerMenuException.class)
    ProblemDetail handleMenuException(CustomerMenuException error) {
        return ProblemDetail.forStatusAndDetail(error.status(), error.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Tham số truy vấn thực đơn không hợp lệ.");
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ProblemDetail unavailable(RuntimeException error) {
        LOG.warn("Could not read customer menu", error);
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Hiện chưa tải được thực đơn.");
    }
}
