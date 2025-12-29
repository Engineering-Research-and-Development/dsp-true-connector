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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

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
@SuppressWarnings({"unused", "FieldCanBeLocal"})
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
        closeable = MockitoAnnotations.openMocks(this);

        testRequest = CredentialRequest.Builder.newInstance()
                .issuerPid("issuer-pid-123")
                .holderPid("did:web:example.com:holder")
                .credentialIds(List.of("MembershipCredential"))
                .status(CredentialStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        ECKey testSigningKey = new ECKeyGenerator(Curve.P_256)
                .keyID("test-key-1")
                .generate();

        when(issuerProperties.getConnectorDid()).thenReturn("did:web:issuer.example.com");
        when(keyService.getSigningJwk(any())).thenReturn(testSigningKey);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
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

        @SuppressWarnings("unchecked")
        Map<String, Object> vc = (Map<String, Object>) jwt.getJWTClaimsSet().getClaim("vc");
        assertNotNull(vc);
        assertTrue(vc.containsKey("@context"));
        assertTrue(vc.containsKey("type"));
        assertTrue(vc.containsKey("credentialSubject"));

        @SuppressWarnings("unchecked")
        List<String> types = (List<String>) vc.get("type");
        assertTrue(types.contains("VerifiableCredential"));
        assertTrue(types.contains("MembershipCredential"));

        @SuppressWarnings("unchecked")
        Map<String, Object> credentialSubject = (Map<String, Object>) vc.get("credentialSubject");
        assertEquals("did:web:example.com:holder", credentialSubject.get("id"));
    }

    @Test
    void generateCredentials_jwtContainsMembershipFields() throws ParseException {
        List<CredentialMessage.CredentialContainer> credentials = issuanceService.generateCredentials(testRequest);

        CredentialMessage.CredentialContainer credential = credentials.get(0);
        SignedJWT jwt = SignedJWT.parse((String) credential.getPayload());

        @SuppressWarnings("unchecked")
        Map<String, Object> vc = (Map<String, Object>) jwt.getJWTClaimsSet().getClaim("vc");
        @SuppressWarnings("unchecked")
        Map<String, Object> credentialSubject = (Map<String, Object>) vc.get("credentialSubject");

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
}

