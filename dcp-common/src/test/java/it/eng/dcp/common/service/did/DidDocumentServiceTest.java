package it.eng.dcp.common.service.did;

import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.model.KeyMetadata;
import it.eng.dcp.common.model.ServiceEntry;
import it.eng.dcp.common.model.VerificationMethod;
import it.eng.dcp.common.service.KeyMetadataService;
import it.eng.dcp.common.service.KeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test class for DidDocumentService.
 */
@ExtendWith(MockitoExtension.class)
class DidDocumentServiceTest {
    @Mock
    private KeyService keyService;
    @Mock
    private KeyMetadataService keyMetadataService;

    private DidDocumentService didDocumentService;

    /**
     * Set up test fixtures.
     */
    @BeforeEach
    void setUp() {
        didDocumentService = new DidDocumentService(keyService, keyMetadataService);
        when(keyService.getKidFromPublicKey()).thenReturn("kid123");
        when(keyService.convertPublicKeyToJWK()).thenReturn(Map.of("kty", "EC"));
    }

    /**
     * Test that provideDidDocument returns valid DidDocument with HTTPS.
     */
    @DisplayName("provideDidDocument returns valid DidDocument with HTTPS")
    @Test
    void provideDidDocument_ReturnsValidDidDocument_WithHTTPS() {
        // Arrange
        KeyMetadata metadata = KeyMetadata.Builder.newInstance()
                .alias("activeAlias")
                .active(true)
                .build();
        when(keyMetadataService.getActiveKeyMetadata()).thenReturn(Optional.of(metadata));

        DidDocumentConfig config = DidDocumentConfig.builder()
                .did("did:web:example.com:holder")
                .protocol("https")
                .host("example.com")
                .port("8080")
                .keystorePath("eckey.p12")
                .keystorePassword("password")
                .serviceEntries(List.of(
                        DidDocumentConfig.ServiceEntryConfig.builder()
                                .id("CredentialService")
                                .type("CredentialService")
                                .endpointPath("")
                                .build()
                ))
                .build();

        // Act
        DidDocument doc = didDocumentService.provideDidDocument(config);

        // Assert
        assertEquals("did:web:example.com:holder", doc.getId());
        assertEquals(1, doc.getServices().size());
        ServiceEntry service = doc.getServices().get(0);
        assertEquals("CredentialService", service.id());
        assertEquals("CredentialService", service.type());
        assertEquals("https://example.com:8080", service.serviceEndpoint());
        assertEquals(1, doc.getVerificationMethods().size());
        VerificationMethod vm = doc.getVerificationMethods().get(0);
        assertTrue(vm.getId().endsWith("#kid123"));
        assertEquals("JsonWebKey2020", vm.getType());
        assertEquals("did:web:example.com:holder", vm.getController());
        assertEquals("EC", vm.getPublicKeyJwk().get("kty"));
        verify(keyService).loadKeyPairFromP12("eckey.p12", "password", "activeAlias");
    }

    /**
     * Test that provideDidDocument returns valid DidDocument with HTTP.
     */
    @DisplayName("provideDidDocument returns valid DidDocument with HTTP")
    @Test
    void provideDidDocument_ReturnsValidDidDocument_WithHTTP() {
        // Arrange
        KeyMetadata metadata = KeyMetadata.Builder.newInstance()
                .alias("activeAlias")
                .active(true)
                .build();
        when(keyMetadataService.getActiveKeyMetadata()).thenReturn(Optional.of(metadata));

        DidDocumentConfig config = DidDocumentConfig.builder()
                .did("did:web:localhost%3A8090:holder")
                .protocol("http")
                .host("localhost")
                .port("8090")
                .keystorePath("eckey.p12")
                .keystorePassword("password")
                .serviceEntries(List.of(
                        DidDocumentConfig.ServiceEntryConfig.builder()
                                .id("CredentialService")
                                .type("CredentialService")
                                .endpointPath("")
                                .build(),
                        DidDocumentConfig.ServiceEntryConfig.builder()
                                .id("IssuerService")
                                .type("IssuerService")
                                .endpointPath("/issuer")
                                .build()
                ))
                .build();

        // Act
        DidDocument doc = didDocumentService.provideDidDocument(config);

        // Assert
        assertEquals("did:web:localhost%3A8090:holder", doc.getId());
        assertEquals(2, doc.getServices().size());
        ServiceEntry service = doc.getServices().get(0);
        assertEquals("CredentialService", service.id());
        assertEquals("CredentialService", service.type());
        assertEquals("http://localhost:8090", service.serviceEndpoint());

        ServiceEntry issuerService = doc.getServices().get(1);
        assertEquals("http://localhost:8090/issuer", issuerService.serviceEndpoint());

        verify(keyService).loadKeyPairFromP12("eckey.p12", "password", "activeAlias");
    }

