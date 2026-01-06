package it.eng.dcp.issuer.service.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import it.eng.dcp.common.config.BaseDidDocumentConfiguration;
import it.eng.dcp.common.service.KeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for VC11JwtGenerator.
 * Verifies correct VC 1.1 structure generation.
 */
@ExtendWith(MockitoExtension.class)
class VC11JwtGeneratorTest {

    @Mock
    private KeyService keyService;

    @Mock
    private BaseDidDocumentConfiguration didDocumentConfig;

    @InjectMocks
    private VC11JwtGenerator generator;

    private ECKey testSigningKey;

    private static final String ISSUER_DID = "did:web:issuer.example.com";
    private static final String HOLDER_DID = "did:web:holder.example.com";

    @BeforeEach
    void setUp() throws Exception {
        testSigningKey = new ECKeyGenerator(Curve.P_256)
                .keyID("test-key-1")
                .generate();

        when(keyService.getSigningJwk(any())).thenReturn(testSigningKey);

        generator = new VC11JwtGenerator(ISSUER_DID, keyService, didDocumentConfig);
    }

    @Test
    void generateJwt_hasNestedVcClaim() throws ParseException {
        Map<String, String> claims = createTestClaims();

        String jwt = generator.generateJwt(HOLDER_DID, "MembershipCredential", claims);

        SignedJWT signedJWT = SignedJWT.parse(jwt);

        // VC 1.1 has NESTED structure with "vc" claim
        @SuppressWarnings("unchecked")
        Map<String, Object> vc = (Map<String, Object>) signedJWT.getJWTClaimsSet().getClaim("vc");
        assertNotNull(vc, "VC 1.1 should have nested 'vc' claim");

        // VC 1.1 fields are inside the vc claim
        assertTrue(vc.containsKey("@context"));
        assertTrue(vc.containsKey("type"));
        assertTrue(vc.containsKey("credentialSubject"));
        assertTrue(vc.containsKey("proof"), "VC 1.1 should have external proof object");
    }

    @Test
    void generateJwt_hasCorrectContext() throws ParseException {
        Map<String, String> claims = createTestClaims();

        String jwt = generator.generateJwt(HOLDER_DID, "MembershipCredential", claims);

        SignedJWT signedJWT = SignedJWT.parse(jwt);

        @SuppressWarnings("unchecked")
        Map<String, Object> vc = (Map<String, Object>) signedJWT.getJWTClaimsSet().getClaim("vc");

        @SuppressWarnings("unchecked")
        List<String> context = (List<String>) vc.get("@context");
        assertNotNull(context);
        assertTrue(context.contains("https://www.w3.org/2018/credentials/v1"),
                "VC 1.1 should use /2018/credentials/v1 context");
    }

    @Test
    void generateJwt_hasIssuanceDateAndExpirationDate() throws ParseException {
        Map<String, String> claims = createTestClaims();

        String jwt = generator.generateJwt(HOLDER_DID, "MembershipCredential", claims);

        SignedJWT signedJWT = SignedJWT.parse(jwt);

        @SuppressWarnings("unchecked")
        Map<String, Object> vc = (Map<String, Object>) signedJWT.getJWTClaimsSet().getClaim("vc");

        // VC 1.1 uses issuanceDate and expirationDate
        String issuanceDate = (String) vc.get("issuanceDate");
        String expirationDate = (String) vc.get("expirationDate");

        assertNotNull(issuanceDate, "VC 1.1 should have issuanceDate");
        assertNotNull(expirationDate, "VC 1.1 should have expirationDate");

        // Should NOT have validFrom/validUntil
        assertFalse(vc.containsKey("validFrom"), "VC 1.1 should NOT have validFrom");
        assertFalse(vc.containsKey("validUntil"), "VC 1.1 should NOT have validUntil");
    }

    @Test
    void generateJwt_hasExternalProof() throws ParseException {
        Map<String, String> claims = createTestClaims();

        String jwt = generator.generateJwt(HOLDER_DID, "MembershipCredential", claims);

        SignedJWT signedJWT = SignedJWT.parse(jwt);

        @SuppressWarnings("unchecked")
        Map<String, Object> vc = (Map<String, Object>) signedJWT.getJWTClaimsSet().getClaim("vc");

        // VC 1.1 has external proof object
        @SuppressWarnings("unchecked")
        Map<String, Object> proof = (Map<String, Object>) vc.get("proof");
        assertNotNull(proof, "VC 1.1 should have external proof object");

        assertEquals("JsonWebSignature2020", proof.get("type"));
        assertNotNull(proof.get("created"));
        assertNotNull(proof.get("verificationMethod"));
        assertEquals("assertionMethod", proof.get("proofPurpose"));
    }

    @Test
    void generateJwt_jwtHeaderHasStandardType() throws ParseException {
        Map<String, String> claims = createTestClaims();

        String jwt = generator.generateJwt(HOLDER_DID, "MembershipCredential", claims);

        SignedJWT signedJWT = SignedJWT.parse(jwt);

        // VC 1.1 JWT doesn't set a specific type (or uses standard "JWT")
        // It should NOT use "vc+ld+jwt"
        String headerType = signedJWT.getHeader().getType() != null
            ? signedJWT.getHeader().getType().getType()
            : null;

        assertNotEquals("vc+ld+jwt", headerType,
                "VC 1.1 should NOT use 'vc+ld+jwt' type");
    }

