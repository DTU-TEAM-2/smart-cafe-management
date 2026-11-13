package vn.codegym.smartcafe.news;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import vn.codegym.smartcafe.config.BearerTokenAuthenticator;

/** Browser-compatible protocol: first text frame is the bearer header; never place tokens in URLs. */
@Configuration
@EnableWebSocket
class NewsSocket extends TextWebSocketHandler implements WebSocketConfigurer {
    private final BearerTokenAuthenticator authenticator;
    private final ConcurrentHashMap<String, Client> clients = new ConcurrentHashMap<>();
    private final ScheduledExecutorService expiry = Executors.newSingleThreadScheduledExecutor(task -> {
        var thread = new Thread(task, "news-socket-expiry");
        thread.setDaemon(true);
        return thread;
    });

    NewsSocket(BearerTokenAuthenticator authenticator) {
        this.authenticator = authenticator;
        expiry.scheduleWithFixedDelay(this::closeExpiredClients, 5, 5, TimeUnit.SECONDS);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Default same-origin checks remain enabled. No SockJS, cookies or permissive CORS.
        registry.addHandler(this, "/api/v1/news/events");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        session.setTextMessageSizeLimit(4096);
        session.setBinaryMessageSizeLimit(1);
        clients.put(session.getId(), new Client(new ConcurrentWebSocketSessionDecorator(session, 5000, 65536),
                null, Instant.now().plusSeconds(10)));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        var client = clients.get(session.getId());
        if (client == null || client.authentication() != null) {
            close(session.getId(), CloseStatus.POLICY_VIOLATION);
            return;
        }
        try {
            var authentication = authenticator.authenticate(message.getPayload());
            if (!authenticator.isCurrentStaff(authentication)) {
                close(session.getId(), CloseStatus.POLICY_VIOLATION);
                return;
            }
            var authenticated = new Client(client.session(), authentication, client.deadline());
            if (!clients.replace(session.getId(), client, authenticated)) return;
            authenticated.session().sendMessage(new TextMessage("{\"type\":\"READY\"}"));
        } catch (RuntimeException failure) {
            close(session.getId(), CloseStatus.SERVER_ERROR);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void changed(NewsChanged event) {
        if (!event.staffFeedChanged()) return;
        // A generic hint cannot expose an article ID, draft content or a removed audience's data.
        var message = new TextMessage("{\"type\":\"NEWS_CHANGED\"}");
        clients.forEach((id, client) -> {
            if (client.authentication() == null) return;
            try {
                if (!authenticator.isCurrentStaff(client.authentication())) {
                    close(id, CloseStatus.POLICY_VIOLATION);
                    return;
                }
                client.session().sendMessage(message);
            } catch (IOException | RuntimeException failure) {
                close(id, CloseStatus.SERVER_ERROR);
            }
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        clients.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        close(session.getId(), CloseStatus.SERVER_ERROR);
    }

    private void closeExpiredClients() {
        clients.forEach((id, client) -> {
            try {
                if (client.authentication() == null ? Instant.now().isAfter(client.deadline())
                        : !authenticator.isCurrentStaff(client.authentication())) {
                    close(id, CloseStatus.POLICY_VIOLATION);
                }
            } catch (RuntimeException failure) {
                close(id, CloseStatus.SERVER_ERROR);
            }
        });
    }

    private void close(String id, CloseStatus status) {
        var client = clients.remove(id);
        if (client != null) {
            try { client.session().close(status); } catch (IOException ignored) { /* Already disconnected. */ }
        }
    }

    @PreDestroy
    void shutdown() {
        expiry.shutdownNow();
        clients.forEach((id, client) -> close(id, CloseStatus.GOING_AWAY));
    }

    private record Client(WebSocketSession session, Authentication authentication, Instant deadline) {}
}
