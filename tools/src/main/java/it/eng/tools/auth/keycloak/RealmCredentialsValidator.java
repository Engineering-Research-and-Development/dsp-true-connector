package it.eng.tools.auth.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.tools.auth.condition.KeycloakAuthenticationModeCondition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Checks the client secret of a tenant realm's user-management client with a real client-credentials
 * token request. The token endpoint comes from the discovery document of the issuer built from
 * {@code base-url} and the realm name only. The token itself is discarded.
 * <p>
 * Failures are reported as {@code false} without any detail, so that neither the secret nor a Keycloak
 * response body can reach a log or an error message.
 */
@Slf4j
@Component
@Conditional(KeycloakAuthenticationModeCondition.class)
public class RealmCredentialsValidator {

    static final int MAX_RESPONSE_SIZE = 256 * 1024;

    private final KeycloakTrustProperties properties;
    private final KeycloakDiscoveryClient discoveryClient;
    private final HttpClient httpClient;
    private final Duration exchangeTimeout;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Creates the validator. It never follows redirects and uses the discovery timeouts of the trust configuration.
     *
     * @param properties the Keycloak trust configuration
     * @param discoveryClient loads the discovery document of the realm
     */
    public RealmCredentialsValidator(KeycloakTrustProperties properties, KeycloakDiscoveryClient discoveryClient) {
        this.properties = properties;
        this.discoveryClient = discoveryClient;
        this.exchangeTimeout = properties.getDiscoveryConnectTimeout().plus(properties.getDiscoveryReadTimeout());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getDiscoveryConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * Requests a client-credentials token for the user-management client of the realm.
     *
     * @param realm the realm name
     * @param clientSecret the secret to check
     * @return {@code true} only when the realm issued an access token for the secret
     */
    public boolean accepts(String realm, String clientSecret) {
        try {
            DiscoveryDocument document = discoveryClient.fetch(properties.issuerFor(realm));
            if (document.tokenEndpoint() == null || document.tokenEndpoint().isBlank()) {
                log.warn("Discovery document of realm {} declares no token_endpoint", realm);
                return false;
            }
            String form = "grant_type=client_credentials"
                    + "&client_id=" + encode(properties.getUserManagementClientId())
                    + "&client_secret=" + encode(clientSecret);
            HttpRequest request = HttpRequest.newBuilder(URI.create(document.tokenEndpoint()))
                    .timeout(properties.getDiscoveryReadTimeout())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            BoundedHttpExchange.Result response = BoundedHttpExchange.send(httpClient, request, exchangeTimeout,
                    MAX_RESPONSE_SIZE);
            if (!response.isSuccessful()) {
                log.info("Realm {} rejected the user-management client credentials with HTTP {}", realm,
                        response.statusCode());
                return false;
            }
            JsonNode token = objectMapper.readTree(response.body()).get("access_token");
            return token != null && token.isTextual() && !token.asText().isBlank();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Validation of the user-management credentials of realm {} was interrupted", realm);
            return false;
        } catch (IOException | RuntimeException e) {
            // Only the type is logged: messages of failed requests can echo request or response content.
            log.warn("Validation of the user-management credentials of realm {} failed: {}", realm,
                    e.getClass().getSimpleName());
            return false;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
