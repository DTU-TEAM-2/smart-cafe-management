package vn.codegym.smartcafe.menu;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static vn.codegym.smartcafe.menu.MenuModels.*;

@Service
@Transactional
class MenuCatalog {
    private static final String CATEGORY_SELECT = """
            SELECT c.code, c.name, c.version,
            (SELECT COUNT(*) FROM menu_items i WHERE i.category_code=c.code AND i.active=TRUE) AS item_count
            FROM menu_categories c
            """;
    private static final String ITEM_FROM = " FROM menu_items i JOIN menu_categories c ON c.code=i.category_code ";
    private static final String ITEM_SELECT = "SELECT i.*, c.name AS category_name" + ITEM_FROM;
    private static final RowMapper<Category> CATEGORY = (rs, index) -> new Category(
            rs.getString("code"), rs.getString("name"), rs.getLong("version"), rs.getLong("item_count"));
    private static final RowMapper<Item> ITEM = (rs, index) -> new Item(
            rs.getString("code"), rs.getString("name"), rs.getBigDecimal("price"), rs.getString("category_code"),
            rs.getString("category_name"), rs.getString("image_url"), rs.getString("description"),
            rs.getBoolean("available"), rs.getLong("version"));
    private final JdbcTemplate jdbc;
    private final MenuImages images;

    MenuCatalog(JdbcTemplate jdbc, MenuImages images) { this.jdbc = jdbc; this.images = images; }

    @Transactional(readOnly = true)
    Page<Category> categories(String keyword, int page, int size) {
        String pattern = contains(keyword.strip());
        String where = " WHERE c.active=TRUE AND (c.code LIKE ? ESCAPE '!' OR c.name LIKE ? ESCAPE '!')";
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM menu_categories c" + where, Long.class, pattern, pattern);
        return new Page<>(jdbc.query(CATEGORY_SELECT + where + " ORDER BY c.code LIMIT ? OFFSET ?", CATEGORY,
                pattern, pattern, size, (long) page * size), total, page, size);
    }

    @Transactional(readOnly = true)
    Category category(String code) {
        return jdbc.query(CATEGORY_SELECT + " WHERE c.code=? AND c.active=TRUE", CATEGORY, normalizeCode(code))
                .stream().findFirst().orElseThrow(() -> missing("Nhóm món"));
    }

    Category createCategory(CategoryInput input) {
        requireInitialVersion(input.version());
        jdbc.update("INSERT INTO menu_categories(code, name) VALUES (?, ?)", input.code(), input.name());
        return category(input.code());
    }

    Category updateCategory(String code, CategoryInput input) {
        requireSameCode(code, input.code());
        lockCategory(code);
        requireWrite(jdbc.update("""
                UPDATE menu_categories SET name=?, version=version+1
                WHERE code=? AND active=TRUE AND version=?
                """, input.name(), input.code(), input.version()));
        return category(code);
    }

    void deleteCategory(String code, long version) {
        // All item writes take this same lock before touching the item row.
        lockCategory(code);
        requireWrite(jdbc.update("""
                UPDATE menu_categories SET active=FALSE, version=version+1
                WHERE code=? AND active=TRUE AND version=?
                """, normalizeCode(code), version));
        jdbc.update("UPDATE menu_items SET active=FALSE, version=version+1 WHERE category_code=? AND active=TRUE", normalizeCode(code));
    }

