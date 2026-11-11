package vn.codegym.smartcafe.billing;

import java.time.Instant;
import java.time.LocalDate;
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
    public BillQueryService(JdbcClient jdbc, IncomeService income) { this.jdbc=jdbc; this.income=income; }
    public record Summary(String id,String billCode,String tableCode,String tableName,String creatorName,
            Instant createdAt,String totalAmount,String status) {}
    public record Page(List<Summary> content,int page,int size,long totalElements) {}
    public record ItemSales(String itemCode,String itemName,String quantity,String revenue) {}
    public record Statistics(IncomeService.Report summary,List<ItemSales> itemSales) {}

    @Transactional(readOnly=true)
    public Page list(String code,LocalDate from,LocalDate to,String status,int page,int size,String sort) {
        if (page<0 || size<1 || size>100) throw bad("page >= 0 and size between 1 and 100 are required");
        if (code!=null && (code.isBlank() || code.length()>64)) throw bad("code must contain 1-64 characters");
        if (status!=null && !List.of("PENDING","PAID","VOID").contains(status)) throw bad("Unsupported bill status");
        String ordering=Map.of("createdAt,desc","created_at DESC,id DESC","createdAt,asc","created_at ASC,id ASC",
                "totalAmount,desc","total_amount DESC,id DESC","totalAmount,asc","total_amount ASC,id ASC").get(sort);
        if (ordering==null) throw bad("Unsupported sort");
        var params=new java.util.HashMap<String,Object>();
        StringBuilder where=new StringBuilder(" WHERE 1=1");
        // Exact code avoids wildcard ambiguity and has an indexed lookup.
        if(code!=null) { where.append(" AND bill_code=:code"); params.put("code",code); }
        if(status!=null) { where.append(" AND status=:status"); params.put("status",status); }
        if(from!=null || to!=null) {
            var window=income.window(null,from,to);
            where.append(" AND created_at>=:start AND created_at<:end");
            params.put("start",window.start()); params.put("end",window.endExclusive());
        }
        long count=jdbc.sql("SELECT COUNT(*) FROM bills"+where).params(params).query(Long.class).single();
        params.put("limit",size); params.put("offset",(long)page*size);
        var rows=jdbc.sql("SELECT id,bill_code,table_code_snapshot,table_name_snapshot,creator_name_snapshot,created_at,total_amount,status FROM bills"
                +where+" ORDER BY "+ordering+" LIMIT :limit OFFSET :offset").params(params).query(SUMMARY).list();
        return new Page(rows,page,size,count);
    }

    @Transactional(readOnly=true)
    public Statistics statistics(String period,LocalDate from,LocalDate to) {
        var window=income.window(period==null && from==null && to==null ? "in_month":period,from,to);
        var summary=income.report(window);
        var items=jdbc.sql("""
                SELECT bi.item_code,bi.item_name,SUM(bi.quantity) quantity,SUM(bi.line_total) revenue
                FROM bill_items bi JOIN bills b ON b.id=bi.bill_id
                WHERE b.status='PAID' AND b.paid_at>=? AND b.paid_at<?
                GROUP BY bi.item_code,bi.item_name ORDER BY revenue DESC,bi.item_code,bi.item_name
                """).param(window.start()).param(window.endExclusive()).query((r,n)->new ItemSales(
                        r.getString("item_code"),r.getString("item_name"),r.getBigDecimal("quantity").toPlainString(),
                        r.getBigDecimal("revenue").toPlainString())).list();
        return new Statistics(summary,items);
    }

    private static final RowMapper<Summary> SUMMARY=(r,n)->new Summary(r.getString("id"),r.getString("bill_code"),
            r.getString("table_code_snapshot"),r.getString("table_name_snapshot"),r.getString("creator_name_snapshot"),
            r.getTimestamp("created_at").toInstant(),r.getBigDecimal("total_amount").toPlainString(),r.getString("status"));
    private static ResponseStatusException bad(String reason) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,reason); }
}
