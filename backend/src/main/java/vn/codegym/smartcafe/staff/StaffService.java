package vn.codegym.smartcafe.staff;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Statement;
import java.time.LocalDate;
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
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public StaffService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public StaffModels.PageResponse<StaffModels.StaffSummary> getStaffList(String keyword, int page, int size) {
        int pageSize = (size <= 0) ? 10 : Math.min(size, 100);
        int pageNumber = Math.max(0, page);
        int offset = pageNumber * pageSize;

        boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();
        String searchPattern = hasKeyword ? "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%" : "";

        String countSql;
        String selectSql;

        if (hasKeyword) {
            countSql = """
                    SELECT COUNT(*)
                    FROM users u
                    LEFT JOIN staff_profiles p ON p.user_id = u.id
                    WHERE (LOWER(u.username) LIKE ? OR LOWER(u.full_name) LIKE ? OR u.phone LIKE ?)
                    """;
            selectSql = """
                    SELECT u.id, u.username, u.full_name, u.email, u.phone,
                           p.address, p.gender, p.birthday, u.role, p.salary, u.status,
                           m.secure_url AS avatar_url
                    FROM users u
                    LEFT JOIN staff_profiles p ON p.user_id = u.id
                    LEFT JOIN media_assets m ON m.id = u.avatar_media_id AND m.deleted_at IS NULL
                    WHERE (LOWER(u.username) LIKE ? OR LOWER(u.full_name) LIKE ? OR u.phone LIKE ?)
                    ORDER BY u.username ASC
                    LIMIT ? OFFSET ?
                    """;
        } else {
            countSql = """
                    SELECT COUNT(*)
                    FROM users u
                    LEFT JOIN staff_profiles p ON p.user_id = u.id
                    """;
            selectSql = """
                    SELECT u.id, u.username, u.full_name, u.email, u.phone,
                           p.address, p.gender, p.birthday, u.role, p.salary, u.status,
                           m.secure_url AS avatar_url
                    FROM users u
                    LEFT JOIN staff_profiles p ON p.user_id = u.id
                    LEFT JOIN media_assets m ON m.id = u.avatar_media_id AND m.deleted_at IS NULL
                    ORDER BY u.username ASC
                    LIMIT ? OFFSET ?
                    """;
        }

        Long total;
        List<StaffModels.StaffSummary> items;
        if (hasKeyword) {
            total = jdbc.queryForObject(countSql, Long.class, searchPattern, searchPattern, searchPattern);
            items = jdbc.query(selectSql, (rs, rowNum) -> {
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
            }, searchPattern, searchPattern, searchPattern, pageSize, offset);
        } else {
            total = jdbc.queryForObject(countSql, Long.class);
            items = jdbc.query(selectSql, (rs, rowNum) -> {
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
        }

        long totalElements = total != null ? total : 0L;
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);

        return new StaffModels.PageResponse<>(items, pageNumber, pageSize, totalElements, totalPages);
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
                WHERE u.id = ?
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

        String email = request.email() != null && !request.email().trim().isEmpty() ? request.email().trim() : null;
        if (email != null) {
            Integer emailCount = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
            if (emailCount != null && emailCount > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã tồn tại trong hệ thống.");
            }
        }

        String role = (request.role() != null && !request.role().trim().isEmpty())
                ? request.role().trim().toUpperCase(Locale.ROOT)
                : "STAFF";
        String passwordHash = passwordEncoder.encode("Staff@123");

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
        StaffValidation.validateUpdate(request);

        StaffModels.StaffDetail existing = getStaffById(id);

        String email = request.email() != null && !request.email().trim().isEmpty() ? request.email().trim() : null;
        if (email != null && !Objects.equals(email, existing.email())) {
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
