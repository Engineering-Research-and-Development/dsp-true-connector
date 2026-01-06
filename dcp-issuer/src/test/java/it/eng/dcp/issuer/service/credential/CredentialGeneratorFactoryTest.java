package it.eng.dcp.issuer.service.credential;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import it.eng.dcp.common.config.BaseDidDocumentConfiguration;
import it.eng.dcp.common.service.KeyService;
import it.eng.dcp.issuer.service.jwt.VcJwtGeneratorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for CredentialGeneratorFactory.
 * Verifies correct generator selection based on credential type.
 */
class CredentialGeneratorFactoryTest {

    @Mock
    private KeyService keyService;

    @Mock
    private BaseDidDocumentConfiguration didDocumentConfig;

    private CredentialGeneratorFactory factory;
    private VcJwtGeneratorFactory jwtGeneratorFactory;
    private AutoCloseable closeable;

    private static final String ISSUER_DID = "did:web:issuer.example.com";

    @BeforeEach
    void setUp() throws Exception {
        closeable = MockitoAnnotations.openMocks(this);

        ECKey testSigningKey = new ECKeyGenerator(Curve.P_256)
                .keyID("test-key-1")
                .generate();

        // Use lenient stubbing since not all tests will use this mock
        lenient().when(keyService.getSigningJwk(any())).thenReturn(testSigningKey);

        jwtGeneratorFactory = new VcJwtGeneratorFactory(ISSUER_DID, keyService, didDocumentConfig);
        factory = new CredentialGeneratorFactory(jwtGeneratorFactory, ISSUER_DID);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    void createGenerator_returnsMembershipGenerator_forMembershipCredential() {
        CredentialGenerator generator = factory.createGenerator("MembershipCredential");

        assertNotNull(generator);
        assertInstanceOf(MembershipCredentialGenerator.class, generator);
        assertEquals("MembershipCredential", generator.getCredentialType());
    }

    @Test
    void createGenerator_returnsOrganizationGenerator_forOrganizationCredential() {
        CredentialGenerator generator = factory.createGenerator("OrganizationCredential");

        assertNotNull(generator);
        assertInstanceOf(OrganizationCredentialGenerator.class, generator);
        assertEquals("OrganizationCredential", generator.getCredentialType());
    }

    @Test
    void createGenerator_returnsGenericGenerator_forUnknownType() {
        CredentialGenerator generator = factory.createGenerator("CustomCredential");

        assertNotNull(generator);
        assertInstanceOf(GenericCredentialGenerator.class, generator);
        assertEquals("CustomCredential", generator.getCredentialType());
    }

    @Test
    void createGenerator_returnsGenericGenerator_forEmptyType() {
        CredentialGenerator generator = factory.createGenerator("");

        assertNotNull(generator);
        assertInstanceOf(GenericCredentialGenerator.class, generator);
        assertEquals("", generator.getCredentialType());
    }

    @Test
    void createGenerator_returnsDifferentInstancesForSameType() {
        CredentialGenerator generator1 = factory.createGenerator("MembershipCredential");
        CredentialGenerator generator2 = factory.createGenerator("MembershipCredential");

        assertNotNull(generator1);
        assertNotNull(generator2);
        assertSame(generator1, generator2, "Should return same registered instance for known types");
    }

    @Test
    void createGenerator_returnsDifferentInstancesForGenericTypes() {
        CredentialGenerator generator1 = factory.createGenerator("CustomType1");
        CredentialGenerator generator2 = factory.createGenerator("CustomType2");

        assertNotNull(generator1);
        assertNotNull(generator2);
        assertNotSame(generator1, generator2, "Should return different instances for different generic types");
        assertEquals("CustomType1", generator1.getCredentialType());
        assertEquals("CustomType2", generator2.getCredentialType());
    }

    @Test
    void createGenerator_handlesNullType() {
        assertDoesNotThrow(() -> factory.createGenerator(null));

        CredentialGenerator generator = factory.createGenerator(null);
        assertNotNull(generator);
        assertInstanceOf(GenericCredentialGenerator.class, generator);
    }
}

