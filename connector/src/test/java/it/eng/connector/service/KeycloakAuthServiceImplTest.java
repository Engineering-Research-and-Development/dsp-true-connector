package it.eng.connector.service;

import it.eng.tools.auth.keycloak.KeycloakLoginProperties;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.service.AuditEventPublisher;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeycloakAuthServiceImplTest {

    @Mock
    private KeycloakLoginProperties keycloakLoginProperties;
    @Mock
    private OkHttpRestClient okHttpRestClient;
    @Mock
    private AuditEventPublisher auditEventPublisher;

    @Mock
    private Response response;

    @InjectMocks
    private KeycloakAuthServiceImpl keycloakAuthServiceImpl;

    @Test
    @DisplayName("Test login functionality")
    void login() {
        when(keycloakLoginProperties.getClientId()).thenReturn("my-client-id");
        when(keycloakLoginProperties.getClientSecret()).thenReturn("my-client-secret");
        when(keycloakLoginProperties.getTokenUrl()).thenReturn("http://localhost:8080/auth/realms/myrealm/protocol/openid-connect/token");
        when(okHttpRestClient.executeCall(any(Request.class))).thenReturn(response);

        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(okhttp3.ResponseBody.create("{\"access_token\":\"dummy-access-token\",\"refresh_token\":\"dummy-refresh-token\",\"expires_in\":3600}",
                okhttp3.MediaType.parse("application/json")));

        AuthService.AuthTokens authTokens = keycloakAuthServiceImpl.login("admin", "admin");

        assertNotNull(authTokens);
        assertNotNull(authTokens.accessToken());
        assertNotNull(authTokens.refreshToken());
        assertEquals(3600, authTokens.expiresInSeconds());
        verify(auditEventPublisher).publishEvent(argThat(event -> event.getEventType() == AuditEventType.APPLICATION_LOGIN));
    }

    @Test
    @DisplayName("keycloak returned error during login")
    void loginError() {
        when(keycloakLoginProperties.getClientId()).thenReturn("my-client-id");
        when(keycloakLoginProperties.getClientSecret()).thenReturn("my-client-secret");
        when(keycloakLoginProperties.getTokenUrl()).thenReturn("http://localhost:8080/auth/realms/myrealm/protocol/openid-connect/token");
        when(okHttpRestClient.executeCall(any(Request.class))).thenReturn(response);

        when(response.isSuccessful()).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> {
            keycloakAuthServiceImpl.login("admin", "admin");
        });
        verify(auditEventPublisher).publishEvent(argThat(event -> event.getEventType() == AuditEventType.APPLICATION_LOGIN_FAILED));
    }

    @Test
    @DisplayName("Test refresh functionality")
    void refresh() {
        when(keycloakLoginProperties.getClientId()).thenReturn("my-client-id");
        when(keycloakLoginProperties.getClientSecret()).thenReturn("my-client-secret");
        when(keycloakLoginProperties.getTokenUrl()).thenReturn("http://localhost:8080/auth/realms/myrealm/protocol/openid-connect/token");
        when(okHttpRestClient.executeCall(any(Request.class))).thenReturn(response);

        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(okhttp3.ResponseBody.create("{\"access_token\":\"dummy-access-token\",\"refresh_token\":\"dummy-refresh-token\",\"expires_in\":3600}",
                okhttp3.MediaType.parse("application/json")));

        AuthService.AuthTokens authTokens = keycloakAuthServiceImpl.refresh("dummy-refresh-token");

        assertNotNull(authTokens);
        assertNotNull(authTokens.accessToken());
        assertNotNull(authTokens.refreshToken());
        assertEquals(3600, authTokens.expiresInSeconds());
        verify(auditEventPublisher).publishEvent(argThat(event -> event.getEventType() == AuditEventType.APPLICATION_TOKEN_REFRESHED));
    }

    @Test
    @DisplayName("keycloak returned error during refresh")
    void refreshError() {
        when(keycloakLoginProperties.getClientId()).thenReturn("my-client-id");
        when(keycloakLoginProperties.getClientSecret()).thenReturn("my-client-secret");
        when(keycloakLoginProperties.getTokenUrl()).thenReturn("http://localhost:8080/auth/realms/myrealm/protocol/openid-connect/token");
        when(okHttpRestClient.executeCall(any(Request.class))).thenReturn(response);

        when(response.isSuccessful()).thenReturn(false);
        when(response.body()).thenReturn(okhttp3.ResponseBody.create("{\"error\":\"invalid_token\",\"error_description\":\"Refresh token is invalid\"}",
                okhttp3.MediaType.parse("application/json")));

        assertThrows(BadCredentialsException.class, () -> {
            keycloakAuthServiceImpl.refresh("dummy-refresh-token");
        });
        verify(auditEventPublisher).publishEvent(argThat(event -> event.getEventType() == AuditEventType.APPLICATION_TOKEN_REFRESH_FAILED));
    }

    @Test
    @DisplayName("Logout successful")
    void logout() {
        when(keycloakLoginProperties.getClientId()).thenReturn("my-client-id");
        when(keycloakLoginProperties.getClientSecret()).thenReturn("my-client-secret");
        when(keycloakLoginProperties.getLogoutUrl()).thenReturn("http://localhost:8080/auth/realms/myrealm/protocol/openid-connect/logout");
        when(okHttpRestClient.executeCall(any(Request.class))).thenReturn(response);

        when(response.isSuccessful()).thenReturn(true);

        assertDoesNotThrow(() -> {
            keycloakAuthServiceImpl.logout("dummy-refresh-token");
        });
        verify(auditEventPublisher).publishEvent(argThat(event -> event.getEventType() == AuditEventType.APPLICATION_LOGOUT));
    }

    @Test
    @DisplayName("Logout failed")
    void logoutError() {
        when(keycloakLoginProperties.getClientId()).thenReturn("my-client-id");
        when(keycloakLoginProperties.getClientSecret()).thenReturn("my-client-secret");
        when(keycloakLoginProperties.getLogoutUrl()).thenReturn("http://localhost:8080/auth/realms/myrealm/protocol/openid-connect/logout");
        when(okHttpRestClient.executeCall(any(Request.class))).thenReturn(response);

        when(response.isSuccessful()).thenReturn(false);
        when(response.body()).thenReturn(okhttp3.ResponseBody.create("{\"error\":\"invalid_token\",\"error_description\":\"Refresh token is invalid\"}",
                okhttp3.MediaType.parse("application/json")));

        assertThrows(BadCredentialsException.class, () -> {
            keycloakAuthServiceImpl.logout("dummy-refresh-token-invalid");
        });
        verify(auditEventPublisher).publishEvent(argThat(event -> event.getEventType() == AuditEventType.APPLICATION_LOGOUT_FAILED));
    }
}