package vn.codegym.smartcafe.home;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
class HomeHighlightsController {
    private static final Logger LOG = LoggerFactory.getLogger(HomeHighlightsController.class);
    private final HomeHighlightsService service;

    HomeHighlightsController(HomeHighlightsService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/home/highlights")
    HomeHighlights highlights() {
        return service.getHighlights();
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ProblemDetail unavailable(RuntimeException error) {
        LOG.warn("Could not read homepage highlights", error);
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Hiện chưa tải được danh sách món.");
    }
}
