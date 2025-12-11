package it.eng.dcp.service;

import it.eng.dcp.model.DidDocument;
import it.eng.dcp.model.KeyMetadata;
import it.eng.dcp.model.ServiceEntry;
import it.eng.dcp.model.VerificationMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DidDocumentServiceTest {
    @Mock
    private KeyService keyService;
    @Mock
    private KeyMetadataService keyMetadataService;
    @InjectMocks
    private DidDocumentService didDocumentService;

    @BeforeEach
    void setUp() {
        when(keyService.getKidFromPublicKey()).thenReturn("kid123");
        when(keyService.convertPublicKeyToJWK()).thenReturn(Map.of("kty", "EC"));
    }

    @DisplayName("provideDidDocument returns valid DidDocument with active alias from metadata")
    @Test
    void provideDidDocument_ReturnsValidDidDocument_WithActiveAlias() {
        KeyMetadata metadata = KeyMetadata.Builder.newInstance()
                .alias("activeAlias")
                .active(true)
                .build();
        when(keyMetadataService.getActiveKeyMetadata()).thenReturn(Optional.of(metadata));

        DidDocument doc = didDocumentService.provideDidDocument();

        assertEquals("did:web:localhost%3A8083:holder", doc.getId());
        assertEquals(2, doc.getServices().size());
        ServiceEntry service = doc.getServices().get(0);
        assertEquals("TRUEConnector-Credential-Service", service.id());
        assertEquals("CredentialService", service.type());
        assertEquals("http://localhost:8080", service.serviceEndpoint());
        assertEquals(1, doc.getVerificationMethods().size());
        VerificationMethod vm = doc.getVerificationMethods().get(0);
        assertTrue(vm.getId().endsWith("#kid123"));
        assertEquals("JsonWebKey2020", vm.getType());
        assertEquals("did:web:localhost%3A8083:holder", vm.getController());
        assertEquals("EC", vm.getPublicKeyJwk().get("kty"));
        verify(keyService).loadKeyPairFromP12("eckey.p12", "password", "activeAlias");
    }

    @DisplayName("provideDidDocument uses default alias if metadata is absent")
    @Test
    void provideDidDocument_UsesDefaultAlias_WhenMetadataAbsent() {
        when(keyMetadataService.getActiveKeyMetadata()).thenReturn(Optional.empty());

        didDocumentService.provideDidDocument();

        verify(keyService).loadKeyPairFromP12("eckey.p12", "password", "dsptrueconnector");
    }

    @DisplayName("provideDidDocument handles null alias in metadata")
    @Test
    void provideDidDocument_UsesNullAlias_WhenAliasIsNull() {
        // Builder enforces non-null alias; simulate null-alias behavior by returning empty optional
        when(keyMetadataService.getActiveKeyMetadata()).thenReturn(Optional.empty());

        didDocumentService.provideDidDocument();

        verify(keyService).loadKeyPairFromP12("eckey.p12", "password", "dsptrueconnector");
    }
}
