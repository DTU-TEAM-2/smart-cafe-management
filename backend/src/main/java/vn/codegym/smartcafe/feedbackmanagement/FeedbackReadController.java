package vn.codegym.smartcafe.feedbackmanagement;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
    @GetMapping("/{feedbackId}/images/{mediaId}/content")
    ResponseEntity<byte[]> imageContent(@PathVariable long feedbackId, @PathVariable long mediaId) {
        var content = feedback.imageContent(feedbackId, mediaId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .contentLength(content.bytes().length)
                .body(content.bytes());
    }
}
