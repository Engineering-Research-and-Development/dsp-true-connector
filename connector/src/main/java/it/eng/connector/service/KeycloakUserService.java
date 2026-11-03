package it.eng.connector.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.tools.auth.condition.KeycloakUserManagementCondition;
import it.eng.tools.auth.keycloak.BoundedHttpExchange;
import it.eng.tools.auth.keycloak.KeycloakDiscoveryClient;
import it.eng.tools.auth.keycloak.KeycloakTrustProperties;
import it.eng.tools.event.realm.RealmCredentialsChangedEvent;
import it.eng.tools.exception.BadRequestException;
import it.eng.tools.exception.UpstreamServiceException;
import it.eng.tools.exception.UserManagementNotConfiguredException;
import it.eng.tools.model.Tenant;
import it.eng.tools.service.TenantContextHolder;
import it.eng.tools.service.TenantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lists the users of one tenant's Keycloak realm.
 *
 * <p>The tenant comes from {@link TenantContextHolder}. The token is a client-credentials token of the
 * tenant realm's user-management client ({@code application.keycloak.user-management-client-id}, view-users only),
 * obtained with the secret stored on the tenant at the token endpoint of the realm's discovery document. The
 * users URL is built from {@code application.keycloak.base-url} and {@code Tenant.realm} only.
 *
 * <p>The tokens live in a cache of this class, separate from every other token cache. An entry is keyed by
 * tenant id, realm and a SHA-256 fingerprint of the secret, so a token obtained with an old secret or for an old
 * realm is never used after a change, even when it is stored after the change. The discovery document is read
 * only when a token has to be requested. The entries of a tenant are dropped on a
 * {@link RealmCredentialsChangedEvent}; an entry is dropped when Keycloak answers 401, and the request is then
 * retried once with a fresh token.
 *
 * <p>Only a 400 or 401 from the token endpoint means the stored credentials are not usable (409). Any other
 * failure of Keycloak, an unexpected status or an I/O error, is an upstream error (502).
 *
 * <p>The bean exists only in Keycloak mode with {@code application.keycloak.user-management=true}.
 */
@Service
@Slf4j
@Conditional(KeycloakUserManagementCondition.class)
public class KeycloakUserService {

    private static final int MAX_RESPONSE_SIZE = 5 * 1024 * 1024;
    private static final long REFRESH_BEFORE_EXPIRY_SECONDS = 30;
    private static final long DEFAULT_TOKEN_LIFETIME_SECONDS = 60;
    private static final int HTTP_BAD_REQUEST = 400;
    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int HTTP_SERVER_ERROR = 500;
    private static final int FINGERPRINT_HEX_LENGTH = 16;
    private static final String NOT_CONFIGURED = "User management not configured for this tenant";
    private static final String UNAVAILABLE = "Keycloak user management is unavailable";

    private final TenantService tenantService;
    private final KeycloakTrustProperties trustProperties;
    private final KeycloakDiscoveryClient discoveryClient;
    private final HttpClient httpClient;
    private final Duration exchangeTimeout;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<TokenKey, CachedToken> tokens = new ConcurrentHashMap<>();

