package it.eng.dcp.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IssuerTrustServiceTest {

    @Test
    @DisplayName("addTrust/isTrusted/getTrustedIssuers/removeTrust lifecycle")
    void addTrustIsTrustedLifecycle() {
        IssuerTrustService svc = new InMemoryIssuerTrustService();

        String type = "CredentialA";
        String did = "did:example:issuer1";

        assertFalse(svc.isTrusted(type, did));
        assertTrue(svc.getTrustedIssuers(type).isEmpty());

        svc.addTrust(type, did);
        assertTrue(svc.isTrusted(type, did));
        Set<String> issuers = svc.getTrustedIssuers(type);
        assertEquals(1, issuers.size());
        assertTrue(issuers.contains(did));

        svc.removeTrust(type, did);
        assertFalse(svc.isTrusted(type, did));
        assertTrue(svc.getTrustedIssuers(type).isEmpty());
    }

    @Test
    @DisplayName("addTrust rejects invalid inputs")
    void addTrustRejectsInvalidInputs() {
        IssuerTrustService svc = new InMemoryIssuerTrustService();
        assertThrows(IllegalArgumentException.class, () -> svc.addTrust(null, "did:ex:1"));
        assertThrows(IllegalArgumentException.class, () -> svc.addTrust("", "did:ex:1"));
        assertThrows(IllegalArgumentException.class, () -> svc.addTrust("type", null));
        assertThrows(IllegalArgumentException.class, () -> svc.addTrust("type", "  "));
    }
}

