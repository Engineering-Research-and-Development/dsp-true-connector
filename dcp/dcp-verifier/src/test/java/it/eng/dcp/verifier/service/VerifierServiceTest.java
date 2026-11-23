package it.eng.dcp.verifier.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import it.eng.dcp.common.audit.DcpAuditEventType;
import it.eng.dcp.common.client.SimpleOkHttpRestClient;
import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.exception.DidResolutionException;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.model.PresentationResponseMessage;
import it.eng.dcp.common.model.ServiceEntry;
import it.eng.dcp.common.service.IssuerTrustService;
import it.eng.dcp.common.service.audit.DcpAuditEventPublisher;
import it.eng.dcp.common.service.did.HttpDidResolverService;
import it.eng.dcp.common.service.did.ResolvedVerificationMethod;
import it.eng.dcp.common.service.sts.SelfIssuedIdTokenService;
import okhttp3.RequestBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for VerifierService.validateAndQueryHolderPresentations().
 *
 * Tests are organized progressively from simple validation failures to complete successful flows,
 * covering each step of the verification process:
 * - Step 3a: Self-issued ID token validation
 * - Step 3b: Access token parsing
 * - Step 3c: Holder DID resolution
 * - Step 4: Presentation query
 * - Step 5: Presentation validation (outer JWT)
 * - Step 5: Credential validation (inner JWTs)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VerifierService - validateAndQueryHolderPresentations")
class VerifierServiceTest {

    @Mock
    private SelfIssuedIdTokenService tokenService;

    @Mock
    private HttpDidResolverService didResolverService;

    @Mock
    private SimpleOkHttpRestClient httpClient;

    @Mock
    private DidDocumentConfig verifierConfig;

    @Mock
    private IssuerTrustService issuerTrustService;

    @Mock
    private DcpAuditEventPublisher auditPublisher;

    private VerifierService verifierService;
    private ObjectMapper objectMapper;

    // Test DIDs
    private static final String VERIFIER_DID = "did:web:localhost:8080:verifier";
    private static final String HOLDER_DID = "did:web:localhost:8080:holder";
    private static final String ISSUER_DID = "did:web:localhost:8084:issuer";

    // Test keys (generated once for performance)
    private ECKey holderKey;
    private ECKey issuerKey;
    private ECKey verifierKey;

    @BeforeEach
    void setUp() throws JOSEException {
        objectMapper = new ObjectMapper();

        // Generate test keys
        holderKey = new ECKeyGenerator(Curve.P_256)
                .keyID("holder-key-1")
                .generate();

        issuerKey = new ECKeyGenerator(Curve.P_256)
                .keyID("issuer-key-1")
                .generate();

        verifierKey = new ECKeyGenerator(Curve.P_256)
                .keyID("verifier-key-1")
                .generate();

        lenient().when(verifierConfig.getDid()).thenReturn(VERIFIER_DID);

        // Create service instance
        verifierService = new VerifierService(
                tokenService,
                didResolverService,
                httpClient,
                objectMapper,
                verifierConfig,
                issuerTrustService,
                auditPublisher
        );
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // STEP 3a: Self-issued ID Token Validation Tests
    // ═════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Step 3a: Self-issued ID Token Validation")
    class Step3aTests {

        @Test
        @DisplayName("Should fail when self-issued ID token is null")
        void shouldFailWhenTokenIsNull() {
            // When/Then
            SecurityException exception = assertThrows(
                    SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(null)
            );

            assertTrue(exception.getMessage().contains("Bearer token is required"));
            verify(tokenService, never()).validateToken(any());

            ArgumentCaptor<DcpAuditEventType> typeCaptor = ArgumentCaptor.forClass(DcpAuditEventType.class);
            verify(auditPublisher).publishEvent(typeCaptor.capture(), any(), any(), any(), any(), any(), any(), any());
            assertEquals(DcpAuditEventType.TOKEN_VALIDATION_FAILED, typeCaptor.getValue());
        }

        @Test
        @DisplayName("Should fail when self-issued ID token is blank")
        void shouldFailWhenTokenIsBlank() {
            // When/Then
            SecurityException exception = assertThrows(
                    SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations("   ")
            );

            assertTrue(exception.getMessage().contains("Bearer token is required"));
            verify(tokenService, never()).validateToken(any());

            ArgumentCaptor<DcpAuditEventType> typeCaptor = ArgumentCaptor.forClass(DcpAuditEventType.class);
            verify(auditPublisher).publishEvent(typeCaptor.capture(), any(), any(), any(), any(), any(), any(), any());
            assertEquals(DcpAuditEventType.TOKEN_VALIDATION_FAILED, typeCaptor.getValue());
        }

        @Test
        @DisplayName("Should fail when token service throws SecurityException")
        void shouldFailWhenTokenValidationFails() {
            // Given
            String invalidToken = "invalid.jwt.token";
            when(tokenService.validateToken(invalidToken))
                    .thenThrow(new SecurityException("Invalid signature"));

            // When/Then
            SecurityException exception = assertThrows(
                    SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(invalidToken)
            );

            assertTrue(exception.getMessage().contains("Step 3a failed"));
            assertTrue(exception.getMessage().contains("Invalid signature"));
            verify(tokenService).validateToken(invalidToken);
        }

        @Test
        @DisplayName("Should fail when self-issued ID token missing 'token' claim")
        void shouldFailWhenTokenClaimMissing() throws Exception {
            // Given
            String selfIssuedToken = "valid.self.issued";
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(HOLDER_DID)
                    .subject(HOLDER_DID)
                    .audience(VERIFIER_DID)
                    .issueTime(new Date())
                    .expirationTime(new Date(System.currentTimeMillis() + 300000))
                    // Missing "token" claim
                    .build();

            when(tokenService.validateToken(selfIssuedToken)).thenReturn(claims);

            // When/Then
            SecurityException exception = assertThrows(
                    SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken)
            );

            assertTrue(exception.getMessage().contains("Step 3a failed"));
            assertTrue(exception.getMessage().toLowerCase().contains("token"));

            ArgumentCaptor<DcpAuditEventType> typeCaptor = ArgumentCaptor.forClass(DcpAuditEventType.class);
            verify(auditPublisher).publishEvent(typeCaptor.capture(), any(), any(), any(), any(), any(), any(), any());
            assertEquals(DcpAuditEventType.TOKEN_VALIDATION_FAILED, typeCaptor.getValue());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // STEP 3b: Access Token Parsing Tests
    // ═════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Step 3b: Access Token Parsing")
    class Step3bTests {

        @Test
        @DisplayName("Should fail when access token is malformed")
        void shouldFailWhenAccessTokenMalformed() throws Exception {
            // Given
            String selfIssuedToken = "valid.self.issued";
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(HOLDER_DID)
                    .subject(HOLDER_DID)
                    .audience(VERIFIER_DID)
                    .claim("token", "not.a.valid.jwt")  // Malformed JWT
                    .build();

            when(tokenService.validateToken(selfIssuedToken)).thenReturn(claims);

            // When/Then
            SecurityException exception = assertThrows(
                    SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken)
            );

            assertTrue(exception.getMessage().contains("Step 3b failed"));
            assertTrue(exception.getMessage().toLowerCase().contains("parse"));
        }

        @Test
        @DisplayName("Should take the holder DID from the validated sub when the access token has no iss")
        void shouldUseValidatedSubWhenAccessTokenHasNoIssuer() throws Exception {
            // Given: an access token without iss/sub, so the holder DID can only come from the validated sub
            String accessToken = createJWT(
                    new JWTClaimsSet.Builder()
                            .audience(VERIFIER_DID)
                            .claim("scope", List.of("MembershipCredential"))
                            .build(),
                    holderKey
            );
            String selfIssuedToken = createValidSelfIssuedToken(accessToken);
            when(tokenService.validateToken(selfIssuedToken)).thenReturn(parseClaims(selfIssuedToken));

            setupSuccessfulHolderResolution();
            setupSuccessfulPresentationQuery(List.of(createValidPresentationJWT(List.of(createValidCredentialJWT()))));
            setupSuccessfulKeyResolution(HOLDER_DID, holderKey);
            setupSuccessfulKeyResolution(ISSUER_DID, issuerKey);
            setupTrustedIssuer(ISSUER_DID);

            // When
            VerifierService.PresentationFlowResult result =
                    verifierService.validateAndQueryHolderPresentations(selfIssuedToken);

            // Then
            assertEquals(HOLDER_DID, result.getHolderDid());
            verify(didResolverService).fetchDidDocumentCached(HOLDER_DID);
        }

        @Test
        @DisplayName("Should handle access token with no scopes")
        void shouldHandleAccessTokenWithNoScopes() throws Exception {
            // Given
            String accessToken = createJWT(
                    new JWTClaimsSet.Builder()
                            .issuer(HOLDER_DID)
                            .subject(HOLDER_DID)
                            .audience(VERIFIER_DID)
                            // No scope claim
                            .build(),
                    holderKey
            );

            String selfIssuedToken = "valid.self.issued";
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(HOLDER_DID)
                    .subject(HOLDER_DID)
                    .audience(VERIFIER_DID)
                    .claim("token", accessToken)
                    .build();

            when(tokenService.validateToken(selfIssuedToken)).thenReturn(claims);

            // Setup mocks for subsequent steps to see how far we get
            setupSuccessfulHolderResolution();
            setupSuccessfulPresentationQuery(List.of());

            // When/Then - Should proceed with empty scopes but fail at Step 4 with ResponseStatusException
            Exception exception = assertThrows(
                    Exception.class,  // Can be ResponseStatusException or SecurityException
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken)
            );

            // Should fail because of empty presentations
            String message = exception.getMessage().toLowerCase();
            assertTrue(message.contains("empty") || message.contains("presentation"),
                    "Expected error about empty presentations but got: " + exception.getMessage());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Presenting party binding: holder DID = validated Self-Issued ID Token sub
    // ═════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Presenting party binding")
    class PresentingPartyBindingTests {

        /** A participant whose access token is wrapped by the presenting party (HOLDER_DID). */
        private static final String VICTIM_DID = "did:web:localhost:8080:victim";

        @Test
        @DisplayName("Should reject a token wrapping another participant's access token")
        void shouldRejectWrappedForeignAccessToken() throws Exception {
            // Given: the holder signs a valid Self-Issued ID Token carrying the victim's access token
            ECKey victimKey = new ECKeyGenerator(Curve.P_256).keyID("victim-key-1").generate();
            String victimAccessToken = createAccessToken(VICTIM_DID, VICTIM_DID, victimKey);
            String selfIssuedToken = createValidSelfIssuedToken(victimAccessToken);
            when(tokenService.validateToken(selfIssuedToken)).thenReturn(parseClaims(selfIssuedToken));

            // When/Then
            SecurityException exception = assertThrows(
                    SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken)
            );

            assertTrue(exception.getMessage().contains("Step 3b failed"));
            assertTrue(exception.getMessage().contains("does not match"));
            // Neither the victim's nor the holder's credential service is ever queried
            verify(didResolverService, never()).fetchDidDocumentCached(anyString());
            verifyNoInteractions(httpClient);
            assertAuditEventPublished(DcpAuditEventType.TOKEN_VALIDATION_FAILED);
        }

        @Test
        @DisplayName("Should reject an access token whose iss names a different DID")
        void shouldRejectAccessTokenWithForeignIssuer() throws Exception {
            // Given
            String accessToken = createAccessToken(VICTIM_DID, HOLDER_DID, holderKey);
            String selfIssuedToken = createValidSelfIssuedToken(accessToken);
            when(tokenService.validateToken(selfIssuedToken)).thenReturn(parseClaims(selfIssuedToken));

            // When/Then
            SecurityException exception = assertThrows(
                    SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken)
            );

            assertTrue(exception.getMessage().contains("Step 3b failed"));
            assertTrue(exception.getMessage().contains("'iss'"));
            verifyNoInteractions(httpClient);
        }

        @Test
        @DisplayName("Should accept an access token whose sub is not the holder (implementation-defined)")
        void shouldAcceptAccessTokenWithOtherSubject() throws Exception {
            // Given: an access token issued by the holder whose sub is the verifier, as other DCP
            // implementations issue it
            String accessToken = createAccessToken(HOLDER_DID, VERIFIER_DID, holderKey);
            String selfIssuedToken = createValidSelfIssuedToken(accessToken);
            when(tokenService.validateToken(selfIssuedToken)).thenReturn(parseClaims(selfIssuedToken));
            setupSuccessfulHolderResolution();
            setupSuccessfulPresentationQuery(List.of(createValidPresentationJWT(List.of(createValidCredentialJWT()))));
            setupSuccessfulKeyResolution(HOLDER_DID, holderKey);
            setupSuccessfulKeyResolution(ISSUER_DID, issuerKey);
            setupTrustedIssuer(ISSUER_DID);

            // When
            VerifierService.PresentationFlowResult result =
                    verifierService.validateAndQueryHolderPresentations(selfIssuedToken);

            // Then: the holder DID is still the validated sub
            assertEquals(HOLDER_DID, result.getHolderDid());
        }

        @Test
        @DisplayName("Should reject a validated Self-Issued ID Token without sub")
        void shouldRejectSelfIssuedTokenWithoutSubject() throws Exception {
            // Given: the token service returns claims without sub
            String selfIssuedToken = "valid.self.issued";
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(HOLDER_DID)
                    .audience(VERIFIER_DID)
                    .claim("token", createValidAccessToken())
                    .build();
            when(tokenService.validateToken(selfIssuedToken)).thenReturn(claims);

            // When/Then
            SecurityException exception = assertThrows(
                    SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken)
            );

            assertTrue(exception.getMessage().contains("Step 3a failed"));
            assertTrue(exception.getMessage().contains("'sub'"));
            verifyNoInteractions(httpClient);
            assertAuditEventPublished(DcpAuditEventType.TOKEN_VALIDATION_FAILED);
        }

