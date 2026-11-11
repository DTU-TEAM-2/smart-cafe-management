package vn.codegym.smartcafe.sales;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(basePackages = {"vn.codegym.smartcafe.sales", "vn.codegym.smartcafe.income", "vn.codegym.smartcafe.billing"})
class SalesErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail rejected(ResponseStatusException error) {
        return ProblemDetail.forStatusAndDetail(error.getStatusCode(), error.getReason() == null ? "Request rejected" : error.getReason());
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(DataIntegrityViolationException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Data changed; reload and retry with the same key");
    }
    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ProblemDetail unavailable(RuntimeException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Business data is temporarily unavailable");
    }
}