    @Transactional(readOnly = true)
    Page<Item> items(String field, String keyword, String categoryCode, int page, int size) {
        var args = new ArrayList<Object>();
        String where = " WHERE i.active=TRUE AND c.active=TRUE";
        if (categoryCode != null && !categoryCode.isBlank()) {
            where += " AND i.category_code=?";
            args.add(normalizeCode(categoryCode));
        }
        if (!keyword.isBlank()) {
            String term = keyword.strip();
            switch (field) {
                case "code" -> { where += " AND i.code=?"; args.add(normalizeCode(term)); }
                case "name" -> { where += " AND i.name LIKE ? ESCAPE '!'"; args.add(contains(term)); }
                case "category" -> {
                    where += " AND (c.code=? OR c.name LIKE ? ESCAPE '!')";
                    args.add(normalizeCode(term)); args.add(contains(term));
                }
                case "price" -> {
                    BigDecimal price;
                    try { price = new BigDecimal(term); }
                    catch (NumberFormatException exception) { throw bad("INVALID_PRICE", "Giá tìm kiếm không hợp lệ."); }
                    if (price.scale() > 0 || price.signum() < 0 || price.compareTo(new BigDecimal("999999999999")) > 0) {
                        throw bad("INVALID_PRICE", "Giá tìm kiếm phải là số VND nguyên trong giới hạn.");
                    }
                    where += " AND i.price=?"; args.add(price);
                }
                default -> throw bad("INVALID_FIELD", "Trường tìm kiếm không hợp lệ.");
            }
        }
        long total = jdbc.queryForObject("SELECT COUNT(*)" + ITEM_FROM + where, Long.class, args.toArray());
        args.add(size); args.add((long) page * size);
        return new Page<>(jdbc.query(ITEM_SELECT + where + " ORDER BY i.code LIMIT ? OFFSET ?", ITEM, args.toArray()), total, page, size);
    }

    @Transactional(readOnly = true)
    Item item(String code) {
        return jdbc.query(ITEM_SELECT + " WHERE i.code=? AND i.active=TRUE AND c.active=TRUE", ITEM, normalizeCode(code))
                .stream().findFirst().orElseThrow(() -> missing("Món"));
    }

    Item createItem(ItemInput input) {
        requireInitialVersion(input.version());
        images.requireSafeUrl(input.imageUrl());
        lockCategory(input.categoryCode());
        jdbc.update("""
                INSERT INTO menu_items(code, name, price, category_code, image_url, description, available)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, input.code(), input.name(), input.price(), input.categoryCode(), input.imageUrl(), input.description(), input.available());
        return item(input.code());
    }

    Item updateItem(String code, ItemInput input) {
        requireSameCode(code, input.code());
        images.requireSafeUrl(input.imageUrl());
        lockCategory(input.categoryCode());
        // Existing categories are immutable, matching the legacy SRS edit operation.
        requireWrite(jdbc.update("""
                UPDATE menu_items SET name=?, price=?, image_url=?, description=?, available=?, version=version+1
                WHERE code=? AND category_code=? AND active=TRUE AND version=?
                """, input.name(), input.price(), input.imageUrl(), input.description(), input.available(),
                input.code(), input.categoryCode(), input.version()));
        return item(code);
    }

    void deleteItem(String code, long version) {
        var groups = jdbc.queryForList("SELECT category_code FROM menu_items WHERE code=? AND active=TRUE", String.class, normalizeCode(code));
        if (groups.isEmpty()) throw missing("Món");
        lockCategory(groups.getFirst());
        requireWrite(jdbc.update("UPDATE menu_items SET active=FALSE, version=version+1 WHERE code=? AND active=TRUE AND version=?", normalizeCode(code), version));
    }

    private void lockCategory(String code) {
        if (jdbc.queryForList("SELECT code FROM menu_categories WHERE code=? AND active=TRUE FOR UPDATE", String.class, normalizeCode(code)).isEmpty()) {
            throw new MenuException(HttpStatus.CONFLICT, "CATEGORY_INACTIVE", "Nhóm món không tồn tại hoặc đã ngừng hoạt động.");
        }
    }

    private void requireWrite(int count) {
        if (count != 1) throw new MenuException(HttpStatus.CONFLICT, "STALE_MENU", "Dữ liệu đã thay đổi, nhóm không khớp hoặc bản ghi đã bị xóa. Hãy tải lại.");
    }

    private void requireInitialVersion(long version) {
        if (version != 0) throw bad("INVALID_VERSION", "Bản ghi mới phải có version bằng 0.");
    }

    private void requireSameCode(String path, String body) {
        if (!normalizeCode(path).equals(body)) throw bad("IMMUTABLE_CODE", "Không được thay đổi mã.");
    }

    private static String contains(String value) {
        return "%" + value.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
    private static MenuException missing(String noun) { return new MenuException(HttpStatus.NOT_FOUND, "MENU_NOT_FOUND", noun + " không tồn tại."); }
    private static MenuException bad(String code, String message) { return new MenuException(HttpStatus.BAD_REQUEST, code, message); }
}
