package vn.codegym.smartcafe.customermenu;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/menu")
class CustomerMenuController {
    private final CustomerMenuService service;

    CustomerMenuController(CustomerMenuService service) {
        this.service = service;
    }

    @GetMapping("/categories")
    List<CustomerMenuModels.Category> categories() {
        return service.listCategories();
    }

    @GetMapping("/items")
    CustomerMenuModels.ItemPage items(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "false") boolean availableOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.listItems(categoryId, keyword, availableOnly, page, size);
    }

    @GetMapping("/items/{itemId}")
    CustomerMenuModels.Item item(@PathVariable long itemId) {
        return service.getItem(itemId);
    }
}