        @Test
        @DisplayName("Should authenticate the presenting party when its access token names itself")
        void shouldAuthenticatePresentingParty() throws Exception {
            // Given: the holder presents its own access token
            String selfIssuedToken = createValidSelfIssuedToken(createValidAccessToken());
            when(tokenService.validateToken(selfIssuedToken)).thenReturn(parseClaims(selfIssuedToken));
            setupSuccessfulHolderResolution();
            setupSuccessfulPresentationQuery(List.of(createValidPresentationJWT(List.of(createValidCredentialJWT()))));
            setupSuccessfulKeyResolution(HOLDER_DID, holderKey);
            setupSuccessfulKeyResolution(ISSUER_DID, issuerKey);
            setupTrustedIssuer(ISSUER_DID);

            // When
            VerifierService.PresentationFlowResult result =
                    verifierService.validateAndQueryHolderPresentations(selfIssuedToken);

            // Then: the holder DID and the queried credential service belong to the validated sub
            assertEquals(HOLDER_DID, result.getHolderDid());
            assertEquals("https://holder.example.com/presentations", result.getCredentialServiceUrl());
            verify(didResolverService, never()).fetchDidDocumentCached(VICTIM_DID);
        }

        private void assertAuditEventPublished(DcpAuditEventType expectedType) {
            ArgumentCaptor<DcpAuditEventType> typeCaptor = ArgumentCaptor.forClass(DcpAuditEventType.class);
            verify(auditPublisher, atLeastOnce())
                    .publishEvent(typeCaptor.capture(), any(), any(), any(), any(), any(), any(), any());
            assertTrue(typeCaptor.getAllValues().contains(expectedType));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // STEP 3c: Holder DID Resolution Tests
    // ═════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Step 3c: Holder DID Resolution")
    class Step3cTests {

        @Test
        @DisplayName("Should fail when holder DID cannot be resolved")
        void shouldFailWhenHolderDidNotResolved() throws Exception {
            // Given
            String accessToken = createValidAccessToken();
            String selfIssuedToken = createValidSelfIssuedToken(accessToken);

            when(tokenService.validateToken(selfIssuedToken))
                    .thenReturn(parseClaims(selfIssuedToken));

            when(didResolverService.fetchDidDocumentCached(HOLDER_DID))
                    .thenThrow(new IOException("DID document not found"));

            // When/Then
            SecurityException exception = assertThrows(
                    SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken)
            );

            assertTrue(exception.getMessage().contains("Step 3c failed"));
//            assertTrue(exception.getMessage().toLowerCase().contains("resolve"));
        }

        @Test
        @DisplayName("Should fail when holder DID document has no services")
        void shouldFailWhenNoServicesInDidDocument() throws Exception {
            // Given
            String accessToken = createValidAccessToken();
            String selfIssuedToken = createValidSelfIssuedToken(accessToken);

            when(tokenService.validateToken(selfIssuedToken))
                    .thenReturn(parseClaims(selfIssuedToken));

            DidDocument holderDoc = DidDocument.Builder.newInstance()
                    .id(HOLDER_DID)
                    .service(List.of())  // Empty services
                    .build();

            when(didResolverService.fetchDidDocumentCached(HOLDER_DID))
                    .thenReturn(holderDoc);

            // When/Then
            SecurityException exception = assertThrows(
                    SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken)
            );

            assertTrue(exception.getMessage().contains("Step 3c failed"));
            assertTrue(exception.getMessage().toLowerCase().contains("service"));
        }

        @Test
        @DisplayName("Should fail when holder DID document missing CredentialService")
        void shouldFailWhenMissingCredentialService() throws Exception {
            // Given
            String accessToken = createValidAccessToken();
            String selfIssuedToken = createValidSelfIssuedToken(accessToken);

            when(tokenService.validateToken(selfIssuedToken))
                    .thenReturn(parseClaims(selfIssuedToken));

            DidDocument holderDoc = DidDocument.Builder.newInstance()
                    .id(HOLDER_DID)
                    .service(List.of(
                            new ServiceEntry(
                                    HOLDER_DID + "#other-service",
                                    "OtherService",
                                    "https://example.com/other"
                            )
                    ))
                    .build();

            when(didResolverService.fetchDidDocumentCached(HOLDER_DID))
                    .thenReturn(holderDoc);

            // When/Then
            SecurityException exception = assertThrows(
                    SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken)
            );

