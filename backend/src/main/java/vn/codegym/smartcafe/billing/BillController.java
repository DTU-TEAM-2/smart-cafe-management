package vn.codegym.smartcafe.billing;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class BillController {
    private final BillQueryService queryService;
    private final BillReader reader;

    public BillController(BillQueryService queryService, BillReader reader) {
        this.queryService = queryService;
        this.reader = reader;
    }

    @GetMapping("/api/v1/bills")
    public ResponseEntity<BillQueryService.Page> list(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(queryService.list(code, from, to, status, page, size, sort));
    }

    @GetMapping("/api/v1/bills/{id}")
    public ResponseEntity<BillSnapshot> detail(@PathVariable long id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(reader.get(id));
    }

    @GetMapping("/api/v1/statistics/revenue")
    public ResponseEntity<BillQueryService.Statistics> statistics(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(queryService.statistics(period, from, to));
    }
}
