package vn.codegym.smartcafe.news;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.TransactionException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice(assignableTypes = {NewsController.class, NewsImageController.class})
class NewsExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(NewsExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail handleResponseStatus(ResponseStatusException ex) {
        var problem = ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason());
        problem.setTitle(ex.getStatusCode().toString());
        if (ex.getBody().getProperties() != null) problem.setProperties(ex.getBody().getProperties());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Dữ liệu không hợp lệ");
        problem.setType(URI.create("urn:smartcafe:problem:bad-request"));
        return problem;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestPartException.class})
    ProblemDetail handleMalformedInput(Exception ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "JSON hoặc tham số yêu cầu không hợp lệ.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleOversizedUpload(MaxUploadSizeExceededException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "Ảnh tối đa 5 MiB.");
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    ProblemDetail handleConcurrentWrite(ConcurrencyFailureException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Dữ liệu đang được thay đổi. Hãy tải lại và thử lại.");
        problem.setProperty("code", "NEWS_WRITE_BUSY");
        return problem;
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ProblemDetail handleDuplicateKey(DuplicateKeyException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        String detail = msg.contains("uq_news_slug")
                ? "Slug tin tức đã tồn tại. Vui lòng chọn slug khác."
                : "Dữ liệu trùng lặp với bản ghi đã có.";
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
        problem.setTitle("Xung đột dữ liệu");
        problem.setType(URI.create("urn:smartcafe:problem:conflict"));
        problem.setProperty("code", msg.contains("uq_news_slug") ? "NEWS_SLUG_TAKEN" : "DATA_CONFLICT");
        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dữ liệu không thỏa mãn ràng buộc toàn vẹn hệ thống.");
        problem.setTitle("Ràng buộc dữ liệu không hợp lệ");
        problem.setType(URI.create("urn:smartcafe:problem:integrity-violation"));
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Một hoặc nhiều trường dữ liệu không hợp lệ.");
        problem.setTitle("Lỗi xác thực dữ liệu");
        problem.setType(URI.create("urn:smartcafe:problem:validation"));
        Map<String, String> fieldErrors = new HashMap<>();
        for (var error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ProblemDetail handleDatabaseError(RuntimeException ex) {
        LOG.warn("Database error processing news request ({})", ex.getClass().getSimpleName());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ tin tức hiện không khả dụng. Vui lòng thử lại sau.");
        problem.setTitle("Lỗi dịch vụ");
        problem.setType(URI.create("urn:smartcafe:problem:service-unavailable"));
        return problem;
    }
}
