package vn.codegym.smartcafe.staff;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.Date;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StaffService {
    private static final String PWD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public StaffService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    private String generateRandomPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(PWD_CHARS.charAt(SECURE_RANDOM.nextInt(PWD_CHARS.length())));
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public StaffModels.PageResponse<StaffModels.StaffSummary> getStaffList(String keyword, int page, int size) {
        int pageSize = (size <= 0) ? 10 : Math.min(size, 100);
        long pageNumber = Math.max(0, (long) page);
        long offset = pageNumber * (long) pageSize;

        boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();
        String searchPattern = hasKeyword ? "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%" : "";

        String countSql;
        String selectSql;

        if (hasKeyword) {
            countSql = """
                    SELECT COUNT(*)
                    FROM users u
                    LEFT JOIN staff_profiles p ON p.user_id = u.id
                    WHERE u.status = 'ACTIVE' AND u.role IN ('ADMIN', 'STAFF')
                          AND (LOWER(CONVERT(u.username USING utf8mb4)) LIKE ? OR LOWER(u.full_name) LIKE ? OR u.phone LIKE ?)
                    """;
            selectSql = """
                    SELECT u.id, u.username, u.full_name, u.email, u.phone,
                           p.address, p.gender, p.birthday, u.role, p.salary, u.status,
                           m.secure_url AS avatar_url
                    FROM users u
                    LEFT JOIN staff_profiles p ON p.user_id = u.id
                    LEFT JOIN media_assets m ON m.id = u.avatar_media_id AND m.deleted_at IS NULL
                    WHERE u.status = 'ACTIVE' AND u.role IN ('ADMIN', 'STAFF')
                          AND (LOWER(CONVERT(u.username USING utf8mb4)) LIKE ? OR LOWER(u.full_name) LIKE ? OR u.phone LIKE ?)
                    ORDER BY u.username ASC
                    LIMIT ? OFFSET ?
                    """;
        } else {
            countSql = """
                    SELECT COUNT(*)
                    FROM users u
                    LEFT JOIN staff_profiles p ON p.user_id = u.id
                    WHERE u.status = 'ACTIVE' AND u.role IN ('ADMIN', 'STAFF')
                    """;
            selectSql = """
                    SELECT u.id, u.username, u.full_name, u.email, u.phone,
                           p.address, p.gender, p.birthday, u.role, p.salary, u.status,
                           m.secure_url AS avatar_url
                    FROM users u
                    LEFT JOIN staff_profiles p ON p.user_id = u.id
                    LEFT JOIN media_assets m ON m.id = u.avatar_media_id AND m.deleted_at IS NULL
                    WHERE u.status = 'ACTIVE' AND u.role IN ('ADMIN', 'STAFF')
                    ORDER BY u.username ASC
                    LIMIT ? OFFSET ?
                    """;
        }

        Long total = hasKeyword
                ? jdbc.queryForObject(countSql, Long.class, searchPattern, searchPattern, searchPattern)
                : jdbc.queryForObject(countSql, Long.class);

        long totalElements = total != null ? total : 0L;
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);

        if (totalElements == 0 || offset >= totalElements) {
            return new StaffModels.PageResponse<>(Collections.emptyList(), (int) pageNumber, pageSize, totalElements, totalPages);
        }

        List<StaffModels.StaffSummary> items = hasKeyword
                ? jdbc.query(selectSql, (rs, rowNum) -> {
                    Date bDate = rs.getDate("birthday");
                    LocalDate birthday = (bDate != null) ? bDate.toLocalDate() : null;
                    return new StaffModels.StaffSummary(
                            rs.getLong("id"),
                            rs.getString("username"),
                            rs.getString("full_name"),
                            rs.getString("email"),
                            rs.getString("phone"),
                            rs.getString("address"),
                            rs.getString("gender"),
                            birthday,
                            rs.getString("role"),
                            rs.getBigDecimal("salary"),
                            rs.getString("status"),
                            rs.getString("avatar_url")
                    );
                }, searchPattern, searchPattern, searchPattern, pageSize, offset)
                : jdbc.query(selectSql, (rs, rowNum) -> {
                    Date bDate = rs.getDate("birthday");
                    LocalDate birthday = (bDate != null) ? bDate.toLocalDate() : null;
                    return new StaffModels.StaffSummary(
                            rs.getLong("id"),
                            rs.getString("username"),
                            rs.getString("full_name"),
                            rs.getString("email"),
                            rs.getString("phone"),
                            rs.getString("address"),
                            rs.getString("gender"),
                            birthday,
                            rs.getString("role"),
                            rs.getBigDecimal("salary"),
                            rs.getString("status"),
                            rs.getString("avatar_url")
                    );
                }, pageSize, offset);

        return new StaffModels.PageResponse<>(items, (int) pageNumber, pageSize, totalElements, totalPages);
    }

    @Transactional(readOnly = true)
    public StaffModels.StaffDetail getStaffById(Long id) {
        String sql = """
                SELECT u.id, u.username, u.full_name, u.email, u.phone,
                       p.address, p.gender, p.birthday, u.role, p.salary, u.status,
                       u.avatar_media_id, m.secure_url AS avatar_url
                FROM users u
                LEFT JOIN staff_profiles p ON p.user_id = u.id
                LEFT JOIN media_assets m ON m.id = u.avatar_media_id AND m.deleted_at IS NULL
                WHERE u.id = ? AND u.status = 'ACTIVE' AND u.role IN ('ADMIN', 'STAFF')
                """;

        List<StaffModels.StaffDetail> list = jdbc.query(sql, (rs, rowNum) -> {
            Date bDate = rs.getDate("birthday");
            LocalDate birthday = (bDate != null) ? bDate.toLocalDate() : null;
            Long mediaId = rs.getObject("avatar_media_id") != null ? rs.getLong("avatar_media_id") : null;
            return new StaffModels.StaffDetail(
                    rs.getLong("id"),
                    rs.getString("username"),
                    rs.getString("full_name"),
                    rs.getString("email"),
                    rs.getString("phone"),
                    rs.getString("address"),
                    rs.getString("gender"),
                    birthday,
                    rs.getString("role"),
                    rs.getBigDecimal("salary"),
                    rs.getString("status"),
                    mediaId,
                    rs.getString("avatar_url")
            );
        }, id);

        if (list.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nhân viên không tồn tại trong hệ thống.");
        }
        return list.get(0);
    }

    @Transactional
    public StaffModels.StaffDetail createStaff(StaffModels.CreateStaffRequest request) {
        StaffValidation.validateCreate(request);

        String username = request.username().trim();
        Integer existsCount = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, username);
        if (existsCount != null && existsCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username đã tồn tại trong hệ thống.");
        }

        String email = request.email().trim();
        Integer emailCount = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
        if (emailCount != null && emailCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã tồn tại trong hệ thống.");
        }

        String role = (request.role() != null && !request.role().trim().isEmpty())
                ? request.role().trim().toUpperCase(Locale.ROOT)
                : "STAFF";

        if ("ADMIN".equalsIgnoreCase(role)) {
            Integer adminCount = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND status = 'ACTIVE'", Integer.class);
            if (adminCount != null && adminCount >= 2) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hệ thống chỉ cho phép tối đa 2 tài khoản Quản trị viên (ADMIN).");
            }
        }

        String tempPassword = generateRandomPassword();
        String passwordHash = passwordEncoder.encode(tempPassword);

        var keyHolder = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                var ps = connection.prepareStatement("""
                        INSERT INTO users(username, password_hash, full_name, email, phone, role, status, avatar_media_id)
                        VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, username);
                ps.setString(2, passwordHash);
                ps.setString(3, request.fullName().trim());
                ps.setString(4, email);
                ps.setString(5, request.phone().trim());
                ps.setString(6, role);
                if (request.avatarMediaId() != null) {
                    ps.setLong(7, request.avatarMediaId());
                } else {
                    ps.setNull(7, java.sql.Types.BIGINT);
                }
                return ps;
            }, keyHolder);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username hoặc email đã tồn tại.");
        }

        Number generatedKey = keyHolder.getKey();
        if (generatedKey == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu thông tin nhân viên.");
        }
        long userId = generatedKey.longValue();

        Date birthdaySql = request.birthday() != null ? Date.valueOf(request.birthday()) : null;
        String gender = request.gender() != null && !request.gender().trim().isEmpty()
                ? request.gender().trim().toUpperCase(Locale.ROOT)
                : null;

        jdbc.update("""
                INSERT INTO staff_profiles(user_id, address, birthday, gender, salary)
                VALUES (?, ?, ?, ?, ?)
                """, userId, request.address().trim(), birthdaySql, gender, request.salary());

        return getStaffById(userId);
    }

    @Transactional
    public StaffModels.StaffDetail updateStaff(Long id, StaffModels.UpdateStaffRequest request) {
        return updateStaff(id, request, null);
    }

    @Transactional
    public StaffModels.StaffDetail updateStaff(Long id, StaffModels.UpdateStaffRequest request, String currentUsername) {
        StaffValidation.validateUpdate(request);

        StaffModels.StaffDetail existing = getStaffById(id);

        if (currentUsername != null && currentUsername.equalsIgnoreCase(existing.username())
                && "ADMIN".equalsIgnoreCase(existing.role())
                && request.role() != null && "STAFF".equalsIgnoreCase(request.role().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không được tự hạ quyền tài khoản Quản trị viên đang đăng nhập.");
        }

        if ("STAFF".equalsIgnoreCase(existing.role()) && request.role() != null && "ADMIN".equalsIgnoreCase(request.role().trim())) {
            Integer adminCount = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND status = 'ACTIVE'", Integer.class);
            if (adminCount != null && adminCount >= 2) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hệ thống chỉ cho phép tối đa 2 tài khoản Quản trị viên (ADMIN).");
            }
        }

        String email = request.email().trim();
        if (!Objects.equals(email, existing.email())) {
            Integer emailCount = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ? AND id != ?", Integer.class, email, id);
            if (emailCount != null && emailCount > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã tồn tại trong hệ thống.");
            }
        }

        String role = (request.role() != null && !request.role().trim().isEmpty())
                ? request.role().trim().toUpperCase(Locale.ROOT)
                : existing.role();

        jdbc.update("""
                UPDATE users
                SET full_name = ?, email = ?, phone = ?, role = ?, avatar_media_id = ?
                WHERE id = ?
                """, request.fullName().trim(), email, request.phone().trim(), role, request.avatarMediaId(), id);

        Date birthdaySql = request.birthday() != null ? Date.valueOf(request.birthday()) : null;
        String gender = request.gender() != null && !request.gender().trim().isEmpty()
                ? request.gender().trim().toUpperCase(Locale.ROOT)
                : null;

        jdbc.update("""
                INSERT INTO staff_profiles(user_id, address, birthday, gender, salary)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    address = VALUES(address),
                    birthday = VALUES(birthday),
                    gender = VALUES(gender),
                    salary = VALUES(salary)
                """, id, request.address().trim(), birthdaySql, gender, request.salary());

        return getStaffById(id);
    }

    @Transactional
    public void deleteStaff(Long id, String currentUsername) {
        StaffModels.StaffDetail target = getStaffById(id);

        if (currentUsername != null && currentUsername.equalsIgnoreCase(target.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể tự xóa tài khoản đang đăng nhập.");
        }

        // Soft delete: chuyển trạng thái INACTIVE để bảo toàn tham chiếu hóa đơn / phiên bàn
        jdbc.update("UPDATE users SET status = 'INACTIVE' WHERE id = ?", id);
    }
}
