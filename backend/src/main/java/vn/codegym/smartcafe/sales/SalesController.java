package vn.codegym.smartcafe.sales;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import vn.codegym.smartcafe.billing.BillSnapshot;

@RestController
@RequestMapping("/api/v1/sales")
public class SalesController {
    private final SalesService service;
    public SalesController(SalesService service) { this.service = service; }

    @GetMapping("/tables")
    ResponseEntity<List<SalesService.Table>> tables() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.tables());
    }
    @GetMapping("/tables/{id}/services")
    ResponseEntity<SalesService.Services> services(@PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.services(id));
    }
    @PostMapping("/tables/{id}/bill")
    ResponseEntity<BillSnapshot> bill(@PathVariable long id, @RequestBody SalesService.BillCommand command, Principal actor) {
        var bill = service.createBill(id, command, actor.getName());
        return ResponseEntity.created(URI.create("/api/v1/bills/" + bill.id()))
                .cacheControl(CacheControl.noStore()).body(bill);
    }
    public record Csrf(String headerName, String token) {}
    public record SessionCommand(String expectedSessionCode) {}
    @PostMapping("/tables/{id}/requests/{requestId}/acknowledge")
    ResponseEntity<Void> acknowledge(@PathVariable long id, @PathVariable long requestId,
            @RequestBody SessionCommand command, Principal actor) {
        service.acknowledge(id,requestId,command.expectedSessionCode(),actor.getName());
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/csrf")
    ResponseEntity<Csrf> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Csrf(token.getHeaderName(), token.getToken()));
    }
}
