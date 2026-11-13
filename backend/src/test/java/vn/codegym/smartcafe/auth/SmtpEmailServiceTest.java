package vn.codegym.smartcafe.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class SmtpEmailServiceTest {

    @Test
    void throwsIllegalStateExceptionWhenMailSenderIsNull() {
        SmtpEmailService service = new SmtpEmailService(null, "from@smartcafe.vn", "smtp.smartcafe.vn");

        assertThatThrownBy(() -> service.sendOtpEmail("user@smartcafe.vn", "123456", "RESET", 600))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Dịch vụ gửi email chưa được cấu hình máy chủ SMTP");
    }

    @Test
    void throwsIllegalStateExceptionWhenMailHostIsNull() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpEmailService service = new SmtpEmailService(mailSender, "from@smartcafe.vn", null);

        assertThatThrownBy(() -> service.sendOtpEmail("user@smartcafe.vn", "123456", "RESET", 600))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Dịch vụ gửi email chưa được cấu hình máy chủ SMTP");
    }

    @Test
    void throwsIllegalStateExceptionWhenMailHostIsBlank() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpEmailService service = new SmtpEmailService(mailSender, "from@smartcafe.vn", "   ");

        assertThatThrownBy(() -> service.sendOtpEmail("user@smartcafe.vn", "123456", "RESET", 600))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Dịch vụ gửi email chưa được cấu hình máy chủ SMTP");
    }
}
