package vn.codegym.smartcafe.news;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import vn.codegym.smartcafe.config.BearerTokenAuthenticator;

/** First frame carries bearer; admission counts both HTTP upgrades and established sockets. */
@Configuration
@EnableWebSocket
class NewsSocket extends TextWebSocketHandler implements WebSocketConfigurer, HandshakeInterceptor {
    private static final String ADMISSION = NewsSocket.class.getName() + ".admission";
    private final BearerTokenAuthenticator authenticator;
    private final MeterRegistry meters;
    private final Clock clock;
    private final int maxConnections, maxPending, maxPerSource, maxPerAccount;
    private final ConcurrentHashMap<String, Client> clients = new ConcurrentHashMap<>();
    private volatile boolean stopping;
    private final ScheduledExecutorService expiry = Executors.newSingleThreadScheduledExecutor(task -> {
        var thread = new Thread(task, "news-socket-expiry");
        thread.setDaemon(true);
        return thread;
    });

    NewsSocket(BearerTokenAuthenticator authenticator, MeterRegistry meters, Clock clock,
            @Value("${smartcafe.news.socket.max-connections:256}") int maxConnections,
            @Value("${smartcafe.news.socket.max-pending:64}") int maxPending,
            @Value("${smartcafe.news.socket.max-per-source:16}") int maxPerSource,
            @Value("${smartcafe.news.socket.max-per-account:4}") int maxPerAccount) {
        if (maxConnections < 1 || maxConnections > 4096 || maxPending < 1 || maxPending > maxConnections
                || maxPerSource < 1 || maxPerSource > maxConnections
                || maxPerAccount < 1 || maxPerAccount > maxConnections) {
            throw new IllegalArgumentException("Invalid News WebSocket connection budgets.");
        }
        this.authenticator = authenticator;
        this.meters = meters;
        this.clock = clock;
        this.maxConnections = maxConnections;
        this.maxPending = maxPending;
        this.maxPerSource = maxPerSource;
        this.maxPerAccount = maxPerAccount;
        Gauge.builder("news.websocket.connections", clients, Map::size).register(meters);
        Gauge.builder("news.websocket.pending", clients,
                map -> map.values().stream().filter(client -> client.authentication() == null).count()).register(meters);
    }

