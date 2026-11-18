package vn.codegym.smartcafe.customersession;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/tables/qr")
class CustomerTableController {
    private final CustomerTableService service;

    CustomerTableController(CustomerTableService service) {
        this.service = service;
    }

    @GetMapping("/{qrPublicId}")
    CustomerTableService.TableContext resolve(@PathVariable String qrPublicId) {
        return service.resolve(qrPublicId);
    }
}
