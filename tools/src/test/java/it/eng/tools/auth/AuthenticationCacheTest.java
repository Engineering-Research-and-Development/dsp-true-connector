package it.eng.tools.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.eng.tools.auth.keycloak.KeycloakAuthenticationProperties;
import it.eng.tools.auth.keycloak.KeycloakAuthenticationService;

@ExtendWith(MockitoExtension.class)
class AuthenticationCacheTest {

    private AuthenticationCache authenticationCache;

    @Mock
    private KeycloakAuthenticationService keycloakAuthService;
    @Mock
    private KeycloakAuthenticationProperties keycloakProperties;

    @BeforeEach
    void setUp() {
        authenticationCache = new AuthenticationCache(List.of(keycloakAuthService), keycloakProperties);
    }

    @Test
    @DisplayName("Should return null token when no providers are configured")
    void noProvidersReturnsNulloken() {
        AuthenticationCache cache = new AuthenticationCache(List.of(), null);
        assertNull(cache.getToken("ROLE_CONNECTOR"));
    }


    @Test
    @DisplayName("Should return null token when Keycloak properties are not configured")
    //TODO check if this test makes sense
    void noKeycloakPropertiesReturnsNullToken() {
        AuthenticationCache cache = new AuthenticationCache(List.of(keycloakAuthService), null);
        when(keycloakAuthService.fetchToken("ROLE_CONNECTOR")).thenReturn(null);
        assertNull(cache.getToken("ROLE_CONNECTOR"));
        verify(keycloakAuthService, times(1)).fetchToken("ROLE_CONNECTOR");
    }

    @Test
    @DisplayName("Should return cached Keycloak token when caching is enabled and token is valid")
    void keycloakCacheEnabled() throws IllegalAccessException {
        when(keycloakProperties.isTokenCaching()).thenReturn(true);

        FieldUtils.writeField(authenticationCache, "cachedToken", "KEYCLOAK_TOKEN", true);
        FieldUtils.writeField(authenticationCache, "expirationTime", LocalDateTime.now().plusDays(1L), true);

        String token = authenticationCache.getToken("ROLE_CONNECTOR");

        verify(keycloakAuthService, times(0)).fetchToken("ROLE_CONNECTOR");
        assertEquals("KEYCLOAK_TOKEN", token);
    }

    @Test
    @DisplayName("Should fetch fresh Keycloak token when caching is disabled")
    void keycloakCacheDisabled() {
        when(keycloakProperties.isTokenCaching()).thenReturn(false);
        when(keycloakAuthService.fetchToken("ROLE_CONNECTOR")).thenReturn(JwtTokenTestUtils.createTestToken());

        String token = authenticationCache.getToken("ROLE_CONNECTOR");

        assertNotNull(token);
        verify(keycloakAuthService).fetchToken("ROLE_CONNECTOR");
    }

    @Test
    @DisplayName("Should refresh Keycloak token when cached token has expired")
    void keycloakTokenExpired() throws IllegalAccessException {
        when(keycloakProperties.isTokenCaching()).thenReturn(true);
        when(keycloakAuthService.fetchToken("ROLE_CONNECTOR")).thenReturn(JwtTokenTestUtils.createTestToken());

        FieldUtils.writeField(authenticationCache, "cachedToken", "EXPIRED", true);
        FieldUtils.writeField(authenticationCache, "expirationTime", LocalDateTime.now().minusDays(1L), true);

        String token = authenticationCache.getToken("ROLE_CONNECTOR");

        assertNotNull(token);
        verify(keycloakAuthService).fetchToken("ROLE_CONNECTOR");
    }

    @Test
    @DisplayName("Should return null when fetched Keycloak token cannot be decoded")
    void keycloakTokenInvalid() throws IllegalAccessException {
        when(keycloakProperties.isTokenCaching()).thenReturn(true);
        when(keycloakAuthService.fetchToken("ROLE_CONNECTOR")).thenReturn("INVALID");

        FieldUtils.writeField(authenticationCache, "cachedToken", "OLD", true);
        FieldUtils.writeField(authenticationCache, "expirationTime", LocalDateTime.now().minusDays(1L), true);

        String token = authenticationCache.getToken("ROLE_CONNECTOR");

        assertNull(token);
        verify(keycloakAuthService).fetchToken("ROLE_CONNECTOR");
    }
}
