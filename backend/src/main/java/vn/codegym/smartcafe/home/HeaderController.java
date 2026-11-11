package vn.codegym.smartcafe.home;

import java.security.Principal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HeaderController {
    private final HeaderService service;

    HeaderController(HeaderService service) { this.service = service; }

    @GetMapping("/api/v1/home/account")
    ResponseEntity<HomeAccount> account(Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.account(principal.getName()));
    }

    @GetMapping("/api/v1/home/notifications")
    ResponseEntity<HomeNotificationFeed> notifications(Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.notifications(principal.getName()));
    }
}
