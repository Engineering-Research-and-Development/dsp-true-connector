package it.eng.dcp.issuer.service;

import it.eng.dcp.common.model.IssuerMetadata;
import it.eng.dcp.issuer.config.CredentialMetadataConfig;
import it.eng.dcp.issuer.config.IssuerProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CredentialMetadataService.
 */
class CredentialMetadataServiceTest {

    @Mock
    private IssuerProperties issuerProperties;

    @Mock
    private CredentialMetadataConfig credentialMetadataConfig;

    @InjectMocks
    private CredentialMetadataService metadataService;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    void buildIssuerMetadata_success() {
        when(issuerProperties.getConnectorDid()).thenReturn("did:web:issuer.example.com");

        CredentialMetadataConfig.CredentialConfig membershipConfig = new CredentialMetadataConfig.CredentialConfig();
        membershipConfig.setId("membership-1");
        membershipConfig.setType("CredentialObject");
        membershipConfig.setCredentialType("MembershipCredential");
        membershipConfig.setCredentialSchema("https://example.com/schemas/membership");
        membershipConfig.setBindingMethods(List.of("did:web", "did:key"));
        membershipConfig.setProfile("vc11-sl2021/jwt");

        when(credentialMetadataConfig.getSupported()).thenReturn(List.of(membershipConfig));

        IssuerMetadata metadata = metadataService.buildIssuerMetadata();

        assertNotNull(metadata);
        assertEquals("did:web:issuer.example.com", metadata.getIssuer());
        assertEquals(1, metadata.getCredentialsSupported().size());

        IssuerMetadata.CredentialObject credentialObject = metadata.getCredentialsSupported().get(0);
        assertEquals("membership-1", credentialObject.getId());
        assertEquals("CredentialObject", credentialObject.getType());
        assertEquals("MembershipCredential", credentialObject.getCredentialType());
        assertEquals("https://example.com/schemas/membership", credentialObject.getCredentialSchema());
        assertTrue(credentialObject.getBindingMethods().contains("did:web"));
        assertEquals("vc11-sl2021/jwt", credentialObject.getProfile());
    }

    @Test
    void buildIssuerMetadata_multipleCredentials() {
        when(issuerProperties.getConnectorDid()).thenReturn("did:web:issuer.example.com");

        CredentialMetadataConfig.CredentialConfig membershipConfig = new CredentialMetadataConfig.CredentialConfig();
        membershipConfig.setCredentialType("MembershipCredential");

        CredentialMetadataConfig.CredentialConfig orgConfig = new CredentialMetadataConfig.CredentialConfig();
        orgConfig.setCredentialType("OrganizationCredential");

        when(credentialMetadataConfig.getSupported()).thenReturn(List.of(membershipConfig, orgConfig));

        IssuerMetadata metadata = metadataService.buildIssuerMetadata();

        assertNotNull(metadata);
        assertEquals(2, metadata.getCredentialsSupported().size());
    }

    @Test
    void buildIssuerMetadata_noIssuerDid_usesDefault() {
        when(issuerProperties.getConnectorDid()).thenReturn(null);

        CredentialMetadataConfig.CredentialConfig config = new CredentialMetadataConfig.CredentialConfig();
        config.setCredentialType("MembershipCredential");

        when(credentialMetadataConfig.getSupported()).thenReturn(List.of(config));

        IssuerMetadata metadata = metadataService.buildIssuerMetadata();

        assertNotNull(metadata);
        assertEquals("did:web:issuer-url", metadata.getIssuer());
    }

    @Test
    void buildIssuerMetadata_blankIssuerDid_usesDefault() {
        when(issuerProperties.getConnectorDid()).thenReturn("");

        CredentialMetadataConfig.CredentialConfig config = new CredentialMetadataConfig.CredentialConfig();
        config.setCredentialType("MembershipCredential");

        when(credentialMetadataConfig.getSupported()).thenReturn(List.of(config));

        IssuerMetadata metadata = metadataService.buildIssuerMetadata();

        assertNotNull(metadata);
        assertEquals("did:web:issuer-url", metadata.getIssuer());
    }

    @Test
    void buildIssuerMetadata_noCredentialsConfigured_throwsException() {
        when(credentialMetadataConfig.getSupported()).thenReturn(List.of());

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                metadataService.buildIssuerMetadata()
        );

