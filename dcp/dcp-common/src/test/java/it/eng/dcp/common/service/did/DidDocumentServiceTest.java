package it.eng.dcp.common.service.did;

import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.model.ServiceEntry;
import it.eng.dcp.common.model.VerificationMethod;
import it.eng.dcp.common.participant.ParticipantContext;
import it.eng.dcp.common.participant.ParticipantKeyProvider;
import it.eng.dcp.common.participant.ParticipantKeyProvider.PublishedKey;
import it.eng.dcp.common.participant.ParticipantState;
import it.eng.dcp.common.participant.SigningKeyReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DidDocumentServiceTest {

    private static final String DID = "did:web:example.com:holder";

    @Mock
    private ParticipantKeyProvider keyProvider;

    private DidDocumentService didDocumentService;

    @BeforeEach
    void setUp() {
        didDocumentService = new DidDocumentService(keyProvider);
    }

    private ParticipantContext participant(List<ServiceEntry> services) {
        return new ParticipantContext("default", DID, new SigningKeyReference("eckey.p12", "alias"),
                services, Map.of(), null, ParticipantState.ENABLED);
    }

    private PublishedKey key(String kid, boolean active) {
        return new PublishedKey(kid, Map.of("kty", "EC", "kid", DID + "#" + kid), active);
    }

    @Test
    @DisplayName("provideDidDocument publishes the participant's services and its active key")
    void provideDidDocument_publishesServicesAndActiveKey() {
        ParticipantContext participant = participant(
                List.of(new ServiceEntry("CredentialService", "CredentialService", "https://example.com:8080")));
        when(keyProvider.publishedKeys(participant)).thenReturn(List.of(key("kid123", true)));

        DidDocument doc = didDocumentService.provideDidDocument(participant);

        assertEquals(DID, doc.getId());
        assertEquals(1, doc.getServices().size());
        assertEquals("https://example.com:8080", doc.getServices().get(0).serviceEndpoint());
        assertEquals(1, doc.getVerificationMethods().size());
        VerificationMethod vm = doc.getVerificationMethods().get(0);
        assertEquals(DID + "#kid123", vm.getId());
        assertEquals("JsonWebKey2020", vm.getType());
        assertEquals(DID, vm.getController());
        assertEquals("EC", vm.getPublicKeyJwk().get("kty"));
        assertEquals(List.of(vm.getId()), doc.getAuthentication());
        assertEquals(List.of(vm.getId()), doc.getCapabilityInvocation());
        assertTrue(doc.getAssertionMethod().isEmpty());
        verify(keyProvider).publishedKeys(participant);
    }

    @Test
    @DisplayName("provideDidDocument also publishes keys retired within the grace period")
    void provideDidDocument_publishesRetiredKeys() {
        ParticipantContext participant = participant(
                List.of(new ServiceEntry("CredentialService", "CredentialService", "https://example.com:8080")));
        when(keyProvider.publishedKeys(participant)).thenReturn(List.of(key("new", true), key("old", false)));

        DidDocument doc = didDocumentService.provideDidDocument(participant);

        assertEquals(List.of(DID + "#new", DID + "#old"),
                doc.getVerificationMethods().stream().map(VerificationMethod::getId).toList());
        assertEquals(List.of(DID + "#new", DID + "#old"), doc.getCapabilityInvocation());
    }

    @Test
    @DisplayName("provideDidDocument lists the key under assertionMethod when an IssuerService is advertised")
    void provideDidDocument_issuerListsAssertionMethod() {
        ParticipantContext participant = participant(List.of(
                new ServiceEntry("CredentialService", "CredentialService", "http://localhost:8090"),
                new ServiceEntry("IssuerService", "IssuerService", "http://localhost:8090/issuer")));
        when(keyProvider.publishedKeys(participant)).thenReturn(List.of(key("kid123", true)));

        DidDocument doc = didDocumentService.provideDidDocument(participant);

        String vmId = doc.getVerificationMethods().get(0).getId();
        assertEquals(List.of(vmId), doc.getAssertionMethod());
    }

    @Test
    @DisplayName("getDidDocument returns a JSON string")
    void getDidDocument_returnsJson() {
        ParticipantContext participant = participant(
                List.of(new ServiceEntry("CredentialService", "CredentialService", "http://localhost:8080")));
        when(keyProvider.publishedKeys(participant)).thenReturn(List.of(key("kid123", true)));

        String json = didDocumentService.getDidDocument(participant);

        assertNotNull(json);
        assertTrue(json.contains(DID));
        assertTrue(json.contains("CredentialService"));
    }
}
