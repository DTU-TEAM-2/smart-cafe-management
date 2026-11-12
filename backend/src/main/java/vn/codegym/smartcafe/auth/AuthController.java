package vn.codegym.smartcafe.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.codegym.smartcafe.auth.dto.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final OtpService otpService;

    public AuthController(AuthService authService, OtpService otpService) {
        this.authService = authService;
        this.otpService = otpService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(@AuthenticationPrincipal UserRecord principal) {
        if (principal == null) {
            throw new InvalidCredentialsException("Chưa đăng nhập.");
        }
        UserDto userDto = authService.getCurrentUser(principal.id());
        return ResponseEntity.ok(userDto);
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout() {
        return ResponseEntity.ok(new MessageResponse("Đăng xuất thành công"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        ForgotPasswordResponse response = otpService.initiatePasswordResetOtp(request.email());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<VerifyOtpResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        VerifyOtpResponse response = otpService.verifyOtp(request.challengeId(), request.otp());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ForgotPasswordResponse> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        ForgotPasswordResponse response = otpService.resendOtp(request.challengeId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-reset-token")
    public ResponseEntity<Map<String, Boolean>> verifyResetTokenPost(@RequestBody Map<String, String> body) {
        String token = body != null ? body.get("token") : null;
        boolean valid = otpService.verifyResetToken(token);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("valid", valid));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        otpService.resetPassword(request.token(), request.newPassword(), request.confirmPassword());
        return ResponseEntity.ok(new MessageResponse("Mật khẩu đã được cập nhật thành công! Bạn có thể đăng nhập bằng mật khẩu mới."));
    }
}
