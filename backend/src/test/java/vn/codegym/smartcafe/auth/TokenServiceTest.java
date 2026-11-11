package vn.codegym.smartcafe.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceTest {

    private final TokenService tokenService = new TokenService("test-secret-key-that-is-at-least-32-bytes-long!", 3600);

    @Test
    void generatesAndParsesValidToken() {
        String token = tokenService.generateToken(1L, "ADMIN", 0L);
        assertNotNull(token);

        TokenService.TokenClaims claims = tokenService.parseToken(token);
        assertNotNull(claims);
        assertEquals(1L, claims.userId());
        assertEquals("ADMIN", claims.role());
        assertEquals(0L, claims.authVersion());
    }

    @Test
    void rejectsTamperedToken() {
        String token = tokenService.generateToken(1L, "STAFF", 0L);
        String tampered = token.substring(0, token.length() - 2) + "aa";
        assertNull(tokenService.parseToken(tampered));
    }

    @Test
    void rejectsExpiredToken() {
        TokenService expiredService = new TokenService("test-secret-key-that-is-at-least-32-bytes-long!", -10);
        String token = expiredService.generateToken(2L, "STAFF", 0L);
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