    @PostConstruct
    void startExpiry() {
        expiry.scheduleWithFixedDelay(this::expirePendingClients, 1, 1, TimeUnit.SECONDS);
        expiry.scheduleWithFixedDelay(this::revalidateClients, 5, 5, TimeUnit.SECONDS);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Keep Spring's same-origin check; do not trust forwarded IP headers from the browser.
        registry.addHandler(this, "/api/v1/news/events").addInterceptors(this);
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Map<String, Object> attributes) {
        String source = source(request.getRemoteAddress());
        String refused;
        synchronized (clients) {
            refused = admissionRefusal(source);
            if (refused == null) {
                String id = UUID.randomUUID().toString();
                clients.put(id, new Client(null, null, source, clock.instant().plusSeconds(10)));
                attributes.put(ADMISSION, id);
                if (request instanceof ServletServerHttpRequest servlet) {
                    servlet.getServletRequest().setAttribute(ADMISSION, id);
                }
                return true;
            }
        }
        rejected(refused);
        response.setStatusCode(stopping ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().set("Retry-After", "10");
        return false;
    }

    private String admissionRefusal(String source) {
        if (stopping) return "stopping";
        if (source == null) return "unknown_source";
        if (clients.size() >= maxConnections) return "total";
        if (clients.values().stream().filter(client -> client.authentication() == null).count() >= maxPending) return "pending";
        if (clients.values().stream().filter(client -> source.equals(client.source())).count() >= maxPerSource) return "source";
        return null;
    }

    private String source(InetSocketAddress address) {
        return address == null || address.getAddress() == null ? null : address.getAddress().getHostAddress();
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Exception exception) {
        boolean failedResponse = response instanceof ServletServerHttpResponse servletResponse
                && servletResponse.getServletResponse().getStatus() >= 400;
        if ((exception != null || failedResponse) && request instanceof ServletServerHttpRequest servlet) {
            Object id = servlet.getServletRequest().getAttribute(ADMISSION);
            if (id instanceof String admission) close(admission, null, CloseStatus.SERVER_ERROR);
        }
        // A failed upgrade without an exception is still reclaimed by the pending deadline.
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        String id = admission(session);
        synchronized (clients) {
            var client = id == null ? null : clients.get(id);
            if (client != null && client.session() == null && client.deadline().isAfter(clock.instant())) {
                session.setTextMessageSizeLimit(4096);
                session.setBinaryMessageSizeLimit(1);
                clients.put(id, new Client(new ConcurrentWebSocketSessionDecorator(session, 5000, 65536),
                        null, client.source(), client.deadline()));
                return;
            }
        }
        if (id != null) close(id, null, CloseStatus.POLICY_VIOLATION);
        session.close(CloseStatus.POLICY_VIOLATION);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String id = admission(session);
        var client = id == null ? null : clients.get(id);
        if (client == null) { session.close(CloseStatus.POLICY_VIOLATION); return; }
        if (client.authentication() != null || !client.deadline().isAfter(clock.instant())) {
            close(id, client, CloseStatus.POLICY_VIOLATION);
            return;
        }
        try {
            var authentication = authenticator.authenticate(message.getPayload());
            if (!authenticator.isCurrentStaff(authentication)) {
                close(id, client, CloseStatus.POLICY_VIOLATION);
                return;
            }
            Client authenticated = null;
            String refusal = "timeout";
            synchronized (clients) {
                if (clients.get(id) != client) return;
                if (client.deadline().isAfter(clock.instant())) {
                    long sameAccount = clients.values().stream().filter(other -> other.authentication() != null
                            && authentication.getName().equals(other.authentication().getName())).count();
                    if (sameAccount < maxPerAccount) {
                        authenticated = new Client(client.session(), authentication, client.source(), client.deadline());
                        clients.put(id, authenticated);
                    } else refusal = "account";
                }
            }
            if (authenticated == null) {
                rejected(refusal);
                close(id, client, refusal.equals("account") ? CloseStatus.SERVICE_OVERLOAD : CloseStatus.POLICY_VIOLATION);
                return;
            }
            authenticated.session().sendMessage(new TextMessage("{\"type\":\"READY\"}"));
        } catch (IOException | RuntimeException failure) {
            close(id, null, CloseStatus.SERVER_ERROR);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void changed(NewsChanged event) {
        if (!event.staffFeedChanged()) return;
        var message = new TextMessage("{\"type\":\"NEWS_CHANGED\"}");
        clients.forEach((id, client) -> {
            if (client.authentication() == null) return;
            try {
                if (!authenticator.isCurrentStaff(client.authentication())) {
                    close(id, client, CloseStatus.POLICY_VIOLATION);
                    return;
                }
                client.session().sendMessage(message);
            } catch (IOException | RuntimeException failure) {
                close(id, client, CloseStatus.SERVER_ERROR);
            }
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String id = admission(session);
        if (id != null) { synchronized (clients) { clients.remove(id); } }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String id = admission(session);
        if (id != null) close(id, null, CloseStatus.SERVER_ERROR);
    }

    void expirePendingClients() {
        Instant now = clock.instant();
        clients.forEach((id, client) -> {
            if (client.authentication() == null && !client.deadline().isAfter(now)) {
                close(id, client, CloseStatus.POLICY_VIOLATION);
            }
        });
    }

    void revalidateClients() {
        clients.forEach((id, client) -> {
            if (client.authentication() == null) return;
            try {
                if (!authenticator.isCurrentStaff(client.authentication())) close(id, client, CloseStatus.POLICY_VIOLATION);
            } catch (RuntimeException failure) {
                close(id, client, CloseStatus.SERVER_ERROR);
            }
        });
    }

    private String admission(WebSocketSession session) {
        Object id = session.getAttributes().get(ADMISSION);
        return id instanceof String value ? value : null;
    }

    private void rejected(String reason) {
        meters.counter("news.websocket.rejected", "reason", reason).increment();
    }

    private void close(String id, Client expected, CloseStatus status) {
        Client removed;
        synchronized (clients) {
            removed = expected == null ? clients.remove(id) : clients.remove(id, expected) ? expected : null;
        }
        if (removed != null && removed.session() != null) {
            try { removed.session().close(status); } catch (IOException | RuntimeException ignored) { /* Already disconnected; budget is released. */ }
        }
    }

    @PreDestroy
    void shutdown() {
        stopping = true;
        expiry.shutdownNow();
        clients.forEach((id, client) -> close(id, client, CloseStatus.GOING_AWAY));
    }

    private record Client(WebSocketSession session, Authentication authentication, String source, Instant deadline) {}
}