    @Test
    void generateJwt_hasCorrectType() throws ParseException {
        Map<String, String> claims = createTestClaims();

        String jwt = generator.generateJwt(HOLDER_DID, "MembershipCredential", claims);

        SignedJWT signedJWT = SignedJWT.parse(jwt);

        @SuppressWarnings("unchecked")
        Map<String, Object> vc = (Map<String, Object>) signedJWT.getJWTClaimsSet().getClaim("vc");

        @SuppressWarnings("unchecked")
        List<String> types = (List<String>) vc.get("type");

        assertNotNull(types);
        assertTrue(types.contains("VerifiableCredential"));
        assertTrue(types.contains("MembershipCredential"));
    }

    @Test
    void generateJwt_issuerIsString() throws ParseException {
        Map<String, String> claims = createTestClaims();

        String jwt = generator.generateJwt(HOLDER_DID, "MembershipCredential", claims);

        SignedJWT signedJWT = SignedJWT.parse(jwt);

        @SuppressWarnings("unchecked")
        Map<String, Object> vc = (Map<String, Object>) signedJWT.getJWTClaimsSet().getClaim("vc");

        // VC 1.1 issuer is string DID
        Object issuer = vc.get("issuer");
        assertNotNull(issuer);
        assertTrue(issuer instanceof String, "VC 1.1 issuer should be a string");
        assertEquals(ISSUER_DID, issuer);
    }

    @Test
    void generateJwt_hasCredentialSubjectWithHolderDid() throws ParseException {
        Map<String, String> claims = createTestClaims();

        String jwt = generator.generateJwt(HOLDER_DID, "MembershipCredential", claims);

        SignedJWT signedJWT = SignedJWT.parse(jwt);

        @SuppressWarnings("unchecked")
        Map<String, Object> vc = (Map<String, Object>) signedJWT.getJWTClaimsSet().getClaim("vc");

        @SuppressWarnings("unchecked")
        Map<String, Object> credentialSubject = (Map<String, Object>) vc.get("credentialSubject");

        assertNotNull(credentialSubject);
        assertEquals(HOLDER_DID, credentialSubject.get("id"));
        assertEquals("Premium", credentialSubject.get("membershipType"));
        assertEquals("Active", credentialSubject.get("status"));
    }

    @Test
    void generateJwt_hasCredentialId() throws ParseException {
        Map<String, String> claims = createTestClaims();

        String jwt = generator.generateJwt(HOLDER_DID, "MembershipCredential", claims);

        SignedJWT signedJWT = SignedJWT.parse(jwt);

        @SuppressWarnings("unchecked")
        Map<String, Object> vc = (Map<String, Object>) signedJWT.getJWTClaimsSet().getClaim("vc");

        assertNotNull(vc.get("id"), "VC 1.1 should have an id field");
        assertTrue(((String) vc.get("id")).startsWith("urn:uuid:"));
    }

    @Test
    void generateJwt_signedWithES256() throws ParseException {
        Map<String, String> claims = createTestClaims();

        String jwt = generator.generateJwt(HOLDER_DID, "MembershipCredential", claims);

        SignedJWT signedJWT = SignedJWT.parse(jwt);

        assertEquals(JWSAlgorithm.ES256, signedJWT.getHeader().getAlgorithm());
        assertEquals("test-key-1", signedJWT.getHeader().getKeyID());
    }

    @Test
    void generateJwt_hasStandardJwtClaims() throws ParseException {
        Map<String, String> claims = createTestClaims();

        String jwt = generator.generateJwt(HOLDER_DID, "MembershipCredential", claims);

        SignedJWT signedJWT = SignedJWT.parse(jwt);

        assertEquals(ISSUER_DID, signedJWT.getJWTClaimsSet().getIssuer());
        assertEquals(HOLDER_DID, signedJWT.getJWTClaimsSet().getSubject());
        assertNotNull(signedJWT.getJWTClaimsSet().getIssueTime());
        assertNotNull(signedJWT.getJWTClaimsSet().getExpirationTime());
        assertNotNull(signedJWT.getJWTClaimsSet().getJWTID());
    }

    @Test
    void generateJwt_noFieldsAtRootLevel() throws ParseException {
        Map<String, String> claims = createTestClaims();

        String jwt = generator.generateJwt(HOLDER_DID, "MembershipCredential", claims);

        SignedJWT signedJWT = SignedJWT.parse(jwt);

        // VC 1.1 should NOT have VC fields at root level
        assertNull(signedJWT.getJWTClaimsSet().getClaim("@context"),
            "VC 1.1 should NOT have @context at root level");
        assertNull(signedJWT.getJWTClaimsSet().getClaim("type"),
            "VC 1.1 should NOT have type at root level");
        assertNull(signedJWT.getJWTClaimsSet().getClaim("credentialSubject"),
            "VC 1.1 should NOT have credentialSubject at root level");
        assertNull(signedJWT.getJWTClaimsSet().getClaim("validFrom"),
            "VC 1.1 should NOT have validFrom at root level");
    }

    private Map<String, String> createTestClaims() {
        Map<String, String> claims = new HashMap<>();
        claims.put("membershipType", "Premium");
        claims.put("status", "Active");
        return claims;
    }
}

