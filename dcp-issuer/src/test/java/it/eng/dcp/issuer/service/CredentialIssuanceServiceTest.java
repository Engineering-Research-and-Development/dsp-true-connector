package it.eng.dcp.issuer.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import it.eng.dcp.common.model.CredentialMessage;
import it.eng.dcp.common.model.CredentialRequest;
import it.eng.dcp.common.model.CredentialStatus;
import it.eng.dcp.common.service.KeyService;
import it.eng.dcp.issuer.config.IssuerDidDocumentConfiguration;
import it.eng.dcp.issuer.config.IssuerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CredentialIssuanceService.
 */
@ExtendWith(MockitoExtension.class)
class CredentialIssuanceServiceTest {

    @Mock
    private IssuerProperties issuerProperties;

    @Mock
    private KeyService keyService;

    @Mock
    private IssuerDidDocumentConfiguration didDocumentConfig;

    @InjectMocks
    private CredentialIssuanceService issuanceService;

    private CredentialRequest testRequest;
    private ECKey testSigningKey;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() throws JOSEException {
        testRequest = CredentialRequest.Builder.newInstance()
                .issuerPid("issuer-pid-123")
                .holderPid("did:web:example.com:holder")
                .credentialIds(List.of("MembershipCredential"))
                .status(CredentialStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        testSigningKey = new ECKeyGenerator(Curve.P_256)
                .keyID("test-key-1")
                .generate();

        // Setup mocks BEFORE creating the service
        when(issuerProperties.getConnectorDid()).thenReturn("did:web:issuer.example.com");
        lenient().when(keyService.getSigningJwk(any())).thenReturn(testSigningKey);

        // Create service AFTER mocks are configured so factory gets correct issuerDid
        issuanceService = new CredentialIssuanceService(issuerProperties, keyService, didDocumentConfig);
    }

    @Test
    void generateCredentials_success_membershipCredential() {
        List<CredentialMessage.CredentialContainer> credentials = issuanceService.generateCredentials(testRequest);

        assertNotNull(credentials);
        assertEquals(1, credentials.size());

        CredentialMessage.CredentialContainer credential = credentials.get(0);
        assertEquals("MembershipCredential", credential.getCredentialType());
        assertEquals("jwt", credential.getFormat());
        assertNotNull(credential.getPayload());

        assertDoesNotThrow(() -> {
            SignedJWT jwt = SignedJWT.parse((String) credential.getPayload());
            assertNotNull(jwt.getJWTClaimsSet());
            assertEquals("did:web:issuer.example.com", jwt.getJWTClaimsSet().getIssuer());
            assertEquals("did:web:example.com:holder", jwt.getJWTClaimsSet().getSubject());
        });
    }

    @Test
    void generateCredentials_success_organizationCredential() {
        CredentialRequest orgRequest = CredentialRequest.Builder.newInstance()
                .issuerPid("issuer-pid-123")
                .holderPid("did:web:example.com:holder")
                .credentialIds(List.of("OrganizationCredential"))
                .status(CredentialStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        List<CredentialMessage.CredentialContainer> credentials = issuanceService.generateCredentials(orgRequest);

        assertNotNull(credentials);
        assertEquals(1, credentials.size());

        CredentialMessage.CredentialContainer credential = credentials.get(0);
        assertEquals("OrganizationCredential", credential.getCredentialType());
        assertEquals("jwt", credential.getFormat());
    }

    @Test
    void generateCredentials_success_multipleCredentials() {
        CredentialRequest multiRequest = CredentialRequest.Builder.newInstance()
                .issuerPid("issuer-pid-123")
                .holderPid("did:web:example.com:holder")
                .credentialIds(List.of("MembershipCredential", "OrganizationCredential"))
                .status(CredentialStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        List<CredentialMessage.CredentialContainer> credentials = issuanceService.generateCredentials(multiRequest);

        assertNotNull(credentials);
        assertEquals(2, credentials.size());
        assertTrue(credentials.stream().anyMatch(c -> c.getCredentialType().equals("MembershipCredential")));
        assertTrue(credentials.stream().anyMatch(c -> c.getCredentialType().equals("OrganizationCredential")));
    }

    @Test
    void generateCredentials_nullRequest_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                issuanceService.generateCredentials(null)
        );
    }

    @Test
    void generateCredentials_withCustomClaims() {
        Map<String, Object> customClaims = new HashMap<>();
        customClaims.put("customField", "customValue");
        customClaims.put("level", 5);

        List<CredentialMessage.CredentialContainer> credentials = issuanceService.generateCredentials(
                testRequest, customClaims, null
        );

        assertNotNull(credentials);
        assertEquals(1, credentials.size());
    }

    @Test
    void generateCredentials_withConstraints() {
        List<Map<String, Object>> constraintsData = new ArrayList<>();
        Map<String, Object> constraint = new HashMap<>();
        constraint.put("claimName", "age");
        constraint.put("operator", "GREATER_THAN");
        constraint.put("value", 18);
        constraintsData.add(constraint);

        List<CredentialMessage.CredentialContainer> credentials = issuanceService.generateCredentials(
                testRequest, null, constraintsData
        );

        assertNotNull(credentials);
        assertEquals(1, credentials.size());
    }

    @Test
    void generateCredentials_withInvalidConstraint_logsWarning() {
        List<Map<String, Object>> constraintsData = new ArrayList<>();
        Map<String, Object> invalidConstraint = new HashMap<>();
        invalidConstraint.put("claimName", "age");
        // Missing operator
        constraintsData.add(invalidConstraint);

        List<CredentialMessage.CredentialContainer> credentials = issuanceService.generateCredentials(
                testRequest, null, constraintsData
        );

        assertNotNull(credentials);
        assertEquals(1, credentials.size());
    }

    @Test
    void generateCredentials_unknownCredentialType_generatesGeneric() {
        CredentialRequest customRequest = CredentialRequest.Builder.newInstance()
                .issuerPid("issuer-pid-123")
                .holderPid("did:web:example.com:holder")
                .credentialIds(List.of("CustomCredential"))
                .status(CredentialStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        List<CredentialMessage.CredentialContainer> credentials = issuanceService.generateCredentials(customRequest);

        assertNotNull(credentials);
        assertEquals(1, credentials.size());
        assertEquals("CustomCredential", credentials.get(0).getCredentialType());
    }

    @Test
    void generateCredentials_jwtContainsVerifiableCredential() throws ParseException {
        List<CredentialMessage.CredentialContainer> credentials = issuanceService.generateCredentials(testRequest);

        CredentialMessage.CredentialContainer credential = credentials.get(0);
        SignedJWT jwt = SignedJWT.parse((String) credential.getPayload());

        // VC 2.0 has flat structure (no nested vc claim)
        // Check for @context at root level
        @SuppressWarnings("unchecked")
        List<String> context = (List<String>) jwt.getJWTClaimsSet().getClaim("@context");
        assertNotNull(context, "VC 2.0 should have @context at root level");
        assertTrue(context.contains("https://www.w3.org/ns/credentials/v2"));

        @SuppressWarnings("unchecked")
        List<String> types = (List<String>) jwt.getJWTClaimsSet().getClaim("type");
        assertNotNull(types);
        assertTrue(types.contains("VerifiableCredential"));
        assertTrue(types.contains("MembershipCredential"));

        @SuppressWarnings("unchecked")
        Map<String, Object> credentialSubject = (Map<String, Object>) jwt.getJWTClaimsSet().getClaim("credentialSubject");
        assertNotNull(credentialSubject);
        assertEquals("did:web:example.com:holder", credentialSubject.get("id"));
    }

    @Test
    void generateCredentials_jwtContainsMembershipFields() throws ParseException {
        List<CredentialMessage.CredentialContainer> credentials = issuanceService.generateCredentials(testRequest);

        CredentialMessage.CredentialContainer credential = credentials.get(0);
        SignedJWT jwt = SignedJWT.parse((String) credential.getPayload());

        // VC 2.0 has credentialSubject at root level
        @SuppressWarnings("unchecked")
        Map<String, Object> credentialSubject = (Map<String, Object>) jwt.getJWTClaimsSet().getClaim("credentialSubject");
        assertNotNull(credentialSubject);

        assertTrue(credentialSubject.containsKey("membershipType"));
        assertTrue(credentialSubject.containsKey("status"));
        assertTrue(credentialSubject.containsKey("membershipId"));
        assertEquals("Premium", credentialSubject.get("membershipType"));
        assertEquals("Active", credentialSubject.get("status"));
    }

    @Test
    void generateCredentials_jwtSignedWithES256() throws ParseException {
        List<CredentialMessage.CredentialContainer> credentials = issuanceService.generateCredentials(testRequest);

        CredentialMessage.CredentialContainer credential = credentials.get(0);
        SignedJWT jwt = SignedJWT.parse((String) credential.getPayload());

        assertEquals(JWSAlgorithm.ES256, jwt.getHeader().getAlgorithm());
        assertEquals("test-key-1", jwt.getHeader().getKeyID());
    }

    @Test
    void generateCredentials_jwtHasExpirationTime() throws ParseException {
        List<CredentialMessage.CredentialContainer> credentials = issuanceService.generateCredentials(testRequest);

        CredentialMessage.CredentialContainer credential = credentials.get(0);
        SignedJWT jwt = SignedJWT.parse((String) credential.getPayload());

        assertNotNull(jwt.getJWTClaimsSet().getExpirationTime());
        assertNotNull(jwt.getJWTClaimsSet().getIssueTime());
        assertTrue(jwt.getJWTClaimsSet().getExpirationTime().after(jwt.getJWTClaimsSet().getIssueTime()));
    }

    @Test
    void generateCredentials_jwtHasUniqueJwtId() throws ParseException {
        List<CredentialMessage.CredentialContainer> credentials1 = issuanceService.generateCredentials(testRequest);
        List<CredentialMessage.CredentialContainer> credentials2 = issuanceService.generateCredentials(testRequest);

        SignedJWT jwt1 = SignedJWT.parse((String) credentials1.get(0).getPayload());
        SignedJWT jwt2 = SignedJWT.parse((String) credentials2.get(0).getPayload());

        assertNotNull(jwt1.getJWTClaimsSet().getJWTID());
        assertNotNull(jwt2.getJWTClaimsSet().getJWTID());
        assertNotEquals(jwt1.getJWTClaimsSet().getJWTID(), jwt2.getJWTClaimsSet().getJWTID());
    }

    @Test
    void generateCredentials_signingFailure_throwsException() {
        assertThrows(RuntimeException.class, () -> {
            when(keyService.getSigningJwk(any())).thenThrow(new JOSEException("Signing failed"));
            issuanceService.generateCredentials(testRequest);
        });
    }

    @Test
    void generateCredentials_allCredentialsFail_throwsException() {
        assertThrows(IllegalStateException.class, () -> {
            when(keyService.getSigningJwk(any())).thenThrow(new RuntimeException("Key generation failed"));
            issuanceService.generateCredentials(testRequest);
        });
    }

    @Test
    void generateCredentials_backwardCompatibility_noCustomClaimsOrConstraints() {
        List<CredentialMessage.CredentialContainer> credentials = issuanceService.generateCredentials(testRequest);

        assertNotNull(credentials);
        assertEquals(1, credentials.size());
        verify(keyService, atLeastOnce()).getSigningJwk(any());
    }

    // ============================================
    // VC 2.0 Profile Tests (vc20-bssl/jwt)
    // ============================================

    @Test
    void generateCredentials_vc20Profile_hasCorrectContext() throws ParseException {
        // By default, credentials use VC 2.0 profile
        List<CredentialMessage.CredentialContainer> credentials = issuanceService.generateCredentials(testRequest);

        SignedJWT jwt = SignedJWT.parse((String) credentials.get(0).getPayload());

        @SuppressWarnings("unchecked")
        List<String> context = (List<String>) jwt.getJWTClaimsSet().getClaim("@context");
        assertNotNull(context, "VC 2.0 should have @context");
        assertTrue(context.contains("https://www.w3.org/ns/credentials/v2"),
                "VC 2.0 should use /ns/credentials/v2 context");
    }

    @Test
    void generateCredentials_vc20Profile_hasValidFromAndValidUntil() throws ParseException {
        List<CredentialMessage.CredentialContainer> credentials = issuanceService.generateCredentials(testRequest);

        SignedJWT jwt = SignedJWT.parse((String) credentials.get(0).getPayload());

        // VC 2.0 uses validFrom and validUntil (not issuanceDate/expirationDate)
        String validFrom = (String) jwt.getJWTClaimsSet().getClaim("validFrom");
        String validUntil = (String) jwt.getJWTClaimsSet().getClaim("validUntil");

        assertNotNull(validFrom, "VC 2.0 should have validFrom");
        assertNotNull(validUntil, "VC 2.0 should have validUntil");

        // Verify they are valid ISO timestamps
        assertDoesNotThrow(() -> Instant.parse(validFrom));
        assertDoesNotThrow(() -> Instant.parse(validUntil));
    }

    @Test
    void generateCredentials_vc20Profile_hasEnvelopedProof() throws ParseException {
        List<CredentialMessage.CredentialContainer> credentials = issuanceService.generateCredentials(testRequest);

        SignedJWT jwt = SignedJWT.parse((String) credentials.get(0).getPayload());

        // VC 2.0 with enveloped proof: NO proof object in payload
        Object proof = jwt.getJWTClaimsSet().getClaim("proof");
        assertNull(proof, "VC 2.0 with enveloped proof should NOT have a proof claim");

        // The JWT signature itself IS the proof
        assertNotNull(jwt.getSignature(), "JWT should have signature");
    }

    @Test
    void generateCredentials_vc20Profile_hasBitstringStatusListEntry() throws ParseException {
        List<CredentialMessage.CredentialContainer> credentials = issuanceService.generateCredentials(testRequest);

        SignedJWT jwt = SignedJWT.parse((String) credentials.get(0).getPayload());

        @SuppressWarnings("unchecked")
        Map<String, Object> credentialStatus = (Map<String, Object>) jwt.getJWTClaimsSet().getClaim("credentialStatus");
        assertNotNull(credentialStatus, "VC 2.0 should have credentialStatus");

        assertEquals("BitstringStatusListEntry", credentialStatus.get("type"),
                "VC 2.0 should use BitstringStatusListEntry");
        assertEquals("revocation", credentialStatus.get("statusPurpose"));
        assertNotNull(credentialStatus.get("statusListIndex"));
        assertNotNull(credentialStatus.get("statusListCredential"));
    }

    @Test
    void generateCredentials_vc20Profile_issuerIsObject() throws ParseException {
        List<CredentialMessage.CredentialContainer> credentials = issuanceService.generateCredentials(testRequest);

        SignedJWT jwt = SignedJWT.parse((String) credentials.get(0).getPayload());

        // VC 2.0 allows issuer as object
        Object issuer = jwt.getJWTClaimsSet().getClaim("issuer");
        assertNotNull(issuer);
        assertTrue(issuer instanceof Map, "VC 2.0 issuer should be an object");

        @SuppressWarnings("unchecked")
        Map<String, Object> issuerObj = (Map<String, Object>) issuer;
        assertEquals("did:web:issuer.example.com", issuerObj.get("id"));
    }

    @Test
    void generateCredentials_vc20Profile_jwtHeaderHasCorrectType() throws ParseException {
        List<CredentialMessage.CredentialContainer> credentials = issuanceService.generateCredentials(testRequest);

        SignedJWT jwt = SignedJWT.parse((String) credentials.get(0).getPayload());

        // VC 2.0 JWT should have type "vc+ld+jwt"
        assertEquals("vc+ld+jwt", jwt.getHeader().getType().getType(),
                "VC 2.0 JWT header should have type 'vc+ld+jwt'");
    }

    @Test
    void generateCredentials_vc20Profile_flatStructureNoNestedVc() throws ParseException {
        List<CredentialMessage.CredentialContainer> credentials = issuanceService.generateCredentials(testRequest);

        SignedJWT jwt = SignedJWT.parse((String) credentials.get(0).getPayload());

        // VC 2.0 has FLAT structure - no nested "vc" claim
        Object vc = jwt.getJWTClaimsSet().getClaim("vc");
        assertNull(vc, "VC 2.0 should NOT have nested 'vc' claim - structure should be flat");

        // All VC fields should be at root level
        assertNotNull(jwt.getJWTClaimsSet().getClaim("@context"));
        assertNotNull(jwt.getJWTClaimsSet().getClaim("type"));
        assertNotNull(jwt.getJWTClaimsSet().getClaim("credentialSubject"));
    }

    // ============================================
    // VC 1.1 Profile Tests (vc11-sl2021/jwt)
    // ============================================

    @Test
    void generateCredentials_vc11Profile_hasNestedVcClaim() throws ParseException {
        // Create context with VC 1.1 profile metadata
        Map<String, Object> customClaims = new HashMap<>();
        Map<String, Map<String, Object>> metadata = new HashMap<>();
        Map<String, Object> credMetadata = new HashMap<>();
        credMetadata.put("profile", "vc11-sl2021/jwt");
        metadata.put("MembershipCredential", credMetadata);
        customClaims.put("__credentialMetadata", metadata);

        List<CredentialMessage.CredentialContainer> credentials = issuanceService.generateCredentials(
                testRequest, customClaims, null
        );

        SignedJWT jwt = SignedJWT.parse((String) credentials.get(0).getPayload());

        // VC 1.1 has NESTED structure with "vc" claim
        @SuppressWarnings("unchecked")
        Map<String, Object> vc = (Map<String, Object>) jwt.getJWTClaimsSet().getClaim("vc");
        assertNotNull(vc, "VC 1.1 should have nested 'vc' claim");

        // VC 1.1 fields are inside the vc claim
        assertTrue(vc.containsKey("@context"));
        assertTrue(vc.containsKey("type"));
        assertTrue(vc.containsKey("credentialSubject"));
        assertTrue(vc.containsKey("proof"), "VC 1.1 should have external proof object");
    }

    @Test
    void generateCredentials_vc11Profile_hasCorrectContext() throws ParseException {
        Map<String, Object> customClaims = new HashMap<>();
        Map<String, Map<String, Object>> metadata = new HashMap<>();
        Map<String, Object> credMetadata = new HashMap<>();
        credMetadata.put("profile", "vc11-sl2021/jwt");
        metadata.put("MembershipCredential", credMetadata);
        customClaims.put("__credentialMetadata", metadata);

        List<CredentialMessage.CredentialContainer> credentials = issuanceService.generateCredentials(
                testRequest, customClaims, null
        );

        SignedJWT jwt = SignedJWT.parse((String) credentials.get(0).getPayload());

        @SuppressWarnings("unchecked")
        Map<String, Object> vc = (Map<String, Object>) jwt.getJWTClaimsSet().getClaim("vc");

        @SuppressWarnings("unchecked")
        List<String> context = (List<String>) vc.get("@context");
        assertNotNull(context);
        assertTrue(context.contains("https://www.w3.org/2018/credentials/v1"),
                "VC 1.1 should use /2018/credentials/v1 context");
    }

    @Test
    void generateCredentials_vc11Profile_hasIssuanceDateAndExpirationDate() throws ParseException {
        Map<String, Object> customClaims = new HashMap<>();
        Map<String, Map<String, Object>> metadata = new HashMap<>();
        Map<String, Object> credMetadata = new HashMap<>();
        credMetadata.put("profile", "vc11-sl2021/jwt");
        metadata.put("MembershipCredential", credMetadata);
        customClaims.put("__credentialMetadata", metadata);

        List<CredentialMessage.CredentialContainer> credentials = issuanceService.generateCredentials(
                testRequest, customClaims, null
        );

        SignedJWT jwt = SignedJWT.parse((String) credentials.get(0).getPayload());

        @SuppressWarnings("unchecked")
        Map<String, Object> vc = (Map<String, Object>) jwt.getJWTClaimsSet().getClaim("vc");

        // VC 1.1 uses issuanceDate and expirationDate (not validFrom/validUntil)
        String issuanceDate = (String) vc.get("issuanceDate");
        String expirationDate = (String) vc.get("expirationDate");

        assertNotNull(issuanceDate, "VC 1.1 should have issuanceDate");
        assertNotNull(expirationDate, "VC 1.1 should have expirationDate");
    }

    @Test
    void generateCredentials_vc11Profile_hasExternalProof() throws ParseException {
        Map<String, Object> customClaims = new HashMap<>();
        Map<String, Map<String, Object>> metadata = new HashMap<>();
        Map<String, Object> credMetadata = new HashMap<>();
        credMetadata.put("profile", "vc11-sl2021/jwt");
        metadata.put("MembershipCredential", credMetadata);
        customClaims.put("__credentialMetadata", metadata);

        List<CredentialMessage.CredentialContainer> credentials = issuanceService.generateCredentials(
                testRequest, customClaims, null
        );

        SignedJWT jwt = SignedJWT.parse((String) credentials.get(0).getPayload());

        @SuppressWarnings("unchecked")
        Map<String, Object> vc = (Map<String, Object>) jwt.getJWTClaimsSet().getClaim("vc");

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
    void generateCredentials_vc11Profile_jwtHeaderHasStandardType() throws ParseException {
        Map<String, Object> customClaims = new HashMap<>();
        Map<String, Map<String, Object>> metadata = new HashMap<>();
        Map<String, Object> credMetadata = new HashMap<>();
        credMetadata.put("profile", "vc11-sl2021/jwt");
        metadata.put("MembershipCredential", credMetadata);
        customClaims.put("__credentialMetadata", metadata);

        List<CredentialMessage.CredentialContainer> credentials = issuanceService.generateCredentials(
                testRequest, customClaims, null
        );

        SignedJWT jwt = SignedJWT.parse((String) credentials.get(0).getPayload());

        // VC 1.1 JWT typically doesn't set a specific type or uses "JWT"
        // Our implementation doesn't set type for VC 1.1, which is valid
        assertNotEquals("vc+ld+jwt", jwt.getHeader().getType() != null ? jwt.getHeader().getType().getType() : null,
                "VC 1.1 should NOT use 'vc+ld+jwt' type");
    }
}

