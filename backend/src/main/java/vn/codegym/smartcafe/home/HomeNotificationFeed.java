package vn.codegym.smartcafe.home;

import java.util.List;

record HomeNotificationFeed(String recipientId, List<Notice> items) {
    record Notice(String id, String type, String tableId, String tableName, String message, String createdAt) {}
}
