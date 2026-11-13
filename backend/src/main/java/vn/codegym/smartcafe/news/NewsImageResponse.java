package vn.codegym.smartcafe.news;

public record NewsImageResponse(
        String mediaId,
        String secureUrl,
        int sortOrder
) {}
