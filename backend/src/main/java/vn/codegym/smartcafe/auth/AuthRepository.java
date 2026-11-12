package vn.codegym.smartcafe.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class AuthRepository {

    private static final Logger log = LoggerFactory.getLogger(AuthRepository.class);
    private final JdbcTemplate jdbcTemplate;

    public AuthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<UserRecord> USER_ROW_MAPPER = (rs, rowNum) -> new UserRecord(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("full_name"),
            rs.getString("email"),
            rs.getString("phone"),
            rs.getString("role"),
            rs.getString("status"),
            rs.getObject("avatar_media_id") != null ? rs.getLong("avatar_media_id") : null,
            rs.getLong("auth_version"),
            toInstant(rs.getTimestamp("password_changed_at"))
    );

    private static final RowMapper<OtpChallengeRecord> OTP_ROW_MAPPER = (rs, rowNum) -> new OtpChallengeRecord(
            rs.getString("id"),
            rs.getObject("user_id") != null ? rs.getLong("user_id") : null,
            rs.getString("email"),
            rs.getString("purpose"),
            rs.getString("code_hash"),
            rs.getInt("attempts"),
            rs.getInt("sends"),
            toInstant(rs.getTimestamp("expires_at")),
            toInstant(rs.getTimestamp("next_send_at")),
            toInstant(rs.getTimestamp("used_at")),
            rs.getString("delivery_status"),
            toInstant(rs.getTimestamp("created_at")),
            rs.getString("last_error_code"),
            toInstant(rs.getTimestamp("sent_at"))
    );

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }

    public Optional<UserRecord> findUserByUsername(String username) {
        String sql = """
            SELECT id, username, password_hash, full_name, email, phone, role, status,
                   avatar_media_id, auth_version, password_changed_at
            FROM users
            WHERE username = ?
            """;
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, USER_ROW_MAPPER, username));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<UserRecord> findUserByEmail(String email) {
        String sql = """
            SELECT id, username, password_hash, full_name, email, phone, role, status,
                   avatar_media_id, auth_version, password_changed_at
            FROM users
            WHERE LOWER(email) = LOWER(?)
            """;
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, USER_ROW_MAPPER, email));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<UserRecord> findUserById(Long id) {
        String sql = """
            SELECT id, username, password_hash, full_name, email, phone, role, status,
                   avatar_media_id, auth_version, password_changed_at
            FROM users
            WHERE id = ?
            """;
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, USER_ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public int updateUserPassword(Long userId, String newPasswordHash, long expectedAuthVersion) {
        String sql = """
            UPDATE users
            SET password_hash = ?,
                password_changed_at = CURRENT_TIMESTAMP(6),
                auth_version = auth_version + 1,
                must_change_password = 0,
                updated_at = CURRENT_TIMESTAMP(6)
            WHERE id = ? AND auth_version = ?
            """;
        return jdbcTemplate.update(sql, newPasswordHash, userId, expectedAuthVersion);
    }

    public int updateUserPassword(Long userId, String newPasswordHash) {
        String sql = """
            UPDATE users
            SET password_hash = ?,
                password_changed_at = CURRENT_TIMESTAMP(6),
                auth_version = auth_version + 1,
                must_change_password = 0,
                updated_at = CURRENT_TIMESTAMP(6)
            WHERE id = ?
            """;
        return jdbcTemplate.update(sql, newPasswordHash, userId);
    }

    public void saveOtpChallenge(OtpChallengeRecord c) {
        String sql = """
            INSERT INTO auth_otp_challenges (
                id, user_id, email, purpose, code_hash, attempts, sends,
                expires_at, next_send_at, delivery_status, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        jdbcTemplate.update(
                sql,
                c.id(),
                c.userId(),
                c.email(),
                c.purpose(),
                c.codeHash(),
                c.attempts(),
                c.sends(),
                Timestamp.from(c.expiresAt()),
                Timestamp.from(c.nextSendAt()),
                c.deliveryStatus(),
                Timestamp.from(c.createdAt())
        );
    }

    public Optional<OtpChallengeRecord> findOtpChallengeById(String challengeId) {
        String sql = """
            SELECT id, user_id, email, purpose, code_hash, attempts, sends,
                   expires_at, next_send_at, used_at, delivery_status, created_at,
                   last_error_code, sent_at
            FROM auth_otp_challenges
            WHERE id = ?
            """;
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, OTP_ROW_MAPPER, challengeId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<OtpChallengeRecord> findOtpChallengeByIdForUpdate(String challengeId) {
        String sql = """
            SELECT id, user_id, email, purpose, code_hash, attempts, sends,
                   expires_at, next_send_at, used_at, delivery_status, created_at,
                   last_error_code, sent_at
            FROM auth_otp_challenges
            WHERE id = ?
            FOR UPDATE
            """;
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, OTP_ROW_MAPPER, challengeId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public int incrementOtpAttempts(String challengeId) {
        String sql = "UPDATE auth_otp_challenges SET attempts = attempts + 1 WHERE id = ? AND attempts < 5 AND used_at IS NULL";
        return jdbcTemplate.update(sql, challengeId);
    }

    // sends counts accepted requests, including delivery failures, rather than delivered emails.
    public int consumeOtpResend(String challengeId, Instant acceptedAt, Instant nextSendAt) {
        return jdbcTemplate.update("""
            UPDATE auth_otp_challenges
            SET sends = sends + 1, next_send_at = ?
            WHERE id = ?
              AND used_at IS NULL
              AND sends < 3
              AND expires_at > ?
              AND next_send_at <= ?
            """, Timestamp.from(nextSendAt), challengeId, Timestamp.from(acceptedAt), Timestamp.from(acceptedAt));
    }

    public int recordOtpDelivery(String challengeId, int expectedSends, String newCodeHash,
                                 boolean delivered, Instant sentAt) {
        return jdbcTemplate.update("""
            UPDATE auth_otp_challenges
            SET code_hash = CASE WHEN ? THEN ? ELSE code_hash END,
                delivery_status = ?,
                sent_at = CASE WHEN ? THEN ? ELSE sent_at END
            WHERE id = ? AND used_at IS NULL AND sends = ?
            """, delivered, newCodeHash, delivered ? "SENT" : "FAILED", delivered,
                Timestamp.from(sentAt), challengeId, expectedSends);
    }

    public int markOtpUsed(String challengeId) {
        String sql = "UPDATE auth_otp_challenges SET used_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND used_at IS NULL AND attempts < 5 AND expires_at > CURRENT_TIMESTAMP(6)";
        return jdbcTemplate.update(sql, challengeId);
    }

    public boolean checkAndIncrementLimit(String emailKey, String purpose, Instant now, Instant windowStart, Instant nextSendAt) {
        // Enforce rate limiting atomically: 1 request per cooldown window, max 5 per hour
        // 1. Try atomic conditional update first (if record already exists)
        String updateSql = """
            UPDATE auth_otp_limits
            SET request_count = CASE
                    WHEN TIMESTAMPDIFF(SECOND, window_start, ?) >= 3600 THEN 1
                    ELSE request_count + 1
                END,
                window_start = CASE
                    WHEN TIMESTAMPDIFF(SECOND, window_start, ?) >= 3600 THEN ?
                    ELSE window_start
                END,
                next_send_at = ?
            WHERE email_key = ?
              AND purpose = ?
              AND ? >= next_send_at
              AND (TIMESTAMPDIFF(SECOND, window_start, ?) >= 3600 OR request_count < 5)
            """;

        String insertSql = """
            INSERT INTO auth_otp_limits (email_key, purpose, next_send_at, window_start, request_count)
            VALUES (?, ?, ?, ?, 1)
            """;

        Timestamp nowTs = Timestamp.from(now);
        Timestamp nextSendTs = Timestamp.from(nextSendAt);

        try {
            int rowsUpdated = jdbcTemplate.update(
                    updateSql,
                    nowTs,
                    nowTs,
                    nowTs,
                    nextSendTs,
                    emailKey,
                    purpose,
                    nowTs,
                    nowTs
            );
            if (rowsUpdated == 1) {
                return true;
            }

            // If 0 rows updated, check if the record already exists
            // If it exists, 0 rows updated means rate limit or cooldown was not satisfied
            String checkExistsSql = "SELECT COUNT(*) FROM auth_otp_limits WHERE email_key = ? AND purpose = ?";
            Integer count = jdbcTemplate.queryForObject(checkExistsSql, Integer.class, emailKey, purpose);
            if (count != null && count > 0) {
                return false;
            }

            // Record does not exist: attempt atomic insert with initial count = 1
            try {
                int inserted = jdbcTemplate.update(insertSql, emailKey, purpose, nextSendTs, nowTs);
                return inserted == 1;
            } catch (org.springframework.dao.DuplicateKeyException dke) {
                // Another concurrent thread inserted the record first: re-evaluate rate limit via update
                int retryUpdated = jdbcTemplate.update(
                        updateSql,
                        nowTs,
                        nowTs,
                        nowTs,
                        nextSendTs,
                        emailKey,
                        purpose,
                        nowTs,
                        nowTs
                );
                return retryUpdated == 1;
            }
        } catch (Exception e) {
            log.error("Failed checking rate limit for key {}: {}", emailKey, e.getMessage());
            return false; // Fail-closed
        }
    }
}
