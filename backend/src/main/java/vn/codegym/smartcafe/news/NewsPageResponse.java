package vn.codegym.smartcafe.news;

import java.util.List;

public record NewsPageResponse(
        List<NewsSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {}
