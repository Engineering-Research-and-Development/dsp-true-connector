package it.eng.tools.auth.keycloak;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Minimal Keycloak stand-in for outbound token tests: serves the discovery document and the token
 * endpoint of the {@code platform} realm, plus a {@code /peer} endpoint acting as another connector.
 * Every request path is recorded.
 */
final class FakeKeycloakServer implements AutoCloseable {

    static final String REALM = "platform";
    static final String PEER_PATH = "/peer";

    private static final Algorithm SIGNING = Algorithm.HMAC256("fake-keycloak-test-signing-key");

    private final HttpServer server;
    private final List<String> requestPaths = Collections.synchronizedList(new ArrayList<>());
    private final List<String> tokenRequestBodies = Collections.synchronizedList(new ArrayList<>());
    private final List<String> peerAuthorizations = Collections.synchronizedList(new ArrayList<>());

    private volatile int discoveryStatus = 200;
    private volatile boolean declareTokenEndpoint = true;
    private volatile String tokenEndpointOverride;
    private volatile int tokenStatus = 200;
    private volatile String tokenBody;
    private volatile Duration tokenLifetime = Duration.ofMinutes(5);
    private final List<Integer> peerStatuses = Collections.synchronizedList(new ArrayList<>());

    FakeKeycloakServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    String issuer() {
        return baseUrl() + "/realms/" + REALM;
    }

    String tokenEndpoint() {
        return issuer() + "/protocol/openid-connect/token";
    }

    String discoveryPath() {
        return "/realms/" + REALM + "/.well-known/openid-configuration";
    }

    String tokenPath() {
        return "/realms/" + REALM + "/protocol/openid-connect/token";
    }

    String peerUrl() {
        return baseUrl() + PEER_PATH;
    }

    void discoveryStatus(int status) {
        this.discoveryStatus = status;
    }

    void declareTokenEndpoint(boolean declare) {
        this.declareTokenEndpoint = declare;
    }

    void tokenEndpointOverride(String endpoint) {
        this.tokenEndpointOverride = endpoint;
    }

    /** Answers token requests with a fresh JWT that expires after {@code lifetime}. */
    void issueTokens(Duration lifetime) {
        this.tokenStatus = 200;
        this.tokenBody = null;
        this.tokenLifetime = lifetime;
    }

    /** Answers token requests with a fixed status and body. */
    void tokenResponse(int status, String body) {
        this.tokenStatus = status;
        this.tokenBody = body;
    }

    /** Queues the statuses the peer answers with, in order; afterwards it answers 200. */
    void peerStatuses(Integer... statuses) {
        peerStatuses.addAll(List.of(statuses));
    }

    List<String> requestPaths() {
        return List.copyOf(requestPaths);
    }

    long count(String path) {
        return requestPaths().stream().filter(path::equals).count();
    }

    List<String> tokenRequestBodies() {
        return List.copyOf(tokenRequestBodies);
    }

    List<String> peerAuthorizations() {
        return List.copyOf(peerAuthorizations);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        requestPaths.add(path);
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (path.equals(discoveryPath())) {
            String tokenEndpoint = tokenEndpointOverride != null ? tokenEndpointOverride : tokenEndpoint();
            respond(exchange, discoveryStatus, "{\"issuer\":\"" + issuer() + "\",\"jwks_uri\":\"" + issuer()
                    + "/protocol/openid-connect/certs\""
                    + (declareTokenEndpoint ? ",\"token_endpoint\":\"" + tokenEndpoint + "\"" : "") + "}");
        } else if (path.equals(tokenPath())) {
            tokenRequestBodies.add(requestBody);
            String body = tokenBody != null ? tokenBody
                    : "{\"access_token\":\"" + newToken(tokenLifetime) + "\",\"token_type\":\"Bearer\"}";
            respond(exchange, tokenStatus, body);
        } else if (path.equals(PEER_PATH)) {
            peerAuthorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            int status = peerStatuses.isEmpty() ? 200 : peerStatuses.remove(0);
            respond(exchange, status, "{}");
        } else {
            respond(exchange, 404, "{}");
        }
    }

    private static String newToken(Duration lifetime) {
        return JWT.create()
                .withSubject("service-account-dsp-connector-backend")
                .withJWTId(UUID.randomUUID().toString())
                .withExpiresAt(new Date(System.currentTimeMillis() + lifetime.toMillis()))
                .sign(SIGNING);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }
}
