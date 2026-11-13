package vn.codegym.smartcafe.customerfeedback;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounds work before CSRF or MVC can parse a submission body. */
public final class FeedbackAdmissionFilter extends OncePerRequestFilter {
    private static final int MAX_TRACKED_SOURCES = 4096;
    private final RequestMatcher submission;
    private final Clock clock;
    private final int perSource;
    private final int totalLimit;
    private final Semaphore multipartSlots;
    private final Map<String, Integer> sources = new HashMap<>();
    private long minute = Long.MIN_VALUE;
    private int attempts;

    public FeedbackAdmissionFilter(RequestMatcher submission, Clock clock, int perSource, int totalLimit, int multipartLimit) {
        if (perSource < 1 || totalLimit < perSource || multipartLimit < 1) {
            throw new IllegalArgumentException("Invalid feedback admission limits");
        }
        this.submission = submission;
        this.clock = clock;
        this.perSource = perSource;
        this.totalLimit = totalLimit;
        this.multipartSlots = new Semaphore(multipartLimit);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !submission.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long retryAfter = charge(request.getRemoteAddr());
        if (retryAfter > 0) {
            reject(response, HttpStatus.TOO_MANY_REQUESTS, retryAfter, "Bạn đã gửi quá nhiều yêu cầu phản hồi. Vui lòng thử lại sau.");
            return;
        }
        String contentType = request.getContentType();
        boolean multipart = contentType != null && contentType.regionMatches(true, 0, "multipart/", 0, 10);
        if (!multipart) {
            chain.doFilter(request, response);
            return;
        }
        if (!multipartSlots.tryAcquire()) {
            reject(response, HttpStatus.SERVICE_UNAVAILABLE, 1, "Hệ thống đang xử lý ảnh phản hồi. Vui lòng thử lại sau.");
            return;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            multipartSlots.release();
        }
    }

    private synchronized long charge(String source) {
        long second = clock.instant().getEpochSecond();
        long currentMinute = Math.floorDiv(second, 60);
        if (currentMinute > minute) {
            minute = currentMinute;
            attempts = 0;
            sources.clear();
        }
        int count = sources.getOrDefault(source, 0);
        if (attempts >= totalLimit || count >= perSource || (count == 0 && sources.size() >= MAX_TRACKED_SOURCES)) {
            return Math.max(1, (minute + 1) * 60 - second);
        }
        attempts++;
        sources.put(source, count + 1);
        return 0;
    }

    private void reject(HttpServletResponse response, HttpStatus status, long retryAfter, String detail) throws IOException {
        response.setStatus(status.value());
        response.setHeader("Retry-After", Long.toString(retryAfter));
        response.setHeader("Cache-Control", "no-store");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"type":"about:blank","title":"Tạm thời không thể gửi phản hồi","status":%d,"detail":"%s"}
                """.formatted(status.value(), detail));
    }
}
