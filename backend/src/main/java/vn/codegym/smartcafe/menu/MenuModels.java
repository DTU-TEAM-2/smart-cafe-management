package vn.codegym.smartcafe.menu;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import jakarta.validation.constraints.*;

final class MenuModels {
    private MenuModels() { }

    static String normalizeCode(String value) {
        return value == null ? null : value.strip().toUpperCase(Locale.ROOT);
    }

    record Category(String code, String name, long version, long itemCount) { }
    record Item(String code, String name, BigDecimal price, String categoryCode,
            String categoryName, String imageUrl, String description, boolean available, long version) { }
    record Page<T>(List<T> content, long totalElements, int page, int size) { }
    record CategoryInput(
            @NotBlank @Pattern(regexp = "[A-Z0-9_-]{1,32}") String code,
            @NotBlank @Size(max = 120) String name,
            @NotNull @PositiveOrZero Long version) {
        CategoryInput { code = normalizeCode(code); name = name == null ? null : name.strip(); }
    }
    record ItemInput(
            @NotBlank @Pattern(regexp = "[A-Z0-9_-]{4,40}") String code,
            @NotBlank @Size(min = 6, max = 160) String name,
            @NotNull @DecimalMin(value = "5000", inclusive = false) @Digits(integer = 12, fraction = 0) BigDecimal price,
            @NotBlank @Pattern(regexp = "[A-Z0-9_-]{1,32}") String categoryCode,
            @NotBlank @Size(max = 1024) String imageUrl,
            @Size(max = 1000) String description,
            @NotNull @PositiveOrZero Long version,
            Boolean available) {
        ItemInput {
            code = normalizeCode(code);
            categoryCode = normalizeCode(categoryCode);
            name = name == null ? null : name.strip();
            description = description == null ? "" : description.strip();
            available = available == null ? Boolean.TRUE : available;
        }
    }
}
