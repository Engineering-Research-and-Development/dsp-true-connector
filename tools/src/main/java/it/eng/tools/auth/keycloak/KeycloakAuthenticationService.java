package it.eng.tools.auth.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.tools.auth.M2mTokenCache;
import it.eng.tools.auth.M2mTokenUnavailableException;
import it.eng.tools.auth.condition.KeycloakAuthenticationModeCondition;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Optional;

/**
 * Obtains access tokens for the connector's outbound client with the OAuth2 client credentials grant.
 * <p>
 * The token endpoint comes from the discovery document of the {@code platform} realm
 * ({@link KeycloakTrustProperties#getPlatformRealm()}). It is resolved on first use, kept, and resolved
 * again after a token request that could not reach it: a transport failure, HTTP 404 or 405, or a 5xx. An
 * OAuth client error such as a wrong secret ({@code 400}/{@code 401}) keeps it, and a missing client ID or
 * secret is reported before discovery is attempted. The connector-to-connector token is cached in {@link M2mTokenCache}
 * under a key built from the client ID and the token endpoint, so it never shares an entry with the
 * {@code INTERNAL} mode tokens or with another client.
 * <p>
 * A failed request is logged at ERROR with the HTTP status and the OAuth {@code error} and
 * {@code error_description}, and surfaces as {@link M2mTokenUnavailableException}. The client secret, the
 * request and response bodies and tokens are never logged.
 */
@Service
@Slf4j
@Conditional(KeycloakAuthenticationModeCondition.class)
public class KeycloakAuthenticationService {

    /** Prefix of the {@link M2mTokenCache} key of the connector-to-connector token. */
    static final String CACHE_KEY_PREFIX = "keycloak-m2m:";

    private static final int MAX_LOGGED_ERROR_LENGTH = 200;
    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_METHOD_NOT_ALLOWED = 405;
    private static final int HTTP_SERVER_ERROR = 500;
    private static final String NOT_PROVIDED = "n/a";

    private final KeycloakAuthenticationProperties properties;
    private final KeycloakTrustProperties trustProperties;
    private final KeycloakDiscoveryClient discoveryClient;
    private final M2mTokenCache tokenCache;
    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile String tokenEndpoint;
    private volatile String connectorCacheKey;

    /**
     * Creates the service.
     *
     * @param properties the outbound client configuration (client ID, secret, token caching)
     * @param trustProperties the Keycloak trust configuration that names the {@code platform} realm
     * @param discoveryClient loads the discovery document of the {@code platform} realm
     * @param tokenCache the cache that holds the connector-to-connector token
     * @param okHttpClient the HTTP client used for token requests
     */
    public KeycloakAuthenticationService(KeycloakAuthenticationProperties properties,
            KeycloakTrustProperties trustProperties, KeycloakDiscoveryClient discoveryClient,
            M2mTokenCache tokenCache, OkHttpClient okHttpClient) {
        this.properties = properties;
        this.trustProperties = trustProperties;
        this.discoveryClient = discoveryClient;
        this.tokenCache = tokenCache;
        this.okHttpClient = okHttpClient;
    }

    /**
     * Returns the token for connector-to-connector calls.
     * <p>
     * With {@code application.keycloak.backend.token-caching} enabled (the default), the token is cached
     * and replaced shortly before it expires; otherwise every call requests a new one.
     *
     * @return the access token, never {@code null}
     * @throws M2mTokenUnavailableException when the token endpoint cannot be resolved or the token request fails
     */
    public String getConnectorToken() {
        requireClientCredentials();
        String endpoint = resolveTokenEndpoint();
        if (!properties.isTokenCaching()) {
            return requestToken(endpoint);
        }
        String cacheKey = CACHE_KEY_PREFIX + properties.getClientId() + "@" + endpoint;
        connectorCacheKey = cacheKey;
        return tokenCache.getOrFetch(cacheKey, () -> requestToken(endpoint));
    }

    /**
     * Evicts the cached connector-to-connector token, so the next {@link #getConnectorToken()} requests a
     * new one. Called after a peer rejects a protocol call with HTTP 401.
     */
    public void invalidateConnectorToken() {
        String cacheKey = connectorCacheKey;
        if (cacheKey != null) {
            tokenCache.invalidate(cacheKey);
        }
    }

