package vn.codegym.smartcafe.billing;

import java.time.LocalDate;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class BillController {
    private final BillQueryService queries;
    private final BillReader reader;
    public BillController(BillQueryService queries,BillReader reader) { this.queries=queries; this.reader=reader; }

    @GetMapping("/api/v1/bills")
    ResponseEntity<BillQueryService.Page> list(@RequestParam(required=false) String code,
            @RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,
            @RequestParam(required=false) String status,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="10") int size,@RequestParam(defaultValue="createdAt,desc") String sort) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(queries.list(code,from,to,status,page,size,sort));
    }
    @GetMapping("/api/v1/bills/{id}")
    ResponseEntity<BillSnapshot> detail(@PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reader.get(id));
    }
    @GetMapping("/api/v1/statistics/revenue")
    ResponseEntity<BillQueryService.Statistics> statistics(@RequestParam(required=false) String period,
            @RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(queries.statistics(period,from,to));
    }
}
