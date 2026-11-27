package vn.codegym.smartcafe.profile;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.codegym.smartcafe.auth.UserRecord;

@RestController
@RequestMapping("/api/v1/profiles/me")
class ProfileController {
    private final ProfileService service;

    ProfileController(ProfileService service) { this.service = service; }

    @GetMapping
    ResponseEntity<ProfileModels.Profile> get(@AuthenticationPrincipal UserRecord principal) {
        return noStore(service.get(principal));
    }

    @PatchMapping(consumes = "application/json")
    ResponseEntity<ProfileModels.Profile> update(@AuthenticationPrincipal UserRecord principal,
            @RequestBody ProfileModels.Patch request) {
        return noStore(service.update(principal, request));
    }

    @PostMapping(path = "/password", consumes = "application/json")
    ResponseEntity<ProfileModels.PasswordChanged> changePassword(@AuthenticationPrincipal UserRecord principal,
            @RequestBody ProfileModels.PasswordChange request) {
        return noStore(service.changePassword(principal, request));
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
