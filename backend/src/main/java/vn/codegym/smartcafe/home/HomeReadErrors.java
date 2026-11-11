package vn.codegym.smartcafe.home;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {HomeHighlightsController.class, HeaderController.class})
class HomeReadErrors {
    private static final Logger LOG = LoggerFactory.getLogger(HomeReadErrors.class);

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ProblemDetail unavailable(RuntimeException error) {
        LOG.warn("Could not read homepage data", error);
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Hiện chưa tải được danh sách món.");
    }
}
