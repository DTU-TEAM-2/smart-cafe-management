package vn.codegym.smartcafe.customermenu;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class CustomerMenuService {
    private static final int MAX_PAGE = 1_000_000;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_KEYWORD_LENGTH = 160;

    private final JdbcClient jdbc;

    CustomerMenuService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<CustomerMenuModels.Category> listCategories() {
        return jdbc.sql("""
                SELECT c.id, c.code, c.name, COUNT(m.id) AS active_item_count
                FROM menu_categories c
                LEFT JOIN menu_items m ON m.category_id = c.id AND m.active = TRUE
                WHERE c.active = TRUE
                GROUP BY c.id, c.code, c.name
                ORDER BY c.name ASC, c.id ASC
                """).query((row, index) -> new CustomerMenuModels.Category(
                        row.getString("id"),
                        row.getString("code"),
                        row.getString("name"),
                        row.getLong("active_item_count"))).list();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CustomerMenuModels.ItemPage listItems(
            Long categoryId, String keyword, boolean availableOnly, int page, int size) {
        String normalizedKeyword = keyword == null ? "" : keyword.strip();
        if (page < 0 || page > MAX_PAGE || size < 1 || size > MAX_PAGE_SIZE
                || normalizedKeyword.length() > MAX_KEYWORD_LENGTH
                || (categoryId != null && categoryId <= 0)) {
            throw new CustomerMenuException(HttpStatus.BAD_REQUEST, "Tham số truy vấn thực đơn không hợp lệ.");
        }

        if (categoryId != null) {
            boolean categoryExists = jdbc.sql("""
                    SELECT 1 FROM menu_categories WHERE id = ? AND active = TRUE
                    """).param(categoryId).query(Integer.class).optional().isPresent();
            if (!categoryExists) {
                throw new CustomerMenuException(HttpStatus.NOT_FOUND, "Không tìm thấy nhóm món.");
            }
        }

        StringBuilder where = new StringBuilder(" WHERE m.active = TRUE AND c.active = TRUE");
        List<Object> params = new ArrayList<>();
        if (categoryId != null) {
            where.append(" AND m.category_id = ?");
            params.add(categoryId);
        }
        if (availableOnly) {
            where.append(" AND m.available = TRUE");
        }
        if (!normalizedKeyword.isEmpty()) {
            String pattern = "%" + escapeLike(normalizedKeyword.toLowerCase(Locale.ROOT)) + "%";
            where.append(" AND (LOWER(m.name) LIKE ? ESCAPE '!' OR LOWER(m.code) LIKE ? ESCAPE '!')");
            params.add(pattern);
            params.add(pattern);
        }

        String fromClause = """
                 FROM menu_items m
                 JOIN menu_categories c ON c.id = m.category_id
                 LEFT JOIN media_assets image ON image.id = m.image_media_id AND image.deleted_at IS NULL
                """ + where;

        long total = jdbc.sql("SELECT COUNT(*)" + fromClause)
                .params(params)
                .query(Long.class)
                .single();

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add((long) page * size);

        var items = jdbc.sql("""
                SELECT m.id, m.code, m.name, m.price, m.available,
                       image.secure_url AS image_url,
                       c.id AS category_id, c.code AS category_code, c.name AS category_name
                """ + fromClause + " ORDER BY m.name ASC, m.id ASC LIMIT ? OFFSET ?")
                .params(pageParams)
                .query((row, index) -> mapItem(row))
                .list();

        return new CustomerMenuModels.ItemPage(items, total, page, size);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CustomerMenuModels.Item getItem(long itemId) {
        if (itemId <= 0) {
            throw new CustomerMenuException(HttpStatus.BAD_REQUEST, "Mã món không hợp lệ.");
        }
        return jdbc.sql("""
                SELECT m.id, m.code, m.name, m.price, m.available,
                       image.secure_url AS image_url,
                       c.id AS category_id, c.code AS category_code, c.name AS category_name
                FROM menu_items m
                JOIN menu_categories c ON c.id = m.category_id
                LEFT JOIN media_assets image ON image.id = m.image_media_id AND image.deleted_at IS NULL
                WHERE m.id = ? AND m.active = TRUE AND c.active = TRUE
                """).param(itemId).query((row, index) -> mapItem(row)).optional()
                .orElseThrow(() -> new CustomerMenuException(HttpStatus.NOT_FOUND, "Không tìm thấy món."));
    }

    private static CustomerMenuModels.Item mapItem(ResultSet row) throws SQLException {
        return new CustomerMenuModels.Item(
                row.getString("id"),
                row.getString("code"),
                row.getString("name"),
                row.getBigDecimal("price").toBigIntegerExact().toString(),
                row.getString("image_url"),
                row.getBoolean("available"),
                row.getString("category_id"),
                row.getString("category_code"),
                row.getString("category_name"));
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