    /**
     * Creates the service.
     *
     * @param tenantService provides the tenant and its decrypted user-management secret
     * @param trustProperties the Keycloak trust configuration
     * @param discoveryClient loads the discovery document of the tenant realm
     */
    @Autowired
    public KeycloakUserService(TenantService tenantService, KeycloakTrustProperties trustProperties,
            KeycloakDiscoveryClient discoveryClient) {
        this(tenantService, trustProperties, discoveryClient, HttpClient.newBuilder()
                .connectTimeout(trustProperties.getDiscoveryConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    /**
     * Creates the service with a given HTTP client, for tests.
     *
     * @param tenantService provides the tenant and its decrypted user-management secret
     * @param trustProperties the Keycloak trust configuration
     * @param discoveryClient loads the discovery document of the tenant realm
     * @param httpClient the HTTP client
     */
    KeycloakUserService(TenantService tenantService, KeycloakTrustProperties trustProperties,
            KeycloakDiscoveryClient discoveryClient, HttpClient httpClient) {
        this.tenantService = tenantService;
        this.trustProperties = trustProperties;
        this.discoveryClient = discoveryClient;
        this.httpClient = httpClient;
        this.exchangeTimeout = trustProperties.getDiscoveryConnectTimeout().plus(trustProperties.getDiscoveryReadTimeout());
    }

    /**
     * Drops the cached tokens of the tenant whose realm credentials changed.
     *
     * @param event the change event
     */
    @EventListener
    public void onRealmCredentialsChanged(RealmCredentialsChangedEvent event) {
        tokens.keySet().removeIf(key -> key.tenantId().equals(event.tenantId()));
    }

    /**
     * Returns the users of the realm of the current tenant, optionally filtered by exact e-mail.
     *
     * @param email optional e-mail filter; may be {@code null} or blank
     * @return the matching users as JSON nodes
     * @throws BadRequestException if no tenant is set, Keycloak refuses the listing with a client error or
     *         returns a response that is not a JSON list
     * @throws UserManagementNotConfiguredException if the tenant has no realm or no usable secret
     * @throws UpstreamServiceException if Keycloak fails or cannot be reached
     */
    public Collection<JsonNode> findUsers(String email) {
        String tenantId = TenantContextHolder.getTenantId();
        if (!StringUtils.hasText(tenantId)) {
            throw new BadRequestException("X-Tenant-Id required");
        }
        Tenant tenant = tenantService.findById(tenantId);
        String realm = tenant.getRealm();
        if (!StringUtils.hasText(realm)) {
            throw new UserManagementNotConfiguredException(NOT_CONFIGURED);
        }
        Optional<String> secret = tenantService.userManagementSecret(tenant);
        if (secret.isEmpty()) {
            throw new UserManagementNotConfiguredException(NOT_CONFIGURED);
        }
        TokenKey key = new TokenKey(tenantId, realm, fingerprint(secret.get()));
        try {
            URI usersUri = usersUri(realm, email);
            BoundedHttpExchange.Result response = getUsers(usersUri, token(key, secret.get(), false));
            if (response.statusCode() == HTTP_UNAUTHORIZED) {
                tokens.remove(key);
                response = getUsers(usersUri, token(key, secret.get(), true));
            }
            if (!response.isSuccessful()) {
                if (isUpstreamFailure(response.statusCode())) {
                    log.warn("Keycloak answered HTTP {} when listing the users of tenant {}", response.statusCode(),
                            tenantId);
                    throw new UpstreamServiceException(UNAVAILABLE);
                }
                throw new BadRequestException("Keycloak returned " + response.statusCode() + " when listing users");
            }
            return objectMapper.readerForListOf(JsonNode.class).readValue(response.body());
        } catch (BadRequestException | UserManagementNotConfiguredException | UpstreamServiceException e) {
            throw e;
        } catch (JsonProcessingException e) {
            log.warn("Keycloak returned an unreadable response for tenant {}", tenantId);
            throw new BadRequestException("Failed to list users in Keycloak");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamServiceException(UNAVAILABLE);
        } catch (IOException | RuntimeException e) {
            // Only the type is logged: messages of failed requests can echo request or response content.
            log.warn("Failed to list users of tenant {}: {}", tenantId, e.getClass().getSimpleName());
            throw new UpstreamServiceException(UNAVAILABLE);
        }
    }

    private URI usersUri(String realm, String email) {
        String url = trustProperties.getBaseUrl() + "/admin/realms/" + realm + "/users";
        if (StringUtils.hasText(email)) {
            url += "?email=" + URLEncoder.encode(email, StandardCharsets.UTF_8) + "&exact=true";
        }
        return URI.create(url);
    }

    private BoundedHttpExchange.Result getUsers(URI uri, String token) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(trustProperties.getDiscoveryReadTimeout())
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .GET()
                .build();
        return BoundedHttpExchange.send(httpClient, request, exchangeTimeout, MAX_RESPONSE_SIZE);
    }

    private String token(TokenKey key, String secret, boolean forceRefresh) throws IOException, InterruptedException {
        CachedToken cached = forceRefresh ? null : tokens.get(key);
        if (cached != null && Instant.now().isBefore(cached.refreshAt())) {
            return cached.token();
        }
        String form = "grant_type=client_credentials"
                + "&client_id=" + URLEncoder.encode(trustProperties.getUserManagementClientId(), StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(secret, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenEndpoint(key.realm())))
                .timeout(trustProperties.getDiscoveryReadTimeout())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        BoundedHttpExchange.Result response = BoundedHttpExchange.send(httpClient, request, exchangeTimeout,
                MAX_RESPONSE_SIZE);
        if (!response.isSuccessful()) {
            tokens.remove(key);
            int status = response.statusCode();
            if (status == HTTP_BAD_REQUEST || status == HTTP_UNAUTHORIZED) {
                log.info("Realm of tenant {} rejected the user-management client credentials with HTTP {}",
                        key.tenantId(), status);
                throw new UserManagementNotConfiguredException(NOT_CONFIGURED);
            }
            log.warn("Token endpoint of the realm of tenant {} answered HTTP {}", key.tenantId(), status);
            throw new UpstreamServiceException(UNAVAILABLE);
        }
        JsonNode json = objectMapper.readTree(response.body());
        JsonNode accessToken = json.get("access_token");
        if (accessToken == null || !accessToken.isTextual() || accessToken.asText().isBlank()) {
            log.warn("Token response of the realm of tenant {} contains no access_token", key.tenantId());
            throw new UpstreamServiceException(UNAVAILABLE);
        }
        long lifetime = json.hasNonNull("expires_in") ? json.get("expires_in").asLong(DEFAULT_TOKEN_LIFETIME_SECONDS)
                : DEFAULT_TOKEN_LIFETIME_SECONDS;
        CachedToken fresh = new CachedToken(accessToken.asText(),
                Instant.now().plusSeconds(Math.max(0, lifetime - REFRESH_BEFORE_EXPIRY_SECONDS)));
        tokens.put(key, fresh);
        // Entries of the tenant under another realm or secret can never be used again.
        tokens.keySet().removeIf(other -> other.tenantId().equals(key.tenantId()) && !other.equals(key));
        return fresh.token();
    }

    // Read on a cache miss only: the discovery client is not called while a cached token is valid.
    private String tokenEndpoint(String realm) {
        String tokenEndpoint = discoveryClient.fetch(trustProperties.issuerFor(realm)).tokenEndpoint();
        if (!StringUtils.hasText(tokenEndpoint)) {
            log.warn("Discovery document of realm {} declares no token_endpoint", realm);
            throw new UpstreamServiceException(UNAVAILABLE);
        }
        return tokenEndpoint;
    }

    // A server error or throttling is Keycloak's failure, not a refusal of the request.
    private static boolean isUpstreamFailure(int status) {
        return status >= HTTP_SERVER_ERROR || status == HTTP_TOO_MANY_REQUESTS;
    }

    // Non-reversible fingerprint of the secret for the cache key; never logged.
    private static String fingerprint(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, FINGERPRINT_HEX_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private record TokenKey(String tenantId, String realm, String secretFingerprint) {
    }

    private record CachedToken(String token, Instant refreshAt) {
    }
}
