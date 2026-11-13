package vn.codegym.smartcafe.customeractions;

import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customer/sessions/{sessionId}/orders")
public class CustomerOrderController {
    private final CustomerOrderService orders;
    public CustomerOrderController(CustomerOrderService orders) { this.orders = orders; }
    public record Line(@NotNull @Positive Long menuItemId,
            @NotNull @Min(1) @Max(2147483647) @Digits(integer = 10, fraction = 0) BigDecimal quantity) { }
    public record Command(@NotNull @Size(min = 1, max = 100) List<@NotNull @Valid Line> items) { }

    @PostMapping
    ResponseEntity<CustomerOrderService.Order> submit(@PathVariable long sessionId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody Command command) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(orders.submit(sessionId, authorization, key, command));
    }
}
