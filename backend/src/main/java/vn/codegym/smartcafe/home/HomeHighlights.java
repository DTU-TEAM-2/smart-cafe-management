package vn.codegym.smartcafe.home;

import java.util.List;

record HomeHighlights(List<NewestItem> newest, List<PopularItem> mostOrdered) {
    record NewestItem(String id, String name, String price) {}
    record PopularItem(String id, String name, String orderedQuantity) {}
}
