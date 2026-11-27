package vn.codegym.smartcafe.news;

import java.util.List;

public record NewsSummaryResponse(
        String id,
        String slug,
        String title,
        String summary,
        String status,
        String publishedAt,
        String createdAt,
        String updatedAt,
        String authorName,
        String thumbnailUrl,
        List<String> audiences
) {}
