package it.eng.dcp.config;

import it.eng.dcp.common.config.DcpProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DcpPropertiesTest {

    @Test
    @DisplayName("basic getters and setters")
    void basicGettersSetters() {
        DcpProperties p = new DcpProperties();
        assertEquals(120, p.getClockSkewSeconds());

        p.setConnectorDid("did:web:example:connector");
        p.setBaseUrl("https://example.com");
        p.setClockSkewSeconds(60);

        // Set trusted issuers using the TrustedIssuers inner class
        DcpProperties.TrustedIssuers trustedIssuers = new DcpProperties.TrustedIssuers();
        trustedIssuers.setIssuers(Map.of("CredTypeA", "did:example:issuer1,did:example:issuer2"));
        p.setTrustedIssuers(trustedIssuers);

        assertEquals("did:web:example:connector", p.getConnectorDid());
        assertEquals("https://example.com", p.getBaseUrl());
        assertEquals(60, p.getClockSkewSeconds());
        assertTrue(p.getTrustedIssuers().getIssuers().containsKey("CredTypeA"));
        assertEquals("did:example:issuer1,did:example:issuer2", p.getTrustedIssuers().getIssuers().get("CredTypeA"));
    }
}

