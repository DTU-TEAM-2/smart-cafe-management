package vn.codegym.smartcafe.profile;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = ProfileController.class)
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
class ProfileErrors {
    @ExceptionHandler(ProfileException.class)
    ResponseEntity<ProblemDetail> profile(ProfileException error, HttpServletRequest request) {
        return problem(error.status, error.code, error.getMessage(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> invalid(HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_PROFILE_REQUEST", "Dữ liệu yêu cầu không hợp lệ.", request);
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    ResponseEntity<ProblemDetail> conflict(HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "PROFILE_CONFLICT", "Thông tin đã thay đổi. Vui lòng tải lại hồ sơ.", request);
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ResponseEntity<ProblemDetail> unavailable(HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "PROFILE_UNAVAILABLE", "Hiện chưa xử lý được hồ sơ. Vui lòng thử lại.", request);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail, HttpServletRequest request) {
        var body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setProperty("code", code);
        body.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }
}
