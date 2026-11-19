package vn.codegym.smartcafe.feedbackmanagement;

import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@Order(-10)
@RestControllerAdvice(assignableTypes = FeedbackReadController.class)
class FeedbackReadErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail request(ResponseStatusException error) {
        return problem(error.getStatusCode(), error.getStatusCode().value() == 404 ? "FEEDBACK_NOT_FOUND" : "INVALID_FEEDBACK_QUERY", error.getReason());
    }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail invalid() { return problem(HttpStatus.BAD_REQUEST, "INVALID_FEEDBACK_QUERY", "Ngày hoặc tham số tìm kiếm không hợp lệ."); }
    @ExceptionHandler({DataAccessException.class, org.springframework.transaction.TransactionException.class})
    ProblemDetail unavailable() { return problem(HttpStatus.SERVICE_UNAVAILABLE, "FEEDBACK_UNAVAILABLE", "Dịch vụ phản hồi tạm thời không khả dụng."); }
    private ProblemDetail problem(HttpStatusCode status, String code, String detail) {
        var result = ProblemDetail.forStatusAndDetail(status, detail);
        result.setProperty("code", code);
        return result;
    }
}