    private void requireClientCredentials() {
        String clientId = properties.getClientId();
        String clientSecret = properties.getClientSecret();
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isEmpty()) {
            log.error("Token request not sent: application.keycloak.backend.client-id or client-secret is not configured");
            throw new M2mTokenUnavailableException(
                    "application.keycloak.backend.client-id or client-secret is not configured");
        }
    }

    private String resolveTokenEndpoint() {
        String endpoint = tokenEndpoint;
        if (endpoint != null) {
            return endpoint;
        }
        String realm = trustProperties.getPlatformRealm();
        try {
            endpoint = discoveryClient.fetch(trustProperties.issuerFor(realm)).tokenEndpoint();
        } catch (KeycloakDiscoveryException | IllegalArgumentException | IllegalStateException e) {
            log.error("Token endpoint of realm {} could not be resolved: {}", realm, e.getMessage());
            throw new M2mTokenUnavailableException("Token endpoint of realm " + realm + " could not be resolved", e);
        }
        if (endpoint == null || endpoint.isBlank()) {
            log.error("Discovery document of realm {} declares no token_endpoint", realm);
            throw new M2mTokenUnavailableException("Discovery document of realm " + realm + " declares no token_endpoint");
        }
        tokenEndpoint = endpoint;
        log.info("Outbound token endpoint resolved from the discovery document of realm {}: {}", realm, endpoint);
        return endpoint;
    }

    // The endpoint may have moved: resolve it again on the next request.
    private void forgetTokenEndpoint() {
        tokenEndpoint = null;
    }

    // An unreachable or wrong endpoint (404, 405, 5xx) is re-resolved; an OAuth client error is not.
    private static boolean isEndpointFailure(int status) {
        return status == HTTP_NOT_FOUND || status == HTTP_METHOD_NOT_ALLOWED || status >= HTTP_SERVER_ERROR;
    }

    private String requestToken(String endpoint) {
        String clientId = properties.getClientId();
        String clientSecret = properties.getClientSecret();
        RequestBody formBody = new FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .build();

        Request request;
        try {
            request = new Request.Builder().url(endpoint).post(formBody).build();
        } catch (IllegalArgumentException e) {
            log.error("Token request not sent: invalid token endpoint {}", endpoint);
            forgetTokenEndpoint();
            throw new M2mTokenUnavailableException("Invalid token endpoint " + endpoint);
        }

        try (Response response = okHttpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String body = responseBody == null ? "" : responseBody.string();
            if (!response.isSuccessful()) {
                if (isEndpointFailure(response.code())) {
                    forgetTokenEndpoint();
                }
                throw failedRequest(clientId, endpoint, response.code(), body);
            }
            Optional<String> token = parse(body).flatMap(json -> text(json, "access_token")).filter(t -> !t.isBlank());
            if (token.isEmpty()) {
                log.error("Token response of {} for client {} contains no access_token", endpoint, clientId);
                throw new M2mTokenUnavailableException("Token response of " + endpoint + " contains no access_token");
            }
            log.debug("Access token obtained for client {} from {}", clientId, endpoint);
            return token.get();
        } catch (IOException e) {
            forgetTokenEndpoint();
            // The cause is not logged: its message can echo response content.
            log.error("Token request for client {} to {} failed: {}", clientId, endpoint, e.getClass().getSimpleName());
            throw new M2mTokenUnavailableException(
                    "Token request to " + endpoint + " failed: " + e.getClass().getSimpleName());
        }
    }

    private M2mTokenUnavailableException failedRequest(String clientId, String endpoint, int status, String body) {
        Optional<JsonNode> json = parse(body);
        String error = json.flatMap(node -> text(node, "error"))
                .map(KeycloakAuthenticationService::sanitize).orElse(NOT_PROVIDED);
        String description = json.flatMap(node -> text(node, "error_description"))
                .map(KeycloakAuthenticationService::sanitize).orElse(NOT_PROVIDED);
        log.error("Token request for client {} to {} failed: HTTP {}, error={}, error_description={}",
                clientId, endpoint, status, error, description);
        return new M2mTokenUnavailableException("Token request to " + endpoint + " failed with HTTP " + status
                + (NOT_PROVIDED.equals(error) ? "" : " (" + error + ")"));
    }

    // Empty when the body is not JSON.
    private Optional<JsonNode> parse(String body) {
        try {
            return Optional.ofNullable(objectMapper.readTree(body));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    // The text value of a field of a JSON object; empty when absent or not text.
    private static Optional<String> text(JsonNode json, String field) {
        if (!json.isObject()) {
            return Optional.empty();
        }
        JsonNode node = json.get(field);
        return node != null && node.isTextual() ? Optional.of(node.asText()) : Optional.empty();
    }

    // OAuth error fields come from the server: strip control characters and bound the length before logging.
    private static String sanitize(String value) {
        if (value.isBlank()) {
            return NOT_PROVIDED;
        }
        String cleaned = value.replaceAll("\\p{Cntrl}", " ").strip();
        return cleaned.length() > MAX_LOGGED_ERROR_LENGTH ? cleaned.substring(0, MAX_LOGGED_ERROR_LENGTH) : cleaned;
    }
}
