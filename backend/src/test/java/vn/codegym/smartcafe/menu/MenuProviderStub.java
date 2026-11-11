package vn.codegym.smartcafe.menu;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** HTTP provider fixture, loopback only. No changes to the production Cloudinary endpoint. */
final class MenuProviderStub implements AutoCloseable {
    static final String DELIVERY_URL = "https://res.cloudinary.com/menu-test/image/upload/v1/report.png";
    static final String PRIVATE_ERROR = "private-provider-body-must-not-be-exposed";
    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> invalidRequest = new AtomicReference<>();

    MenuProviderStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/v1_1/menu-test/image/upload", exchange -> {
            calls.incrementAndGet();
            String form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
            String expectedAuth = "Basic " + Base64.getEncoder().encodeToString("test-only-key:test-only-secret".getBytes(StandardCharsets.ISO_8859_1));
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            boolean valid = exchange.getRequestMethod().equals("POST")
                    && expectedAuth.equals(exchange.getRequestHeaders().getFirst("Authorization"))
                    && contentType != null && contentType.startsWith("multipart/form-data;")
                    && form.contains("name=\"file\"") && form.contains("filename=\"menu.png\"");
            if (!valid) invalidRequest.set("Provider did not receive the expected POST/auth/multipart contract");
            int responseStatus = valid ? status.get() : 400;
            String body = responseStatus == 200 ? "{\"secure_url\":\"" + DELIVERY_URL + "\"}"
                    : "{\"error\":{\"message\":\"" + PRIVATE_ERROR + "\"}}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
            finally { exchange.close(); }
        });
        server.start();
    }

    RestClient client() {
        var delegate = new SimpleClientHttpRequestFactory();
        delegate.setConnectTimeout(Duration.ofSeconds(5));
        delegate.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory((uri, method) -> {
            if (!"https".equals(uri.getScheme()) || !"api.cloudinary.com".equals(uri.getHost())
                    || !"/v1_1/menu-test/image/upload".equals(uri.getPath())) {
                throw new IllegalArgumentException("Unexpected upload destination; external requests are blocked in this test");
            }
            return delegate.createRequest(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + uri.getRawPath()), method);
        }).build();
    }
    void respondWith(int nextStatus) { status.set(nextStatus); invalidRequest.set(null); }
    int calls() { return calls.get(); }
    String invalidRequest() { return invalidRequest.get(); }
    @Override public void close() { server.stop(0); executor.close(); }
}
