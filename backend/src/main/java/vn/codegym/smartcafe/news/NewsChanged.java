package vn.codegym.smartcafe.news;

public record NewsChanged(String id, String type, long version, boolean staffFeedChanged) {}
