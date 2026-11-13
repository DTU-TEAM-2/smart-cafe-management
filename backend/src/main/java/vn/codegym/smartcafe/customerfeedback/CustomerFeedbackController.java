package vn.codegym.smartcafe.customerfeedback;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customer/feedback")
public class CustomerFeedbackController {
    private final CustomerFeedbackService service;
    private final FeedbackSubmission submission;

    public CustomerFeedbackController(CustomerFeedbackService service, FeedbackSubmission submission) { this.service = service; this.submission = submission; }

    public record Command(
            @NotBlank(message = "Vui lòng nhập tên.") @Size(max = 160, message = "Tên không quá 160 ký tự.") String name,
            @NotBlank(message = "Vui lòng nhập email.") @Email(message = "Email không hợp lệ.")
            @Size(max = 255, message = "Email không quá 255 ký tự.") String email,
            @NotBlank(message = "Vui lòng nhập nội dung phản hồi.") String content,
            @NotNull(message = "Vui lòng chọn số sao.") @Min(value = 1, message = "Số sao từ 1 đến 5.")
            @Max(value = 5, message = "Số sao từ 1 đến 5.")
            @Digits(integer = 1, fraction = 0, message = "Số sao phải là số nguyên từ 1 đến 5.") BigDecimal rating) { }

    public record Submitted(String id, String message) { }
    public record Csrf(String headerName, String token) { }

    @GetMapping("/csrf")
    ResponseEntity<Csrf> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new Csrf(token.getHeaderName(), token.getToken()));
    }

    @PostMapping(consumes = "application/json")
    ResponseEntity<Submitted> submit(@Valid @RequestBody Command command) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(new Submitted(service.submit(command), "Cảm ơn bạn đã gửi phản hồi!"));
    }

    @PostMapping(consumes = "multipart/form-data")
    ResponseEntity<Submitted> submitWithImages(@Valid @RequestPart("feedback") Command command,
            @RequestPart("images") java.util.List<org.springframework.web.multipart.MultipartFile> images) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(new Submitted(submission.submit(command, images), "Cảm ơn bạn đã gửi phản hồi!"));
    }
}
