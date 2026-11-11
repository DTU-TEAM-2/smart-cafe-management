package vn.codegym.smartcafe.auth;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String mailHost;

    public SmtpEmailService(
            @Autowired(required = false) JavaMailSender mailSender,
            @Value("${smartcafe.mail.from:${spring.mail.username:}}") String fromAddress,
            @Value("${spring.mail.host:}") String mailHost
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.mailHost = mailHost;
    }

    @Override
    public void validateReady() {
        if (mailSender == null || mailHost == null || mailHost.isBlank()) {
            log.error("MAIL_NOT_CONFIGURED: SMTP host is not configured. Cannot send OTP email to destination.");
            throw new IllegalStateException("Dịch vụ gửi email chưa được cấu hình máy chủ SMTP.");
        }
        if (mailSender instanceof org.springframework.mail.javamail.JavaMailSenderImpl senderImpl) {
            try {
                senderImpl.testConnection();
            } catch (Exception ex) {
                log.error("MAIL_SERVER_UNAVAILABLE: SMTP connection test failed: {}", ex.getMessage());
                throw new RuntimeException("Máy chủ SMTP hiện không phản hồi. Vui lòng thử lại sau.", ex);
            }
        }
    }

    @Override
    public void sendOtpEmail(String toEmail, String otpCode, String purpose, long validitySeconds) {
        if (mailSender == null || mailHost == null || mailHost.isBlank()) {
            log.error("MAIL_NOT_CONFIGURED: SMTP host is not configured. Cannot send OTP email to destination.");
            throw new IllegalStateException("Dịch vụ gửi email chưa được cấu hình máy chủ SMTP.");
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

            String sender = (fromAddress != null && !fromAddress.isBlank()) ? fromAddress : "no-reply@smartcafe.vn";
            helper.setFrom(sender, "LECKER Coffee Cafe");
            helper.setTo(toEmail);

            String purposeLabel = "RESET".equalsIgnoreCase(purpose) ? "Đặt lại mật khẩu" : "Xác minh tài khoản";
            helper.setSubject("[LECKER Coffee Cafe] Mã xác minh OTP: " + otpCode);

            long minutes = Math.max(1, validitySeconds / 60);

            String htmlBody = """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                  <meta charset="UTF-8">
                  <style>
                    body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #120f0d; color: #f5efe6; margin: 0; padding: 24px; }
                    .card { max-width: 500px; margin: 0 auto; background: #1c1714; border: 1px solid rgba(220, 185, 135, 0.25); border-radius: 8px; padding: 32px; box-shadow: 0 10px 30px rgba(0,0,0,0.5); }
                    .brand { color: #c5a059; font-size: 22px; font-weight: bold; letter-spacing: 2px; text-align: center; margin-bottom: 24px; }
                    .brand-sub { display: block; font-size: 11px; letter-spacing: 1px; color: #a49789; font-weight: normal; margin-top: 4px; }
                    h2 { color: #f5efe6; font-size: 18px; margin-top: 0; text-align: center; }
                    .otp-box { background: #251f1a; border: 1px dashed #c5a059; border-radius: 6px; padding: 18px; text-align: center; font-size: 32px; font-weight: bold; letter-spacing: 8px; color: #e5ad57; margin: 24px 0; }
                    p { line-height: 1.6; font-size: 14px; color: #ded5c9; }
                    .warning { font-size: 12px; color: #a49789; border-top: 1px solid rgba(255,255,255,0.08); padding-top: 16px; margin-top: 24px; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <div class="brand">LECKER<span class="brand-sub">COFFEE CAFE</span></div>
                    <h2>Yêu cầu %s</h2>
                    <p>Xin chào,</p>
                    <p>Chúng tôi đã nhận được yêu cầu %s cho tài khoản liên kết với email này. Vui lòng sử dụng mã OTP dưới đây để hoàn tất xác minh:</p>
                    <div class="otp-box">%s</div>
                    <p>Mã này có hiệu lực trong vòng <strong>%d phút</strong> và chỉ sử dụng được một lần.</p>
                    <div class="warning">
                      <p><strong>Lưu ý bảo mật:</strong> Tuyệt đối không chia sẻ mã này với bất kỳ ai, kể cả nhân viên quán. Nếu bạn không yêu cầu hành động này, vui lòng bỏ qua email này.</p>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(purposeLabel, purposeLabel.toLowerCase(), otpCode, minutes);

            String textBody = """
                LECKER COFFEE CAFE
                Yêu cầu: %s

                Mã xác minh OTP của bạn là: %s
                Mã có hiệu lực trong %d phút.

                Lưu ý: Không chia sẻ mã OTP với bất kỳ ai.
                """.formatted(purposeLabel, otpCode, minutes);

            helper.setText(textBody, htmlBody);
            mailSender.send(message);
            log.info("MAIL_SEND_SUCCESS: OTP email sent to recipient domain for purpose '{}'", purpose);
        } catch (Exception ex) {
            log.error("MAIL_SEND_FAILURE: Failed to send OTP email: {}", ex.getMessage());
            throw new RuntimeException("Không thể gửi email OTP qua SMTP. Vui lòng thử lại sau.", ex);
        }
    }
}
