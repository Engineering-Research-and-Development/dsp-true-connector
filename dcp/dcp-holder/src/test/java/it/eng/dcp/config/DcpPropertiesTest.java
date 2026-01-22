package it.eng.dcp.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DcpPropertiesTest {

    @Test
    @DisplayName("basic getters and setters")
    void basicGettersSetters() {
        it.eng.dcp.common.config.DcpProperties p = new it.eng.dcp.common.config.DcpProperties();
        assertEquals(120, p.getClockSkewSeconds());
        assertTrue(p.getSupportedProfiles().isEmpty());

        p.setConnectorDid("did:web:example:connector");
        p.setBaseUrl("https://example.com");
        p.setClockSkewSeconds(60);
        p.setSupportedProfiles(List.of("VC11_SL2021_JWT"));
        p.setTrustedIssuers(Map.of("CredTypeA", List.of("did:example:issuer1")));

        assertEquals("did:web:example:connector", p.getConnectorDid());
        assertEquals("https://example.com", p.getBaseUrl());
        assertEquals(60, p.getClockSkewSeconds());
        assertEquals(1, p.getSupportedProfiles().size());
        assertTrue(p.getTrustedIssuers().containsKey("CredTypeA"));
    }
}

