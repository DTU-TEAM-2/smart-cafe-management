package vn.codegym.smartcafe.customeractions;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
public class CustomerRequestController {
    private final CustomerRequestService requests;
    public CustomerRequestController(CustomerRequestService requests) { this.requests = requests; }
    public record Csrf(String headerName, String token) { }

    @GetMapping("/api/v1/customer/csrf")
    ResponseEntity<Csrf> csrf(CsrfToken csrf) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Csrf(csrf.getHeaderName(), csrf.getToken()));
    }

    @PostMapping("/api/v1/customer/sessions/{sessionId}/waiter-requests")
    ResponseEntity<CustomerRequestService.Request> waiter(@PathVariable long sessionId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(requests.submit(sessionId, authorization, key, "WAITER"));
    }

    @PostMapping("/api/v1/customer/sessions/{sessionId}/bill-requests")
    ResponseEntity<CustomerRequestService.Request> bill(@PathVariable long sessionId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(requests.submit(sessionId, authorization, key, "BILL"));
    }
}
