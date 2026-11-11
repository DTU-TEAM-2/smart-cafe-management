package vn.codegym.smartcafe.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] secretKey;
    private final long tokenValiditySeconds;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenService(
            @Value("${smartcafe.auth.token-secret:}") String secret,
            @Value("${smartcafe.auth.token-validity-seconds:86400}") long tokenValiditySeconds
    ) {
        if (secret != null && !secret.isBlank()) {
            this.secretKey = secret.getBytes(StandardCharsets.UTF_8);
        } else {
            byte[] ephemeralKey = new byte[32];
            new SecureRandom().nextBytes(ephemeralKey);
            this.secretKey = ephemeralKey;
            log.warn("AUTH_TOKEN_SECRET not set; using ephemeral 256-bit key for this process.");
        }
        this.tokenValiditySeconds = tokenValiditySeconds;
    }

    public byte[] getSecretKey() {
        return this.secretKey;
    }

    public record TokenClaims(Long userId, String username, String role, long authVersion, Instant expiresAt) {}

    public record ResetGrantClaims(String challengeId, Long userId, long authVersion, Instant expiresAt) {}

    /**
     * Generate standard unified bearer token:
     * id:username:role:authVersion:expMillis
     */
    public String generateToken(Long userId, String username, String role, long authVersion) {
        long expMillis = Instant.now().toEpochMilli() + (tokenValiditySeconds * 1000L);
        String safeUsername = username != null ? username : "";
        String payload = userId + ":" + safeUsername + ":" + role + ":" + authVersion + ":" + expMillis;
        String payloadBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signature = sign(payloadBase64);
        return payloadBase64 + "." + signature;
    }

    public String generateToken(Long userId, String role, long authVersion) {
        return generateToken(userId, "", role, authVersion);
    }

    /**
     * Parse and verify bearer token.
     * Supports:
     * - 5 fields: id:username:role:authVersion:exp
     * - 4 fields legacy (DT2-1 HeaderMySqlTest): id:username:role:exp (authVersion=0)
     * - 4 fields alternative: id:role:authVersion:exp (username="")
     */
    public TokenClaims parseToken(String token) {
        if (token == null || !token.contains(".")) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            return null;
        }
        String payloadBase64 = parts[0];
        String providedSignature = parts[1];

        String expectedSignature = sign(payloadBase64);
        if (!MessageDigest.isEqual(providedSignature.getBytes(StandardCharsets.UTF_8), expectedSignature.getBytes(StandardCharsets.UTF_8))) {
            return null;
        }

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(payloadBase64);
            String payload = new String(decoded, StandardCharsets.UTF_8);
            String[] fields = payload.split(":");

            Long userId;
            String username = null;
            String role;
            long authVersion;
            long expValue;

            if (fields.length == 5) {
                // id:username:role:authVersion:exp
                userId = Long.valueOf(fields[0]);
                username = fields[1];
                role = fields[2];
                authVersion = Long.parseLong(fields[3]);
                expValue = Long.parseLong(fields[4]);
            } else if (fields.length == 4) {
                // Determine whether field 1 is username or role
                if (fields[2].equalsIgnoreCase("ADMIN") || fields[2].equalsIgnoreCase("STAFF") || fields[2].equalsIgnoreCase("CUSTOMER")) {
                    userId = Long.valueOf(fields[0]);
                    username = fields[1];
                    role = fields[2];
                    authVersion = 0L;
                    expValue = Long.parseLong(fields[3]);
                } else {
                    // id:role:authVersion:exp
                    userId = Long.valueOf(fields[0]);
                    role = fields[1];
                    authVersion = Long.parseLong(fields[2]);
                    expValue = Long.parseLong(fields[3]);
                }
            } else {
                return null;
            }

            Instant expiresAt = expValue > 10_000_000_000L
                    ? Instant.ofEpochMilli(expValue)
                    : Instant.ofEpochSecond(expValue);

            if (Instant.now().isAfter(expiresAt)) {
                return null;
            }

            return new TokenClaims(userId, username, role, authVersion, expiresAt);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Generate stateless HMAC reset grant token.
     * Payload: RESET:<challengeId>:<userId>:<authVersion>:<expMillis>
     */
    public String generateResetGrantToken(String challengeId, Long userId, long authVersion, long validitySeconds) {
        long expMillis = Instant.now().toEpochMilli() + (validitySeconds * 1000L);
        String payload = "RESET:" + challengeId + ":" + userId + ":" + authVersion + ":" + expMillis;
        String payloadBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signature = sign(payloadBase64);
        return payloadBase64 + "." + signature;
    }

    /**
     * Parse and verify stateless HMAC reset grant token.
     */
    public ResetGrantClaims parseResetGrantToken(String token) {
        if (token == null || !token.contains(".")) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            return null;
        }
        String payloadBase64 = parts[0];
        String providedSignature = parts[1];

        String expectedSignature = sign(payloadBase64);
        if (!MessageDigest.isEqual(providedSignature.getBytes(StandardCharsets.UTF_8), expectedSignature.getBytes(StandardCharsets.UTF_8))) {
            return null;
        }

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(payloadBase64);
            String payload = new String(decoded, StandardCharsets.UTF_8);
            String[] fields = payload.split(":");
            if (fields.length != 5 || !"RESET".equals(fields[0])) {
                return null;
            }
            String challengeId = fields[1];
            Long userId = Long.valueOf(fields[2]);
            long authVersion = Long.parseLong(fields[3]);
            long expMillis = Long.parseLong(fields[4]);

            Instant expiresAt = expMillis > 10_000_000_000L
                    ? Instant.ofEpochMilli(expMillis)
                    : Instant.ofEpochSecond(expMillis);

            if (Instant.now().isAfter(expiresAt)) {
                return null;
            }

            return new ResetGrantClaims(challengeId, userId, authVersion, expiresAt);
        } catch (Exception ex) {
            return null;
        }
    }

    public String generateRandomHex(int byteCount) {
        byte[] bytes = new byte[byteCount];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public String hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKey, HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256", ex);
        }
    }

    private String sign(String data) {
        return hmacSha256(data);
    }
}
