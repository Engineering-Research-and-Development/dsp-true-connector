package it.eng.dcp.issuer.service.credential;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import it.eng.dcp.common.config.BaseDidDocumentConfiguration;
import it.eng.dcp.common.model.*;
import it.eng.dcp.common.service.KeyService;
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
 * Unit tests for MembershipCredentialGenerator.
 * Verifies correct membership credential generation.
 */
class MembershipCredentialGeneratorTest {

    @Mock
    private KeyService keyService;

    @Mock
    private BaseDidDocumentConfiguration didDocumentConfig;

    private MembershipCredentialGenerator generator;
    private VcJwtGeneratorFactory jwtGeneratorFactory;
    private ProfileExtractor profileExtractor;
    private AutoCloseable closeable;

    private static final String ISSUER_DID = "did:web:issuer.example.com";
    private static final String HOLDER_DID = "did:web:holder.example.com";

    @BeforeEach
    void setUp() throws Exception {
        closeable = MockitoAnnotations.openMocks(this);

        ECKey testSigningKey = new ECKeyGenerator(Curve.P_256)
                .keyID("test-key-1")
                .generate();

        when(keyService.getSigningJwk(any())).thenReturn(testSigningKey);

        jwtGeneratorFactory = new VcJwtGeneratorFactory(ISSUER_DID, keyService, didDocumentConfig);
        profileExtractor = new ProfileExtractor();
        generator = new MembershipCredentialGenerator(jwtGeneratorFactory, profileExtractor);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    void getCredentialType_returnsMembershipCredential() {
        assertEquals("MembershipCredential", generator.getCredentialType());
    }

    @Test
    void generateCredential_createsValidContainer() {
        CredentialGenerationContext context = createTestContext();

        CredentialMessage.CredentialContainer container = generator.generateCredential(context);

        assertNotNull(container);
        assertEquals("MembershipCredential", container.getCredentialType());
        assertEquals("jwt", container.getFormat());
        assertNotNull(container.getPayload());
        assertTrue(container.getPayload() instanceof String);
    }

    @Test
    void generateCredential_jwtContainsMembershipClaims() throws ParseException {
        CredentialGenerationContext context = createTestContext();

        CredentialMessage.CredentialContainer container = generator.generateCredential(context);

        SignedJWT jwt = SignedJWT.parse((String) container.getPayload());

        @SuppressWarnings("unchecked")
        Map<String, Object> credentialSubject = (Map<String, Object>) jwt.getJWTClaimsSet().getClaim("credentialSubject");

        assertNotNull(credentialSubject);
        assertEquals(HOLDER_DID, credentialSubject.get("id"));
        assertEquals("Premium", credentialSubject.get("membershipType"));
        assertEquals("Active", credentialSubject.get("status"));
        assertNotNull(credentialSubject.get("membershipId"));
        assertTrue(((String) credentialSubject.get("membershipId")).startsWith("MEMBER-"));
    }

    @Test
    void generateCredential_usesDefaultProfile() throws ParseException {
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
    void generateCredential_usesVC11Profile_whenSpecified() throws ParseException {
        CredentialGenerationContext context = createTestContextWithProfile(ProfileId.VC11_SL2021_JWT);

        CredentialMessage.CredentialContainer container = generator.generateCredential(context);

        SignedJWT jwt = SignedJWT.parse((String) container.getPayload());

        // VC 1.1 should have nested structure
        @SuppressWarnings("unchecked")
        Map<String, Object> vc = (Map<String, Object>) jwt.getJWTClaimsSet().getClaim("vc");
        assertNotNull(vc, "VC 1.1 should have nested 'vc' claim");

        @SuppressWarnings("unchecked")
        Map<String, Object> credentialSubject = (Map<String, Object>) vc.get("credentialSubject");
        assertNotNull(credentialSubject);
        assertEquals("Premium", credentialSubject.get("membershipType"));
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

    private CredentialGenerationContext createTestContext() {
        CredentialRequest request = CredentialRequest.Builder.newInstance()
                .issuerPid("test-issuer-pid")
                .holderPid(HOLDER_DID)
                .credentialIds(List.of("MembershipCredential"))
                .status(CredentialStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        return CredentialGenerationContext.withConstraints(request, new HashMap<>(), List.of());
    }

    private CredentialGenerationContext createTestContextWithProfile(ProfileId profile) {
        CredentialRequest request = CredentialRequest.Builder.newInstance()
                .issuerPid("test-issuer-pid")
                .holderPid(HOLDER_DID)
                .credentialIds(List.of("MembershipCredential"))
                .status(CredentialStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        Map<String, Object> claims = new HashMap<>();
        Map<String, Map<String, Object>> metadata = new HashMap<>();
        Map<String, Object> credMetadata = new HashMap<>();
        credMetadata.put("profile", profile.getSpecAlias());
        metadata.put("MembershipCredential", credMetadata);
        claims.put("__credentialMetadata", metadata);

        return CredentialGenerationContext.withConstraints(request, claims, List.of());
    }
}

