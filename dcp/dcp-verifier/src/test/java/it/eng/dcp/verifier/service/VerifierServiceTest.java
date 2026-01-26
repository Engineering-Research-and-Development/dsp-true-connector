package it.eng.dcp.verifier.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import it.eng.dcp.common.client.SimpleOkHttpRestClient;
import it.eng.dcp.common.config.BaseDidDocumentConfiguration;
import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.exception.DidResolutionException;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.model.PresentationResponseMessage;
import it.eng.dcp.common.model.ServiceEntry;
import it.eng.dcp.common.service.did.HttpDidResolverService;
import it.eng.dcp.common.service.sts.SelfIssuedIdTokenService;
import okhttp3.RequestBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
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
    private BaseDidDocumentConfiguration verifierConfig;

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

        // Setup verifier config mock
        DidDocumentConfig didDocumentConfig = mock(DidDocumentConfig.class);
        when(didDocumentConfig.getDid()).thenReturn(VERIFIER_DID);
        when(verifierConfig.getDidDocumentConfig()).thenReturn(didDocumentConfig);

        // Create service instance
        verifierService = new VerifierService(
                tokenService,
                didResolverService,
                httpClient,
                objectMapper,
                verifierConfig
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
        @DisplayName("Should fail when access token missing 'iss' claim")
        void shouldFailWhenAccessTokenMissingIssuer() throws Exception {
            // Given
            String accessToken = createJWT(
                    new JWTClaimsSet.Builder()
                            // Missing issuer
                            .subject(HOLDER_DID)
                            .claim("scope", List.of("MembershipCredential"))
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

            // When/Then
            SecurityException exception = assertThrows(
                    SecurityException.class,
                    () -> verifierService.validateAndQueryHolderPresentations(selfIssuedToken)
            );

            assertTrue(exception.getMessage().contains("Step 3b failed"));
            assertTrue(exception.getMessage().toLowerCase().contains("iss"));
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

    // ═════════════════════════════════════════════════════════════════════════════
    // STEP 4: Presentation Query Tests
    // ═════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Step 4: Presentation Query")
    class Step4Tests {

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
            when(didResolverService.resolvePublicKey(eq(HOLDER_DID), anyString(), anyString()))
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
            when(didResolverService.resolvePublicKey(eq(ISSUER_DID), anyString(), eq("assertionMethod")))
                    .thenThrow(new DidResolutionException("Cannot resolve issuer key"));
//            when(didResolverService.resolvePublicKey(eq(ISSUER_DID), anyString(), eq("capabilityInvocation")))
//                    .thenThrow(new DidResolutionException("Cannot resolve issuer key"));

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

            // When
            VerifierService.PresentationFlowResult result =
                    verifierService.validateAndQueryHolderPresentations(selfIssuedToken);

            // Then
            assertNotNull(result);
            assertEquals(1, result.getValidatedPresentations().size());
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
        return createJWT(
                new JWTClaimsSet.Builder()
                        .issuer(HOLDER_DID)
                        .subject(HOLDER_DID)
                        .claim("vp", Map.of(
                                "@context", List.of("https://www.w3.org/2018/credentials/v1"),
                                "type", List.of("VerifiablePresentation"),
                                "verifiableCredential", credentialJwts
                        ))
                        .issueTime(new Date())
                        .jwtID(UUID.randomUUID().toString())
                        .build(),
                holderKey
        );
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

    private void setupSuccessfulKeyResolution(String did, ECKey key) throws DidResolutionException {
        when(didResolverService.resolvePublicKey(eq(did), anyString(), eq("assertionMethod")))
                .thenReturn(key.toPublicJWK());
//        when(didResolverService.resolvePublicKey(eq(did), anyString(), eq("capabilityInvocation")))
//                .thenReturn(key.toPublicJWK());
    }
}
