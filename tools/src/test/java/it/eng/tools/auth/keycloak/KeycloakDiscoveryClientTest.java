package it.eng.tools.auth.keycloak;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class KeycloakDiscoveryClientTest {

    private static final String PATH = "/realms/platform/.well-known/openid-configuration";

    private HttpServer server;
    private KeycloakTrustProperties properties;
    private KeycloakDiscoveryClient client;
    private final AtomicInteger requests = new AtomicInteger();
    private final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        properties = new KeycloakTrustProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        properties.setDiscoveryConnectTimeout(Duration.ofMillis(200));
        properties.setDiscoveryReadTimeout(Duration.ofMillis(500));
        properties.init();
        client = KeycloakTestHttpClients.discoveryClient(properties);
    }

    @AfterEach
    void tearDown() {
        release.countDown();
        server.stop(0);
    }

    private void respond(int status, String body) {
        server.createContext(PATH, exchange -> {
            requests.incrementAndGet();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
    }

    private String issuer() {
        return properties.issuerFor("platform");
    }

    @Test
    void happyPath() {
        respond(200, "{\"issuer\":\"" + issuer() + "\",\"jwks_uri\":\"" + issuer() + "/certs\",\"token_endpoint\":\""
                + issuer() + "/token\",\"extra\":1}");
        DiscoveryDocument doc = client.fetch(issuer());
        assertEquals(issuer(), doc.issuer());
        assertEquals(issuer() + "/certs", doc.jwksUri());
        assertEquals(issuer() + "/token", doc.tokenEndpoint());
    }

    @Test
    void missingTokenEndpointIsNull() {
        respond(200, "{\"issuer\":\"" + issuer() + "\",\"jwks_uri\":\"" + issuer() + "/certs\"}");
        assertNull(client.fetch(issuer()).tokenEndpoint());
    }

    @Test
    void issuerMismatch() {
        respond(200, "{\"issuer\":\"http://other/realms/platform\",\"jwks_uri\":\"http://other/certs\"}");
        KeycloakDiscoveryException e = assertThrows(KeycloakDiscoveryException.class, () -> client.fetch(issuer()));
        assertTrue(e.getMessage().contains(issuer()));
    }

    @Test
    void missingJwksUri() {
        respond(200, "{\"issuer\":\"" + issuer() + "\"}");
        assertThrows(KeycloakDiscoveryException.class, () -> client.fetch(issuer()));
    }

    @Test
    void notFoundAndServerError() {
        respond(404, "secret-body");
        KeycloakDiscoveryException e = assertThrows(KeycloakDiscoveryException.class, () -> client.fetch(issuer()));
        assertTrue(e.getMessage().contains("404"));
        assertFalse(e.getMessage().contains("secret-body"));
        server.removeContext(PATH);
        respond(500, "secret-body");
        e = assertThrows(KeycloakDiscoveryException.class, () -> client.fetch(issuer()));
        assertTrue(e.getMessage().contains("500"));
    }

    @Test
    void invalidJsonDoesNotLeakBody() {
        respond(200, "not-json-secret");
        KeycloakDiscoveryException e = assertThrows(KeycloakDiscoveryException.class, () -> client.fetch(issuer()));
        assertFalse(e.getMessage().contains("secret"));
        assertTrue(e.getMessage().contains(issuer()));
    }

    @Test
    void readTimeout() {
        server.createContext(PATH, exchange -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        assertThrows(KeycloakDiscoveryException.class, () -> client.fetch(issuer()));
    }

    @Test
    @DisplayName("A server that sends the headers and then stalls the body fails within connect plus read timeout")
    void stalledBodyTimesOut() {
        server.createContext(PATH, exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write("{\"issuer\":".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        KeycloakDiscoveryException e = assertTimeoutPreemptively(Duration.ofSeconds(3),
                () -> assertThrows(KeycloakDiscoveryException.class, () -> client.fetch(issuer())));
        assertTrue(e.getMessage().contains("HttpTimeoutException"));
    }

    @Test
    @DisplayName("A discovery document larger than the size limit is rejected")
    void oversizedDocumentIsRejected() {
        respond(200, "{\"issuer\":\"" + "x".repeat(KeycloakDiscoveryClient.MAX_DOCUMENT_SIZE) + "\"}");
        assertThrows(KeycloakDiscoveryException.class, () -> client.fetch(issuer()));
    }

    @Test
    void foreignIssuerIsRejectedWithoutRequest() {
        respond(200, "{}");
        assertThrows(IllegalArgumentException.class, () -> client.fetch("http://evil.example.com/realms/platform"));
        assertThrows(IllegalArgumentException.class, () -> client.fetch(null));
        assertEquals(0, requests.get());
    }
}
