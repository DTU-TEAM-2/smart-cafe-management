package vn.codegym.smartcafe.home;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class HomeHighlightsService {
    private final JdbcClient jdbc;

    HomeHighlightsService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public HomeHighlights getHighlights() {
        var newest = jdbc.sql("""
                SELECT m.id, m.name, m.price, image.secure_url AS image_url
                FROM menu_items m
                JOIN menu_categories c ON c.id = m.category_id
                LEFT JOIN media_assets image ON image.id = m.image_media_id AND image.deleted_at IS NULL
                WHERE m.active = TRUE AND c.active = TRUE
                ORDER BY m.created_at DESC, m.id DESC
                LIMIT 5
                """).query((row, index) -> new HomeHighlights.NewestItem(
                        row.getString("id"), row.getString("name"),
                        row.getBigDecimal("price").toBigIntegerExact().toString(), row.getString("image_url"))).list();

        var mostOrdered = jdbc.sql("""
                SELECT m.id, m.name, totals.ordered_quantity, image.secure_url AS image_url
                FROM menu_items m
                JOIN menu_categories c ON c.id = m.category_id
                LEFT JOIN media_assets image ON image.id = m.image_media_id AND image.deleted_at IS NULL
                JOIN v_menu_item_order_totals totals ON totals.menu_item_id = m.id
                WHERE m.active = TRUE AND c.active = TRUE AND totals.ordered_quantity > 0
                ORDER BY totals.ordered_quantity DESC, m.id ASC
                LIMIT 5
                """).query((row, index) -> new HomeHighlights.PopularItem(
                        row.getString("id"), row.getString("name"),
                        row.getBigDecimal("ordered_quantity").toBigIntegerExact().toString(), row.getString("image_url"))).list();

        return new HomeHighlights(newest, mostOrdered);
    }
}
