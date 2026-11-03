package it.eng.tools.auth.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.tools.auth.condition.KeycloakAuthenticationModeCondition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;

/**
 * Loads the OpenID discovery document of a trusted Keycloak issuer. The client is stateless and does
 * not cache: callers own caching.
 * <p>
 * The whole request, body included, must complete within {@code discovery-connect-timeout} plus
 * {@code discovery-read-timeout}, and the body must not exceed {@value #MAX_DOCUMENT_SIZE} bytes.
 */
@Slf4j
@Component
@Conditional(KeycloakAuthenticationModeCondition.class)
public class KeycloakDiscoveryClient {

    private static final String DISCOVERY_PATH = "/.well-known/openid-configuration";
    private static final String REALMS_SEGMENT = "/realms/";
    static final int MAX_DOCUMENT_SIZE = 256 * 1024;

    private final KeycloakTrustProperties properties;
    private final HttpClient httpClient;
    private final Duration exchangeTimeout;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Creates the client. It never follows redirects and uses the discovery timeouts of the trust configuration.
     *
     * @param properties the Keycloak trust configuration
     */
    public KeycloakDiscoveryClient(KeycloakTrustProperties properties) {
        this.properties = properties;
        this.exchangeTimeout = properties.getDiscoveryConnectTimeout().plus(properties.getDiscoveryReadTimeout());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getDiscoveryConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * Fetches {@code <expectedIssuer>/.well-known/openid-configuration}.
     * <p>
     * The argument must be produced by {@link KeycloakTrustProperties#issuerFor(String)}. It must never
     * be a URL taken from a token or from a tenant record. As a second guard, an issuer that does not
     * start with the configured {@code base-url} followed by {@code /realms/} is rejected before any
     * request is made.
     *
     * @param expectedIssuer the issuer URL, produced by {@code issuerFor(...)}
     * @return the discovery document, with {@code issuer} equal to {@code expectedIssuer}
     * @throws KeycloakDiscoveryException when the request fails or times out, the status is not 2xx, the
     *         body is too large or not valid JSON, the {@code issuer} differs from {@code expectedIssuer} or
     *         {@code jwks_uri} is missing
     * @throws IllegalArgumentException when {@code expectedIssuer} is not an issuer of the configured Keycloak
     */
    public DiscoveryDocument fetch(String expectedIssuer) {
        if (expectedIssuer == null || !expectedIssuer.startsWith(properties.getBaseUrl() + REALMS_SEGMENT)) {
            throw new IllegalArgumentException("Issuer is not an issuer of the configured Keycloak");
        }
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(expectedIssuer + DISCOVERY_PATH))
                    .timeout(properties.getDiscoveryReadTimeout())
                    .header("Accept", "application/json")
                    .GET()
                    .build();
        } catch (IllegalArgumentException e) {
            throw new KeycloakDiscoveryException("Invalid discovery URL for issuer " + expectedIssuer);
        }

        JsonNode json;
        try {
            BoundedHttpExchange.Result response = BoundedHttpExchange.send(httpClient, request, exchangeTimeout,
                    MAX_DOCUMENT_SIZE);
            if (!response.isSuccessful()) {
                log.warn("Discovery request for issuer {} returned HTTP {}", expectedIssuer, response.statusCode());
                throw new KeycloakDiscoveryException(
                        "Discovery request for issuer " + expectedIssuer + " returned HTTP " + response.statusCode());
            }
            json = objectMapper.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KeycloakDiscoveryException("Discovery request for issuer " + expectedIssuer + " was interrupted", e);
        } catch (IOException e) {
            // The cause is not logged: a parse error message can echo response content.
            log.warn("Discovery request for issuer {} failed: {}", expectedIssuer, e.getClass().getSimpleName());
            throw new KeycloakDiscoveryException(
                    "Discovery request for issuer " + expectedIssuer + " failed: " + e.getClass().getSimpleName());
        }

        String issuer = text(json, "issuer");
        if (!expectedIssuer.equals(issuer)) {
            throw new KeycloakDiscoveryException("Discovery document issuer does not match expected issuer " + expectedIssuer
                    + " (check the Keycloak hostname configuration)");
        }
        String jwksUri = text(json, "jwks_uri");
        if (jwksUri == null || jwksUri.isBlank()) {
            throw new KeycloakDiscoveryException("Discovery document of issuer " + expectedIssuer + " has no jwks_uri");
        }
        return new DiscoveryDocument(issuer, jwksUri, text(json, "token_endpoint"));
    }

    private static String text(JsonNode json, String field) {
        if (json == null || !json.isObject()) {
            return null;
        }
        JsonNode node = json.get(field);
        return node != null && node.isTextual() ? node.asText() : null;
    }
}
