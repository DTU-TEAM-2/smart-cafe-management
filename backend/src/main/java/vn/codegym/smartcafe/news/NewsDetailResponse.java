package vn.codegym.smartcafe.news;

import java.util.List;

public record NewsDetailResponse(
        String id,
        String slug,
        String title,
        String summary,
        String body,
        String status,
        String authorId,
        String authorName,
        String updatedByUserId,
        String publishedAt,
        String createdAt,
        String updatedAt,
        long version,
        List<NewsImageResponse> images,
        List<String> audiences
) {}
