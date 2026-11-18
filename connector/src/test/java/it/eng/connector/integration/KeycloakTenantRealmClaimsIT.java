package it.eng.connector.integration;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the claims carried by tokens issued from the seeded tenant realms.
 *
 * <p>Only the claims are asserted; binding a token to a tenant route is out of scope here.
 */
class KeycloakTenantRealmClaimsIT extends BaseKeycloakIntegrationTest {

    private static final String AUDIENCE = "dsp-true-connector";
    private static final String AUTHORIZED_PARTY = "keycloak-api";

    @Test
    @DisplayName("Tenant A admin token carries tenant_id engineering-a, audience and azp")
    void tenantAAdminTokenClaims() throws Exception {
        assertClaims(adminAccessToken(), "engineering-a");
    }

    @Test
    @DisplayName("Tenant B admin token carries tenant_id engineering-b, audience and azp")
    void tenantBAdminTokenClaims() throws Exception {
        assertClaims(tenantBAdminAccessToken(), "engineering-b");
    }

    private void assertClaims(String accessToken, String expectedTenantId) {
        DecodedJWT jwt = JWT.decode(accessToken);

        assertThat(jwt.getClaim("tenant_id").asString()).isEqualTo(expectedTenantId);
        assertThat(jwt.getClaim("azp").asString()).isEqualTo(AUTHORIZED_PARTY);
        assertThat(jwt.getAudience()).contains(AUDIENCE);
    }
}
