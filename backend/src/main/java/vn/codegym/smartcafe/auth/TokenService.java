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

    public record TokenClaims(Long userId, String role, long authVersion, Instant expiresAt) {}

    public String generateToken(Long userId, String role, long authVersion) {
        long now = Instant.now().getEpochSecond();
        long exp = now + tokenValiditySeconds;
        String payload = userId + ":" + role + ":" + authVersion + ":" + exp;
        String payloadBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signature = sign(payloadBase64);
        return payloadBase64 + "." + signature;
    }

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
            if (fields.length != 4) {
                return null;
            }
            Long userId = Long.valueOf(fields[0]);
            String role = fields[1];
            long authVersion = Long.parseLong(fields[2]);
            long expEpoch = Long.parseLong(fields[3]);
            Instant expiresAt = Instant.ofEpochSecond(expEpoch);

            if (Instant.now().isAfter(expiresAt)) {
                return null;
            }
            return new TokenClaims(userId, role, authVersion, expiresAt);
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
