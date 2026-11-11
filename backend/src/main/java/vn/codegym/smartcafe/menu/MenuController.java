package vn.codegym.smartcafe.menu;

import java.net.URI;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static vn.codegym.smartcafe.menu.MenuModels.*;

@RestController
@RequestMapping("/api/v1/menu")
@Validated
class MenuController {
    private final MenuCatalog catalog;
    private final MenuImages images;
    MenuController(MenuCatalog catalog, MenuImages images) { this.catalog = catalog; this.images = images; }

    @GetMapping("/csrf") CsrfToken csrf(CsrfToken token) { return token; }
    @GetMapping("/categories") Page<Category> categories(
            @RequestParam(defaultValue = "") @Size(max = 160) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) { return catalog.categories(keyword, page, size); }
    @GetMapping("/categories/{code}") Category category(@PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{1,32}") String code) { return catalog.category(code); }
    @PostMapping("/categories") ResponseEntity<Category> createCategory(@Valid @RequestBody CategoryInput input) {
        var result = catalog.createCategory(input);
        return ResponseEntity.created(URI.create("/api/v1/menu/categories/" + result.code())).body(result);
    }
    @PutMapping("/categories/{code}") Category updateCategory(@PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{1,32}") String code, @Valid @RequestBody CategoryInput input) { return catalog.updateCategory(code, input); }
    @DeleteMapping("/categories/{code}") ResponseEntity<Void> deleteCategory(@PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{1,32}") String code, @RequestParam @Min(0) long version) {
        catalog.deleteCategory(code, version); return ResponseEntity.noContent().build();
    }
    @GetMapping("/items") Page<Item> items(
            @RequestParam(defaultValue = "name") @Pattern(regexp = "code|name|price|category") String field,
            @RequestParam(defaultValue = "") @Size(max = 160) String keyword,
            @RequestParam(required = false) @Pattern(regexp = "[A-Za-z0-9_-]{1,32}") String categoryCode,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) { return catalog.items(field, keyword, categoryCode, page, size); }
    @GetMapping("/items/{code}") Item item(@PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{4,40}") String code) { return catalog.item(code); }
    @PostMapping("/items") ResponseEntity<Item> createItem(@Valid @RequestBody ItemInput input) {
        var result = catalog.createItem(input);
        return ResponseEntity.created(URI.create("/api/v1/menu/items/" + result.code())).body(result);
    }
    @PutMapping("/items/{code}") Item updateItem(@PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{4,40}") String code, @Valid @RequestBody ItemInput input) { return catalog.updateItem(code, input); }
    @DeleteMapping("/items/{code}") ResponseEntity<Void> deleteItem(@PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{4,40}") String code, @RequestParam @Min(0) long version) {
        catalog.deleteItem(code, version); return ResponseEntity.noContent().build();
    }
    @PostMapping(value = "/images", consumes = "multipart/form-data") MenuImages.Upload upload(@RequestPart("file") MultipartFile file) { return images.upload(file); }
}
