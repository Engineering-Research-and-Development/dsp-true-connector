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
 * Unit tests for VC20JwtGenerator.
 * Verifies correct VC 2.0 structure generation.
 */
@ExtendWith(MockitoExtension.class)
class VC20JwtGeneratorTest {

    @Mock
    private KeyService keyService;

    @Mock
    private BaseDidDocumentConfiguration didDocumentConfig;

    @InjectMocks
    private VC20JwtGenerator generator;

    private ECKey testSigningKey;

    private static final String ISSUER_DID = "did:web:issuer.example.com";
    private static final String HOLDER_DID = "did:web:holder.example.com";

    @BeforeEach
    void setUp() throws Exception {
        testSigningKey = new ECKeyGenerator(Curve.P_256)
                .keyID("test-key-1")
                .generate();

        when(keyService.getSigningJwk(any())).thenReturn(testSigningKey);

        generator = new VC20JwtGenerator(ISSUER_DID, keyService, didDocumentConfig);
    }

    @Test
    void generateJwt_hasCorrectContext() throws ParseException {
        Map<String, String> claims = createTestClaims();

        String jwt = generator.generateJwt(HOLDER_DID, "MembershipCredential", claims);

        SignedJWT signedJWT = SignedJWT.parse(jwt);

        @SuppressWarnings("unchecked")
        List<String> context = (List<String>) signedJWT.getJWTClaimsSet().getClaim("@context");

        assertNotNull(context, "VC 2.0 should have @context");
        assertTrue(context.contains("https://www.w3.org/ns/credentials/v2"),
                "VC 2.0 should use /ns/credentials/v2 context");
    }

    @Test
    void generateJwt_hasFlatStructure() throws ParseException {
        Map<String, String> claims = createTestClaims();

        String jwt = generator.generateJwt(HOLDER_DID, "MembershipCredential", claims);

        SignedJWT signedJWT = SignedJWT.parse(jwt);

        // VC 2.0 has FLAT structure - no nested "vc" claim
        Object vc = signedJWT.getJWTClaimsSet().getClaim("vc");
        assertNull(vc, "VC 2.0 should NOT have nested 'vc' claim");

        // All fields should be at root level
        assertNotNull(signedJWT.getJWTClaimsSet().getClaim("@context"));
        assertNotNull(signedJWT.getJWTClaimsSet().getClaim("type"));
        assertNotNull(signedJWT.getJWTClaimsSet().getClaim("credentialSubject"));
    }

    @Test
    void generateJwt_hasValidFromAndValidUntil() throws ParseException {
        Map<String, String> claims = createTestClaims();

        String jwt = generator.generateJwt(HOLDER_DID, "MembershipCredential", claims);

        SignedJWT signedJWT = SignedJWT.parse(jwt);

        // VC 2.0 uses validFrom and validUntil
        String validFrom = (String) signedJWT.getJWTClaimsSet().getClaim("validFrom");
        String validUntil = (String) signedJWT.getJWTClaimsSet().getClaim("validUntil");

        assertNotNull(validFrom, "VC 2.0 should have validFrom");
        assertNotNull(validUntil, "VC 2.0 should have validUntil");
    }

    @Test
    void generateJwt_hasEnvelopedProof() throws ParseException {
        Map<String, String> claims = createTestClaims();

        String jwt = generator.generateJwt(HOLDER_DID, "MembershipCredential", claims);

        SignedJWT signedJWT = SignedJWT.parse(jwt);

        // VC 2.0 with enveloped proof: NO proof object in payload
        Object proof = signedJWT.getJWTClaimsSet().getClaim("proof");
        assertNull(proof, "VC 2.0 with enveloped proof should NOT have a proof claim");

        // The JWT signature itself IS the proof
        assertNotNull(signedJWT.getSignature(), "JWT should have signature");
    }

    @Test
    void generateJwt_hasBitstringStatusListEntry() throws ParseException {
        Map<String, String> claims = createTestClaims();
        String statusListId = "did:web:issuer.example.com/status/1";
        int statusListIndex = 42;
        String jwt = generator.generateJwt(HOLDER_DID, "MembershipCredential", claims, statusListId, statusListIndex);
        SignedJWT signedJWT = SignedJWT.parse(jwt);
        @SuppressWarnings("unchecked")
        Map<String, Object> credentialStatus = (Map<String, Object>) signedJWT.getJWTClaimsSet().getClaim("credentialStatus");
        assertNotNull(credentialStatus, "VC 2.0 should have credentialStatus");
        assertEquals("BitstringStatusListEntry", credentialStatus.get("type"),
                "VC 2.0 should use BitstringStatusListEntry");
        assertEquals("revocation", credentialStatus.get("statusPurpose"));
        assertEquals(String.valueOf(statusListIndex), credentialStatus.get("statusListIndex"));
        assertEquals(statusListId, credentialStatus.get("statusListCredential"));
        assertEquals(statusListId + "#" + statusListIndex, credentialStatus.get("id"));
    }

