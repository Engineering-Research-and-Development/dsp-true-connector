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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealmCredentialsValidatorTest {

    private static final String REALM = "tenant-EngineeringA";

    private HttpServer server;
    private RealmCredentialsValidator validator;
    private final AtomicReference<String> tokenRequestBody = new AtomicReference<>();
    private volatile int tokenStatus = 200;
    private volatile String tokenBody = "{\"access_token\":\"abc\"}";

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        String issuer = base + "/realms/" + REALM;
        respond("/realms/" + REALM + "/.well-known/openid-configuration", 200,
                "{\"issuer\":\"" + issuer + "\",\"jwks_uri\":\"" + issuer + "/certs\",\"token_endpoint\":\""
                        + issuer + "/token\"}");
        server.createContext("/realms/" + REALM + "/token", exchange -> {
            tokenRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = tokenBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(tokenStatus, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        KeycloakTrustProperties properties = new KeycloakTrustProperties();
        properties.setBaseUrl(base);
        properties.setDiscoveryConnectTimeout(Duration.ofMillis(300));
        properties.setDiscoveryReadTimeout(Duration.ofMillis(500));
        properties.init();
        validator = new RealmCredentialsValidator(properties, new KeycloakDiscoveryClient(properties));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void respond(String path, int status, String body) {
        server.createContext(path, exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    @Test
    @DisplayName("accepts: realm issues a token for the default client id and the given secret")
    void accepts_validSecret() {
        assertTrue(validator.accepts(REALM, "s3cret&=x"));
        assertTrue(tokenRequestBody.get().contains("grant_type=client_credentials"));
        assertTrue(tokenRequestBody.get().contains("client_id=connector-user-reader"));
        assertTrue(tokenRequestBody.get().contains("client_secret=s3cret%26%3Dx"));
    }

    @Test
    @DisplayName("accepts: rejected secret (401) is false")
    void accepts_rejected() {
        tokenStatus = 401;
        tokenBody = "{\"error\":\"unauthorized_client\"}";
        assertFalse(validator.accepts(REALM, "wrong"));
    }

    @Test
    @DisplayName("accepts: 200 without an access_token is false")
    void accepts_noToken() {
        tokenBody = "{}";
        assertFalse(validator.accepts(REALM, "x"));
    }

    @Test
    @DisplayName("accepts: unknown realm (no discovery document) and invalid realm name are false")
    void accepts_discoveryFails() {
        assertFalse(validator.accepts("other", "x"));
        assertFalse(validator.accepts("../x", "x"));
    }
}
