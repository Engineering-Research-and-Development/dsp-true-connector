package it.eng.tools.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import it.eng.tools.auth.AuthenticationCache;
import it.eng.tools.auth.ConnectorCredentialProvider;
import it.eng.tools.auth.M2mTokenCache;
import it.eng.tools.auth.internal.InternalServiceTokenIssuer;

/**
 * Unit tests for {@link CredentialUtils}.
 */
@ExtendWith(MockitoExtension.class)
class CredentialUtilsTest {

    @Mock
    private AuthenticationCache authenticationCache;
    @Mock
    private M2mTokenCache m2mTokenCache;
    @Mock
    private ObjectProvider<ConnectorCredentialProvider> connectorCredentialProviderObjectProvider;
    @Mock
    private ObjectProvider<InternalServiceTokenIssuer> internalServiceTokenIssuerObjectProvider;
    @Mock
    private ConnectorCredentialProvider connectorCredentialProvider;
    @Mock
    private InternalServiceTokenIssuer internalServiceTokenIssuer;

    private CredentialUtils credentialUtils;

    @BeforeEach
    void setUp() {
        credentialUtils = new CredentialUtils(authenticationCache, m2mTokenCache,
                connectorCredentialProviderObjectProvider, internalServiceTokenIssuerObjectProvider);
    }

    @Test
    @DisplayName("getConnectorCredentials: should use the INTERNAL-mode provider/cache path when present")
    void getConnectorCredentials_providerPresent_usesCache() {
        when(connectorCredentialProviderObjectProvider.getIfAvailable()).thenReturn(connectorCredentialProvider);
        when(m2mTokenCache.getOrFetch(org.mockito.ArgumentMatchers.eq("connector-m2m"), org.mockito.ArgumentMatchers.any()))
                .thenReturn("m2m-token");

        String result = credentialUtils.getConnectorCredentials();

        assertEquals("Bearer m2m-token", result);
    }

    @Test
    @DisplayName("getConnectorCredentials: should fall back to Keycloak cache when provider is absent")
    void getConnectorCredentials_providerAbsent_fallsBackToKeycloak() {
        when(connectorCredentialProviderObjectProvider.getIfAvailable()).thenReturn(null);
        when(authenticationCache.getToken("ROLE_CONNECTOR")).thenReturn("keycloak-token");

        String result = credentialUtils.getConnectorCredentials();

        assertEquals("Bearer keycloak-token", result);
    }

    @Test
    @DisplayName("getConnectorCredentials: should fall back to Keycloak when provider is present but yields no token")
    void getConnectorCredentials_providerPresentButNoToken_fallsBackToKeycloak() {
        when(connectorCredentialProviderObjectProvider.getIfAvailable()).thenReturn(connectorCredentialProvider);
        when(m2mTokenCache.getOrFetch(org.mockito.ArgumentMatchers.eq("connector-m2m"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(null);
        when(authenticationCache.getToken("ROLE_CONNECTOR")).thenReturn("keycloak-token");

        String result = credentialUtils.getConnectorCredentials();

        assertEquals("Bearer keycloak-token", result);
    }

    @Test
    @DisplayName("getAPICredentials: should use the INTERNAL-mode issuer/cache path when present")
    void getAPICredentials_issuerPresent_usesCache() {
        when(internalServiceTokenIssuerObjectProvider.getIfAvailable()).thenReturn(internalServiceTokenIssuer);
        when(m2mTokenCache.getOrFetch(org.mockito.ArgumentMatchers.eq("internal-api"), org.mockito.ArgumentMatchers.any()))
                .thenReturn("internal-token");

        String result = credentialUtils.getAPICredentials();

        assertEquals("Bearer internal-token", result);
    }

    @Test
    @DisplayName("getAPICredentials: should fall back to Keycloak cache when issuer is absent")
    void getAPICredentials_issuerAbsent_fallsBackToKeycloak() {
        when(internalServiceTokenIssuerObjectProvider.getIfAvailable()).thenReturn(null);
        when(authenticationCache.getToken("ROLE_ADMIN")).thenReturn("keycloak-admin-token");

        String result = credentialUtils.getAPICredentials();

        assertEquals("Bearer keycloak-admin-token", result);
    }

    @Test
    @DisplayName("getAPICredentials: should fall back to Keycloak when issuer is present but yields no token")
    void getAPICredentials_issuerPresentButNoToken_fallsBackToKeycloak() {
        when(internalServiceTokenIssuerObjectProvider.getIfAvailable()).thenReturn(internalServiceTokenIssuer);
        when(m2mTokenCache.getOrFetch(org.mockito.ArgumentMatchers.eq("internal-api"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(null);
        when(authenticationCache.getToken("ROLE_ADMIN")).thenReturn("keycloak-admin-token");

        String result = credentialUtils.getAPICredentials();

        assertEquals("Bearer keycloak-admin-token", result);
    }

    @Test
    @DisplayName("invalidateCachedCredentials: should evict both the connector-m2m and internal-api cache slots")
    void invalidateCachedCredentials_evictsBothSlots() {
        credentialUtils.invalidateCachedCredentials();

        verify(m2mTokenCache).invalidate("connector-m2m");
        verify(m2mTokenCache).invalidate("internal-api");
    }
}
