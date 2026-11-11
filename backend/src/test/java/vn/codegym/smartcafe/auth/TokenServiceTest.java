package vn.codegym.smartcafe.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceTest {

    private final TokenService tokenService = new TokenService("test-secret-key-that-is-at-least-32-bytes-long!", 3600);

    @Test
    void generatesAndParsesValidToken() {
        String token = tokenService.generateToken(1L, "admin", "ADMIN", 0L);
        assertNotNull(token);

        TokenService.TokenClaims claims = tokenService.parseToken(token);
        assertNotNull(claims);
        assertEquals(1L, claims.userId());
        assertEquals("admin", claims.username());
        assertEquals("ADMIN", claims.role());
        assertEquals(0L, claims.authVersion());
    }

    @Test
    void parsesLegacyFourFieldBearerToken() {
        // Legacy format: id:username:role:expMillis
        long expMillis = Instant.now().plusSeconds(3600).toEpochMilli();
        String payload = "2:header-2:ADMIN:" + expMillis;
        String b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signature = tokenService.hmacSha256(b64);
        String legacyToken = b64 + "." + signature;

        TokenService.TokenClaims claims = tokenService.parseToken(legacyToken);
        assertNotNull(claims);
        assertEquals(2L, claims.userId());
        assertEquals("header-2", claims.username());
        assertEquals("ADMIN", claims.role());
        assertEquals(0L, claims.authVersion());
    }

    @Test
    void generatesAndParsesResetGrantToken() {
        String grantToken = tokenService.generateResetGrantToken("ch-123456", 10L, 2L, 600);
        assertNotNull(grantToken);

        TokenService.ResetGrantClaims claims = tokenService.parseResetGrantToken(grantToken);
        assertNotNull(claims);
        assertEquals("ch-123456", claims.challengeId());
        assertEquals(10L, claims.userId());
        assertEquals(2L, claims.authVersion());
        assertTrue(Instant.now().isBefore(claims.expiresAt()));
    }

    @Test
    void rejectsTamperedToken() {
        String token = tokenService.generateToken(1L, "staff", "STAFF", 0L);
        String tampered = token.substring(0, token.length() - 2) + "aa";
        assertNull(tokenService.parseToken(tampered));
    }

    @Test
    void rejectsExpiredToken() {
        TokenService expiredService = new TokenService("test-secret-key-that-is-at-least-32-bytes-long!", -10);
        String token = expiredService.generateToken(2L, "staff", "STAFF", 0L);
        assertNull(expiredService.parseToken(token));
    }

    @Test
    void computesHmacSha256Deterministically() {
        String hash1 = tokenService.hmacSha256("test-data");
        String hash2 = tokenService.hmacSha256("test-data");
        assertEquals(hash1, hash2);

        String hash3 = tokenService.hmacSha256("different-data");
        assertNotEquals(hash1, hash3);
    }
}
