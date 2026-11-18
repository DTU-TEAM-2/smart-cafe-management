package vn.codegym.smartcafe.customerfeedback;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounds work before CSRF can create a session or MVC can parse a submission body. */
public final class FeedbackAdmissionFilter extends OncePerRequestFilter {
    public static final int DEFAULT_MAX_JSON_BYTES = 131072;
    private static final int MAX_TRACKED_SOURCES = 4096;
    private final RequestMatcher submission;
    private final RequestMatcher csrf;
    private final Clock clock;
    private final MinuteQuota submissionQuota;
    private final MinuteQuota csrfQuota;
    private final Semaphore multipartSlots;
    private final int maxJsonBytes;

    public FeedbackAdmissionFilter(RequestMatcher submission, RequestMatcher csrf, Clock clock,
            int perSource, int totalLimit, int multipartLimit, int csrfPerSource, int csrfTotalLimit,
            int maxJsonBytes) {
        if (multipartLimit < 1 || maxJsonBytes < 1) {
            throw new IllegalArgumentException("Invalid feedback admission limits");
        }
        this.submission = submission;
        this.csrf = csrf;
        this.clock = clock;
        this.submissionQuota = new MinuteQuota(perSource, totalLimit);
        this.csrfQuota = new MinuteQuota(csrfPerSource, csrfTotalLimit);
        this.multipartSlots = new Semaphore(multipartLimit);
        this.maxJsonBytes = maxJsonBytes;
    }

    public FeedbackAdmissionFilter(RequestMatcher submission, RequestMatcher csrf, Clock clock,
            int perSource, int totalLimit, int multipartLimit, int csrfPerSource, int csrfTotalLimit) {
        this(submission, csrf, clock, perSource, totalLimit, multipartLimit, csrfPerSource, csrfTotalLimit, DEFAULT_MAX_JSON_BYTES);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !submission.matches(request) && !csrf.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean csrfRequest = csrf.matches(request);
        MinuteQuota quota = csrfRequest ? csrfQuota : submissionQuota;
        long retryAfter = quota.charge(request.getRemoteAddr(), clock.instant().getEpochSecond());
        if (retryAfter > 0) {
            reject(response, HttpStatus.TOO_MANY_REQUESTS, retryAfter, "Bạn đã gửi quá nhiều yêu cầu phản hồi. Vui lòng thử lại sau.");
            return;
        }
        if (csrfRequest) {
            chain.doFilter(request, response);
            return;
        }
        String contentType = request.getContentType();
        boolean multipart = contentType != null && contentType.regionMatches(true, 0, "multipart/", 0, 10);
        if (multipart) {
            if (!multipartSlots.tryAcquire()) {
                reject(response, HttpStatus.SERVICE_UNAVAILABLE, 1, "Hệ thống đang xử lý ảnh phản hồi. Vui lòng thử lại sau.");
                return;
            }
            try {
                chain.doFilter(request, response);
            } finally {
                multipartSlots.release();
            }
            return;
        }

        long contentLength = request.getContentLengthLong();
        if (contentLength > maxJsonBytes) {
            reject(response, HttpStatus.PAYLOAD_TOO_LARGE, 0, "Dung lượng yêu cầu quá lớn.");
            return;
        }
        byte[] body;
        try (var in = request.getInputStream()) {
            body = in.readNBytes(maxJsonBytes + 1);
        }
        if (body.length > maxJsonBytes) {
            reject(response, HttpStatus.PAYLOAD_TOO_LARGE, 0, "Dung lượng yêu cầu quá lớn.");
            return;
        }
        chain.doFilter(new BoundedHttpServletRequest(request, body), response);
    }

    private static final class MinuteQuota {
        private final int perSource;
        private final int totalLimit;
        private final Map<String, Integer> sources = new HashMap<>();
        private long minute = Long.MIN_VALUE;
        private int attempts;

        private MinuteQuota(int perSource, int totalLimit) {
            if (perSource < 1 || totalLimit < perSource) {
                throw new IllegalArgumentException("Invalid feedback admission limits");
            }
            this.perSource = perSource;
            this.totalLimit = totalLimit;
        }

        private synchronized long charge(String source, long second) {
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
    }

    private void reject(HttpServletResponse response, HttpStatus status, long retryAfter, String detail) throws IOException {
        response.setStatus(status.value());
        if (retryAfter > 0) {
            response.setHeader("Retry-After", Long.toString(retryAfter));
        }
        response.setHeader("Cache-Control", "no-store");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String title = status == HttpStatus.PAYLOAD_TOO_LARGE
                ? "Dung lượng yêu cầu quá lớn"
                : "Tạm thời không thể gửi phản hồi";
        response.getWriter().write("""
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s"}
                """.formatted(title, status.value(), detail));
    }

    private static final class BoundedHttpServletRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        BoundedHttpServletRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            var stream = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return stream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int read() {
                    return stream.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return stream.read(b, off, len);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            Charset charset = StandardCharsets.UTF_8;
            String encoding = getCharacterEncoding();
            if (encoding != null) {
                try {
                    charset = Charset.forName(encoding);
                } catch (Exception ignored) {
                    charset = StandardCharsets.UTF_8;
                }
            }
            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), charset));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
