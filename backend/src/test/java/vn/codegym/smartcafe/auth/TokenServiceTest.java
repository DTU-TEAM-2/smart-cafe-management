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

    @Test
    void ephemeralSecretKeySharesSameSourceForSigningAndVerification() {
        // When secret is not configured or blank, an ephemeral key is generated
        TokenService ephemeralService = new TokenService("", 3600);
        byte[] secretKey = ephemeralService.getSecretKey();
        assertNotNull(secretKey);
        assertEquals(32, secretKey.length, "Ephemeral secret key must be at least 32 bytes (256-bit)");

        // Token signed with ephemeral key can be parsed and verified by the same service/key
        String token = ephemeralService.generateToken(99L, "ephemeral_user", "ADMIN", 0L);
        assertNotNull(token);
        TokenService.TokenClaims claims = ephemeralService.parseToken(token);
        assertNotNull(claims);
        assertEquals(99L, claims.userId());
        assertEquals("ephemeral_user", claims.username());
    }

    @Test
    void rejectsConfiguredSecretShorterThan32BytesOnStartup() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                new TokenService("too-short-secret", 3600)
        );
        assertTrue(ex.getMessage().contains("at least 32 bytes (256-bit)"));
    }
}