            assertTrue(exception.getMessage().contains("Step 3c failed"));
            assertTrue(exception.getMessage().toLowerCase().contains("credentialservice"));
        }
    }

    @Nested
    @DisplayName("Step 3c: transport security of the holder credential service")
    class Step3cTransportSecurityTests {

        @Test
        @DisplayName("Should reject an http CredentialService endpoint by default")
        void shouldRejectHttpCredentialService() throws Exception {
            String accessToken = createValidAccessToken();
            String selfIssuedToken = createValidSelfIssuedToken(accessToken);
            when(tokenService.validateToken(selfIssuedToken)).thenReturn(parseClaims(selfIssuedToken));
            when(didResolverService.fetchDidDocumentCached(HOLDER_DID)).thenReturn(DidDocument.Builder.newInstance()
                    .id(HOLDER_DID)
                    .service(List.of(new ServiceEntry(HOLDER_DID + "#cs", "CredentialService",
                            "http://holder.example.com/dcp")))
                    .build());

            SecurityException exception = assertThrows(SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken));

            assertTrue(exception.getMessage().contains("Step 3c failed"));
            assertTrue(exception.getMessage().contains("Insecure"));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // STEP 4: Presentation Query Tests
    // ═════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Step 4: Presentation Query")
    class Step4Tests {

        @Test
        @DisplayName("Should query the holder with the Verifier's own Self-Issued ID Token carrying the access token")
        void shouldSendVerifierSelfIssuedIdTokenWithAccessToken() throws Exception {
            // Given
            String accessToken = createValidAccessToken();
            String selfIssuedToken = createValidSelfIssuedToken(accessToken);

            when(tokenService.validateToken(selfIssuedToken))
                    .thenReturn(parseClaims(selfIssuedToken));
            when(tokenService.createAndSignToken(HOLDER_DID, accessToken, verifierConfig))
                    .thenReturn("verifier-id-token");

            setupSuccessfulHolderResolution();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
            when(httpClient.executeAndDeserialize(
                    anyString(),
                    eq("POST"),
                    headers.capture(),
                    any(RequestBody.class),
                    eq(PresentationResponseMessage.class)
            )).thenReturn(null);

            // When
            assertThrows(SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken));

            // Then: aud = holder DID, token = the access token received from the holder, never the raw access token
            verify(tokenService).createAndSignToken(HOLDER_DID, accessToken, verifierConfig);
            assertEquals("Bearer verifier-id-token", headers.getValue().get("Authorization"));
        }

        @Test
        @DisplayName("Should fail when holder credential service returns null")
        void shouldFailWhenCredentialServiceReturnsNull() throws Exception {
            // Given
            String accessToken = createValidAccessToken();
            String selfIssuedToken = createValidSelfIssuedToken(accessToken);

            when(tokenService.validateToken(selfIssuedToken))
                    .thenReturn(parseClaims(selfIssuedToken));

            setupSuccessfulHolderResolution();

            when(httpClient.executeAndDeserialize(
                    anyString(),
                    eq("POST"),
                    anyMap(),
                    any(RequestBody.class),
                    eq(PresentationResponseMessage.class)
            )).thenReturn(null);

            // When/Then
            SecurityException exception = assertThrows(
                    SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken)
            );

            assertTrue(exception.getMessage().contains("Step 4 failed"));
            assertTrue(exception.getMessage().toLowerCase().contains("rejected") ||
                       exception.getMessage().toLowerCase().contains("invalid response"));

            // PRESENTATION_QUERY_SENT should have been published before the failure
            ArgumentCaptor<DcpAuditEventType> typeCaptor = ArgumentCaptor.forClass(DcpAuditEventType.class);
            verify(auditPublisher, atLeast(1)).publishEvent(typeCaptor.capture(), any(), any(), any(), any(), any(), any(), any());
            assertTrue(typeCaptor.getAllValues().contains(DcpAuditEventType.PRESENTATION_QUERY_SENT));
        }

        @Test
        @DisplayName("Should fail when holder returns empty presentation array")
        void shouldFailWhenEmptyPresentationArray() throws Exception {
            // Given
            String accessToken = createValidAccessToken();
            String selfIssuedToken = createValidSelfIssuedToken(accessToken);

            when(tokenService.validateToken(selfIssuedToken))
                    .thenReturn(parseClaims(selfIssuedToken));

            setupSuccessfulHolderResolution();
            setupSuccessfulPresentationQuery(List.of());  // Empty presentations

            // When/Then
            assertThrows(
                    Exception.class,  // ResponseStatusException in real code
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken)
            );
        }

        @Test
        @DisplayName("Should fail when network error occurs")
        void shouldFailWhenNetworkError() throws Exception {
            // Given
            String accessToken = createValidAccessToken();
            String selfIssuedToken = createValidSelfIssuedToken(accessToken);

            when(tokenService.validateToken(selfIssuedToken))
                    .thenReturn(parseClaims(selfIssuedToken));

            setupSuccessfulHolderResolution();

            when(httpClient.executeAndDeserialize(
                    anyString(),
                    eq("POST"),
                    anyMap(),
                    any(RequestBody.class),
                    eq(PresentationResponseMessage.class)
            )).thenThrow(new IOException("Connection timeout"));

            // When/Then
            IOException exception = assertThrows(
                    IOException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken)
            );

            assertTrue(exception.getMessage().contains("Step 4 failed"));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // STEP 5: Presentation Validation Tests (Outer JWT)
    // ═════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Step 5: Presentation Validation (Outer JWT)")
    class Step5PresentationTests {

        @Test
        @DisplayName("Should fail when presentation is not a JWT string")
        void shouldFailWhenPresentationNotJWT() throws Exception {
            // Given
            String accessToken = createValidAccessToken();
            String selfIssuedToken = createValidSelfIssuedToken(accessToken);

            when(tokenService.validateToken(selfIssuedToken))
                    .thenReturn(parseClaims(selfIssuedToken));

            setupSuccessfulHolderResolution();

            // Return non-JWT presentation
            PresentationResponseMessage response = PresentationResponseMessage.Builder.newInstance()
                    .presentation(List.of(Map.of("invalid", "object")))  // Not a JWT string
                    .build();

            when(httpClient.executeAndDeserialize(
                    anyString(),
                    eq("POST"),
                    anyMap(),
                    any(RequestBody.class),
                    eq(PresentationResponseMessage.class)
            )).thenReturn(response);

            // When/Then
            SecurityException exception = assertThrows(
                    SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken)
            );

            assertTrue(exception.getMessage().contains("Step 5 failed"));
            assertTrue(exception.getMessage().toLowerCase().contains("no valid presentations"));
        }

        @Test
        @DisplayName("Should fail when presentation JWT is malformed")
        void shouldFailWhenPresentationJWTMalformed() throws Exception {
            // Given
            String accessToken = createValidAccessToken();
            String selfIssuedToken = createValidSelfIssuedToken(accessToken);

            when(tokenService.validateToken(selfIssuedToken))
                    .thenReturn(parseClaims(selfIssuedToken));

            setupSuccessfulHolderResolution();

            PresentationResponseMessage response = PresentationResponseMessage.Builder.newInstance()
                    .presentation(List.of("malformed.jwt.string"))
                    .build();

            when(httpClient.executeAndDeserialize(
                    anyString(),
                    eq("POST"),
                    anyMap(),
                    any(RequestBody.class),
                    eq(PresentationResponseMessage.class)
            )).thenReturn(response);

            // When/Then
            SecurityException exception = assertThrows(
                    SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken)
            );

            assertTrue(exception.getMessage().contains("Step 5 failed"));
            assertTrue(exception.getMessage().toLowerCase().contains("jwt"));
        }

        @Test
        @DisplayName("Should fail when presentation JWT missing holder DID")
        void shouldFailWhenPresentationMissingHolderDid() throws Exception {
            // Given
            String accessToken = createValidAccessToken();
            String selfIssuedToken = createValidSelfIssuedToken(accessToken);

            when(tokenService.validateToken(selfIssuedToken))
                    .thenReturn(parseClaims(selfIssuedToken));

            setupSuccessfulHolderResolution();

            // Create presentation JWT without iss/sub
            String presentationJwt = createJWT(
                    new JWTClaimsSet.Builder()
                            // Missing iss and sub
                            .claim("vp", Map.of(
                                    "verifiableCredential", List.of()
                            ))
                            .build(),
                    holderKey
            );

            setupSuccessfulPresentationQuery(List.of(presentationJwt));

            // When/Then
            SecurityException exception = assertThrows(
                    SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken)
            );

            assertTrue(exception.getMessage().contains("Step 5 failed"));
            assertTrue(exception.getMessage().toLowerCase().contains("holder did"));
        }

        @Test
        @DisplayName("Should fail when presentation holder DID mismatch")
        void shouldFailWhenHolderDidMismatch() throws Exception {
            // Given
            String accessToken = createValidAccessToken();
            String selfIssuedToken = createValidSelfIssuedToken(accessToken);

            when(tokenService.validateToken(selfIssuedToken))
                    .thenReturn(parseClaims(selfIssuedToken));

            setupSuccessfulHolderResolution();

            // Create presentation JWT with wrong holder DID
            String wrongDid = "did:web:wrong:holder";
            String presentationJwt = createJWT(
                    new JWTClaimsSet.Builder()
                            .issuer(wrongDid)
                            .subject(wrongDid)
                            .claim("vp", Map.of(
                                    "verifiableCredential", List.of()
                            ))
                            .build(),
                    holderKey
            );

            setupSuccessfulPresentationQuery(List.of(presentationJwt));

            // When/Then
            SecurityException exception = assertThrows(
                    SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken)
            );

            assertTrue(exception.getMessage().contains("Step 5 failed"));
            assertTrue(exception.getMessage().toLowerCase().contains("mismatch"));
        }

        @Test
        @DisplayName("Should fail when presentation JWT signature invalid")
        void shouldFailWhenPresentationSignatureInvalid() throws Exception {
            // Given
            String accessToken = createValidAccessToken();
            String selfIssuedToken = createValidSelfIssuedToken(accessToken);

            when(tokenService.validateToken(selfIssuedToken))
                    .thenReturn(parseClaims(selfIssuedToken));

            setupSuccessfulHolderResolution();

            String presentationJwt = createValidPresentationJWT(List.of());

            setupSuccessfulPresentationQuery(List.of(presentationJwt));

            // Setup DID resolution to fail
            when(didResolverService.resolveVerificationMethod(eq(HOLDER_DID), anyString(), anyString()))
                    .thenThrow(new DidResolutionException("Cannot resolve holder key"));

            // When/Then
            SecurityException exception = assertThrows(
                    SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken)
            );

            assertTrue(exception.getMessage().contains("Step 5 failed"));
            assertTrue(exception.getMessage().toLowerCase().contains("signature"));
        }

        @Test
        @DisplayName("Should fail when presentation JWT missing 'vp' claim")
        void shouldFailWhenPresentationMissingVpClaim() throws Exception {
            // Given
            String accessToken = createValidAccessToken();
            String selfIssuedToken = createValidSelfIssuedToken(accessToken);

            when(tokenService.validateToken(selfIssuedToken))
                    .thenReturn(parseClaims(selfIssuedToken));

            setupSuccessfulHolderResolution();

            String presentationJwt = createJWT(
                    new JWTClaimsSet.Builder()
                            .issuer(HOLDER_DID)
                            .subject(HOLDER_DID)
                            // Missing vp claim
                            .build(),
                    holderKey
            );

            setupSuccessfulPresentationQuery(List.of(presentationJwt));
            setupSuccessfulKeyResolution(HOLDER_DID, holderKey);

            // When/Then
            SecurityException exception = assertThrows(
                    SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken)
            );

            assertTrue(exception.getMessage().contains("Step 5 failed"));
            assertTrue(exception.getMessage().toLowerCase().contains("vp"));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // STEP 5: Credential Validation Tests (Inner JWT)
    // ═════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Step 5: Credential Validation (Inner JWT)")
    class Step5CredentialTests {

        @Test
        @DisplayName("Should skip non-JWT credentials and continue")
        void shouldSkipNonJWTCredentials() throws Exception {
            // Given
            String accessToken = createValidAccessToken();
            String selfIssuedToken = createValidSelfIssuedToken(accessToken);

            when(tokenService.validateToken(selfIssuedToken))
                    .thenReturn(parseClaims(selfIssuedToken));

            setupSuccessfulHolderResolution();

            // Create presentation with non-JWT credential (should be skipped)
            String presentationJwt = createJWT(
                    new JWTClaimsSet.Builder()
                            .issuer(HOLDER_DID)
                            .subject(HOLDER_DID)
                            .claim("vp", Map.of(
                                    "verifiableCredential", List.of(Map.of("invalid", "credential"))
                            ))
                            .build(),
                    holderKey
            );

            setupSuccessfulPresentationQuery(List.of(presentationJwt));
            setupSuccessfulKeyResolution(HOLDER_DID, holderKey);

            // When - Should skip non-JWT credential and complete successfully
            VerifierService.PresentationFlowResult result =
                    verifierService.validateAndQueryHolderPresentations(selfIssuedToken);

            // Then - Validation completes but with 0 valid credentials (non-JWT was skipped)
            assertNotNull(result);
            assertNotNull(result.getValidatedPresentations());
            assertEquals(1, result.getValidatedPresentations().size());

            // The presentation is valid but contains no valid credentials
            VerifierService.ValidatedPresentation vp = result.getValidatedPresentations().get(0);
            assertEquals(HOLDER_DID, vp.getHolderDid());
            assertEquals(0, vp.getCredentials().size(), "Non-JWT credentials should be skipped");
        }

        @Test
        @DisplayName("Should fail when credential JWT is malformed")
        void shouldFailWhenCredentialJWTMalformed() throws Exception {
            // Given
            String accessToken = createValidAccessToken();
            String selfIssuedToken = createValidSelfIssuedToken(accessToken);

            when(tokenService.validateToken(selfIssuedToken))
                    .thenReturn(parseClaims(selfIssuedToken));

            setupSuccessfulHolderResolution();

            String presentationJwt = createValidPresentationJWT(List.of("malformed.credential.jwt"));

            setupSuccessfulPresentationQuery(List.of(presentationJwt));
            setupSuccessfulKeyResolution(HOLDER_DID, holderKey);

            // When/Then
            SecurityException exception = assertThrows(
                    SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken)
            );

            assertTrue(exception.getMessage().contains("Step 5 failed"));
            assertTrue(exception.getMessage().toLowerCase().contains("credential"));
        }

        @Test
        @DisplayName("Should fail when credential JWT missing issuer")
        void shouldFailWhenCredentialMissingIssuer() throws Exception {
            // Given
            String accessToken = createValidAccessToken();
            String selfIssuedToken = createValidSelfIssuedToken(accessToken);

            when(tokenService.validateToken(selfIssuedToken))
                    .thenReturn(parseClaims(selfIssuedToken));

            setupSuccessfulHolderResolution();

            // Create credential without issuer
            String credentialJwt = createJWT(
                    new JWTClaimsSet.Builder()
                            // Missing issuer
                            .subject(HOLDER_DID)
                            .claim("vc", Map.of(
                                    "type", List.of("VerifiableCredential", "MembershipCredential")
                            ))
                            .build(),
                    issuerKey
            );

            String presentationJwt = createValidPresentationJWT(List.of(credentialJwt));

            setupSuccessfulPresentationQuery(List.of(presentationJwt));
            setupSuccessfulKeyResolution(HOLDER_DID, holderKey);

            // When/Then
            SecurityException exception = assertThrows(
                    SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken)
            );

            assertTrue(exception.getMessage().contains("Step 5 failed"));
            assertTrue(exception.getMessage().toLowerCase().contains("issuer"));
        }

        @Test
        @DisplayName("Should fail when credential signature invalid")
        void shouldFailWhenCredentialSignatureInvalid() throws Exception {
            // Given
            String accessToken = createValidAccessToken();
            String selfIssuedToken = createValidSelfIssuedToken(accessToken);

            when(tokenService.validateToken(selfIssuedToken))
                    .thenReturn(parseClaims(selfIssuedToken));

            setupSuccessfulHolderResolution();

            String credentialJwt = createValidCredentialJWT();
            String presentationJwt = createValidPresentationJWT(List.of(credentialJwt));

            setupSuccessfulPresentationQuery(List.of(presentationJwt));
            setupSuccessfulKeyResolution(HOLDER_DID, holderKey);

            // Setup issuer key resolution to fail
            when(didResolverService.resolveVerificationMethod(eq(ISSUER_DID), anyString(), eq("assertionMethod")))
                    .thenThrow(new DidResolutionException("Cannot resolve issuer key"));

            // When/Then
            SecurityException exception = assertThrows(
                    SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken)
            );

            assertTrue(exception.getMessage().contains("Step 5 failed"));
            assertTrue(exception.getMessage().toLowerCase().contains("signature"));
        }

        @Test
        @DisplayName("Should fail when credential subject mismatch")
        void shouldFailWhenCredentialSubjectMismatch() throws Exception {
            // Given
            String accessToken = createValidAccessToken();
            String selfIssuedToken = createValidSelfIssuedToken(accessToken);

            when(tokenService.validateToken(selfIssuedToken))
                    .thenReturn(parseClaims(selfIssuedToken));

            setupSuccessfulHolderResolution();

            // Create credential with wrong subject
            String wrongSubject = "did:web:wrong:subject";
            String credentialJwt = createJWT(
                    new JWTClaimsSet.Builder()
                            .issuer(ISSUER_DID)
                            .subject(wrongSubject)
                            .claim("vc", Map.of(
                                    "type", List.of("VerifiableCredential", "MembershipCredential"),
                                    "credentialSubject", Map.of("id", wrongSubject)
                            ))
                            .issueTime(new Date())
                            .expirationTime(new Date(System.currentTimeMillis() + 86400000))
                            .build(),
                    issuerKey
            );

            String presentationJwt = createValidPresentationJWT(List.of(credentialJwt));

            setupSuccessfulPresentationQuery(List.of(presentationJwt));
            setupSuccessfulKeyResolution(HOLDER_DID, holderKey);
            setupSuccessfulKeyResolution(ISSUER_DID, issuerKey);

            // When/Then
            SecurityException exception = assertThrows(
                    SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken)
            );

            assertTrue(exception.getMessage().contains("Step 5 failed"));
            assertTrue(exception.getMessage().toLowerCase().contains("subject"));
        }

        @Test
        @DisplayName("Should accept a VC 2.0 credential whose top-level credentialSubject.id is the holder")
        void shouldAcceptVc20CredentialBoundToHolder() throws Exception {
            String credentialJwt = createVc20CredentialJWT(Optional.of(HOLDER_DID), Optional.of(Map.of("id", HOLDER_DID)));

            VerifierService.PresentationFlowResult result = runPresentationFlowWith(credentialJwt);

            assertEquals(1, result.getValidatedPresentations().get(0).getCredentials().size());
        }

        @Test
        @DisplayName("Should reject a foreign VC 2.0 credential embedded in the holder's own presentation")
        void shouldRejectForeignVc20Credential() throws Exception {
            String otherDid = "did:web:other.example.com:participant";
            // No sub claim and no vc claim: before, the binding check was skipped for this shape
            String foreignCredential = createJWT(
                    new JWTClaimsSet.Builder()
                            .issuer(ISSUER_DID)
                            .claim("type", List.of("VerifiableCredential", "MembershipCredential"))
                            .claim("credentialSubject", Map.of("id", otherDid))
                            .issueTime(new Date())
                            .expirationTime(new Date(System.currentTimeMillis() + 86400000))
                            .build(),
                    issuerKey);

            SecurityException ex = assertThrows(SecurityException.class, () -> runPresentationFlowWith(foreignCredential));

            assertTrue(ex.getMessage().contains("Credential subject mismatch"));
        }

        @Test
        @DisplayName("Should reject a VC 1.1 credential without credentialSubject.id")
        void shouldRejectVc11CredentialWithoutSubjectId() throws Exception {
            String credentialJwt = createJWT(
                    new JWTClaimsSet.Builder()
                            .issuer(ISSUER_DID)
                            .claim("vc", Map.of(
                                    "type", List.of("VerifiableCredential", "MembershipCredential"),
                                    "credentialSubject", Map.of("membershipType", "Premium")))
                            .issueTime(new Date())
                            .expirationTime(new Date(System.currentTimeMillis() + 86400000))
                            .build(),
                    issuerKey);

            SecurityException ex = assertThrows(SecurityException.class, () -> runPresentationFlowWith(credentialJwt));

            assertTrue(ex.getMessage().contains("Credential subject id missing"));
        }

        @Test
        @DisplayName("Should accept a VC 1.1 credential whose subject is carried only in sub")
        void shouldAcceptVc11CredentialWithSubjectInSubOnly() throws Exception {
            String credentialJwt = createJWT(
                    new JWTClaimsSet.Builder()
                            .issuer(ISSUER_DID)
                            .subject(HOLDER_DID)
                            .claim("vc", Map.of(
                                    "type", List.of("VerifiableCredential", "MembershipCredential"),
                                    "credentialSubject", Map.of("membershipType", "Premium")))
                            .issueTime(new Date())
                            .expirationTime(new Date(System.currentTimeMillis() + 86400000))
                            .build(),
                    issuerKey);

            VerifierService.PresentationFlowResult result = runPresentationFlowWith(credentialJwt);

            assertEquals(1, result.getValidatedPresentations().get(0).getCredentials().size());
        }

        @Test
        @DisplayName("Should reject a credential whose subject id is a DID URL with a fragment")
        void shouldRejectSubjectIdWithFragment() throws Exception {
            String credentialJwt = createVc20CredentialJWT(Optional.empty(),
                    Optional.of(Map.of("id", HOLDER_DID + "#device-1")));

            SecurityException ex = assertThrows(SecurityException.class, () -> runPresentationFlowWith(credentialJwt));

            assertTrue(ex.getMessage().contains("Credential subject mismatch"));
        }

        @Test
        @DisplayName("Should reject a credential mixing shapes when the top-level subject is foreign")
        void shouldRejectMixedShapesWithForeignTopLevelSubject() throws Exception {
            String credentialJwt = createJWT(
                    new JWTClaimsSet.Builder()
                            .issuer(ISSUER_DID)
                            .claim("vc", Map.of(
                                    "type", List.of("VerifiableCredential", "MembershipCredential"),
                                    "credentialSubject", Map.of("id", HOLDER_DID)))
                            .claim("credentialSubject", Map.of("id", "did:web:other.example.com:participant"))
                            .issueTime(new Date())
                            .expirationTime(new Date(System.currentTimeMillis() + 86400000))
                            .build(),
                    issuerKey);

            SecurityException ex = assertThrows(SecurityException.class, () -> runPresentationFlowWith(credentialJwt));

            assertTrue(ex.getMessage().contains("Credential subject mismatch"));
        }

        @Test
        @DisplayName("Should reject a VC 2.0 credential without credentialSubject")
        void shouldRejectVc20CredentialWithoutCredentialSubject() throws Exception {
            String credentialJwt = createVc20CredentialJWT(Optional.of(HOLDER_DID), Optional.empty());

            SecurityException ex = assertThrows(SecurityException.class, () -> runPresentationFlowWith(credentialJwt));

            assertTrue(ex.getMessage().contains("Credential subject id missing"));
        }

        @Test
        @DisplayName("Should reject a credential whose sub differs from the holder even if credentialSubject.id matches")
        void shouldRejectCredentialWithForeignSub() throws Exception {
            String credentialJwt = createVc20CredentialJWT(Optional.of("did:web:other.example.com:participant"),
                    Optional.of(Map.of("id", HOLDER_DID)));

            SecurityException ex = assertThrows(SecurityException.class, () -> runPresentationFlowWith(credentialJwt));

            assertTrue(ex.getMessage().contains("Credential subject mismatch"));
        }

        @Test
        @DisplayName("Should reject a credential with several subjects when one is not the holder")
        void shouldRejectCredentialWithOneForeignSubjectInArray() throws Exception {
            String credentialJwt = createVc20CredentialJWT(Optional.empty(),
                    Optional.of(List.of(Map.of("id", HOLDER_DID), Map.of("id", "did:web:other.example.com:participant"))));

            SecurityException ex = assertThrows(SecurityException.class, () -> runPresentationFlowWith(credentialJwt));

            assertTrue(ex.getMessage().contains("Credential subject mismatch"));
        }

        @Test
        @DisplayName("Should fail when credential is expired")
        void shouldFailWhenCredentialExpired() throws Exception {
            // Given
            String accessToken = createValidAccessToken();
            String selfIssuedToken = createValidSelfIssuedToken(accessToken);

            when(tokenService.validateToken(selfIssuedToken))
                    .thenReturn(parseClaims(selfIssuedToken));

            setupSuccessfulHolderResolution();

            // Create expired credential
            String credentialJwt = createJWT(
                    new JWTClaimsSet.Builder()
                            .issuer(ISSUER_DID)
                            .subject(HOLDER_DID)
                            .claim("vc", Map.of(
                                    "type", List.of("VerifiableCredential", "MembershipCredential"),
                                    "credentialSubject", Map.of("id", HOLDER_DID)
                            ))
                            .issueTime(new Date(System.currentTimeMillis() - 86400000))
                            .expirationTime(new Date(System.currentTimeMillis() - 3600000))  // Expired 1 hour ago
                            .build(),
                    issuerKey
            );

            String presentationJwt = createValidPresentationJWT(List.of(credentialJwt));

            setupSuccessfulPresentationQuery(List.of(presentationJwt));
            setupSuccessfulKeyResolution(HOLDER_DID, holderKey);
            setupSuccessfulKeyResolution(ISSUER_DID, issuerKey);

            // When/Then
            SecurityException exception = assertThrows(
                    SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken)
            );

            assertTrue(exception.getMessage().contains("Step 5 failed"));
            assertTrue(exception.getMessage().toLowerCase().contains("expired"));
        }

        @Test
        @DisplayName("Should fail when credential issued in future")
        void shouldFailWhenCredentialIssuedInFuture() throws Exception {
            // Given
            String accessToken = createValidAccessToken();
            String selfIssuedToken = createValidSelfIssuedToken(accessToken);

            when(tokenService.validateToken(selfIssuedToken))
                    .thenReturn(parseClaims(selfIssuedToken));

            setupSuccessfulHolderResolution();

            // Create credential issued in future
            String credentialJwt = createJWT(
                    new JWTClaimsSet.Builder()
                            .issuer(ISSUER_DID)
                            .subject(HOLDER_DID)
                            .claim("vc", Map.of(
                                    "type", List.of("VerifiableCredential", "MembershipCredential"),
                                    "credentialSubject", Map.of("id", HOLDER_DID)
                            ))
                            .issueTime(new Date(System.currentTimeMillis() + 3600000))  // Issued 1 hour in future
                            .expirationTime(new Date(System.currentTimeMillis() + 86400000))
                            .build(),
                    issuerKey
            );

            String presentationJwt = createValidPresentationJWT(List.of(credentialJwt));

            setupSuccessfulPresentationQuery(List.of(presentationJwt));
            setupSuccessfulKeyResolution(HOLDER_DID, holderKey);
            setupSuccessfulKeyResolution(ISSUER_DID, issuerKey);

            // When/Then
            SecurityException exception = assertThrows(
                    SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken)
            );

            assertTrue(exception.getMessage().contains("Step 5 failed"));
            assertTrue(exception.getMessage().toLowerCase().contains("future"));
        }

        @Test
        @DisplayName("Should fail when credential issuer is not in trusted issuers list")
        void shouldFailWhenCredentialIssuerNotTrusted() throws Exception {
            // Given
            String accessToken = createValidAccessToken();
            String selfIssuedToken = createValidSelfIssuedToken(accessToken);

            when(tokenService.validateToken(selfIssuedToken))
                    .thenReturn(parseClaims(selfIssuedToken));

            setupSuccessfulHolderResolution();

            String credentialJwt = createValidCredentialJWT();
            String presentationJwt = createValidPresentationJWT(List.of(credentialJwt));

            setupSuccessfulPresentationQuery(List.of(presentationJwt));
            setupSuccessfulKeyResolution(HOLDER_DID, holderKey);
            setupSuccessfulKeyResolution(ISSUER_DID, issuerKey);
            // Intentionally do NOT call setupTrustedIssuer — issuer is untrusted
            when(issuerTrustService.isTrusted(anyString(), eq(ISSUER_DID))).thenReturn(false);

            // When/Then
            SecurityException exception = assertThrows(
                    SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken)
            );

            assertTrue(exception.getMessage().contains("Step 5 failed"));
            assertTrue(exception.getMessage().toLowerCase().contains("not trusted"));

            ArgumentCaptor<DcpAuditEventType> typeCaptor = ArgumentCaptor.forClass(DcpAuditEventType.class);
            verify(auditPublisher, atLeast(1)).publishEvent(typeCaptor.capture(), any(), any(), any(), any(), any(), any(), any());
            assertTrue(typeCaptor.getAllValues().contains(DcpAuditEventType.PRESENTATION_INVALID));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // STEP 5: Presentation and Credential Proof Tests (DCP §5.4.3)
    // ═════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Step 5: Presentation and credential proofs")
    class ProofValidationTests {

        @Test
        @DisplayName("Should reject a VP whose payload was changed after signing")
        void shouldRejectTamperedPresentation() throws Exception {
            String presentationJwt = createValidPresentationJWT(List.of(createValidCredentialJWT()));
            JWTClaimsSet changed = new JWTClaimsSet.Builder(parseClaims(presentationJwt))
                    .claim("vp", Map.of("type", List.of("VerifiablePresentation"),
                            "verifiableCredential", List.of(createValidCredentialJWT("AdminCredential"))))
                    .build();

            SecurityException exception = assertThrows(SecurityException.class,
                    () -> runFlowWithPresentation(tamper(presentationJwt, changed)));

            assertTrue(exception.getMessage().contains("Presentation signature validation failed: Invalid signature"));
        }

        @Test
        @DisplayName("Should reject a VC whose payload was changed after signing")
        void shouldRejectTamperedCredential() throws Exception {
            String credentialJwt = createValidCredentialJWT();
            JWTClaimsSet changed = new JWTClaimsSet.Builder(parseClaims(credentialJwt))
                    .expirationTime(new Date(System.currentTimeMillis() + 10L * 365 * 86400000))
                    .build();

            SecurityException exception = assertThrows(SecurityException.class,
                    () -> runPresentationFlowWith(tamper(credentialJwt, changed)));

            assertTrue(exception.getMessage().contains("Credential signature validation failed: Invalid signature"));
        }

        @Test
        @DisplayName("Should reject a VC signed with a key that is not the issuer's")
        void shouldRejectCredentialSignedWithForeignKey() throws Exception {
            ECKey otherKey = new ECKeyGenerator(Curve.P_256).keyID(issuerKey.getKeyID()).generate();
            String credentialJwt = createJWT(parseClaims(createValidCredentialJWT()), otherKey);

            SecurityException exception = assertThrows(SecurityException.class,
                    () -> runPresentationFlowWith(credentialJwt));

            assertTrue(exception.getMessage().contains("Credential signature validation failed: Invalid signature"));
        }

        @Test
        @DisplayName("Should reject a VC whose verification method belongs to a DID other than the issuer")
        void shouldRejectCredentialVerificationMethodOfOtherDid() throws Exception {
            String selfIssuedToken = setUpPresentationResponse(createValidPresentationJWT(
                    List.of(createValidCredentialJWT())));
            setupSuccessfulKeyResolution(HOLDER_DID, holderKey);
            when(didResolverService.resolveVerificationMethod(eq(ISSUER_DID), anyString(), eq("assertionMethod")))
                    .thenReturn(Optional.of(verificationMethod("did:web:attacker.example.com", issuerKey)));

            SecurityException exception = assertThrows(SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken));

            assertTrue(exception.getMessage().contains(
                    "verification method did:web:attacker.example.com#issuer-key-1 does not belong to DID="
                            + ISSUER_DID));
            verify(issuerTrustService, never()).isTrusted(anyString(), anyString());
        }

        @Test
        @DisplayName("Should reject a VC whose issuer key has no assertionMethod relationship")
        void shouldRejectCredentialKeyWithoutAssertionMethod() throws Exception {
            String selfIssuedToken = setUpPresentationResponse(createValidPresentationJWT(
                    List.of(createValidCredentialJWT())));
            setupSuccessfulKeyResolution(HOLDER_DID, holderKey);
            when(didResolverService.resolveVerificationMethod(eq(ISSUER_DID), anyString(), eq("assertionMethod")))
                    .thenReturn(Optional.empty());

            SecurityException exception = assertThrows(SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken));

            assertTrue(exception.getMessage().contains("No assertionMethod key found for DID=" + ISSUER_DID));
            verify(didResolverService, never()).resolveVerificationMethod(
                    eq(ISSUER_DID), anyString(), eq("capabilityInvocation"));
        }

        @Test
        @DisplayName("Should reject a VP whose holder key has no authentication relationship")
        void shouldRejectPresentationKeyWithoutAuthentication() throws Exception {
            String selfIssuedToken = setUpPresentationResponse(createValidPresentationJWT(
                    List.of(createValidCredentialJWT())));
            when(didResolverService.resolveVerificationMethod(eq(HOLDER_DID), anyString(), eq("authentication")))
                    .thenReturn(Optional.empty());

            SecurityException exception = assertThrows(SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken));

            assertTrue(exception.getMessage().contains("No authentication key found for DID=" + HOLDER_DID));
            verify(didResolverService, never()).resolveVerificationMethod(
                    eq(HOLDER_DID), anyString(), eq("assertionMethod"));
            verify(didResolverService, never()).resolveVerificationMethod(
                    eq(HOLDER_DID), anyString(), eq("capabilityInvocation"));
        }

        @Test
        @DisplayName("Should reject a VP whose verification method belongs to a DID other than the holder")
        void shouldRejectPresentationVerificationMethodOfOtherDid() throws Exception {
            String selfIssuedToken = setUpPresentationResponse(createValidPresentationJWT(
                    List.of(createValidCredentialJWT())));
            when(didResolverService.resolveVerificationMethod(eq(HOLDER_DID), anyString(), eq("authentication")))
                    .thenReturn(Optional.of(verificationMethod("did:web:attacker.example.com", holderKey)));

            SecurityException exception = assertThrows(SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken));

            assertTrue(exception.getMessage().contains("does not belong to DID=" + HOLDER_DID));
        }

        @Test
        @DisplayName("Should reject a VC 2.0 credential whose issuer property names a DID other than iss")
        void shouldRejectVc20IssuerPropertyOfOtherDid() throws Exception {
            JWTClaimsSet claims = new JWTClaimsSet.Builder(parseClaims(createVc20CredentialJWT(
                    Optional.of(HOLDER_DID), Optional.of(Map.of("id", HOLDER_DID)))))
                    .claim("issuer", Map.of("id", "did:web:attacker.example.com"))
                    .build();

            SecurityException exception = assertThrows(SecurityException.class,
                    () -> runPresentationFlowWith(createJWT(claims, issuerKey)));

            assertTrue(exception.getMessage().contains("Credential issuer mismatch"));
        }

        @Test
        @DisplayName("Should reject a VC 1.1 credential whose vc.issuer names a DID other than iss")
        void shouldRejectVc11IssuerPropertyOfOtherDid() throws Exception {
            JWTClaimsSet claims = new JWTClaimsSet.Builder(parseClaims(createValidCredentialJWT()))
                    .claim("vc", Map.of(
                            "type", List.of("VerifiableCredential", "MembershipCredential"),
                            "issuer", "did:web:attacker.example.com",
                            "credentialSubject", Map.of("id", HOLDER_DID)))
                    .build();

            SecurityException exception = assertThrows(SecurityException.class,
                    () -> runPresentationFlowWith(createJWT(claims, issuerKey)));

            assertTrue(exception.getMessage().contains("Credential issuer mismatch"));
        }

        @Test
        @DisplayName("Should accept a VC whose issuer property is an object naming the iss DID")
        void shouldAcceptIssuerPropertyMatchingIss() throws Exception {
            JWTClaimsSet claims = new JWTClaimsSet.Builder(parseClaims(createVc20CredentialJWT(
                    Optional.of(HOLDER_DID), Optional.of(Map.of("id", HOLDER_DID)))))
                    .claim("issuer", Map.of("id", ISSUER_DID, "name", "Issuer"))
                    .build();

            VerifierService.PresentationFlowResult result = runPresentationFlowWith(createJWT(claims, issuerKey));

            assertEquals(ISSUER_DID, result.getValidatedPresentations().get(0).getCredentials().get(0).getIssuerDid());
        }

        @Test
        @DisplayName("Should reject a proof whose key is not an EC key")
        void shouldRejectNonEcKey() throws Exception {
            String selfIssuedToken = setUpPresentationResponse(createValidPresentationJWT(
                    List.of(createValidCredentialJWT())));
            RSAKey rsaKey = new RSAKeyGenerator(2048).keyID("rsa-1").generate();
            when(didResolverService.resolveVerificationMethod(eq(HOLDER_DID), anyString(), eq("authentication")))
                    .thenReturn(Optional.of(new ResolvedVerificationMethod(HOLDER_DID + "#rsa-1",
                            rsaKey.toPublicJWK())));

            SecurityException exception = assertThrows(SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken));

            assertTrue(exception.getMessage().contains("unsupported key type RSA"));
        }

        /**
         * Stubs the flow up to the presentation query, which returns the given presentation.
         *
         * @param presentationJwt the presentation the holder returns
         * @return the Self-Issued ID Token that starts the flow
         * @throws Exception if building the tokens fails
         */
        private String setUpPresentationResponse(String presentationJwt) throws Exception {
            String selfIssuedToken = createValidSelfIssuedToken(createValidAccessToken());
            when(tokenService.validateToken(selfIssuedToken)).thenReturn(parseClaims(selfIssuedToken));
            setupSuccessfulHolderResolution();
            setupSuccessfulPresentationQuery(List.of(presentationJwt));
            return selfIssuedToken;
        }

        private void runFlowWithPresentation(String presentationJwt) throws Exception {
            String selfIssuedToken = setUpPresentationResponse(presentationJwt);
            setupSuccessfulKeyResolution(HOLDER_DID, holderKey);
            lenient().when(didResolverService.resolveVerificationMethod(eq(ISSUER_DID), anyString(),
                    eq("assertionMethod"))).thenReturn(Optional.of(verificationMethod(ISSUER_DID, issuerKey)));
            lenient().when(issuerTrustService.isTrusted(anyString(), eq(ISSUER_DID))).thenReturn(true);
            verifierService.validateAndQueryHolderPresentations(selfIssuedToken);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // COMPLETE SUCCESS SCENARIOS
    // ═════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Complete Success Scenarios")
    class SuccessTests {

        @Test
        @DisplayName("Should successfully validate complete flow with single credential")
        void shouldSuccessfullyValidateCompleteFlow() throws Exception {
            // Given
            String accessToken = createValidAccessToken();
            String selfIssuedToken = createValidSelfIssuedToken(accessToken);

            when(tokenService.validateToken(selfIssuedToken))
                    .thenReturn(parseClaims(selfIssuedToken));

            setupSuccessfulHolderResolution();

            String credentialJwt = createValidCredentialJWT();
            String presentationJwt = createValidPresentationJWT(List.of(credentialJwt));

            setupSuccessfulPresentationQuery(List.of(presentationJwt));
            setupSuccessfulKeyResolution(HOLDER_DID, holderKey);
            setupSuccessfulKeyResolution(ISSUER_DID, issuerKey);
            setupTrustedIssuer(ISSUER_DID);

            // When
            VerifierService.PresentationFlowResult result =
                    verifierService.validateAndQueryHolderPresentations(selfIssuedToken);

            // Then
            assertNotNull(result);
            assertEquals(HOLDER_DID, result.getHolderDid());
            assertEquals(List.of("MembershipCredential"), result.getScopes());
            assertNotNull(result.getValidatedPresentations());
            assertEquals(1, result.getValidatedPresentations().size());

            VerifierService.ValidatedPresentation vp = result.getValidatedPresentations().get(0);
            assertEquals(HOLDER_DID, vp.getHolderDid());
            assertEquals(1, vp.getCredentials().size());

            VerifierService.ValidatedCredential vc = vp.getCredentials().get(0);
            assertEquals(ISSUER_DID, vc.getIssuerDid());
            assertEquals("MembershipCredential", vc.getCredentialType());

            // Verify audit events: SELF_ISSUED_TOKEN_VALIDATED, PRESENTATION_QUERY_SENT, PRESENTATION_VERIFIED
            ArgumentCaptor<DcpAuditEventType> typeCaptor = ArgumentCaptor.forClass(DcpAuditEventType.class);
            verify(auditPublisher, atLeast(3)).publishEvent(typeCaptor.capture(), any(), any(), any(), any(), any(), any(), any());
            List<DcpAuditEventType> capturedTypes = typeCaptor.getAllValues();
            assertTrue(capturedTypes.contains(DcpAuditEventType.SELF_ISSUED_TOKEN_VALIDATED));
            assertTrue(capturedTypes.contains(DcpAuditEventType.PRESENTATION_QUERY_SENT));
            assertTrue(capturedTypes.contains(DcpAuditEventType.PRESENTATION_VERIFIED));
        }

        @Test
        @DisplayName("Should look up the VP key under authentication and the VC key under assertionMethod only")
        void shouldUseOnlyTheSpecifiedVerificationRelationships() throws Exception {
            // When
            VerifierService.PresentationFlowResult result = runPresentationFlowWith(createValidCredentialJWT());

            // Then
            assertEquals(1, result.getValidatedPresentations().size());
            verify(didResolverService).resolveVerificationMethod(eq(HOLDER_DID), anyString(), eq("authentication"));
            verify(didResolverService).resolveVerificationMethod(eq(ISSUER_DID), anyString(), eq("assertionMethod"));
            verify(didResolverService, never()).resolveVerificationMethod(
                    anyString(), anyString(), eq("capabilityInvocation"));
            verify(didResolverService, never()).resolvePublicKey(anyString(), any(), any());
        }

        @Test
        @DisplayName("Should successfully validate with multiple credentials")
        void shouldSuccessfullyValidateMultipleCredentials() throws Exception {
            // Given
            String accessToken = createValidAccessToken();
            String selfIssuedToken = createValidSelfIssuedToken(accessToken);

            when(tokenService.validateToken(selfIssuedToken))
                    .thenReturn(parseClaims(selfIssuedToken));

            setupSuccessfulHolderResolution();

            String credential1 = createValidCredentialJWT("MembershipCredential");
            String credential2 = createValidCredentialJWT("DataAccessCredential");
            String presentationJwt = createValidPresentationJWT(List.of(credential1, credential2));

            setupSuccessfulPresentationQuery(List.of(presentationJwt));
            setupSuccessfulKeyResolution(HOLDER_DID, holderKey);
            setupSuccessfulKeyResolution(ISSUER_DID, issuerKey);
            setupTrustedIssuer(ISSUER_DID);

            // When
            VerifierService.PresentationFlowResult result =
                    verifierService.validateAndQueryHolderPresentations(selfIssuedToken);

            // Then
            assertNotNull(result);
            assertEquals(1, result.getValidatedPresentations().size());

            VerifierService.ValidatedPresentation vp = result.getValidatedPresentations().get(0);
            assertEquals(2, vp.getCredentials().size());

            List<String> credentialTypes = vp.getCredentials().stream()
                    .map(VerifierService.ValidatedCredential::getCredentialType)
                    .toList();

            assertTrue(credentialTypes.contains("MembershipCredential"));
            assertTrue(credentialTypes.contains("DataAccessCredential"));
        }

        @Test
        @DisplayName("Should successfully validate with multiple presentations")
        void shouldSuccessfullyValidateMultiplePresentations() throws Exception {
            // Given
            String accessToken = createValidAccessToken();
            String selfIssuedToken = createValidSelfIssuedToken(accessToken);

            when(tokenService.validateToken(selfIssuedToken))
                    .thenReturn(parseClaims(selfIssuedToken));

            setupSuccessfulHolderResolution();

            String credential1 = createValidCredentialJWT("MembershipCredential");
            String presentation1 = createValidPresentationJWT(List.of(credential1));

            String credential2 = createValidCredentialJWT("DataAccessCredential");
            String presentation2 = createValidPresentationJWT(List.of(credential2));

            setupSuccessfulPresentationQuery(List.of(presentation1, presentation2));
            setupSuccessfulKeyResolution(HOLDER_DID, holderKey);
            setupSuccessfulKeyResolution(ISSUER_DID, issuerKey);
            setupTrustedIssuer(ISSUER_DID);

            // When
            VerifierService.PresentationFlowResult result =
                    verifierService.validateAndQueryHolderPresentations(selfIssuedToken);

            // Then
            assertNotNull(result);
            assertEquals(2, result.getValidatedPresentations().size());

            for (VerifierService.ValidatedPresentation vp : result.getValidatedPresentations()) {
                assertEquals(HOLDER_DID, vp.getHolderDid());
                assertEquals(1, vp.getCredentials().size());
            }
        }

        @Test
        @DisplayName("Should successfully validate credential without expiration")
        void shouldSuccessfullyValidateCredentialWithoutExpiration() throws Exception {
            // Given
            String accessToken = createValidAccessToken();
            String selfIssuedToken = createValidSelfIssuedToken(accessToken);

            when(tokenService.validateToken(selfIssuedToken))
                    .thenReturn(parseClaims(selfIssuedToken));

            setupSuccessfulHolderResolution();

            // Create credential without expiration
            String credentialJwt = createJWT(
                    new JWTClaimsSet.Builder()
                            .issuer(ISSUER_DID)
                            .subject(HOLDER_DID)
                            .claim("vc", Map.of(
                                    "type", List.of("VerifiableCredential", "MembershipCredential"),
                                    "credentialSubject", Map.of("id", HOLDER_DID)
                            ))
                            .issueTime(new Date())
                            // No expiration time
                            .build(),
                    issuerKey
            );

            String presentationJwt = createValidPresentationJWT(List.of(credentialJwt));

            setupSuccessfulPresentationQuery(List.of(presentationJwt));
            setupSuccessfulKeyResolution(HOLDER_DID, holderKey);
            setupSuccessfulKeyResolution(ISSUER_DID, issuerKey);
            setupTrustedIssuer(ISSUER_DID);

            // When
            VerifierService.PresentationFlowResult result =
                    verifierService.validateAndQueryHolderPresentations(selfIssuedToken);

            // Then
            assertNotNull(result);
            assertEquals(1, result.getValidatedPresentations().size());
        }
    }

    @Nested
    @DisplayName("Presentation binding (aud / exp)")
    class PresentationBinding {

        private String selfIssuedToken;

        private void givenPresentation(String presentationJwt) throws Exception {
            String accessToken = createValidAccessToken();
            selfIssuedToken = createValidSelfIssuedToken(accessToken);
            when(tokenService.validateToken(selfIssuedToken)).thenReturn(parseClaims(selfIssuedToken));
            setupSuccessfulHolderResolution();
            setupSuccessfulPresentationQuery(List.of(presentationJwt));
            setupSuccessfulKeyResolution(HOLDER_DID, holderKey);
        }

        @Test
        @DisplayName("Should reject expired presentation")
        void shouldRejectExpiredPresentation() throws Exception {
            givenPresentation(createPresentationJWT(List.of(), VERIFIER_DID,
                    new Date(System.currentTimeMillis() - 60_000L)));

            SecurityException ex = assertThrows(SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken));

            assertTrue(ex.getMessage().contains("Presentation has expired"));
        }

        @Test
        @DisplayName("Should reject presentation addressed to another verifier")
        void shouldRejectPresentationForWrongAudience() throws Exception {
            givenPresentation(createPresentationJWT(List.of(), "did:web:other-verifier.example.com",
                    new Date(System.currentTimeMillis() + 300_000L)));

            SecurityException ex = assertThrows(SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken));

            assertTrue(ex.getMessage().contains("audience does not match"));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═════════════════════════════════════════════════════════════════════════════

    private String createJWT(JWTClaimsSet claims, ECKey signingKey) throws JOSEException {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(signingKey.getKeyID())
                .build();

        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new ECDSASigner(signingKey));
        return jwt.serialize();
    }

    private String createValidAccessToken() throws JOSEException {
        return createJWT(
                new JWTClaimsSet.Builder()
                        .issuer(HOLDER_DID)
                        .subject(HOLDER_DID)
                        .audience(VERIFIER_DID)
                        .claim("scope", List.of("MembershipCredential"))
                        .issueTime(new Date())
                        .expirationTime(new Date(System.currentTimeMillis() + 300000))
                        .build(),
                holderKey
        );
    }

    private String createAccessToken(String issuer, String subject, ECKey signingKey) throws JOSEException {
        return createJWT(
                new JWTClaimsSet.Builder()
                        .issuer(issuer)
                        .subject(subject)
                        .audience(VERIFIER_DID)
                        .claim("scope", List.of("MembershipCredential"))
                        .issueTime(new Date())
                        .expirationTime(new Date(System.currentTimeMillis() + 300000))
                        .build(),
                signingKey
        );
    }

    private String createValidSelfIssuedToken(String accessToken) throws JOSEException {
        return createJWT(
                new JWTClaimsSet.Builder()
                        .issuer(HOLDER_DID)
                        .subject(HOLDER_DID)
                        .audience(VERIFIER_DID)
                        .claim("token", accessToken)
                        .issueTime(new Date())
                        .expirationTime(new Date(System.currentTimeMillis() + 300000))
                        .jwtID(UUID.randomUUID().toString())
                        .build(),
                holderKey
        );
    }

    /**
     * Creates a VC 2.0 JWT credential (top-level claims, no vc claim).
     *
     * @param sub the sub claim, empty to omit it
     * @param credentialSubject the credentialSubject claim (object or array), empty to omit it
     * @return the signed credential JWT
     * @throws JOSEException if signing fails
     */
    private String createVc20CredentialJWT(Optional<String> sub, Optional<Object> credentialSubject)
            throws JOSEException {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(ISSUER_DID)
                .claim("@context", List.of("https://www.w3.org/ns/credentials/v2"))
                .claim("type", List.of("VerifiableCredential", "MembershipCredential"))
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 86400000))
                .jwtID(UUID.randomUUID().toString());
        sub.ifPresent(builder::subject);
        credentialSubject.ifPresent(subject -> builder.claim("credentialSubject", subject));
        return createJWT(builder.build(), issuerKey);
    }

    /**
     * Runs the full presentation flow with one presentation from the holder carrying the given credential.
     *
     * @param credentialJwt the credential embedded in the holder's presentation
     * @return the flow result
     * @throws Exception if the flow fails
     */
    private VerifierService.PresentationFlowResult runPresentationFlowWith(String credentialJwt) throws Exception {
        String selfIssuedToken = createValidSelfIssuedToken(createValidAccessToken());
        when(tokenService.validateToken(selfIssuedToken)).thenReturn(parseClaims(selfIssuedToken));
        setupSuccessfulHolderResolution();
        setupSuccessfulPresentationQuery(List.of(createValidPresentationJWT(List.of(credentialJwt))));
        setupSuccessfulKeyResolution(HOLDER_DID, holderKey);
        setupSuccessfulKeyResolution(ISSUER_DID, issuerKey);
        lenient().when(issuerTrustService.isTrusted(anyString(), eq(ISSUER_DID))).thenReturn(true);
        return verifierService.validateAndQueryHolderPresentations(selfIssuedToken);
    }

    private String createValidCredentialJWT() throws JOSEException {
        return createValidCredentialJWT("MembershipCredential");
    }

    private String createValidCredentialJWT(String credentialType) throws JOSEException {
        return createJWT(
                new JWTClaimsSet.Builder()
                        .issuer(ISSUER_DID)
                        .subject(HOLDER_DID)
                        .claim("vc", Map.of(
                                "type", List.of("VerifiableCredential", credentialType),
                                "credentialSubject", Map.of(
                                        "id", HOLDER_DID,
                                        "membershipType", "Premium",
                                        "status", "Active"
                                )
                        ))
                        .issueTime(new Date())
                        .expirationTime(new Date(System.currentTimeMillis() + 86400000))
                        .jwtID(UUID.randomUUID().toString())
                        .build(),
                issuerKey
        );
    }

    private String createValidPresentationJWT(List<String> credentialJwts) throws JOSEException {
        return createPresentationJWT(credentialJwts, VERIFIER_DID, new Date(System.currentTimeMillis() + 300_000L));
    }

    private String createPresentationJWT(List<String> credentialJwts, String audience, Date expiration)
            throws JOSEException {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(HOLDER_DID)
                .subject(HOLDER_DID)
                .claim("vp", Map.of(
                        "@context", List.of("https://www.w3.org/2018/credentials/v1"),
                        "type", List.of("VerifiablePresentation"),
                        "verifiableCredential", credentialJwts
                ))
                .issueTime(new Date())
                .jwtID(UUID.randomUUID().toString());
        if (audience != null) {
            builder.audience(audience);
        }
        if (expiration != null) {
            builder.expirationTime(expiration);
        }
        return createJWT(builder.build(), holderKey);
    }

    private JWTClaimsSet parseClaims(String jwt) throws Exception {
        return SignedJWT.parse(jwt).getJWTClaimsSet();
    }

    private void setupSuccessfulHolderResolution() throws IOException {
        DidDocument holderDoc = DidDocument.Builder.newInstance()
                .id(HOLDER_DID)
                .service(List.of(
                        new ServiceEntry(
                                HOLDER_DID + "#credential-service",
                                "CredentialService",
                                "https://holder.example.com/presentations"
                        )
                ))
                .build();

        when(didResolverService.fetchDidDocumentCached(HOLDER_DID))
                .thenReturn(holderDoc);
    }

    private void setupSuccessfulPresentationQuery(List<Object> presentations) throws IOException {
        PresentationResponseMessage response = PresentationResponseMessage.Builder.newInstance()
                .presentation(presentations)
                .build();

        when(httpClient.executeAndDeserialize(
                anyString(),
                eq("POST"),
                anyMap(),
                any(RequestBody.class),
                eq(PresentationResponseMessage.class)
        )).thenReturn(response);
    }

    /**
     * Stubs the resolver to return the key of a DID under the relationship the verifier requires for it:
     * {@code authentication} for the holder (VP proof), {@code assertionMethod} for any other DID (VC proof).
     *
     * @param did the DID that controls the key
     * @param key the key to return
     * @throws DidResolutionException never; declared by the stubbed method
     */
    private void setupSuccessfulKeyResolution(String did, ECKey key) throws DidResolutionException {
        String relationship = HOLDER_DID.equals(did) ? "authentication" : "assertionMethod";
        when(didResolverService.resolveVerificationMethod(eq(did), anyString(), eq(relationship)))
                .thenReturn(Optional.of(verificationMethod(did, key)));
    }

    private static ResolvedVerificationMethod verificationMethod(String did, ECKey key) {
        return new ResolvedVerificationMethod(did + "#" + key.getKeyID(), key.toPublicJWK());
    }

    /**
     * Replaces the payload of a signed JWT and keeps its original header and signature.
     *
     * @param jwt the signed JWT
     * @param claims the claims to put in place of the signed payload
     * @return the tampered JWT
     */
    private static String tamper(String jwt, JWTClaimsSet claims) {
        String[] parts = jwt.split("\\.");
        return parts[0] + "." + Base64URL.encode(claims.toString()) + "." + parts[2];
    }

    /**
     * Stubs the trust service to accept any credential type issued by the given DID.
     *
     * @param issuerDid the issuer DID to mark as trusted for any credential type
     */
    private void setupTrustedIssuer(String issuerDid) {
        when(issuerTrustService.isTrusted(anyString(), eq(issuerDid))).thenReturn(true);
    }
}
