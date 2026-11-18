package vn.codegym.smartcafe.auth;

import java.time.*;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PasswordExpirationTest {
    @Test void exactUtcBoundaryAndExplicitFlag() {
        var changed = Instant.parse("2026-10-16T03:00:00.123456Z");
        var boundary = changed.plus(Duration.ofDays(30));
        assertThat(PasswordExpiration.at(changed, false, boundary.minusNanos(1)).mustChangePassword()).isFalse();
        assertThat(PasswordExpiration.at(changed, false, boundary).mustChangePassword()).isTrue();
        assertThat(PasswordExpiration.at(changed, false, boundary.plusNanos(1)).mustChangePassword()).isTrue();
        assertThat(PasswordExpiration.at(changed, true, changed.plusSeconds(1)).mustChangePassword()).isTrue();
        assertThat(PasswordExpiration.at(boundary, false, boundary).mustChangePassword()).isFalse();
        assertThat(PasswordExpiration.at(null, false, boundary).mustChangePassword()).isTrue();
    }

    @Test void vietnamJvmDoesNotShiftUtcExpiry() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
            var changed = Instant.parse("2026-10-16T17:00:00Z");
            var clock = Clock.fixed(Instant.parse("2026-11-15T17:00:00Z"), ZoneId.systemDefault());
            var result = PasswordExpiration.at(changed, false, clock.instant());
            assertThat(result.passwordExpiresAt()).isEqualTo(clock.instant());
            assertThat(result.mustChangePassword()).isTrue();
        } finally { TimeZone.setDefault(original); }
    }
}
