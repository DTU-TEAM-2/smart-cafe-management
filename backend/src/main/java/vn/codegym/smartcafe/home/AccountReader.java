package vn.codegym.smartcafe.home;

import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** One account projection for Header and Profile, including missing personnel/media relations. */
@Repository
public class AccountReader {
    private final JdbcClient jdbc;

    public AccountReader(JdbcClient jdbc) { this.jdbc = jdbc; }

    public record Account(HomeAccount home, Instant passwordChangedAt, boolean mustChangePassword,
                          long version, long authVersion) {}

    public Optional<Account> byUsername(String username) { return read("u.username", username); }
    public Optional<Account> byId(long id) { return read("u.id", id); }

    private Optional<Account> read(String column, Object identity) {
        return jdbc.sql("""
                SELECT u.id, u.username, u.full_name, u.email, u.phone, u.role,
                       u.password_changed_at, u.must_change_password, u.version, u.auth_version,
                       p.address, p.birthday, p.gender, p.salary, a.secure_url
                FROM users u
                LEFT JOIN staff_profiles p ON p.user_id=u.id AND u.role IN ('ADMIN','STAFF')
                LEFT JOIN media_assets a ON a.id=u.avatar_media_id AND a.deleted_at IS NULL
                WHERE
                """ + column + "=? AND u.status='ACTIVE'")
                .param(identity).query((row, index) -> new Account(new HomeAccount(
                        row.getString("id"), row.getString("username"), row.getString("full_name"),
                        row.getString("email"), row.getString("phone"), row.getString("role"),
                        row.getString("address"), row.getString("birthday"), row.getString("gender"),
                        row.getBigDecimal("salary") == null ? null : row.getBigDecimal("salary").toPlainString(),
                        row.getString("secure_url")), row.getTimestamp("password_changed_at").toInstant(),
                        row.getBoolean("must_change_password"), row.getLong("version"), row.getLong("auth_version")))
                .optional();
    }
}
