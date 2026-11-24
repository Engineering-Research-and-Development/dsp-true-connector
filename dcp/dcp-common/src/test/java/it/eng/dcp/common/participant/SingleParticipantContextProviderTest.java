package it.eng.dcp.common.participant;

import it.eng.dcp.common.config.DcpProperties;
import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.model.ServiceEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleParticipantContextProviderTest {

    private static final String DID = "did:web:example.com:holder";

    private DcpProperties properties;
    private SingleParticipantContextProvider provider;

    @BeforeEach
    void setUp() {
        properties = new DcpProperties();
        properties.setConnectorDid(DID);
        properties.setTrustedIssuers(Map.of("MembershipCredential", "did:web:i1, did:web:i2"));
        properties.getIssuer().setLocation("did:web:issuer");
        DidDocumentConfig config = DidDocumentConfig.builder()
                .did(DID)
                .protocol("https")
                .host("example.com")
                .port("8080")
                .keystorePath("eckey.p12")
                .keystoreAlias("holder")
                .serviceEntries(List.of(
                        DidDocumentConfig.ServiceEntryConfig.builder()
                                .id("CredentialService").type("CredentialService").endpointPath("/dcp").build(),
                        DidDocumentConfig.ServiceEntryConfig.builder()
                                .id("IssuerService").type("IssuerService").endpointPath("").build()))
                .build();
        provider = new SingleParticipantContextProvider(properties, config);
    }

    @Test
    @DisplayName("the single participant has the ID 'default' and the configured DID, keys and endpoints")
    void defaultParticipant() {
        ParticipantContext context = provider.find(ParticipantContext.DEFAULT_ID).orElseThrow();

        assertEquals("default", context.id());
        assertEquals(DID, context.did());
        assertEquals(new SigningKeyReference("eckey.p12", "holder"), context.signingKey());
        assertEquals(List.of(
                new ServiceEntry("CredentialService", "CredentialService", "https://example.com:8080/dcp"),
                new ServiceEntry("IssuerService", "IssuerService", "https://example.com:8080")),
                context.serviceEntries());
        assertEquals(List.of("did:web:i1", "did:web:i2"), context.trustedIssuers().get("MembershipCredential"));
        assertEquals("did:web:issuer", context.issuerMetadata().issuerLocation());
        assertTrue(context.isEnabled());
    }

    @Test
    @DisplayName("an unknown ID returns empty")
    void unknownId() {
        assertEquals(Optional.empty(), provider.find("other"));
        assertEquals(Optional.empty(), provider.findEnabled("other"));
    }

    @Test
    @DisplayName("the participant ID follows dcp.participant.id")
    void configuredId() {
        properties.getParticipant().setId("tenant-a");
        DidDocumentConfig config = DidDocumentConfig.builder().did(DID).baseUrl("https://example.com")
                .serviceEntries(List.of()).build();

        SingleParticipantContextProvider custom = new SingleParticipantContextProvider(properties, config);

        assertTrue(custom.find("tenant-a").isPresent());
        assertTrue(custom.find("default").isEmpty());
    }

    @Test
    @DisplayName("a disabled participant is reported as disabled and never returned as usable")
    void disabledParticipantIsNotUsable() {
        ParticipantContext disabled = new ParticipantContext("off", "did:web:off",
                new SigningKeyReference("k.p12", "a"), List.of(), Map.of(), null, ParticipantState.DISABLED);
        ParticipantContextProvider disabledProvider = id -> "off".equals(id) ? Optional.of(disabled) : Optional.empty();

        assertEquals(ParticipantState.DISABLED, disabledProvider.find("off").orElseThrow().state());
        assertTrue(disabledProvider.findEnabled("off").isEmpty());
    }

    @Test
    @DisplayName("a node without a configured DID still starts; the participant is rejected on first use")
    void blankDidFailsLazily() {
        DidDocumentConfig noDid = DidDocumentConfig.builder().baseUrl("https://example.com")
                .serviceEntries(List.of()).build();

        SingleParticipantContextProvider lazy = new SingleParticipantContextProvider(properties, noDid);

        assertEquals(Optional.empty(), lazy.find("other"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> lazy.find("default"));
    }
}
