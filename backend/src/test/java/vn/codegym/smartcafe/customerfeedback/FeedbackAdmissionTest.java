package vn.codegym.smartcafe.customerfeedback;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeedbackAdmissionTest {
    @Test
    void boundsAttemptsAcrossSourcesAndResetsAtNextMinute() throws Exception {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(Instant.ofEpochSecond(65));
        var filter = new FeedbackAdmissionFilter(request -> true, request -> false, clock, 2, 3, 1, 12, 60);

        assertThat(send(filter, "192.0.2.1", false).getStatus()).isEqualTo(200);
        assertThat(send(filter, "192.0.2.1", false).getStatus()).isEqualTo(200);
        var rejected = send(filter, "192.0.2.1", false);
        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("Retry-After")).isEqualTo("55");
        assertThat(send(filter, "192.0.2.2", false).getStatus()).isEqualTo(200);
        assertThat(send(filter, "192.0.2.3", false).getStatus()).isEqualTo(429);

        when(clock.instant()).thenReturn(Instant.ofEpochSecond(120));
        assertThat(send(filter, "192.0.2.1", false).getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsConcurrentMultipartBeforeBodyProcessingAndReleasesSlot() throws Exception {
        var filter = new FeedbackAdmissionFilter(request -> true, request -> false,
                Clock.fixed(Instant.ofEpochSecond(65), ZoneOffset.UTC), 20, 120, 1, 12, 60);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var processed = new AtomicReference<Integer>();
        try (var workers = Executors.newFixedThreadPool(9)) {
            var active = workers.submit(() -> {
                filter.doFilter(request("192.0.2.1", true), new MockHttpServletResponse(), (req, res) -> {
                    entered.countDown();
                    try {
                        if (release.await(5, TimeUnit.SECONDS)) processed.set(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                return null;
            });
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                var excess = new ArrayList<java.util.concurrent.Future<MockHttpServletResponse>>();
                for (int i = 0; i < 8; i++) {
                    excess.add(workers.submit(() -> send(filter, "192.0.2.2", true)));
                }
                for (var result : excess) {
                    var rejected = result.get(5, TimeUnit.SECONDS);
                    assertThat(rejected.getStatus()).isEqualTo(503);
                    assertThat(rejected.getHeader("Retry-After")).isEqualTo("1");
                }
                assertThat(send(filter, "192.0.2.2", false).getStatus()).isEqualTo(200);
            } finally {
                release.countDown();
            }
            active.get(5, TimeUnit.SECONDS);
            assertThat(processed.get()).isEqualTo(1);
            assertThat(send(filter, "192.0.2.2", true).getStatus()).isEqualTo(200);
        }
    }

    @Test
    void rejectsOversizedJsonViaContentLengthBeforeReadingStream() throws Exception {
        var filter = new FeedbackAdmissionFilter(request -> true, request -> false,
                Clock.fixed(Instant.ofEpochSecond(65), ZoneOffset.UTC), 20, 120, 1, 12, 60, 50);

        var request = request("192.0.2.1", false);
        request.setContent(new byte[51]);
        var response = new MockHttpServletResponse();
        var downstreamCalled = new boolean[]{false};

        filter.doFilter(request, response, (req, res) -> downstreamCalled[0] = true);

        assertThat(downstreamCalled[0]).isFalse();
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("\"status\":413");
        assertThat(response.getContentAsString()).contains("Dung lượng yêu cầu quá lớn");
    }

    @Test
    void rejectsChunkedOversizedJsonStreamAndDoesNotCallDownstream() throws Exception {
        var filter = new FeedbackAdmissionFilter(request -> true, request -> false,
                Clock.fixed(Instant.ofEpochSecond(65), ZoneOffset.UTC), 20, 120, 1, 12, 60, 50);

        var request = new MockHttpServletRequest("POST", "/api/v1/customer/feedback") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }

            @Override
            public int getContentLength() {
                return -1;
            }
        };
        request.setRemoteAddr("192.0.2.1");
        request.setContentType("application/json");
        request.setContent(new byte[51]);

        var response = new MockHttpServletResponse();
        var downstreamCalled = new boolean[]{false};

        filter.doFilter(request, response, (req, res) -> downstreamCalled[0] = true);

        assertThat(downstreamCalled[0]).isFalse();
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("\"status\":413");
        assertThat(response.getContentAsString()).contains("Dung lượng yêu cầu quá lớn");
    }

    @Test
    void allowsChunkedJsonWithinLimitAndDownstreamCanReadBody() throws Exception {
        var filter = new FeedbackAdmissionFilter(request -> true, request -> false,
                Clock.fixed(Instant.ofEpochSecond(65), ZoneOffset.UTC), 20, 120, 1, 12, 60, 50);

        byte[] payload = "{\"test\":123}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var request = new MockHttpServletRequest("POST", "/api/v1/customer/feedback") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }

            @Override
            public int getContentLength() {
                return -1;
            }
        };
        request.setRemoteAddr("192.0.2.1");
        request.setContentType("application/json");
        request.setContent(payload);

        var response = new MockHttpServletResponse();
        var readBody = new byte[payload.length];

        filter.doFilter(request, response, (req, res) -> {
            ((MockHttpServletResponse) res).setStatus(200);
            try {
                req.getInputStream().read(readBody);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(readBody).isEqualTo(payload);
    }

    @Test
    void acceptsValidJsonPayloadNear64KiBThreshold() throws Exception {
        var filter = new FeedbackAdmissionFilter(request -> true, request -> false,
                Clock.fixed(Instant.ofEpochSecond(65), ZoneOffset.UTC), 20, 120, 1, 12, 60, 131072);

        String largeContent = "x".repeat(65000);
        byte[] payload = ("{\"name\":\"Khách 64K\",\"email\":\"customer64k@example.invalid\",\"content\":\""
                + largeContent + "\",\"rating\":5}").getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(payload.length).isGreaterThan(60 * 1024).isLessThan(131072);

        var request = request("192.0.2.1", false);
        request.setContent(payload);

        var response = new MockHttpServletResponse();
        var downstreamReadBytes = new byte[payload.length];
        var downstreamCalled = new boolean[]{false};

        filter.doFilter(request, response, (req, res) -> {
            downstreamCalled[0] = true;
            ((MockHttpServletResponse) res).setStatus(200);
            try {
                req.getInputStream().read(downstreamReadBytes);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertThat(downstreamCalled[0]).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(downstreamReadBytes).isEqualTo(payload);
    }

    @Test
    void concurrentOversizedAndChunkedRequestsRejectWith413WithoutExceedingBoundedMemory() throws Exception {
        int maxLimit = 100;
        var filter = new FeedbackAdmissionFilter(request -> true, request -> false,
                Clock.fixed(Instant.ofEpochSecond(65), ZoneOffset.UTC), 50, 200, 1, 12, 60, maxLimit);

        int workersCount = 8;
        var bytesReadTracker = new java.util.concurrent.atomic.AtomicLong();
        var executor = Executors.newFixedThreadPool(workersCount);

        try {
            var tasks = new ArrayList<java.util.concurrent.Callable<MockHttpServletResponse>>();
            for (int i = 0; i < workersCount; i++) {
                final int id = i;
                tasks.add(() -> {
                    var countingStream = new java.io.InputStream() {
                        private int count = 0;
                        private final int total = 1024 * 1024; // 1 MB
                        @Override
                        public int read() {
                            if (count >= total) return -1;
                            count++;
                            bytesReadTracker.incrementAndGet();
                            return 'a';
                        }
                    };

                    var req = new MockHttpServletRequest("POST", "/api/v1/customer/feedback") {
                        @Override
                        public long getContentLengthLong() {
                            return -1; // chunked
                        }
                        @Override
                        public int getContentLength() {
                            return -1;
                        }
                        @Override
                        public jakarta.servlet.ServletInputStream getInputStream() {
                            return new jakarta.servlet.ServletInputStream() {
                                @Override public boolean isFinished() { return false; }
                                @Override public boolean isReady() { return true; }
                                @Override public void setReadListener(jakarta.servlet.ReadListener readListener) {}
                                @Override public int read() { return countingStream.read(); }
                            };
                        }
                    };
                    req.setRemoteAddr("192.0.2." + (10 + id));
                    req.setContentType("application/json");

                    var res = new MockHttpServletResponse();
                    filter.doFilter(req, res, (r, s) -> { throw new AssertionError("Downstream should not be reached"); });
                    return res;
                });
            }

            var results = executor.invokeAll(tasks, 10, TimeUnit.SECONDS);
            for (var future : results) {
                var response = future.get();
                assertThat(response.getStatus()).isEqualTo(413);
                assertThat(response.getContentAsString()).contains("\"status\":413");
                assertThat(response.getContentAsString()).contains("Dung lượng yêu cầu quá lớn");
            }

            long totalRead = bytesReadTracker.get();
            assertThat(totalRead).isEqualTo(workersCount * (maxLimit + 1L));
        } finally {
            executor.shutdownNow();
        }
    }

    private MockHttpServletResponse send(FeedbackAdmissionFilter filter, String source, boolean multipart) throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(request(source, multipart), response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
        return response;
    }

    private MockHttpServletRequest request(String source, boolean multipart) {
        var request = new MockHttpServletRequest("POST", "/api/v1/customer/feedback");
        request.setRemoteAddr(source);
        request.setContentType(multipart ? "multipart/form-data; boundary=test" : "application/json");
        return request;
    }
}