    /**
     * Test that provideDidDocument uses default alias if metadata is absent.
     */
    @DisplayName("provideDidDocument uses default alias if metadata is absent")
    @Test
    void provideDidDocument_UsesDefaultAlias_WhenMetadataAbsent() {
        // Arrange
        when(keyMetadataService.getActiveKeyMetadata()).thenReturn(Optional.empty());

        DidDocumentConfig config = DidDocumentConfig.builder()
                .did("did:web:localhost:holder")
                .protocol("https")
                .host("localhost")
                .port("8080")
                .keystorePath("eckey.p12")
                .keystorePassword("password")
                .serviceEntries(List.of(
                        DidDocumentConfig.ServiceEntryConfig.builder()
                                .id("Service1")
                                .type("Type1")
                                .build()
                ))
                .build();

        // Act
        didDocumentService.provideDidDocument(config);

        // Assert
        verify(keyService).loadKeyPairFromP12("eckey.p12", "password", "dcp-issuer");
    }

    /**
     * Test that provideDidDocument uses custom controller for verification method.
     */
    @DisplayName("provideDidDocument uses custom controller for verification method")
    @Test
    void provideDidDocument_UsesCustomController() {
        // Arrange
        KeyMetadata metadata = KeyMetadata.Builder.newInstance()
                .alias("testAlias")
                .active(true)
                .build();
        when(keyMetadataService.getActiveKeyMetadata()).thenReturn(Optional.of(metadata));

        DidDocumentConfig config = DidDocumentConfig.builder()
                .did("did:web:example.com:issuer")
                .protocol("https")
                .host("example.com")
                .port("9443")
                .verificationMethodController("did:web:example.com:controller")
                .keystorePath("eckey-issuer.p12")
                .keystorePassword("secret")
                .serviceEntries(List.of(
                        DidDocumentConfig.ServiceEntryConfig.builder()
                                .id("IssuerService")
                                .type("IssuerService")
                                .build()
                ))
                .build();

        // Act
        DidDocument doc = didDocumentService.provideDidDocument(config);

        // Assert
        ServiceEntry service = doc.getServices().get(0);
        assertEquals("https://example.com:9443", service.serviceEndpoint());

        VerificationMethod vm = doc.getVerificationMethods().get(0);
        assertEquals("did:web:example.com:controller", vm.getController());
        verify(keyService).loadKeyPairFromP12("eckey-issuer.p12", "secret", "testAlias");
    }

    /**
     * Test that provideDidDocument uses baseUrl if provided.
     */
    @DisplayName("provideDidDocument uses baseUrl if provided")
    @Test
    void provideDidDocument_UsesBaseUrl_WhenProvided() {
        // Arrange
        KeyMetadata metadata = KeyMetadata.Builder.newInstance()
                .alias("alias1")
                .active(true)
                .build();
        when(keyMetadataService.getActiveKeyMetadata()).thenReturn(Optional.of(metadata));

        DidDocumentConfig config = DidDocumentConfig.builder()
                .did("did:web:production.example.com:holder")
                .baseUrl("https://production.example.com")
                .keystorePath("eckey.p12")
                .keystorePassword("password")
                .serviceEntries(List.of(
                        DidDocumentConfig.ServiceEntryConfig.builder()
                                .id("Service1")
                                .type("Type1")
                                .endpointPath("/api/v1")
                                .build()
                ))
                .build();

        // Act
        DidDocument doc = didDocumentService.provideDidDocument(config);

        // Assert
        ServiceEntry service = doc.getServices().get(0);
        assertEquals("https://production.example.com/api/v1", service.serviceEndpoint());
    }

    /**
     * Test that getDidDocument returns JSON string.
     */
    @DisplayName("getDidDocument returns valid JSON string")
    @Test
    void getDidDocument_ReturnsValidJsonString() {
        // Arrange
        KeyMetadata metadata = KeyMetadata.Builder.newInstance()
                .alias("activeAlias")
                .active(true)
                .build();
        when(keyMetadataService.getActiveKeyMetadata()).thenReturn(Optional.of(metadata));

        DidDocumentConfig config = DidDocumentConfig.builder()
                .did("did:web:localhost:holder")
                .protocol("http")
                .host("localhost")
                .port("8080")
                .keystorePath("eckey.p12")
                .keystorePassword("password")
                .serviceEntries(List.of(
                        DidDocumentConfig.ServiceEntryConfig.builder()
                                .id("CredentialService")
                                .type("CredentialService")
                                .build()
                ))
                .build();

        // Act
        String jsonDidDocument = didDocumentService.getDidDocument(config);

        // Assert
        assertNotNull(jsonDidDocument);
        assertTrue(jsonDidDocument.contains("did:web:localhost:holder"));
        assertTrue(jsonDidDocument.contains("CredentialService"));
    }
}

