package vn.codegym.smartcafe.customermenu;

import java.util.List;

/** Public customer menu DTOs mapped from approved MySQL catalog tables. */
final class CustomerMenuModels {
    private CustomerMenuModels() { }

    record Category(String id, String code, String name, long activeItemCount) { }

    record Item(
            String id,
            String code,
            String name,
            String price,
            String imageUrl,
            boolean available,
            String categoryId,
            String categoryCode,
            String categoryName) { }

    record ItemPage(List<Item> content, long totalElements, int page, int size) { }
}
