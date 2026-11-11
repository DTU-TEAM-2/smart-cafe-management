package vn.codegym.smartcafe.auth;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class AuthRepository {

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

    public int incrementOtpAttempts(String challengeId) {
        String sql = "UPDATE auth_otp_challenges SET attempts = attempts + 1 WHERE id = ? AND attempts < 5 AND used_at IS NULL";
        return jdbcTemplate.update(sql, challengeId);
    }

    public int updateOtpResend(String challengeId, String newCodeHash, Instant newExpiresAt, Instant newNextSendAt) {
        String sql = """
            UPDATE auth_otp_challenges
            SET code_hash = ?,
                sends = sends + 1,
                expires_at = ?,
                next_send_at = ?,
                sent_at = CURRENT_TIMESTAMP(6)
            WHERE id = ? AND used_at IS NULL
            """;
        return jdbcTemplate.update(
                sql,
                newCodeHash,
                Timestamp.from(newExpiresAt),
                Timestamp.from(newNextSendAt),
                challengeId
        );
    }

    public int markOtpUsed(String challengeId) {
        String sql = "UPDATE auth_otp_challenges SET used_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND used_at IS NULL AND attempts < 5 AND expires_at > CURRENT_TIMESTAMP(6)";
        return jdbcTemplate.update(sql, challengeId);
    }

    public boolean checkAndIncrementLimit(String emailKey, String purpose, Instant now, Instant windowStart, Instant nextSendAt) {
        // Enforce rate limiting: 1 request per cooldown window, max 5 per hour
        String selectSql = "SELECT next_send_at, window_start, request_count FROM auth_otp_limits WHERE email_key = ? AND purpose = ?";
        try {
            return jdbcTemplate.query(selectSql, rs -> {
                if (!rs.next()) {
                    String insertSql = "INSERT INTO auth_otp_limits (email_key, purpose, next_send_at, window_start, request_count) VALUES (?, ?, ?, ?, 1)";
                    jdbcTemplate.update(insertSql, emailKey, purpose, Timestamp.from(nextSendAt), Timestamp.from(now));
                    return true;
                }
                Timestamp nextSend = rs.getTimestamp("next_send_at");
                Timestamp winStart = rs.getTimestamp("window_start");
                int count = rs.getInt("request_count");

                if (now.isBefore(nextSend.toInstant())) {
                    return false; // Still in cooldown
                }

                if (now.toEpochMilli() - winStart.getTime() > 3600_000L) {
                    // Reset 1-hour window
                    String updateSql = "UPDATE auth_otp_limits SET window_start = ?, request_count = 1, next_send_at = ? WHERE email_key = ? AND purpose = ?";
                    jdbcTemplate.update(updateSql, Timestamp.from(now), Timestamp.from(nextSendAt), emailKey, purpose);
                    return true;
                }

                if (count >= 5) {
                    return false; // Exceeded 5 requests per hour
                }

                String updateSql = "UPDATE auth_otp_limits SET request_count = request_count + 1, next_send_at = ? WHERE email_key = ? AND purpose = ?";
                jdbcTemplate.update(updateSql, Timestamp.from(nextSendAt), emailKey, purpose);
                return true;
            }, emailKey, purpose);
        } catch (Exception e) {
            return true;
        }
    }
}
