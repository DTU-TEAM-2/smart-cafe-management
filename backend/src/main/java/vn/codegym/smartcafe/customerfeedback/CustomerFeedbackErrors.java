package vn.codegym.smartcafe.customerfeedback;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CustomerFeedbackController.class)
class CustomerFeedbackErrors {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException error) {
        var result = problem(HttpStatus.BAD_REQUEST, "Vui lòng kiểm tra thông tin phản hồi.");
        result.setProperty("fieldErrors", error.getBindingResult().getFieldErrors().stream()
                .map(field -> new FieldError(field.getField(), field.getDefaultMessage())).toList());
        return result;
    }

    record FieldError(String field, String message) { }

    @ExceptionHandler({HttpMessageNotReadableException.class, org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.multipart.MaxUploadSizeExceededException.class})
    ProblemDetail malformed(Exception error) {
        return problem(HttpStatus.BAD_REQUEST, "Dữ liệu phản hồi không hợp lệ.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException error) {
        return problem(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class, IllegalStateException.class})
    ProblemDetail unavailable(RuntimeException error) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Hiện chưa gửi được phản hồi. Vui lòng thử lại.");
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        var result = ProblemDetail.forStatusAndDetail(status, detail);
        result.setTitle(status == HttpStatus.BAD_REQUEST ? "Thông tin phản hồi không hợp lệ" : "Dịch vụ tạm thời gián đoạn");
        return result;
    }
}
