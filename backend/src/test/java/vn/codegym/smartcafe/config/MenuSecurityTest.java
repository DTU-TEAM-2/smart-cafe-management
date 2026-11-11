package vn.codegym.smartcafe.config;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.assertj.core.api.Assertions.*;

class MenuSecurityTest {
    @Test void missingIssuerOrAudienceRejectsAllTokens() {
        var security = new MenuSecurity();
        assertThatThrownBy(() -> security.menuJwtDecoder("", "").decode("arbitrary" )).isInstanceOf(BadJwtException.class);
        assertThatThrownBy(() -> security.menuJwtDecoder("https://issuer.invalid", "").decode("arbitrary")).isInstanceOf(BadJwtException.class);
    }
    @Test void insecureIssuerIsRejectedWithoutNetworkCalls() {
        assertThatThrownBy(() -> new MenuSecurity().menuJwtDecoder("http://issuer.invalid", "menu")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rolesComeOnlyFromKnownArrayValues() {
        var security = new MenuSecurity();
        var now = Instant.now();
        var jwt = Jwt.withTokenValue("test-only").header("alg", "RS256").subject("test-only")
                .issuedAt(now).expiresAt(now.plusSeconds(60)).claim("roles", List.of("ADMIN", "STAFF", "ROOT", 99)).build();
        assertThat(security.menuRoles().convert(jwt).getAuthorities()).extracting("authority")
                .filteredOn(authority -> authority.toString().startsWith("ROLE_"))
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_STAFF");
        var malformed = Jwt.withTokenValue("test-only").header("alg", "RS256").subject("test-only")
                .issuedAt(now).expiresAt(now.plusSeconds(60)).claim("roles", "ADMIN").build();
        assertThat(security.menuRoles().convert(malformed).getAuthorities()).extracting("authority")
                .filteredOn(authority -> authority.toString().startsWith("ROLE_")).isEmpty();
    }
}
