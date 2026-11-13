package it.eng.tools.util;

import it.eng.tools.auth.ConnectorCredentialProvider;
import it.eng.tools.auth.M2mTokenCache;
import it.eng.tools.auth.M2mTokenUnavailableException;
import it.eng.tools.auth.keycloak.KeycloakAuthenticationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CredentialUtils}.
 */
@ExtendWith(MockitoExtension.class)
class CredentialUtilsTest {

    @Mock
    private M2mTokenCache m2mTokenCache;
    @Mock
    private ObjectProvider<ConnectorCredentialProvider> connectorCredentialProviderObjectProvider;
    @Mock
    private ConnectorCredentialProvider connectorCredentialProvider;
    @Mock
    private ObjectProvider<KeycloakAuthenticationService> keycloakServiceObjectProvider;
    @Mock
    private KeycloakAuthenticationService keycloakAuthenticationService;

    private CredentialUtils credentialUtils;

    @BeforeEach
    void setUp() {
        credentialUtils = new CredentialUtils(m2mTokenCache, connectorCredentialProviderObjectProvider,
                keycloakServiceObjectProvider);
    }

    @Test
    @DisplayName("getConnectorCredentials: should use the INTERNAL-mode provider/cache path when present")
    void getConnectorCredentials_providerPresent_usesCache() {
        when(connectorCredentialProviderObjectProvider.getIfAvailable()).thenReturn(connectorCredentialProvider);
        when(m2mTokenCache.getOrFetch(ArgumentMatchers.eq("connector-m2m"), ArgumentMatchers.any()))
                .thenReturn("m2m-token");

        String result = credentialUtils.getConnectorCredentials();

        assertEquals("Bearer m2m-token", result);
        verifyNoInteractions(keycloakServiceObjectProvider);
    }

    @Test
    @DisplayName("getConnectorCredentials: should use the Keycloak platform token when the INTERNAL provider is absent")
    void getConnectorCredentials_providerAbsent_usesKeycloak() {
        when(connectorCredentialProviderObjectProvider.getIfAvailable()).thenReturn(null);
        when(keycloakServiceObjectProvider.getIfAvailable()).thenReturn(keycloakAuthenticationService);
        when(keycloakAuthenticationService.getConnectorToken()).thenReturn("keycloak-token");

        String result = credentialUtils.getConnectorCredentials();

        assertEquals("Bearer keycloak-token", result);
    }

    @Test
    @DisplayName("getConnectorCredentials: should fall back to Keycloak when the provider is present but yields no token")
    void getConnectorCredentials_providerPresentButNoToken_fallsBackToKeycloak() {
        when(connectorCredentialProviderObjectProvider.getIfAvailable()).thenReturn(connectorCredentialProvider);
        when(m2mTokenCache.getOrFetch(ArgumentMatchers.eq("connector-m2m"), ArgumentMatchers.any()))
                .thenReturn(null);
        when(keycloakServiceObjectProvider.getIfAvailable()).thenReturn(keycloakAuthenticationService);
        when(keycloakAuthenticationService.getConnectorToken()).thenReturn("keycloak-token");

        String result = credentialUtils.getConnectorCredentials();

        assertEquals("Bearer keycloak-token", result);
    }

    @Test
    @DisplayName("getConnectorCredentials: should propagate a failed Keycloak token request instead of returning null")
    void getConnectorCredentials_keycloakFailure_throws() {
        when(connectorCredentialProviderObjectProvider.getIfAvailable()).thenReturn(null);
        when(keycloakServiceObjectProvider.getIfAvailable()).thenReturn(keycloakAuthenticationService);
        when(keycloakAuthenticationService.getConnectorToken())
                .thenThrow(new M2mTokenUnavailableException("Token request failed with HTTP 401 (invalid_client)"));

        assertThrows(M2mTokenUnavailableException.class, () -> credentialUtils.getConnectorCredentials());
    }

    @Test
    @DisplayName("getConnectorCredentials: should return null when no credential source is configured")
    void getConnectorCredentials_noSource_returnsNull() {
        when(connectorCredentialProviderObjectProvider.getIfAvailable()).thenReturn(null);
        when(keycloakServiceObjectProvider.getIfAvailable()).thenReturn(null);

        assertNull(credentialUtils.getConnectorCredentials());
    }

    @Test
    @DisplayName("invalidateCachedCredentials: should evict only the connector-m2m slot when Keycloak is not active")
    void invalidateCachedCredentials_evictsConnectorSlot() {
        when(keycloakServiceObjectProvider.getIfAvailable()).thenReturn(null);

        credentialUtils.invalidateCachedCredentials();

        verify(m2mTokenCache).invalidate("connector-m2m");
        verifyNoMoreInteractions(m2mTokenCache);
    }

    @Test
    @DisplayName("invalidateCachedCredentials: should also evict the KEYCLOAK entry in Keycloak mode")
    void invalidateCachedCredentials_evictsKeycloakEntry() {
        when(keycloakServiceObjectProvider.getIfAvailable()).thenReturn(keycloakAuthenticationService);

        credentialUtils.invalidateCachedCredentials();

        verify(m2mTokenCache).invalidate("connector-m2m");
        verify(keycloakAuthenticationService).invalidateConnectorToken();
    }
}
