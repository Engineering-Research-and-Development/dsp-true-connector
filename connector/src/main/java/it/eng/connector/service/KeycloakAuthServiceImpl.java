package it.eng.connector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.tools.auth.condition.KeycloakAuthenticationModeCondition;
import it.eng.tools.auth.keycloak.KeycloakLoginProperties;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.service.AuditEventPublisher;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@Conditional(KeycloakAuthenticationModeCondition.class)
public class KeycloakAuthServiceImpl implements AuthService {

    private final KeycloakLoginProperties keycloakLoginProperties;
    private final OkHttpRestClient okHttpRestClient;
    private final AuditEventPublisher publisher;

    public KeycloakAuthServiceImpl(KeycloakLoginProperties keycloakLoginProperties,
                                   OkHttpRestClient okHttpRestClient,
                                   AuditEventPublisher publisher) {
        this.keycloakLoginProperties = keycloakLoginProperties;
        this.okHttpRestClient = okHttpRestClient;
        this.publisher = publisher;
    }

    @Override
    public AuthTokens login(String email, String password) {
        FormBody.Builder formBuilder = new FormBody.Builder()
                .add("grant_type", "password")
                .add("client_id", keycloakLoginProperties.getClientId())
                .add("username", email)
                .add("password", password)
                .add("scope", "openid");

        // Include client_secret only if your Keycloak client is confidential
        if (keycloakLoginProperties.getClientSecret() != null && !keycloakLoginProperties.getClientSecret().trim().isEmpty()) {
            formBuilder.add("client_secret", keycloakLoginProperties.getClientSecret());
        }

        RequestBody requestBody = formBuilder.build();

        // Build the HTTP POST request
        Request request = new Request.Builder()
                .url(keycloakLoginProperties.getTokenUrl())
                .post(requestBody)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();

        // Execute the request synchronously
        try (Response response = okHttpRestClient.executeCall(request)) {
            if (!response.isSuccessful()) {
                log.error("Unexpected code {}", response);
                publisher.publishEvent(AuditEvent.Builder.newInstance()
                        .eventType(AuditEventType.APPLICATION_LOGIN_FAILED)
                        .description("Unable to get token from Keycloak")
                        .username(email)
                        .details(auditMap("statusCode", response.code()))
                        .build());
                throw new BadCredentialsException("Unable to get token from Keycloak");
            }

            // Return the JSON payload containing access_token, refresh_token, etc.
            Map<String, Object> tokenResponse = new ObjectMapper().readValue(response.body().string(), Map.class);
            log.info("Token response from Keycloak received");
            publisher.publishEvent(AuditEvent.Builder.newInstance()
                    .eventType(AuditEventType.APPLICATION_LOGIN)
                    .description("User logged in successfully via Keycloak")
                    .username(email)
                    .build());

            return tokenResponse != null ? new AuthTokens(
                    tokenResponse.get("access_token").toString(),
                    tokenResponse.get("refresh_token").toString(),
                    ((Number) tokenResponse.get("expires_in")).longValue()) : null;
        } catch (BadCredentialsException e) {
            throw e;
        } catch (Exception e) {
            publisher.publishEvent(AuditEvent.Builder.newInstance()
                    .eventType(AuditEventType.APPLICATION_LOGIN_FAILED)
                    .description("Unable to get token from Keycloak")
                    .username(email)
                    .details(auditMap("errorMessage", e.getMessage()))
                    .build());
            throw new BadCredentialsException("Unable to get token from Keycloak");
        }
    }

