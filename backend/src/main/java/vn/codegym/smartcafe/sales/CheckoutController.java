package vn.codegym.smartcafe.sales;

import java.security.Principal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CheckoutController {
    private final CheckoutService service;
    CheckoutController(CheckoutService service) { this.service = service; }

    @PostMapping("/api/v1/bills/{id}/payments")
    ResponseEntity<CheckoutService.Receipt> confirm(@PathVariable long id,
            @RequestBody CheckoutService.ConfirmPayment command, Principal actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.confirmPayment(id,command,actor.getName()));
    }

    @PostMapping("/api/v1/sales/tables/{id}/reset")
    ResponseEntity<Void> reset(@PathVariable long id,@RequestBody SalesController.SessionCommand command,Principal actor) {
        service.resetPaidSession(id,command.expectedSessionCode(),actor.getName());
        return ResponseEntity.noContent().build();
    }
}
