package it.eng.connector.integration;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import it.eng.tools.util.CredentialUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that in {@code KEYCLOAK} mode the outbound connector-to-connector token comes from the
 * {@code platform} realm, through the token endpoint of its discovery document.
 */
class KeycloakOutboundTokenIT extends BaseKeycloakIntegrationTest {

    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired
    private CredentialUtils credentialUtils;

    @Test
    @DisplayName("Connector credentials carry a client credentials token of the platform realm and are cached")
    void connectorCredentialsComeFromPlatformRealm() {
        String authorization = credentialUtils.getConnectorCredentials();

        assertThat(authorization).startsWith(BEARER_PREFIX);
        DecodedJWT jwt = JWT.decode(authorization.substring(BEARER_PREFIX.length()));
        assertThat(jwt.getIssuer()).isEqualTo(realmUrl("platform"));
        assertThat(jwt.getClaim("azp").asString()).isEqualTo("dsp-connector-backend");
        assertThat(credentialUtils.getConnectorCredentials()).isEqualTo(authorization);
    }
}
