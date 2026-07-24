package it.eng.connector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.tools.auth.condition.KeycloakAuthenticationModeCondition;
import it.eng.tools.auth.keycloak.KeycloakLoginProperties;
import it.eng.tools.client.rest.OkHttpRestClient;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

@Service
@Slf4j
@Conditional(KeycloakAuthenticationModeCondition.class)
public class KeycloakAuthServiceImpl implements AuthService {

    private final KeycloakLoginProperties keycloakLoginProperties;
    private final OkHttpRestClient okHttpRestClient;

    public KeycloakAuthServiceImpl(KeycloakLoginProperties keycloakLoginProperties, OkHttpRestClient okHttpRestClient) {
        this.keycloakLoginProperties = keycloakLoginProperties;
        this.okHttpRestClient = okHttpRestClient;
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
                throw new BadCredentialsException("Unable to get token from Keycloak");
            }

            // Return the JSON payload containing access_token, refresh_token, etc.
            Map<String, Object> tokenResponse = new ObjectMapper().readValue(response.body().string(), Map.class);

            return tokenResponse != null ? new AuthTokens(
                    tokenResponse.get("access_token").toString(),
                    tokenResponse.get("refresh_token").toString(),
                    ((Number) tokenResponse.get("expires_in")).longValue()) : null;
        } catch (IOException e) {
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
                throw new BadCredentialsException("Unable to get token from Keycloak using refresh token");
            }

            // Return the JSON payload containing access_token, refresh_token, etc.
            Map<String, Object> tokenResponse = new ObjectMapper().readValue(response.body().string(), Map.class);

            return tokenResponse != null ? new AuthTokens(
                    tokenResponse.get("access_token").toString(),
                    tokenResponse.get("refresh_token").toString(),
                    ((Number) tokenResponse.get("expires_in")).longValue()) : null;
        } catch (IOException e) {
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

        // 4. Execute the request synchronously
        try (Response response = okHttpRestClient.executeCall(request)) {
            if (!response.isSuccessful()) {
                log.error("Logout failed. Server response: {} | Body: {}", response, response.body());
                throw new BadCredentialsException("Unable to logout from Keycloak using refresh token");
            }

            log.info("Logout successful! Status code: {}", response.code());
        }
    }
}
