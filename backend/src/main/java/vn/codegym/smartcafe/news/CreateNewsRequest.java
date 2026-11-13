package vn.codegym.smartcafe.news;

import java.util.List;

public record CreateNewsRequest(
        String title,
        String summary,
        String body,
        String slug,
        String status,
        List<String> imageMediaIds,
        List<String> audiences
) {}
