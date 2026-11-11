package vn.codegym.smartcafe.income;

import java.time.LocalDate;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class IncomeController {
    private final IncomeService service;
    public IncomeController(IncomeService service) { this.service = service; }
    @GetMapping("/api/v1/income")
    ResponseEntity<IncomeService.Report> income(@RequestParam(required=false) String period,
            @RequestParam(required=false) LocalDate from, @RequestParam(required=false) LocalDate to) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.report(service.window(period, from, to)));
    }
}
