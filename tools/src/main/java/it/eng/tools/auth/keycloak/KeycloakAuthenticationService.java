package it.eng.tools.auth.keycloak;

import java.io.IOException;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import it.eng.tools.auth.AuthProvider;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Keycloak authentication service for connector-to-connector communication.
 * Uses OAuth2 client credentials flow to obtain JWT access tokens.
 */
@Service
@Slf4j
@ConditionalOnProperty(value = "application.keycloak.enable", havingValue = "true")
public class KeycloakAuthenticationService implements AuthProvider {

    private final KeycloakAuthenticationProperties keycloakProperties;
    private final OkHttpClient okHttpClient;

    public KeycloakAuthenticationService(KeycloakAuthenticationProperties keycloakProperties, OkHttpClient okHttpClient) {
        this.keycloakProperties = keycloakProperties;
        this.okHttpClient = okHttpClient;
    }

    @PostConstruct
    public void init() {
        log.info("=== KeycloakAuthenticationService INITIALIZED ===");
        log.info("Keycloak client ID: {}", keycloakProperties != null ? keycloakProperties.getClientId() : "null");
        log.info("Keycloak token URL: {}", keycloakProperties != null ? keycloakProperties.getTokenUrl() : "null");
        log.info("Token caching enabled: {}", keycloakProperties != null && keycloakProperties.isTokenCaching());
        log.info("=== End KeycloakAuthenticationService initialization ===");
    }

    /**
     * Fetches an access token from Keycloak using client credentials flow.
     *
     * @return the access token, or null if the request fails
     */
    @Override
    public String fetchToken() {
        String token = null;
        Response tokenResponse = null;
        try {
            log.info("Retrieving Keycloak access token using client credentials...");

            RequestBody formBody = new FormBody.Builder()
                    .add("grant_type", "client_credentials")
                    .add("client_id", keycloakProperties.getClientId())
                    .add("client_secret", keycloakProperties.getClientSecret())
                    .build();

            Request request = new Request.Builder()
                    .url(keycloakProperties.getTokenUrl())
                    .post(formBody)
                    .build();

            tokenResponse = okHttpClient.newCall(request).execute();

            if (tokenResponse == null) {
                log.error("Token response is null");
                return null;
            }

            if (!tokenResponse.isSuccessful()) {
                log.error("Failed to fetch token. HTTP status: {}", tokenResponse.code());
                return null;
            }

            var responseBody = tokenResponse.body();
            if (responseBody == null) {
                log.error("Token response body is null");
                return null;
            }

            String jsonResponse = responseBody.string();
            ObjectNode node = new ObjectMapper().readValue(jsonResponse, ObjectNode.class);

            if (node.has("access_token")) {
                token = node.get("access_token").asText();
                log.info("Successfully retrieved Keycloak access token");
            } else {
                log.error("No 'access_token' field in response");
            }
        } catch (IOException e) {
            log.error("Error retrieving Keycloak token", e);
        } catch (Exception e) {
            log.error("Unexpected error while fetching token", e);
        } finally {
            if (tokenResponse != null) {
                tokenResponse.close();
            }
        }
        return token;
    }

    /**
     * Validates a Keycloak token.
     * Currently returns true for any non-null token.
     * Enhanced validation can be added by calling Keycloak's introspection endpoint.
     *
     * @param token the token to validate
     * @return true if valid, false otherwise
     */
    @Override
    public boolean validateToken(String token) {
        if (token == null) {
            log.error("Token is null");
            return false;
        }
        // Basic validation - token exists
        // For more robust validation, implement introspection endpoint call
        return true;
    }
}


