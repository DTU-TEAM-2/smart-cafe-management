package vn.codegym.smartcafe.menu;

import java.util.Map;
import java.util.TreeMap;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = MenuController.class)
class MenuErrors {
    @ExceptionHandler(MenuException.class)
    ProblemDetail menu(MenuException error) { return problem(error.status(), error.code(), error.getMessage()); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail body(MethodArgumentNotValidException error) {
        Map<String, String> fields = new TreeMap<>();
        error.getBindingResult().getFieldErrors().forEach(field -> fields.putIfAbsent(field.getField(), field.getDefaultMessage()));
        var result = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Kiểm tra lại dữ liệu nhập.");
        result.setProperty("fieldErrors", fields);
        return result;
    }
    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class,
            MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    ProblemDetail parameters(Exception error) { return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Tham số hoặc JSON không hợp lệ."); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict() { return problem(HttpStatus.CONFLICT, "MENU_CONFLICT", "Mã đã được sử dụng hoặc dữ liệu tham chiếu không hợp lệ."); }
    @ExceptionHandler(TransientDataAccessException.class)
    ProblemDetail retry() { return problem(HttpStatus.CONFLICT, "RETRY_MENU", "Dữ liệu đang thay đổi. Tải lại rồi thử lại."); }
    @ExceptionHandler(DataAccessException.class)
    ProblemDetail database() { return problem(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE", "Dịch vụ dữ liệu tạm thời không khả dụng."); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail tooLarge() { return problem(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGE_TOO_LARGE", "Ảnh tối đa 5 MB."); }
    @ExceptionHandler(MultipartException.class)
    ProblemDetail multipart() { return problem(HttpStatus.BAD_REQUEST, "INVALID_MULTIPART", "Tệp tải lên không hợp lệ."); }

    private ProblemDetail problem(HttpStatus status, String code, String message) {
        var result = ProblemDetail.forStatusAndDetail(status, message);
        result.setProperty("code", code);
        return result;
    }
}
