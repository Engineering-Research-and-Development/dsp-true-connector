package it.eng.dcp.issuer.service.credential;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import it.eng.dcp.common.config.BaseDidDocumentConfiguration;
import it.eng.dcp.common.model.CredentialGenerationContext;
import it.eng.dcp.common.model.CredentialMessage;
import it.eng.dcp.common.model.CredentialRequest;
import it.eng.dcp.common.model.CredentialStatus;
import it.eng.dcp.common.service.KeyService;
import it.eng.dcp.issuer.config.CredentialMetadataConfigLoader;
import it.eng.dcp.issuer.service.jwt.VcJwtGeneratorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.text.ParseException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GenericCredentialGenerator.
 * Verifies correct generic credential generation for unknown types.
 */
class GenericCredentialGeneratorTest {

    @Mock
    private KeyService keyService;

    @Mock
    private BaseDidDocumentConfiguration didDocumentConfig;
    @Mock
    private CredentialMetadataConfigLoader configLoader;

    private GenericCredentialGenerator generator;
    private VcJwtGeneratorFactory jwtGeneratorFactory;
    private ProfileExtractor profileExtractor;
    private AutoCloseable closeable;

    private static final String ISSUER_DID = "did:web:issuer.example.com";
    private static final String HOLDER_DID = "did:web:holder.example.com";
    private static final String CUSTOM_CREDENTIAL_TYPE = "CustomCredential";

    @BeforeEach
    void setUp() throws Exception {
        closeable = MockitoAnnotations.openMocks(this);

        ECKey testSigningKey = new ECKeyGenerator(Curve.P_256)
                .keyID("test-key-1")
                .generate();

        when(keyService.getSigningJwk(any())).thenReturn(testSigningKey);

        jwtGeneratorFactory = new VcJwtGeneratorFactory(ISSUER_DID, keyService, didDocumentConfig);
        profileExtractor = new ProfileExtractor();
        generator = new GenericCredentialGenerator(jwtGeneratorFactory, profileExtractor, ISSUER_DID, CUSTOM_CREDENTIAL_TYPE);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    void getCredentialType_returnsCustomType() {
        assertEquals(CUSTOM_CREDENTIAL_TYPE, generator.getCredentialType());
    }

    @Test
    void generateCredential_createsValidContainer() {
        CredentialGenerationContext context = createTestContext();

        CredentialMessage.CredentialContainer container = generator.generateCredential(context);

        assertNotNull(container);
        assertEquals(CUSTOM_CREDENTIAL_TYPE, container.getCredentialType());
        assertEquals("jwt", container.getFormat());
        assertNotNull(container.getPayload());
        assertTrue(container.getPayload() instanceof String);
    }

    @Test
    void generateCredential_jwtContainsGenericClaims() throws ParseException {
        CredentialGenerationContext context = createTestContext();

        CredentialMessage.CredentialContainer container = generator.generateCredential(context);

        SignedJWT jwt = SignedJWT.parse((String) container.getPayload());

        @SuppressWarnings("unchecked")
        Map<String, Object> credentialSubject = (Map<String, Object>) jwt.getJWTClaimsSet().getClaim("credentialSubject");

        assertNotNull(credentialSubject);
        assertEquals(HOLDER_DID, credentialSubject.get("id"));
        assertEquals("Active", credentialSubject.get("status"));
        assertEquals(ISSUER_DID, credentialSubject.get("issuedBy"));
    }

    @Test
    void generateCredential_usesDefaultVC20Profile() throws ParseException {
        CredentialGenerationContext context = createTestContext();

        CredentialMessage.CredentialContainer container = generator.generateCredential(context);

        SignedJWT jwt = SignedJWT.parse((String) container.getPayload());

        // VC 2.0 (default) should have flat structure
        Object vcClaim = jwt.getJWTClaimsSet().getClaim("vc");
        assertNull(vcClaim, "VC 2.0 (default) should NOT have nested 'vc' claim");

        // Should have VC 2.0 context
        @SuppressWarnings("unchecked")
        List<String> context2 = (List<String>) jwt.getJWTClaimsSet().getClaim("@context");
        assertNotNull(context2);
        assertTrue(context2.contains("https://www.w3.org/ns/credentials/v2"));
    }

    @Test
    void generateCredential_jwtIsSignedAndValid() throws ParseException {
        CredentialGenerationContext context = createTestContext();

        CredentialMessage.CredentialContainer container = generator.generateCredential(context);

        SignedJWT jwt = SignedJWT.parse((String) container.getPayload());

        assertNotNull(jwt.getSignature());
        assertEquals(ISSUER_DID, jwt.getJWTClaimsSet().getIssuer());
        assertEquals(HOLDER_DID, jwt.getJWTClaimsSet().getSubject());
    }

    @Test
    void generateCredential_handlesCredentialTypeInJwt() throws ParseException {
        CredentialGenerationContext context = createTestContext();

        CredentialMessage.CredentialContainer container = generator.generateCredential(context);

        SignedJWT jwt = SignedJWT.parse((String) container.getPayload());

        @SuppressWarnings("unchecked")
        List<String> types = (List<String>) jwt.getJWTClaimsSet().getClaim("type");

        assertNotNull(types);
        assertTrue(types.contains("VerifiableCredential"));
        assertTrue(types.contains(CUSTOM_CREDENTIAL_TYPE));
    }

    private CredentialGenerationContext createTestContext() {
        CredentialRequest request = CredentialRequest.Builder.newInstance()
                .issuerPid("test-issuer-pid")
                .holderPid(HOLDER_DID)
                .credentialIds(List.of(CUSTOM_CREDENTIAL_TYPE))
                .status(CredentialStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        return CredentialGenerationContext.withConstraints(request, new HashMap<>(), List.of());
    }
}

