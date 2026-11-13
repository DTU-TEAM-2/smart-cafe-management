package vn.codegym.smartcafe.customeractions;

import java.math.BigDecimal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customer/sessions/{sessionId}/order-items/{itemId}")
public class CustomerOrderEditController {
    private final CustomerOrderEditService edits;
    public CustomerOrderEditController(CustomerOrderEditService edits) { this.edits = edits; }
    public record Change(@NotNull @Min(0) Long expectedVersion,
            @NotNull @Min(1) @Max(2147483647) @Digits(integer = 10, fraction = 0) BigDecimal quantity) { }
    public record Cancel(@NotNull @Min(0) Long expectedVersion) { }

    @PatchMapping
    ResponseEntity<CustomerOrderService.Order> change(@PathVariable long sessionId, @PathVariable long itemId,
            @RequestHeader(value = "Authorization", required = false) String authorization, @Valid @RequestBody Change command) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(edits.edit(sessionId, itemId, authorization, command.expectedVersion(), command.quantity().intValueExact()));
    }

    @DeleteMapping
    ResponseEntity<CustomerOrderService.Order> cancel(@PathVariable long sessionId, @PathVariable long itemId,
            @RequestHeader(value = "Authorization", required = false) String authorization, @Valid @RequestBody Cancel command) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(edits.edit(sessionId, itemId, authorization, command.expectedVersion(), null));
    }
}
