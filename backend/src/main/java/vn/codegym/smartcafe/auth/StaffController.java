package vn.codegym.smartcafe.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/staff")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
public class StaffController {

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getStaffDashboard() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "area", "STAFF",
                "message", "Chào mừng Nhân viên đến với không gian phục vụ."
        ));
    }
}
