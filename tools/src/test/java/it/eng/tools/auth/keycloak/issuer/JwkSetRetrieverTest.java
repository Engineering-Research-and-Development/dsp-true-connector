package it.eng.tools.auth.keycloak.issuer;

import it.eng.tools.auth.keycloak.KeycloakTestHttpClients;
import com.nimbusds.jose.util.Resource;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

class JwkSetRetrieverTest {

    private HttpServer server;
    private JwkSetRetriever retriever;
    private final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        retriever = new JwkSetRetriever(
                KeycloakTestHttpClients.withTimeouts(Duration.ofMillis(200), Duration.ofMillis(500)), 16);
    }

    @AfterEach
    void tearDown() {
        release.countDown();
        server.stop(0);
    }

    private void respond(String path, int status, String body) {
        server.createContext(path, exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
    }

    private URL url(String path) throws IOException {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path).toURL();
    }

    @Test
    @DisplayName("A 2xx response within the size limit is returned with its content type")
    void success() throws IOException {
        respond("/ok", 200, "{\"keys\":[]}");
        Resource resource = retriever.retrieveResource(url("/ok"));
        assertEquals("{\"keys\":[]}", resource.getContent());
        assertEquals("application/json", resource.getContentType());
    }

    @Test
    @DisplayName("Error status, oversized body and non-http URLs are rejected")
    void failures() throws IOException {
        respond("/error", 500, "");
        respond("/big", 200, "{\"keys\":[],\"padding\":\"xxxxxxxx\"}");
        assertThrows(IOException.class, () -> retriever.retrieveResource(url("/error")));
        assertThrows(IOException.class, () -> retriever.retrieveResource(url("/big")));
        assertThrows(IOException.class, () -> retriever.retrieveResource(URI.create("file:///etc/hosts").toURL()));
    }

    @Test
    @DisplayName("A server that sends the headers and then stalls the body fails within connect plus read timeout")
    void stalledBodyTimesOut() {
        server.createContext("/stalled", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write("{\"keys\"".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        assertTimeoutPreemptively(Duration.ofSeconds(3),
                () -> assertThrows(SocketTimeoutException.class, () -> retriever.retrieveResource(url("/stalled"))));
    }
}