        assertTrue(exception.getMessage().contains("No credentials configured"));
    }

    @Test
    void buildIssuerMetadata_nullCredentialType_throwsException() {
        when(issuerProperties.getConnectorDid()).thenReturn("did:web:issuer.example.com");

        CredentialMetadataConfig.CredentialConfig invalidConfig = new CredentialMetadataConfig.CredentialConfig();
        invalidConfig.setCredentialType(null);

        when(credentialMetadataConfig.getSupported()).thenReturn(List.of(invalidConfig));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                metadataService.buildIssuerMetadata()
        );

        assertTrue(exception.getMessage().contains("Failed to build any valid credentials"));
    }

    @Test
    void buildIssuerMetadata_blankCredentialType_throwsException() {
        when(issuerProperties.getConnectorDid()).thenReturn("did:web:issuer.example.com");

        CredentialMetadataConfig.CredentialConfig invalidConfig = new CredentialMetadataConfig.CredentialConfig();
        invalidConfig.setCredentialType("");

        when(credentialMetadataConfig.getSupported()).thenReturn(List.of(invalidConfig));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                metadataService.buildIssuerMetadata()
        );

        assertTrue(exception.getMessage().contains("Failed to build any valid credentials"));
    }

    @Test
    void buildIssuerMetadata_generatesIdWhenMissing() {
        when(issuerProperties.getConnectorDid()).thenReturn("did:web:issuer.example.com");

        CredentialMetadataConfig.CredentialConfig config = new CredentialMetadataConfig.CredentialConfig();
        config.setCredentialType("MembershipCredential");
        config.setId(null);

        when(credentialMetadataConfig.getSupported()).thenReturn(List.of(config));

        IssuerMetadata metadata = metadataService.buildIssuerMetadata();

        assertNotNull(metadata);
        IssuerMetadata.CredentialObject credentialObject = metadata.getCredentialsSupported().get(0);
        assertNotNull(credentialObject.getId());
        assertFalse(credentialObject.getId().isBlank());
    }

    @Test
    void buildIssuerMetadata_usesDefaultTypeWhenMissing() {
        when(issuerProperties.getConnectorDid()).thenReturn("did:web:issuer.example.com");

        CredentialMetadataConfig.CredentialConfig config = new CredentialMetadataConfig.CredentialConfig();
        config.setCredentialType("MembershipCredential");
        config.setType(null);

        when(credentialMetadataConfig.getSupported()).thenReturn(List.of(config));

        IssuerMetadata metadata = metadataService.buildIssuerMetadata();

        assertNotNull(metadata);
        IssuerMetadata.CredentialObject credentialObject = metadata.getCredentialsSupported().get(0);
        assertEquals("CredentialObject", credentialObject.getType());
    }

    @Test
    void buildIssuerMetadata_usesDefaultBindingMethodsWhenMissing() {
        when(issuerProperties.getConnectorDid()).thenReturn("did:web:issuer.example.com");

        CredentialMetadataConfig.CredentialConfig config = new CredentialMetadataConfig.CredentialConfig();
        config.setCredentialType("MembershipCredential");
        config.setBindingMethods(null);

        when(credentialMetadataConfig.getSupported()).thenReturn(List.of(config));

        IssuerMetadata metadata = metadataService.buildIssuerMetadata();

        assertNotNull(metadata);
        IssuerMetadata.CredentialObject credentialObject = metadata.getCredentialsSupported().get(0);
        assertNotNull(credentialObject.getBindingMethods());
        assertTrue(credentialObject.getBindingMethods().contains("did:web"));
    }

    @Test
    void buildIssuerMetadata_usesDefaultProfileWhenMissing() {
        when(issuerProperties.getConnectorDid()).thenReturn("did:web:issuer.example.com");
        when(issuerProperties.getSupportedProfiles()).thenReturn(List.of("vc11-sl2021/jwt"));

        CredentialMetadataConfig.CredentialConfig config = new CredentialMetadataConfig.CredentialConfig();
        config.setCredentialType("MembershipCredential");
        config.setProfile(null);

        when(credentialMetadataConfig.getSupported()).thenReturn(List.of(config));

        IssuerMetadata metadata = metadataService.buildIssuerMetadata();

        assertNotNull(metadata);
        IssuerMetadata.CredentialObject credentialObject = metadata.getCredentialsSupported().get(0);
        assertEquals("vc11-sl2021/jwt", credentialObject.getProfile());
    }

    @Test
    void buildIssuerMetadata_usesDefaultProfileWhenNoSupportedProfiles() {
        when(issuerProperties.getConnectorDid()).thenReturn("did:web:issuer.example.com");
        when(issuerProperties.getSupportedProfiles()).thenReturn(null);

        CredentialMetadataConfig.CredentialConfig config = new CredentialMetadataConfig.CredentialConfig();
        config.setCredentialType("MembershipCredential");
        config.setProfile(null);

        when(credentialMetadataConfig.getSupported()).thenReturn(List.of(config));

        IssuerMetadata metadata = metadataService.buildIssuerMetadata();

        assertNotNull(metadata);
        IssuerMetadata.CredentialObject credentialObject = metadata.getCredentialsSupported().get(0);
        assertEquals("vc11-sl2021/jwt", credentialObject.getProfile());
    }

    @Test
    void buildIssuerMetadata_normalizesProfileId() {
        when(issuerProperties.getConnectorDid()).thenReturn("did:web:issuer.example.com");
        when(issuerProperties.getSupportedProfiles()).thenReturn(List.of("VC11_SL2021_JWT"));

        CredentialMetadataConfig.CredentialConfig config = new CredentialMetadataConfig.CredentialConfig();
        config.setCredentialType("MembershipCredential");
        config.setProfile(null);

        when(credentialMetadataConfig.getSupported()).thenReturn(List.of(config));

        IssuerMetadata metadata = metadataService.buildIssuerMetadata();

        assertNotNull(metadata);
        IssuerMetadata.CredentialObject credentialObject = metadata.getCredentialsSupported().get(0);
        assertEquals("vc11-sl2021/jwt", credentialObject.getProfile());
    }

    @Test
    void buildIssuerMetadata_includesIssuancePolicy() {
        when(issuerProperties.getConnectorDid()).thenReturn("did:web:issuer.example.com");

        CredentialMetadataConfig.CredentialConfig config = new CredentialMetadataConfig.CredentialConfig();
        config.setCredentialType("MembershipCredential");
        Map<String, Object> policy = new HashMap<>();
        policy.put("requiresProof", true);
        policy.put("proofType", "signature");
        config.setIssuancePolicy(policy);

        when(credentialMetadataConfig.getSupported()).thenReturn(List.of(config));

        IssuerMetadata metadata = metadataService.buildIssuerMetadata();

        assertNotNull(metadata);
        IssuerMetadata.CredentialObject credentialObject = metadata.getCredentialsSupported().get(0);
        assertNotNull(credentialObject.getIssuancePolicy());
        assertEquals(true, credentialObject.getIssuancePolicy().get("requiresProof"));
        assertEquals("signature", credentialObject.getIssuancePolicy().get("proofType"));
    }

    @Test
    void buildIssuerMetadata_includesCredentialSchema() {
        when(issuerProperties.getConnectorDid()).thenReturn("did:web:issuer.example.com");

        CredentialMetadataConfig.CredentialConfig config = new CredentialMetadataConfig.CredentialConfig();
        config.setCredentialType("MembershipCredential");
        config.setCredentialSchema("https://schema.org/MembershipCredential");

        when(credentialMetadataConfig.getSupported()).thenReturn(List.of(config));

        IssuerMetadata metadata = metadataService.buildIssuerMetadata();

        assertNotNull(metadata);
        IssuerMetadata.CredentialObject credentialObject = metadata.getCredentialsSupported().get(0);
        assertEquals("https://schema.org/MembershipCredential", credentialObject.getCredentialSchema());
    }

    @Test
    void buildIssuerMetadata_partialFailure_buildsValidCredentials() {
        when(issuerProperties.getConnectorDid()).thenReturn("did:web:issuer.example.com");

        CredentialMetadataConfig.CredentialConfig validConfig = new CredentialMetadataConfig.CredentialConfig();
        validConfig.setCredentialType("MembershipCredential");

        CredentialMetadataConfig.CredentialConfig invalidConfig = new CredentialMetadataConfig.CredentialConfig();
        invalidConfig.setCredentialType(null);

        when(credentialMetadataConfig.getSupported()).thenReturn(List.of(validConfig, invalidConfig));

        IssuerMetadata metadata = metadataService.buildIssuerMetadata();

        assertNotNull(metadata);
        assertEquals(1, metadata.getCredentialsSupported().size());
        assertEquals("MembershipCredential", metadata.getCredentialsSupported().get(0).getCredentialType());
    }

    @Test
    void buildIssuerMetadata_emptyBindingMethods_usesDefault() {
        when(issuerProperties.getConnectorDid()).thenReturn("did:web:issuer.example.com");

        CredentialMetadataConfig.CredentialConfig config = new CredentialMetadataConfig.CredentialConfig();
        config.setCredentialType("MembershipCredential");
        config.setBindingMethods(List.of());

        when(credentialMetadataConfig.getSupported()).thenReturn(List.of(config));

        IssuerMetadata metadata = metadataService.buildIssuerMetadata();

        assertNotNull(metadata);
        IssuerMetadata.CredentialObject credentialObject = metadata.getCredentialsSupported().get(0);
        assertNotNull(credentialObject.getBindingMethods());
        assertTrue(credentialObject.getBindingMethods().contains("did:web"));
    }

    @Test
    void buildIssuerMetadata_blankCredentialSchema_notIncluded() {
        when(issuerProperties.getConnectorDid()).thenReturn("did:web:issuer.example.com");

        CredentialMetadataConfig.CredentialConfig config = new CredentialMetadataConfig.CredentialConfig();
        config.setCredentialType("MembershipCredential");
        config.setCredentialSchema("");

        when(credentialMetadataConfig.getSupported()).thenReturn(List.of(config));

        IssuerMetadata metadata = metadataService.buildIssuerMetadata();

        assertNotNull(metadata);
        IssuerMetadata.CredentialObject credentialObject = metadata.getCredentialsSupported().get(0);
        // Schema should be null or not set when blank
        assertTrue(credentialObject.getCredentialSchema() == null || credentialObject.getCredentialSchema().isBlank());
    }
}

