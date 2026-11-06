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
                SELECT m.id, m.name, m.price
                FROM menu_items m
                JOIN menu_categories c ON c.id = m.category_id
                WHERE m.active = TRUE AND c.active = TRUE
                ORDER BY m.created_at DESC, m.id DESC
                LIMIT 5
                """).query((row, index) -> new HomeHighlights.NewestItem(
                        row.getString("id"), row.getString("name"),
                        row.getBigDecimal("price").toBigIntegerExact().toString())).list();

        var mostOrdered = jdbc.sql("""
                SELECT m.id, m.name, totals.ordered_quantity
                FROM menu_items m
                JOIN menu_categories c ON c.id = m.category_id
                JOIN v_menu_item_order_totals totals ON totals.menu_item_id = m.id
                WHERE m.active = TRUE AND c.active = TRUE AND totals.ordered_quantity > 0
                ORDER BY totals.ordered_quantity DESC, m.id ASC
                LIMIT 5
                """).query((row, index) -> new HomeHighlights.PopularItem(
                        row.getString("id"), row.getString("name"),
                        row.getBigDecimal("ordered_quantity").toBigIntegerExact().toString())).list();

        return new HomeHighlights(newest, mostOrdered);
    }
}
