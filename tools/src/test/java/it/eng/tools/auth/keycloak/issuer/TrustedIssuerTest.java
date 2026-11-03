package it.eng.tools.auth.keycloak.issuer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrustedIssuerTest {

    private static final String ISSUER = "https://kc.example/realms/r";

    @Test
    @DisplayName("Valid static and tenant issuers are created")
    void validIssuers() {
        assertEquals(IssuerType.PLATFORM, new TrustedIssuer(ISSUER, "r", IssuerType.PLATFORM, null).type());
        assertEquals("t1", new TrustedIssuer(ISSUER, "r", IssuerType.TENANT, "t1").tenantId());
    }

    @Test
    @DisplayName("Missing components and a misplaced tenant id are rejected")
    void invalidIssuers() {
        assertThrows(NullPointerException.class, () -> new TrustedIssuer(null, "r", IssuerType.ADMIN, null));
        assertThrows(NullPointerException.class, () -> new TrustedIssuer(ISSUER, null, IssuerType.ADMIN, null));
        assertThrows(NullPointerException.class, () -> new TrustedIssuer(ISSUER, "r", null, null));
        assertThrows(IllegalArgumentException.class, () -> new TrustedIssuer(ISSUER, "r", IssuerType.TENANT, null));
        assertThrows(IllegalArgumentException.class, () -> new TrustedIssuer(ISSUER, "r", IssuerType.ADMIN, "t1"));
    }
}