    @Test
    void generateJwt_issuerIsObject() throws ParseException {
        Map<String, String> claims = createTestClaims();

        String jwt = generator.generateJwt(HOLDER_DID, "MembershipCredential", claims);

        SignedJWT signedJWT = SignedJWT.parse(jwt);

        // VC 2.0 allows issuer as object
        Object issuer = signedJWT.getJWTClaimsSet().getClaim("issuer");
        assertNotNull(issuer);
        assertTrue(issuer instanceof Map, "VC 2.0 issuer should be an object");

        @SuppressWarnings("unchecked")
        Map<String, Object> issuerObj = (Map<String, Object>) issuer;
        assertEquals(ISSUER_DID, issuerObj.get("id"));
    }

    @Test
    void generateJwt_jwtHeaderHasCorrectType() throws ParseException {
        Map<String, String> claims = createTestClaims();

        String jwt = generator.generateJwt(HOLDER_DID, "MembershipCredential", claims);

        SignedJWT signedJWT = SignedJWT.parse(jwt);

        // VC 2.0 JWT should have type "vc+ld+jwt"
        assertEquals("vc+ld+jwt", signedJWT.getHeader().getType().getType(),
                "VC 2.0 JWT header should have type 'vc+ld+jwt'");
    }

    @Test
    void generateJwt_hasCorrectType() throws ParseException {
        Map<String, String> claims = createTestClaims();

        String jwt = generator.generateJwt(HOLDER_DID, "MembershipCredential", claims);

        SignedJWT signedJWT = SignedJWT.parse(jwt);

        @SuppressWarnings("unchecked")
        List<String> types = (List<String>) signedJWT.getJWTClaimsSet().getClaim("type");

        assertNotNull(types);
        assertTrue(types.contains("VerifiableCredential"));
        assertTrue(types.contains("MembershipCredential"));
    }

    @Test
    void generateJwt_hasCredentialSubjectWithHolderDid() throws ParseException {
        Map<String, String> claims = createTestClaims();

        String jwt = generator.generateJwt(HOLDER_DID, "MembershipCredential", claims);

        SignedJWT signedJWT = SignedJWT.parse(jwt);

        @SuppressWarnings("unchecked")
        Map<String, Object> credentialSubject = (Map<String, Object>) signedJWT.getJWTClaimsSet().getClaim("credentialSubject");

        assertNotNull(credentialSubject);
        assertEquals(HOLDER_DID, credentialSubject.get("id"));
        assertEquals("Premium", credentialSubject.get("membershipType"));
        assertEquals("Active", credentialSubject.get("status"));
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
    void generateJwt_withStatusListInfo_hasBitstringStatusListEntry() throws Exception {
        Map<String, String> claims = createTestClaims();
        String statusListId = "did:web:issuer.example.com/status/1";
        int statusListIndex = 42;
        String jwt = generator.generateJwt(HOLDER_DID, "MembershipCredential", claims, statusListId, statusListIndex);
        SignedJWT signedJWT = SignedJWT.parse(jwt);
        Map<String, Object> credentialStatus = (Map<String, Object>) signedJWT.getJWTClaimsSet().getClaim("credentialStatus");
        assertNotNull(credentialStatus, "VC 2.0 should have credentialStatus when status list info is provided");
        assertEquals("BitstringStatusListEntry", credentialStatus.get("type"));
        assertEquals("revocation", credentialStatus.get("statusPurpose"));
        assertEquals(String.valueOf(statusListIndex), credentialStatus.get("statusListIndex"));
        assertEquals(statusListId, credentialStatus.get("statusListCredential"));
        assertEquals(statusListId + "#" + statusListIndex, credentialStatus.get("id"));
    }

    @Test
    void generateJwt_withoutStatusListInfo_noCredentialStatus() throws Exception {
        Map<String, String> claims = createTestClaims();
        String jwt = generator.generateJwt(HOLDER_DID, "MembershipCredential", claims);
        SignedJWT signedJWT = SignedJWT.parse(jwt);
        assertNull(signedJWT.getJWTClaimsSet().getClaim("credentialStatus"), "VC 2.0 should NOT have credentialStatus when status list info is not provided");
    }

    private Map<String, String> createTestClaims() {
        Map<String, String> claims = new HashMap<>();
        claims.put("membershipType", "Premium");
        claims.put("status", "Active");
        return claims;
    }
}
