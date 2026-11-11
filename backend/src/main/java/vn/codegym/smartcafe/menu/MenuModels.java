package vn.codegym.smartcafe.menu;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;

final class MenuModels {
    private MenuModels() { }

    static String normalizeCode(String value) {
        return value == null ? null : value.strip().toUpperCase(Locale.ROOT);
    }

    record Category(String code, String name, long version, long itemCount) { }
    record Item(String code, String name, BigDecimal price, String categoryCode,
            String categoryName, Long imageMediaId, String imageUrl, boolean available, long version) { }
    record Page<T>(List<T> content, long totalElements, int page, int size) { }
    record CategoryInput(
            @NotBlank @Pattern(regexp = "[A-Z0-9_-]{1,32}") String code,
            @NotBlank @Size(max = 120) String name,
            @NotNull @PositiveOrZero Long version) {
        CategoryInput { code = normalizeCode(code); name = name == null ? null : name.strip(); }
        @JsonAnySetter void rejectUnknown(String property, Object value) {
            throw new IllegalArgumentException("Unknown category input property");
        }
    }
    record ItemInput(
            @NotBlank @Pattern(regexp = "[A-Z0-9_-]{4,40}") String code,
            @NotBlank @Size(min = 6, max = 160) String name,
            @NotNull @DecimalMin(value = "5000", inclusive = false) @Digits(integer = 18, fraction = 0) BigDecimal price,
            @NotBlank @Pattern(regexp = "[A-Z0-9_-]{1,32}") String categoryCode,
            @Positive Long imageMediaId,
            @NotNull @PositiveOrZero Long version,
            Boolean available) {
        ItemInput {
            code = normalizeCode(code);
            categoryCode = normalizeCode(categoryCode);
            name = name == null ? null : name.strip();
            available = available == null ? Boolean.TRUE : available;
        }
        @JsonAnySetter void rejectUnknown(String property, Object value) {
            throw new IllegalArgumentException("Unknown item input property");
        }
    }
}
