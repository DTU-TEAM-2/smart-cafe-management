package vn.codegym.smartcafe.income;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IncomeService {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final JdbcClient jdbc;
    private final Clock clock;
    public IncomeService(JdbcClient jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }
    public record Window(LocalDate from, LocalDate to) {
        public Timestamp start() { return Timestamp.from(from.atStartOfDay(BUSINESS_ZONE).toInstant()); }
        public Timestamp endExclusive() { return Timestamp.from(to.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant()); }
    }
    public record Day(LocalDate date, String revenue, long billCount) {}
    public record Report(LocalDate from, LocalDate to, String timezone, String totalAmount,
            long billCount, String averageBillAmount, List<Day> dailyRevenue) {}

    public Window window(String period, LocalDate from, LocalDate to) {
        var today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        if (from != null || to != null) {
            if (from == null || to == null || from.getYear()<1001 || to.getYear()>9998
                    || from.isAfter(to) || to.isAfter(from.plusYears(1).minusDays(1)))
                throw bad("Provide from <= to within one year");
            if (period != null) throw bad("Choose period or from/to, not both");
            return new Window(from, to);
        }
        return switch (period == null ? "today" : period) {
            case "today" -> new Window(today, today);
            case "in_week" -> { var monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); yield new Window(monday, monday.plusDays(6)); }
            case "in_month" -> new Window(today.withDayOfMonth(1), today.with(TemporalAdjusters.lastDayOfMonth()));
            case "in_year" -> new Window(today.withDayOfYear(1), today.with(TemporalAdjusters.lastDayOfYear()));
            default -> throw bad("Unsupported period");
        };
    }

    @Transactional(readOnly = true)
    public Report report(Window window) {
        var rows = jdbc.sql("""
                SELECT DATE(DATE_ADD(paid_at,INTERVAL 7 HOUR)) business_date,
                    SUM(total_amount) revenue,COUNT(*) bill_count FROM bills
                WHERE status='PAID' AND paid_at>=? AND paid_at<?
                GROUP BY business_date ORDER BY business_date
                """).param(window.start()).param(window.endExclusive()).query((r, n) ->
                        new Day(r.getDate("business_date").toLocalDate(), r.getBigDecimal("revenue").toPlainString(), r.getLong("bill_count"))).list();
        var index = new java.util.HashMap<LocalDate, Day>();
        rows.forEach(d -> index.put(d.date(), d));
        var days = new ArrayList<Day>();
        for (var date = window.from(); !date.isAfter(window.to()); date = date.plusDays(1))
            days.add(index.getOrDefault(date, new Day(date, "0", 0)));
        var total = rows.stream().map(d -> new BigDecimal(d.revenue())).reduce(BigDecimal.ZERO, BigDecimal::add);
        long count = rows.stream().mapToLong(Day::billCount).sum();
        return new Report(window.from(), window.to(), BUSINESS_ZONE.getId(), total.toPlainString(), count,
                count == 0 ? "0" : total.divide(BigDecimal.valueOf(count), 0, RoundingMode.HALF_UP).toPlainString(), days);
    }
    private static ResponseStatusException bad(String reason) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason); }
}
