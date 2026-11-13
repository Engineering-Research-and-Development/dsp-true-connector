package it.eng.tools.auth.keycloak;

import it.eng.tools.auth.ConnectorCredentialProvider;
import it.eng.tools.auth.M2mTokenCache;
import it.eng.tools.auth.internal.InternalCallTokenIssuer;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.util.CredentialUtils;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Wires {@link OkHttpRestClient}, {@link CredentialUtils} and {@link KeycloakAuthenticationService} as in
 * {@code KEYCLOAK} mode and sends protocol calls to a peer through a local stand-in for Keycloak.
 */
class KeycloakOutboundProtocolCallTest {

    private FakeKeycloakServer keycloak;
    private CredentialUtils credentialUtils;
    private OkHttpRestClient restClient;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws IOException {
        keycloak = new FakeKeycloakServer();
        KeycloakTrustProperties trustProperties = new KeycloakTrustProperties();
        trustProperties.setBaseUrl(keycloak.baseUrl());
        trustProperties.init();
        KeycloakAuthenticationProperties properties = new KeycloakAuthenticationProperties();
        properties.setClientId("dsp-connector-backend");
        properties.setClientSecret("very-secret-client-value");
        OkHttpClient okHttpClient = new OkHttpClient();
        M2mTokenCache tokenCache = new M2mTokenCache();
        KeycloakAuthenticationService service = new KeycloakAuthenticationService(properties, trustProperties,
                KeycloakTestHttpClients.discoveryClient(trustProperties), tokenCache, okHttpClient);

        ObjectProvider<ConnectorCredentialProvider> noInternalProvider = mock(ObjectProvider.class);
        ObjectProvider<KeycloakAuthenticationService> keycloakProvider = mock(ObjectProvider.class);
        when(keycloakProvider.getIfAvailable()).thenReturn(service);
        credentialUtils = new CredentialUtils(tokenCache, noInternalProvider, keycloakProvider);
        restClient = new OkHttpRestClient(okHttpClient, credentialUtils, new InternalCallTokenIssuer(), "8080", false);
    }

    @AfterEach
    void tearDown() {
        keycloak.close();
    }

    @Test
    @DisplayName("Protocol calls carry the cached platform token")
    void protocolCallsReuseCachedToken() {

        assertTrue(restClient.sendRequestProtocol(keycloak.peerUrl(), null, credentialUtils::getConnectorCredentials)
                .isSuccess());
        assertTrue(restClient.sendRequestProtocol(keycloak.peerUrl(), null, credentialUtils::getConnectorCredentials)
                .isSuccess());

        List<String> authorizations = keycloak.peerAuthorizations();
        assertEquals(2, authorizations.size());
        assertTrue(authorizations.getFirst().startsWith("Bearer "));
        assertEquals(authorizations.get(0), authorizations.get(1));
        assertEquals(1, keycloak.count(keycloak.tokenPath()));
    }

    @Test
    @DisplayName("A peer 401 evicts the KEYCLOAK entry and the retry carries a new token")
    void peer401EvictsKeycloakEntry() {
        keycloak.peerStatuses(401);

        GenericApiResponse<String> response = restClient.sendRequestProtocol(keycloak.peerUrl(), null,
                credentialUtils::getConnectorCredentials);

        assertTrue(response.isSuccess());
        List<String> authorizations = keycloak.peerAuthorizations();
        assertEquals(2, authorizations.size());
        assertNotEquals(authorizations.get(0), authorizations.get(1));
        assertEquals(2, keycloak.count(keycloak.tokenPath()));
    }

    @Test
    @DisplayName("When no token can be obtained the protocol call is not sent")
    void tokenUnavailableRequestNotSent() {
        keycloak.tokenResponse(401, "{\"error\":\"invalid_client\"}");

        GenericApiResponse<String> response = restClient.sendRequestProtocol(keycloak.peerUrl(), null,
                credentialUtils::getConnectorCredentials);

        assertFalse(response.isSuccess());
        assertTrue(keycloak.peerAuthorizations().isEmpty());
        assertTrue(keycloak.requestPaths().stream().noneMatch(path -> path.contains("/realms/dsp-connector/")));
    }
}
