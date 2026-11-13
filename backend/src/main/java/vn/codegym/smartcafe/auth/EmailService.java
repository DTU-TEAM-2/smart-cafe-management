package vn.codegym.smartcafe.auth;

public interface EmailService {
    void sendOtpEmail(String toEmail, String otpCode, String purpose, long validitySeconds);
    void validateReady();
}
