package vn.codegym.smartcafe.billing;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.codegym.smartcafe.income.IncomeService;

@Service
public class BillQueryService {
    private final JdbcClient jdbc;
    private final IncomeService income;

    public BillQueryService(JdbcClient jdbc, IncomeService income) {
        this.jdbc = jdbc;
        this.income = income;
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
            List<IncomeService.Day> dailyRevenue
    ) {}

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

        if (code != null) {
            where.append(" AND bill_code LIKE :code ESCAPE '!'");
            String literalCode = code.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_");
            params.put("code", "%" + literalCode + "%");
        }
        if (status != null) {
            where.append(" AND status = :status");
            params.put("status", status);
        }
        if (from != null || to != null) {
            var window = income.window(null, from, to);
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

    public Statistics statistics(String period, LocalDate from, LocalDate to) {
        var report = income.report(income.window(period, from, to));
        return new Statistics(
                new SummaryStatistics(report.from(), report.to(), report.timezone(),
                        report.totalAmount(), report.billCount(), report.averageBillAmount()),
                report.dailyRevenue());
    }

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
