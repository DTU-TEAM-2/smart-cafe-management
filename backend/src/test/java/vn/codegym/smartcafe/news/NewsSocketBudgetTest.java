package vn.codegym.smartcafe.news;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.*;
import vn.codegym.smartcafe.config.BearerTokenAuthenticator;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NewsSocketBudgetTest {
    final BearerTokenAuthenticator authenticator = mock(BearerTokenAuthenticator.class);
    final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    final MutableClock clock = new MutableClock();
    NewsSocket socket;
    final Authentication staff = UsernamePasswordAuthenticationToken.authenticated("budget-staff", null,
            List.of(new SimpleGrantedAuthority("ROLE_STAFF")));

    @BeforeEach
    void setup() {
        socket = new NewsSocket(authenticator, meters, clock, 128, 64, 16, 4);
        when(authenticator.authenticate("Bearer staff")).thenReturn(staff);
        when(authenticator.isCurrentStaff(staff)).thenReturn(true);
    }
    @AfterEach
    void cleanup() { socket.shutdown(); meters.close(); }

    @Test
    void rejectsExcessSourceBeforeUpgradeWithoutTrustingForwardedHeaders() {
        for (int i = 0; i < 16; i++) assertThat(admit("192.0.2.1").accepted()).isTrue();
        var request = new MockHttpServletRequest("GET", "/api/v1/news/events");
        request.setRemoteAddr("192.0.2.1"); request.addHeader("X-Forwarded-For", "198.51.100.200");
        var response = new MockHttpServletResponse();
        assertThat(socket.beforeHandshake(new ServletServerHttpRequest(request), new ServletServerHttpResponse(response),
                socket, new HashMap<>())).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("10");
        assertThat(connections()).isEqualTo(16);
        assertThat(rejections("source")).isEqualTo(1);
        verifyNoInteractions(authenticator);
    }

    @Test
    void totalBudgetIncludesAuthenticatedClientsAndTransportErrorsReleaseSlots() throws Exception {
        socket.shutdown(); meters.clear();
        socket = new NewsSocket(authenticator, meters, clock, 4, 4, 4, 4);
        var sessions = new ArrayList<WebSocketSession>();
        for (int i = 0; i < 4; i++) {
            var session = establish(admit("192.0.2.1")); sessions.add(session);
            socket.handleMessage(session, new TextMessage("Bearer staff"));
        }
        assertThat(admit("192.0.2.2").accepted()).isFalse();
        assertThat(rejections("total")).isEqualTo(1);
        socket.handleTransportError(sessions.getFirst(), new java.io.IOException("test disconnect"));
        assertThat(connections()).isEqualTo(3);
        assertThat(admit("192.0.2.2").accepted()).isTrue();
    }

    @Test
    void pendingBudgetIsGlobalAndFailedHandshakeReleasesReservation() {
        for (int i = 0; i < 63; i++) assertThat(admit(source(i)).accepted()).isTrue();
        var last = admit(source(63));
        assertThat(last.accepted()).isTrue();
        assertThat(admit(source(64)).accepted()).isFalse();
        assertThat(connections()).isEqualTo(64);
        socket.afterHandshake(last.request(), new ServletServerHttpResponse(last.response()), socket,
                new java.io.IOException("failed upgrade"));
        assertThat(connections()).isEqualTo(63);
        var deniedOrigin = admit(source(64));
        assertThat(deniedOrigin.accepted()).isTrue();
        deniedOrigin.response().setStatus(403);
        socket.afterHandshake(deniedOrigin.request(), new ServletServerHttpResponse(deniedOrigin.response()), socket, null);
        assertThat(connections()).isEqualTo(63);
        verifyNoInteractions(authenticator);
    }

    @Test
    void concurrentAuthenticationIsBoundedPerAccountAndDisconnectReclaimsSlot() throws Exception {
        var sessions = new ArrayList<WebSocketSession>();
        for (int i = 0; i < 12; i++) sessions.add(establish(admit(source(i))));
        try (var pool = Executors.newFixedThreadPool(12)) {
            var jobs = sessions.stream().map(session -> pool.submit(() -> {
                socket.handleMessage(session, new TextMessage("Bearer staff")); return null;
            })).toList();
            for (var job : jobs) job.get(10, TimeUnit.SECONDS);
        }
        assertThat(connections()).isEqualTo(4);
        assertThat(meters.get("news.websocket.pending").gauge().value()).isZero();
        assertThat(rejections("account")).isEqualTo(8);
        var rejected = sessions.stream().filter(this::wasClosed).toList();
        assertThat(rejected).hasSize(8);
        for (var session : rejected) verify(session).close(CloseStatus.SERVICE_OVERLOAD);
        socket.afterConnectionClosed(sessions.stream().filter(session -> !wasClosed(session)).findFirst().orElseThrow(), CloseStatus.NORMAL);
        var replacement = establish(admit(source(20)));
        socket.handleMessage(replacement, new TextMessage("Bearer staff"));
        verify(replacement).sendMessage(any(TextMessage.class));
        assertThat(connections()).isEqualTo(4);
        clearInvocations(authenticator);
        socket.revalidateClients();
        verify(authenticator, times(4)).isCurrentStaff(staff);
        verifyNoMoreInteractions(authenticator);
    }

    @Test
    void expiryReclaimsUnfinishedUpgradesAndSilentSocketsAndLateBearerNeverQueriesDatabase() throws Exception {
        var unfinished = admit(source(1));
        var silent = establish(admit(source(2)));
        var late = establish(admit(source(3)));
        clock.advance(Duration.ofSeconds(10));
        socket.handleMessage(late, new TextMessage("Bearer staff"));
        verify(late).close(CloseStatus.POLICY_VIOLATION);
        verifyNoInteractions(authenticator);
        socket.expirePendingClients();
        verify(silent).close(CloseStatus.POLICY_VIOLATION);
        assertThat(connections()).isZero();
        var orphan = mock(WebSocketSession.class);
        when(orphan.getAttributes()).thenReturn(unfinished.attributes());
        socket.afterConnectionEstablished(orphan);
        verify(orphan).close(CloseStatus.POLICY_VIOLATION);
        assertThat(connections()).isZero();
    }

    @Test
    void twoBurstsOfOneThousandConcurrentAdmissionsStayBoundedAndRecover() throws Exception {
        for (int burst = 0; burst < 2; burst++) {
            var accepted = new ConcurrentLinkedQueue<WebSocketSession>();
            try (var pool = Executors.newFixedThreadPool(32)) {
                var jobs = IntStream.range(0, 1000).mapToObj(i -> pool.submit(() -> {
                    var admission = admit(source(i));
                    if (admission.accepted()) accepted.add(establish(admission));
                    return null;
                })).toList();
                for (var job : jobs) job.get(20, TimeUnit.SECONDS);
            }
            assertThat(accepted).hasSize(64);
            assertThat(connections()).isEqualTo(64);
            for (var session : accepted) socket.afterConnectionClosed(session, CloseStatus.NORMAL);
            assertThat(connections()).isZero();
        }
        assertThat(rejections("pending")).isEqualTo(1872);
        verifyNoInteractions(authenticator);
    }

    @Test
    void shutdownRefusesNewUpgradesAndInvalidLimitsFailClosed() {
        assertThat(admit(source(1)).accepted()).isTrue(); socket.shutdown();
        var refused = admit(source(2));
        assertThat(refused.accepted()).isFalse();
        assertThat(refused.response().getStatus()).isEqualTo(503);
        assertThat(connections()).isZero();
        assertThatIllegalArgumentException().isThrownBy(() -> new NewsSocket(authenticator, meters, clock, 0, 1, 1, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new NewsSocket(authenticator, meters, clock, 4, 5, 1, 1));
    }

    private Admission admit(String address) {
        var request = new MockHttpServletRequest("GET", "/api/v1/news/events"); request.setRemoteAddr(address);
        var servlet = new ServletServerHttpRequest(request); var response = new MockHttpServletResponse();
        var attributes = new HashMap<String, Object>();
        boolean accepted = socket.beforeHandshake(servlet, new ServletServerHttpResponse(response), socket, attributes);
        return new Admission(servlet, response, attributes, accepted);
    }
    private WebSocketSession establish(Admission admission) throws Exception {
        assertThat(admission.accepted()).isTrue();
        var session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(UUID.randomUUID().toString());
        when(session.getAttributes()).thenReturn(admission.attributes()); when(session.isOpen()).thenReturn(true);
        socket.afterConnectionEstablished(session); return session;
    }
    private boolean wasClosed(WebSocketSession session) {
        return mockingDetails(session).getInvocations().stream().anyMatch(call -> call.getMethod().getName().equals("close"));
    }
    private String source(int i) { return "192.0." + (i / 250) + "." + (i % 250 + 1); }
    private double connections() { return meters.get("news.websocket.connections").gauge().value(); }
    private double rejections(String reason) { return meters.get("news.websocket.rejected").tag("reason", reason).counter().count(); }
    private record Admission(ServletServerHttpRequest request, MockHttpServletResponse response,
            Map<String, Object> attributes, boolean accepted) {}
    private static class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2030-01-01T00:00:00Z"));
        void advance(Duration duration) { now.updateAndGet(value -> value.plus(duration)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }
}
