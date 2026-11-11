package vn.codegym.smartcafe.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice(basePackages = "vn.codegym.smartcafe.auth")
public class AuthErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthErrorHandler.class);
    private static final MediaType PROBLEM_JSON = MediaType.parseMediaType("application/problem+json");

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(InvalidCredentialsException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return problem(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Xác thực không thành công", ex.getMessage(), null);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidToken(InvalidTokenException ex) {
        log.warn("Token validation failed: {}", ex.getMessage());
        return problem(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Token không hợp lệ", ex.getMessage(), null);
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidPassword(InvalidPasswordException ex) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "Mật khẩu không hợp lệ", ex.getMessage(), null);
    }

    @ExceptionHandler(PasswordMismatchException.class)
    public ResponseEntity<Map<String, Object>> handlePasswordMismatch(PasswordMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, "PASSWORD_MISMATCH", "Mật khẩu không khớp", ex.getMessage(), null);
    }

    @ExceptionHandler(OtpException.class)
    public ResponseEntity<Map<String, Object>> handleOtpException(OtpException ex) {
        HttpStatus status = ex.getRetryAfterSeconds() != null ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_REQUEST;
        String code = ex.getRetryAfterSeconds() != null ? "RATE_LIMITED" : "OTP_ERROR";
        HttpHeaders headers = new HttpHeaders();
        if (ex.getRetryAfterSeconds() != null) {
            headers.add("Retry-After", String.valueOf(ex.getRetryAfterSeconds()));
        }
        Map<String, Object> body = buildProblem(status, code, "Lỗi xác minh OTP", ex.getMessage(), null);
        if (ex.getRetryAfterSeconds() != null) {
            body.put("retryAfterSeconds", ex.getRetryAfterSeconds());
        }
        return ResponseEntity.status(status).headers(headers).contentType(PROBLEM_JSON).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Dữ liệu không hợp lệ", "Vui lòng kiểm tra lại các trường dữ liệu.", fieldErrors);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "FORBIDDEN", "Từ chối truy cập", "Bạn không có quyền thực hiện hành động này.", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex) {
        log.error("Unhandled error: ", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Lỗi máy chủ", "Hệ thống gặp sự cố tạm thời. Vui lòng thử lại sau.", null);
    }

    private ResponseEntity<Map<String, Object>> problem(HttpStatus status, String code, String title, String detail, Map<String, String> fieldErrors) {
        return ResponseEntity.status(status)
                .contentType(PROBLEM_JSON)
                .body(buildProblem(status, code, title, detail, fieldErrors));
    }

    private Map<String, Object> buildProblem(HttpStatus status, String code, String title, String detail, Map<String, String> fieldErrors) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "about:blank");
        body.put("title", title);
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("code", code);
        if (fieldErrors != null && !fieldErrors.isEmpty()) {
            body.put("fieldErrors", fieldErrors);
        }
        return body;
    }
}
