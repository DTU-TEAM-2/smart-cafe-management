package vn.codegym.smartcafe.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BillQueryService {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final JdbcClient jdbc;
    private final Clock clock;

    public BillQueryService(JdbcClient jdbc, Optional<Clock> clock) {
        this.jdbc = jdbc;
        this.clock = clock.orElseGet(Clock::systemUTC);
    }

    public record Summary(
            String id,
            String billCode,
            String tableCode,
            String tableName,
            String creatorName,
            Instant createdAt,
            String totalAmount,
            String status
    ) {}

    public record Page(
            List<Summary> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    public record DayRevenue(
            LocalDate date,
            String revenue,
            long billCount
    ) {}

    public record ItemSales(
            String itemCode,
            String itemName,
            long quantity,
            String revenue,
            double percentage
    ) {}

    public record SummaryStatistics(
            LocalDate from,
            LocalDate to,
            String timezone,
            String totalRevenue,
            long totalBills,
            String averageBillAmount
    ) {}

    public record Statistics(
            SummaryStatistics summary,
            List<DayRevenue> dailyRevenue,
            List<ItemSales> itemSales
    ) {}

    public record Window(LocalDate from, LocalDate to) {
        public Timestamp start() {
            return Timestamp.from(from.atStartOfDay(BUSINESS_ZONE).toInstant());
        }
        public Timestamp endExclusive() {
            return Timestamp.from(to.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant());
        }
    }

    public Window calculateWindow(String period, LocalDate from, LocalDate to) {
        var today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        if (from != null || to != null) {
            if (from == null || to == null) {
                throw bad("Cần cung cấp cả ngày bắt đầu và ngày kết thúc.");
            }
            if (from.isAfter(to)) {
                throw bad("Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.");
            }
            if (to.isAfter(from.plusYears(1))) {
                throw bad("Khoảng thời gian tra cứu tối đa là 1 năm.");
            }
            if (period != null) {
                throw bad("Chỉ chọn kỳ hoặc khoảng ngày tùy chọn, không truyền cả hai.");
            }
            return new Window(from, to);
        }
        return switch (period == null ? "today" : period) {
            case "today" -> new Window(today, today);
            case "in_week" -> {
                var monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new Window(monday, monday.plusDays(6));
            }
            case "in_month" -> new Window(today.withDayOfMonth(1), today.with(TemporalAdjusters.lastDayOfMonth()));
            case "in_year" -> new Window(today.withDayOfYear(1), today.with(TemporalAdjusters.lastDayOfYear()));
            default -> throw bad("Kỳ thống kê không hợp lệ: " + period);
        };
    }

    @Transactional(readOnly = true)
    public Page list(String code, LocalDate from, LocalDate to, String status, int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw bad("page >= 0 và size từ 1 đến 100.");
        }
        if (code != null) {
            String trimmed = code.trim();
            if (trimmed.isEmpty() || trimmed.length() > 64) {
                throw bad("Mã hóa đơn tìm kiếm phải từ 1 đến 64 ký tự.");
            }
        }
        if (status != null && !List.of("PENDING", "PAID", "VOID").contains(status)) {
            throw bad("Trạng thái hóa đơn không hợp lệ: " + status);
        }
        if ((from != null || to != null) && (from == null || to == null || from.isAfter(to))) {
            throw bad("Khoảng ngày tạo không hợp lệ.");
        }

        String ordering = Map.of(
                "createdAt,desc", "created_at DESC, id DESC",
                "createdAt,asc", "created_at ASC, id ASC",
                "totalAmount,desc", "total_amount DESC, id DESC",
                "totalAmount,asc", "total_amount ASC, id ASC"
        ).get(sort);
        if (ordering == null) {
            throw bad("Thứ tự sắp xếp không được hỗ trợ: " + sort);
        }

        var params = new HashMap<String, Object>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");

        if (code != null && !code.isBlank()) {
            where.append(" AND (bill_code = :code OR bill_code LIKE :codeLike)");
            params.put("code", code.trim());
            params.put("codeLike", "%" + code.trim() + "%");
        }
        if (status != null) {
            where.append(" AND status = :status");
            params.put("status", status);
        }
        if (from != null && to != null) {
            var window = new Window(from, to);
            where.append(" AND created_at >= :start AND created_at < :end");
            params.put("start", window.start());
            params.put("end", window.endExclusive());
        }

        long count = jdbc.sql("SELECT COUNT(*) FROM bills" + where).params(params).query(Long.class).single();
        int totalPages = count == 0 ? 0 : (int) Math.ceil((double) count / size);

        params.put("limit", size);
        params.put("offset", (long) page * size);

        var rows = jdbc.sql("""
                SELECT id, bill_code, table_code_snapshot, table_name_snapshot, creator_name_snapshot,
                       created_at, total_amount, status
                FROM bills
                """ + where + " ORDER BY " + ordering + " LIMIT :limit OFFSET :offset")
                .params(params)
                .query(SUMMARY_MAPPER)
                .list();

        return new Page(rows, page, size, count, totalPages);
    }

    @Transactional(readOnly = true)
    public Statistics statistics(String period, LocalDate from, LocalDate to) {
        var window = calculateWindow(period, from, to);

        // Daily revenue from PAID bills
        var dailyRows = jdbc.sql("""
                SELECT DATE(CONVERT_TZ(paid_at, '+00:00', '+07:00')) AS business_date,
                       SUM(total_amount) AS revenue,
                       COUNT(*) AS bill_count
                FROM bills
                WHERE status = 'PAID' AND paid_at >= ? AND paid_at < ?
                GROUP BY business_date
                ORDER BY business_date
                """).param(window.start()).param(window.endExclusive())
                .query((r, n) -> new DayRevenue(
                        r.getDate("business_date").toLocalDate(),
                        r.getBigDecimal("revenue").toPlainString(),
                        r.getLong("bill_count")
                )).list();

        var dailyMap = new HashMap<LocalDate, DayRevenue>();
        for (var row : dailyRows) {
            dailyMap.put(row.date(), row);
        }

        var fullDays = new ArrayList<DayRevenue>();
        for (var date = window.from(); !date.isAfter(window.to()); date = date.plusDays(1)) {
            fullDays.add(dailyMap.getOrDefault(date, new DayRevenue(date, "0", 0)));
        }

        BigDecimal totalRevenue = dailyRows.stream()
                .map(d -> new BigDecimal(d.revenue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalBills = dailyRows.stream().mapToLong(DayRevenue::billCount).sum();

        String averageBillAmount = totalBills == 0
                ? "0"
                : totalRevenue.divide(BigDecimal.valueOf(totalBills), 0, RoundingMode.HALF_UP).toPlainString();

        var summary = new SummaryStatistics(
                window.from(),
                window.to(),
                BUSINESS_ZONE.getId(),
                totalRevenue.toPlainString(),
                totalBills,
                averageBillAmount
        );

        // Item sales breakdown for pie chart / breakdown table
        var rawItemRows = jdbc.sql("""
                SELECT bi.item_code, bi.item_name,
                       SUM(bi.quantity) AS total_qty,
                       SUM(bi.line_total) AS total_rev
                FROM bill_items bi
                JOIN bills b ON b.id = bi.bill_id
                WHERE b.status = 'PAID' AND b.paid_at >= ? AND b.paid_at < ?
                GROUP BY bi.item_code, bi.item_name
                ORDER BY total_rev DESC, bi.item_code
                """).param(window.start()).param(window.endExclusive())
                .query((r, n) -> new RawItem(
                        r.getString("item_code"),
                        r.getString("item_name"),
                        r.getLong("total_qty"),
                        r.getBigDecimal("total_rev")
                )).list();

        double totalRevDouble = totalRevenue.doubleValue();
        var itemSales = new ArrayList<ItemSales>();
        for (var raw : rawItemRows) {
            double pct = totalRevDouble > 0
                    ? Math.round((raw.rev.doubleValue() / totalRevDouble * 100.0) * 10.0) / 10.0
                    : 0.0;
            itemSales.add(new ItemSales(
                    raw.code,
                    raw.name,
                    raw.qty,
                    raw.rev.toPlainString(),
                    pct
            ));
        }

        return new Statistics(summary, fullDays, itemSales);
    }

    private record RawItem(String code, String name, long qty, BigDecimal rev) {}

    private static final RowMapper<Summary> SUMMARY_MAPPER = (r, n) -> new Summary(
            r.getString("id"),
            r.getString("bill_code"),
            r.getString("table_code_snapshot"),
            r.getString("table_name_snapshot"),
            r.getString("creator_name_snapshot"),
            r.getTimestamp("created_at").toInstant(),
            r.getBigDecimal("total_amount").toPlainString(),
            r.getString("status")
    );

    private static ResponseStatusException bad(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
