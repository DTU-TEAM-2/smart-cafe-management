package vn.codegym.smartcafe.news;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.security.Principal;

@RestController
@RequestMapping("/api/v1/news")
public class NewsController {
    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping
    public NewsPageResponse listNews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String status,
            Principal principal
    ) {
        return newsService.listNews(page, size, sort, status, principal.getName());
    }

    @GetMapping("/{id:[0-9]+}")
    public ResponseEntity<NewsDetailResponse> getNewsById(@PathVariable long id, Principal principal) {
        var post = newsService.getNewsById(id, principal.getName());
        return ResponseEntity.ok().eTag("\"" + post.version() + "\"").body(post);
    }

    @PostMapping
    public ResponseEntity<NewsDetailResponse> createNews(
            @RequestBody CreateNewsRequest request,
            Principal principal
    ) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cần đăng nhập để thực hiện thao tác này.");
        }
        var created = newsService.createNews(request, principal.getName());
        URI location = URI.create("/api/v1/news/" + created.id());
        return ResponseEntity.created(location).eTag("\"" + created.version() + "\"").body(created);
    }

    @PutMapping("/{id:[0-9]+}")
    public ResponseEntity<NewsDetailResponse> updateNews(
            @PathVariable long id,
            @RequestBody UpdateNewsRequest request,
            Principal principal,
            @RequestHeader(value = "If-Match", required = false) String ifMatch
    ) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cần đăng nhập để thực hiện thao tác này.");
        }
        var updated = newsService.updateNews(id, request, principal.getName(), expectedVersion(ifMatch));
        return ResponseEntity.ok().eTag("\"" + updated.version() + "\"").body(updated);
    }

    @DeleteMapping("/{id:[0-9]+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNews(
            @PathVariable long id,
            Principal principal,
            @RequestHeader(value = "If-Match", required = false) String ifMatch
    ) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cần đăng nhập để thực hiện thao tác này.");
        }
        newsService.deleteNews(id, principal.getName(), expectedVersion(ifMatch));
    }

    private long expectedVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                    "Cần gửi If-Match lấy từ ETag của tin tức hiện tại.");
        }
        if (!ifMatch.matches("\"[0-9]+\"")) {
            throw new IllegalArgumentException("If-Match phải chứa một ETag phiên bản hợp lệ.");
        }
        try {
            return Long.parseLong(ifMatch.substring(1, ifMatch.length() - 1));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("If-Match vượt giới hạn phiên bản hợp lệ.");
        }
    }
}
