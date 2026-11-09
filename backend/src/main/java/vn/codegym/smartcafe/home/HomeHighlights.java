package vn.codegym.smartcafe.home;

import java.util.List;

record HomeHighlights(List<NewestItem> newest, List<PopularItem> mostOrdered) {
    record NewestItem(String id, String name, String price, String imageUrl) {}
    record PopularItem(String id, String name, String orderedQuantity, String imageUrl) {}
}
