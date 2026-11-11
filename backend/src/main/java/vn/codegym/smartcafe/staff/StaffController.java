package vn.codegym.smartcafe.staff;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/staff")
public class StaffController {
    private final StaffService staffService;
    private final StaffImageService staffImageService;

    public StaffController(StaffService staffService, StaffImageService staffImageService) {
        this.staffService = staffService;
        this.staffImageService = staffImageService;
    }

    @GetMapping("/csrf")
    public ResponseEntity<StaffModels.CsrfResponse> csrf(org.springframework.security.web.csrf.CsrfToken token) {
        return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(new StaffModels.CsrfResponse(token.getHeaderName(), token.getToken()));
    }

    @GetMapping
    public StaffModels.PageResponse<StaffModels.StaffSummary> getStaffList(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return staffService.getStaffList(keyword, page, size);
    }

    @GetMapping("/{id}")
    public StaffModels.StaffDetail getStaffById(@PathVariable Long id) {
        return staffService.getStaffById(id);
    }

    @PostMapping
    public ResponseEntity<StaffModels.StaffDetail> createStaff(@RequestBody StaffModels.CreateStaffRequest request) {
        StaffModels.StaffDetail created = staffService.createStaff(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public StaffModels.StaffDetail updateStaff(
            @PathVariable Long id,
            @RequestBody StaffModels.UpdateStaffRequest request) {
        return staffService.updateStaff(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStaff(@PathVariable Long id, Authentication authentication) {
        String currentUsername = authentication != null ? authentication.getName() : null;
        staffService.deleteStaff(id, currentUsername);
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public StaffModels.ImageUploadResponse uploadAvatar(@RequestParam("file") MultipartFile file) {
        return staffImageService.upload(file);
    }
}
