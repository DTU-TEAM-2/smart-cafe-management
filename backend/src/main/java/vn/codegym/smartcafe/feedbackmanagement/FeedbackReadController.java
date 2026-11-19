package vn.codegym.smartcafe.feedbackmanagement;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/feedbacks")
public class FeedbackReadController {
    private final FeedbackReadService feedback;
    public FeedbackReadController(FeedbackReadService feedback) { this.feedback = feedback; }
    @GetMapping
    ResponseEntity<FeedbackReadService.Page> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "newest") String sort) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(feedback.list(date, page, size, sort));
    }
    @GetMapping("/{feedbackId}")
    ResponseEntity<FeedbackReadService.Detail> detail(@PathVariable long feedbackId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(feedback.detail(feedbackId));
    }
}
