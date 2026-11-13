package vn.codegym.smartcafe.news;

import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class NewsService {
    // Only the News uploader's namespace may cross the News read/write boundary.
    static final String NEWS_MEDIA_PREDICATE = """
            m.deleted_at IS NULL AND m.provider='FIREBASE'
            AND m.provider_asset_id REGEXP '^news/[a-f0-9-]{36}[.](png|jpg)$'
            AND m.mime_type IN ('image/png','image/jpeg')
            """;
    private static final Set<String> ALLOWED_STATUSES = Set.of("DRAFT", "PUBLISHED", "ARCHIVED");
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    private static final Map<String, String> SORT_ALLOWLIST = Map.of(
            "publishedAt,desc", "p.published_at DESC, p.id DESC",
            "publishedAt,asc", "p.published_at ASC, p.id ASC",
            "createdAt,desc", "p.created_at DESC, p.id DESC",
            "createdAt,asc", "p.created_at ASC, p.id ASC",
            "updatedAt,desc", "p.updated_at DESC, p.id DESC",
            "updatedAt,asc", "p.updated_at ASC, p.id ASC",
            "id,desc", "p.id DESC",
            "id,asc", "p.id ASC"
    );

    private final JdbcClient jdbc;
    private final ApplicationEventPublisher events;
    private final NewsReaderAccess readers;

    public NewsService(JdbcClient jdbc, ApplicationEventPublisher events, NewsReaderAccess readers) {
        this.jdbc = jdbc;
        this.events = events;
        this.readers = readers;
    }

    @Transactional(readOnly = true)
    public NewsPageResponse listNews(int page, int size, String sortParam, String statusFilter, String username) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page phải >= 0; size phải từ 1 đến 100.");
        }
        int pageIndex = page;
        int pageSize = size;

        String sortKey = (sortParam == null || sortParam.isBlank()) ? "publishedAt,desc" : sortParam.strip();
        String sortClause = SORT_ALLOWLIST.get(sortKey);
        if (sortClause == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Sort không hợp lệ. Giá trị cho phép: " + String.join(", ", SORT_ALLOWLIST.keySet()));
        }

        String filter = null;
        if (statusFilter != null && !statusFilter.isBlank()) {
            filter = statusFilter.strip().toUpperCase(Locale.ROOT);
            if (!ALLOWED_STATUSES.contains(filter)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Status lọc không hợp lệ. Giá trị cho phép: DRAFT, PUBLISHED, ARCHIVED");
            }
        }

        String access = readers.predicate(username);
        String conditions = "WHERE 1=1" + access
                + (filter != null ? " AND p.status = ?" : " AND p.status <> 'ARCHIVED'");
        String countSql = "SELECT COUNT(*) FROM news_posts p " + conditions;

        var countSpec = jdbc.sql(countSql);
        if (filter != null) {
            countSpec = countSpec.param(filter);
        }
        long totalElements = countSpec.query(Long.class).optional().orElse(0L);

        if (totalElements == 0) {
            return new NewsPageResponse(List.of(), pageIndex, pageSize, 0, 0, true);
        }

        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        long offset = (long) pageIndex * pageSize;

        String selectSql = """
                SELECT p.id, p.slug, p.title, p.summary, p.status, p.published_at, p.created_at, p.updated_at,
                       p.audience_staff, p.audience_customer,
                       u.full_name AS author_name,
                       (SELECT CONCAT('/api/v1/news/images/',m.id) FROM news_images ni
                        JOIN media_assets m ON m.id = ni.media_id AND %s
                        WHERE ni.news_id = p.id
                        ORDER BY ni.sort_order ASC LIMIT 1) AS thumbnail_url
                FROM news_posts p
                JOIN users u ON u.id = p.author_user_id
                """
                .formatted(NEWS_MEDIA_PREDICATE)
                + conditions + "\n"
                + "ORDER BY " + sortClause + "\n"
                + "LIMIT ? OFFSET ?";

        var selectSpec = jdbc.sql(selectSql);
        if (filter != null) {
            selectSpec = selectSpec.param(filter);
        }
        selectSpec = selectSpec.param(pageSize).param(offset);

        List<NewsSummaryResponse> content = selectSpec.query((row, index) -> new NewsSummaryResponse(
                row.getString("id"),
                row.getString("slug"),
                row.getString("title"),
                row.getString("summary"),
                row.getString("status"),
                formatTimestamp(row.getObject("published_at", LocalDateTime.class)),
                formatTimestamp(row.getObject("created_at", LocalDateTime.class)),
                formatTimestamp(row.getObject("updated_at", LocalDateTime.class)),
                row.getString("author_name"),
                row.getString("thumbnail_url"),
                new Audiences(row.getBoolean("audience_staff"), row.getBoolean("audience_customer")).values()
        )).list();

        boolean last = ((long) pageIndex + 1) >= totalPages;
        return new NewsPageResponse(content, pageIndex, pageSize, totalElements, totalPages, last);
    }

    @Transactional(readOnly = true)
    public NewsDetailResponse getNewsById(long id, String username) {
        return readNews(id, readers.predicate(username));
    }

    private NewsDetailResponse readNews(long id, String access) {
        var post = jdbc.sql("""
                SELECT p.id, p.slug, p.title, p.summary, p.body, p.status,
                       p.author_user_id, u.full_name AS author_name,
                       p.updated_by_user_id,
                       p.published_at, p.created_at, p.updated_at, p.version,
                       p.audience_staff, p.audience_customer
                FROM news_posts p
                JOIN users u ON u.id = p.author_user_id
                WHERE p.id = ?
                """ + access)
                .param(id)
                .query((row, index) -> new NewsDetailRecord(
                        row.getString("id"),
                        row.getString("slug"),
                        row.getString("title"),
                        row.getString("summary"),
                        row.getString("body"),
                        row.getString("status"),
                        row.getString("author_user_id"),
                        row.getString("author_name"),
                        row.getString("updated_by_user_id"),
                        formatTimestamp(row.getObject("published_at", LocalDateTime.class)),
                        formatTimestamp(row.getObject("created_at", LocalDateTime.class)),
                        formatTimestamp(row.getObject("updated_at", LocalDateTime.class)),
                        row.getLong("version"),
                        new Audiences(row.getBoolean("audience_staff"), row.getBoolean("audience_customer"))
                )).optional().orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài viết tin tức với ID: " + id));

        List<NewsImageResponse> images = jdbc.sql("""
                SELECT ni.media_id, ni.sort_order, CONCAT('/api/v1/news/images/',m.id) AS secure_url
                FROM news_images ni
                JOIN media_assets m ON m.id = ni.media_id AND %s
                WHERE ni.news_id = ?
                ORDER BY ni.sort_order ASC
                """.formatted(NEWS_MEDIA_PREDICATE))
                .param(id)
                .query((row, index) -> new NewsImageResponse(
                        row.getString("media_id"),
                        row.getString("secure_url"),
                        row.getInt("sort_order")
                )).list();

        return new NewsDetailResponse(
                post.id(), post.slug(), post.title(), post.summary(), post.body(),
                post.status(), post.authorId(), post.authorName(), post.updatedByUserId(),
                post.publishedAt(), post.createdAt(), post.updatedAt(), post.version(),
                images, post.audiences().values()
        );
    }

    @Transactional
    public NewsDetailResponse createNews(CreateNewsRequest request, String username) {
        validateContent(request.title(), request.summary(), request.body());
        long authorId = getUserId(username);
        var audiences = parseAudiences(request.audiences());

        String status = (request.status() == null || request.status().isBlank())
                ? "DRAFT"
                : request.status().strip().toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Trạng thái không hợp lệ. Giá trị cho phép: DRAFT, PUBLISHED, ARCHIVED");
        }

        String slug = resolveUniqueSlug(request.title(), request.slug(), null);

        LocalDateTime publishedAt = null;
        if ("PUBLISHED".equals(status)) {
            publishedAt = LocalDateTime.now(ZoneOffset.UTC);
        }

        List<Long> mediaIds = parseMediaIds(request.imageMediaIds());
        validateMediaAssets(mediaIds);

        long newsId = insertPost(request, slug, status, authorId, publishedAt, audiences);

        if (mediaIds != null && !mediaIds.isEmpty()) {
            int sortOrder = 0;
            for (Long mediaId : mediaIds) {
                jdbc.sql("""
                        INSERT INTO news_images (news_id, media_id, sort_order)
                        VALUES (?, ?, ?)
                        """)
                        .param(newsId)
                        .param(mediaId)
                        .param(sortOrder++)
                        .update();
            }
        }

        events.publishEvent(new NewsChanged(String.valueOf(newsId), "CREATED", 0,
                audiences.staff() && "PUBLISHED".equals(status)));
        return readNews(newsId, "");
    }

    @Transactional
    public NewsDetailResponse updateNews(long id, UpdateNewsRequest request, String username, long expectedVersion) {
        validateContent(request.title(), request.summary(), request.body());
        long updaterId = getUserId(username);

        var existing = lockPost(id, expectedVersion);
        var audiences = request.audiences() == null ? existing.audiences() : parseAudiences(request.audiences());

        String targetStatus = (request.status() == null || request.status().isBlank())
                ? existing.status()
                : request.status().strip().toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(targetStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Trạng thái không hợp lệ. Giá trị cho phép: DRAFT, PUBLISHED, ARCHIVED");
        }

        String slug = (request.slug() != null && !request.slug().isBlank())
                ? resolveUniqueSlug(request.title(), request.slug(), id)
                : existing.slug();

        LocalDateTime publishedAt;
        if ("PUBLISHED".equals(targetStatus)) {
            publishedAt = existing.publishedAt() != null ? existing.publishedAt() : LocalDateTime.now(ZoneOffset.UTC);
        } else if ("DRAFT".equals(targetStatus)) {
            publishedAt = null;
        } else {
            publishedAt = existing.publishedAt();
        }

        List<Long> mediaIds = parseMediaIds(request.imageMediaIds());
        if (mediaIds != null) {
            validateMediaAssets(mediaIds);
        }

        int updatedRows = jdbc.sql("""
                UPDATE news_posts
                SET slug = ?, title = ?, summary = ?, body = ?, status = ?,
                    updated_by_user_id = ?, published_at = ?, version = version + 1,
                    updated_at = UTC_TIMESTAMP(6), audience_staff = ?, audience_customer = ?
                WHERE id = ?
                """)
                .param(slug)
                .param(request.title().strip())
                .param(request.summary() != null ? request.summary().strip() : null)
                .param(request.body().strip())
                .param(targetStatus)
                .param(updaterId)
                .param(publishedAt)
                .param(audiences.staff()).param(audiences.customer())
                .param(id)
                .update();

        if (updatedRows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài viết tin tức với ID: " + id);
        }

        if (mediaIds != null) {
            jdbc.sql("DELETE FROM news_images WHERE news_id = ?").param(id).update();
            int sortOrder = 0;
            for (Long mediaId : mediaIds) {
                jdbc.sql("""
                        INSERT INTO news_images (news_id, media_id, sort_order)
                        VALUES (?, ?, ?)
                        """)
                        .param(id)
                        .param(mediaId)
                        .param(sortOrder++)
                        .update();
            }
        }

        events.publishEvent(new NewsChanged(String.valueOf(id), "UPDATED", existing.version() + 1,
                (existing.audiences().staff() && "PUBLISHED".equals(existing.status()))
                        || (audiences.staff() && "PUBLISHED".equals(targetStatus))));
        return readNews(id, "");
    }

    @Transactional
    public void deleteNews(long id, String username, long expectedVersion) {
        long updaterId = getUserId(username);
        var existing = lockPost(id, expectedVersion);
        if ("ARCHIVED".equals(existing.status())) return;
        // Reversible removal from the active list; retain the article and shared image links.
        jdbc.sql("""
                UPDATE news_posts SET status='ARCHIVED', updated_by_user_id=?,
                    version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE id=?
                """).param(updaterId).param(id).update();
        events.publishEvent(new NewsChanged(String.valueOf(id), "ARCHIVED", existing.version() + 1,
                existing.audiences().staff() && "PUBLISHED".equals(existing.status())));
    }

    private ExistingPost lockPost(long id, long expectedVersion) {
        var post = jdbc.sql("SELECT id, slug, status, published_at, version, audience_staff, audience_customer FROM news_posts WHERE id=? FOR UPDATE")
                .param(id).query((row, index) -> new ExistingPost(row.getLong("id"), row.getString("slug"),
                        row.getString("status"), row.getObject("published_at", LocalDateTime.class), row.getLong("version"),
                        new Audiences(row.getBoolean("audience_staff"), row.getBoolean("audience_customer"))))
                .optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy bài viết tin tức với ID: " + id));
        if (post.version() != expectedVersion) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                    "Tin tức đã được thay đổi. Hãy tải lại trước khi sửa hoặc xóa.");
        }
        return post;
    }

    private long insertPost(CreateNewsRequest request, String slug, String status, long authorId,
                            LocalDateTime publishedAt, Audiences audiences) {
        boolean autoSlug = request.slug() == null || request.slug().isBlank();
        for (int attempt = 0; ; attempt++) {
            var keyHolder = new GeneratedKeyHolder();
            try {
                jdbc.sql("""
                        INSERT INTO news_posts (slug, title, summary, body, status, author_user_id,
                            published_at, created_at, updated_at, audience_staff, audience_customer)
                        VALUES (?, ?, ?, ?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), ?, ?)
                        """)
                        .param(slug).param(request.title().strip())
                        .param(request.summary() != null ? request.summary().strip() : null)
                        .param(request.body().strip()).param(status).param(authorId).param(publishedAt)
                        .param(audiences.staff()).param(audiences.customer())
                        .update(keyHolder, "id");
                Number key = keyHolder.getKey();
                if (key == null) throw new IllegalStateException("Missing generated news ID");
                return key.longValue();
            } catch (DuplicateKeyException conflict) {
                if (!autoSlug || attempt >= 3 || !String.valueOf(conflict.getMostSpecificCause().getMessage())
                        .contains("uq_news_slug")) throw conflict;
                // The UNIQUE index arbitrates races. A new suffix works even with a repeatable-read snapshot.
                slug = slugify(request.title()) + "-" + UUID.randomUUID().toString().substring(0, 8);
            }
        }
    }

    private void validateContent(String title, String summary, String body) {
        if (title == null || title.strip().isEmpty()) {
            throw new IllegalArgumentException("Tiêu đề tin tức không được để trống.");
        }
        if (title.strip().codePointCount(0, title.strip().length()) > 200) {
            throw new IllegalArgumentException("Tiêu đề tin tức tối đa 200 ký tự.");
        }
        if (summary != null && summary.strip().codePointCount(0, summary.strip().length()) > 500) {
            throw new IllegalArgumentException("Tóm tắt tin tức tối đa 500 ký tự.");
        }
        if (body == null || body.strip().isEmpty()) {
            throw new IllegalArgumentException("Nội dung tin tức không được để trống.");
        }
    }

    private void validateMediaAssets(List<Long> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) return;

        if (mediaIds.size() > 20) {
            throw new IllegalArgumentException("Mỗi bài viết đính kèm tối đa 20 ảnh.");
        }

        Set<Long> unique = new HashSet<>();
        for (Long mediaId : mediaIds) {
            if (mediaId == null || mediaId <= 0) {
                throw new IllegalArgumentException("mediaId không hợp lệ: " + mediaId);
            }
            if (!unique.add(mediaId)) {
                throw new IllegalArgumentException("Danh sách ảnh không được chứa mediaId trùng lặp: " + mediaId);
            }
            boolean exists = jdbc.sql("""
                    SELECT m.id FROM media_assets m WHERE m.id = ? AND %s FOR SHARE
                    """.formatted(NEWS_MEDIA_PREDICATE))
                    .param(mediaId)
                    .query(Long.class).optional().isPresent();
            if (!exists) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Chỉ được gắn ảnh PNG/JPEG còn hiệu lực do News tải lên: ID " + mediaId);
            }
        }
    }

    private List<Long> parseMediaIds(List<String> ids) {
        if (ids == null) return null;
        if (ids.size() > 20) throw new IllegalArgumentException("Mỗi bài viết đính kèm tối đa 20 ảnh.");
        return ids.stream().map(id -> {
            if (id == null || !id.matches("[1-9][0-9]*")) {
                throw new IllegalArgumentException("mediaId phải là số nguyên dương hợp lệ.");
            }
            try { return Long.parseLong(id); }
            catch (NumberFormatException invalid) { throw new IllegalArgumentException("mediaId vượt giới hạn hợp lệ."); }
        }).toList();
    }

    private String resolveUniqueSlug(String title, String providedSlug, Long currentId) {
        if (providedSlug != null && !providedSlug.isBlank()) {
            String slug = providedSlug.strip().toLowerCase(Locale.ROOT);
            if (!SLUG_PATTERN.matcher(slug).matches() || slug.length() > 180) {
                throw new IllegalArgumentException("Slug không hợp lệ (chỉ chấp nhận chữ không dấu, số, dấu gạch ngang, tối đa 180 ký tự).");
            }
            int conflict = checkSlugConflict(slug, currentId);
            if (conflict > 0) {
                var failure = new ResponseStatusException(HttpStatus.CONFLICT, "Đường dẫn bài viết đã được sử dụng.");
                failure.getBody().setProperty("code", "NEWS_SLUG_TAKEN");
                throw failure;
            }
            return slug;
        }

        String base = slugify(title);
        String candidate = base;
        int counter = 1;
        while (checkSlugConflict(candidate, currentId) > 0) {
            String suffix = "-" + counter;
            if (base.length() + suffix.length() > 180) {
                base = base.substring(0, 180 - suffix.length());
            }
            candidate = base + suffix;
            counter++;
            if (counter > 100) {
                candidate = base.substring(0, Math.min(base.length(), 160)) + "-" + UUID.randomUUID().toString().substring(0, 8);
                break;
            }
        }
        return candidate;
    }

    private int checkSlugConflict(String slug, Long currentId) {
        if (currentId == null) {
            return jdbc.sql("SELECT COUNT(*) FROM news_posts WHERE slug = ?")
                    .param(slug)
                    .query(Integer.class)
                    .optional().orElse(0);
        } else {
            return jdbc.sql("SELECT COUNT(*) FROM news_posts WHERE slug = ? AND id <> ?")
                    .param(slug)
                    .param(currentId)
                    .query(Integer.class)
                    .optional().orElse(0);
        }
    }

    public static String slugify(String input) {
        if (input == null || input.isBlank()) return "news-" + System.currentTimeMillis();
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String withoutAccents = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd').replace('Đ', 'd');
        String slug = withoutAccents.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.isBlank()) slug = "news";
        if (slug.length() > 160) slug = slug.substring(0, 160).replaceAll("-+$", "");
        return slug;
    }

    private long getUserId(String username) {
        return jdbc.sql("SELECT id FROM users WHERE username = ? AND status = 'ACTIVE' AND role='ADMIN'")
                .param(username)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tài khoản không hợp lệ hoặc đã bị khóa."));
    }

    private String formatTimestamp(LocalDateTime ts) {
        return ts != null ? ts.toInstant(ZoneOffset.UTC).toString() : null;
    }

    private record NewsDetailRecord(
            String id, String slug, String title, String summary, String body,
            String status, String authorId, String authorName, String updatedByUserId,
            String publishedAt, String createdAt, String updatedAt, long version, Audiences audiences
    ) {}

    private record ExistingPost(long id, String slug, String status, LocalDateTime publishedAt, long version,
                                Audiences audiences) {}

    private Audiences parseAudiences(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Phải chọn ít nhất một đối tượng nhận tin: STAFF hoặc CUSTOMER.");
        }
        Set<String> unique = new HashSet<>();
        for (String value : values) {
            if (value == null || !Set.of("STAFF", "CUSTOMER").contains(value) || !unique.add(value)) {
                throw new IllegalArgumentException("Đối tượng nhận tin không hợp lệ hoặc bị trùng.");
            }
        }
        return new Audiences(unique.contains("STAFF"), unique.contains("CUSTOMER"));
    }

    private record Audiences(boolean staff, boolean customer) {
        List<String> values() {
            var values = new ArrayList<String>();
            if (staff) values.add("STAFF");
            if (customer) values.add("CUSTOMER");
            return List.copyOf(values);
        }
    }
}
