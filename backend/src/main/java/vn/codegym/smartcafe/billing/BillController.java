package vn.codegym.smartcafe.billing;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BillController {
    private final BillReader reader;

    public BillController(BillReader reader) {
        this.reader = reader;
    }

    @GetMapping("/api/v1/bills/{id}")
    ResponseEntity<BillSnapshot> detail(@PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reader.get(id));
    }
}