    @Override
    public AuthTokens refresh(String refreshTokenId) {
        FormBody.Builder formBuilder = new FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", keycloakLoginProperties.getClientId())
                .add("refresh_token", refreshTokenId);

        // Include client_secret only if your Keycloak client is confidential
        if (keycloakLoginProperties.getClientSecret() != null && !keycloakLoginProperties.getClientSecret().trim().isEmpty()) {
            formBuilder.add("client_secret", keycloakLoginProperties.getClientSecret());
        }

        RequestBody requestBody = formBuilder.build();

        // Build the HTTP POST request
        Request request = new Request.Builder()
                .url(keycloakLoginProperties.getTokenUrl())
                .post(requestBody)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();

        // Execute the request synchronously
        try (Response response = okHttpRestClient.executeCall(request)) {
            if (!response.isSuccessful()) {
                log.error("Refreshing access token failed. Server response: {} | Body: {}", response, response.body().string());
                publisher.publishEvent(AuditEvent.Builder.newInstance()
                        .eventType(AuditEventType.APPLICATION_TOKEN_REFRESH_FAILED)
                        .description("Unable to get token from Keycloak using refresh token")
                        .details(auditMap("statusCode", response.code()))
                        .build());
                throw new BadCredentialsException("Unable to get token from Keycloak using refresh token");
            }

            // Return the JSON payload containing access_token, refresh_token, etc.
            Map<String, Object> tokenResponse = new ObjectMapper().readValue(response.body().string(), Map.class);
            log.info("Token response from Keycloak received - refreshToken flow");
            publisher.publishEvent(AuditEvent.Builder.newInstance()
                    .eventType(AuditEventType.APPLICATION_TOKEN_REFRESHED)
                    .description("Token refreshed successfully via Keycloak")
                    .build());

            return tokenResponse != null ? new AuthTokens(
                    tokenResponse.get("access_token").toString(),
                    tokenResponse.get("refresh_token").toString(),
                    ((Number) tokenResponse.get("expires_in")).longValue()) : null;
        } catch (BadCredentialsException e) {
            throw e;
        } catch (Exception e) {
            publisher.publishEvent(AuditEvent.Builder.newInstance()
                    .eventType(AuditEventType.APPLICATION_TOKEN_REFRESH_FAILED)
                    .description("Unable to get token from Keycloak using refresh token")
                    .details(auditMap("errorMessage", e.getMessage()))
                    .build());
            throw new BadCredentialsException("Unable to get token from Keycloak");
        }
    }

    @Override
    public void logout(String refreshTokenId) {
        // Build the x-www-form-urlencoded request body
        // Keycloak requires the refresh_token to identify and terminate the specific session
        FormBody.Builder formBuilder = new FormBody.Builder()
                .add("client_id", keycloakLoginProperties.getClientId())
                .add("refresh_token", refreshTokenId);

        // Include client_secret only if your Keycloak client is confidential
        if (keycloakLoginProperties.getClientSecret() != null && !keycloakLoginProperties.getClientSecret().trim().isEmpty()) {
            formBuilder.add("client_secret", keycloakLoginProperties.getClientSecret());
        }

        RequestBody requestBody = formBuilder.build();

        // Build the HTTP POST request
        Request request = new Request.Builder()
                .url(keycloakLoginProperties.getLogoutUrl())
                .post(requestBody)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();

        // Execute the request synchronously
        try (Response response = okHttpRestClient.executeCall(request)) {
            if (!response.isSuccessful()) {
                log.error("Logout failed. Server response: {} | Body: {}", response, response.body());
                publisher.publishEvent(AuditEvent.Builder.newInstance()
                        .eventType(AuditEventType.APPLICATION_LOGOUT_FAILED)
                        .description("Unable to logout from Keycloak using refresh token")
                        .details(auditMap("statusCode", response.code()))
                        .build());
                throw new BadCredentialsException("Unable to logout from Keycloak using refresh token");
            }
            log.info("Logout successful! Status code: {}", response.code());
            publisher.publishEvent(AuditEvent.Builder.newInstance()
                    .eventType(AuditEventType.APPLICATION_LOGOUT)
                    .description("Logout successful via Keycloak")
                    .details(auditMap("statusCode", response.code()))
                    .build());
        } catch (BadCredentialsException e) {
            throw e;
        } catch (Exception e) {
            publisher.publishEvent(AuditEvent.Builder.newInstance()
                    .eventType(AuditEventType.APPLICATION_LOGOUT_FAILED)
                    .description("Unable to logout from Keycloak using refresh token")
                    .details(auditMap("errorMessage", e.getMessage()))
                    .build());
            throw new BadCredentialsException("Unable to logout from Keycloak using refresh token");
        }
    }

    /**
     * Helper to construct audit event maps, silently skipping null values.
     *
     * @param keyValuePairs an array of key-value pairs
     * @return a map containing the non-null key-value pairs
     */
    private Map<String, Object> auditMap(Object... keyValuePairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValuePairs.length - 1; i += 2) {
            Object value = keyValuePairs[i + 1];
            if (value != null) {
                map.put((String) keyValuePairs[i], value);
            }
        }
        return map;
    }
}