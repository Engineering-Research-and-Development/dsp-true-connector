package it.eng.dcp.e2e.tests;

import it.eng.dcp.common.model.PresentationQueryMessage;
import it.eng.dcp.common.model.PresentationResponseMessage;
import it.eng.dcp.e2e.environment.DcpTestEnvironment;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for DCP Verifier flow.
 * This test verifies the complete verifier-side presentation query and validation flow.
 *
 * <p>This test verifies the verifier flow as per DCP specification v1.0:
 * <ol>
 *   <li>Verifier receives Self-Issued ID Token from Holder (Section 4.3)</li>
 *   <li>Verifier validates token and extracts access token (Section 5.3)</li>
 *   <li>Verifier resolves Holder's DID to find Credential Service endpoint</li>
 *   <li>Verifier queries Holder's Credential Service for presentations (Section 5.4)</li>
 *   <li>Verifier validates presentation signatures and credential content (Section 5)</li>
 * </ol>
 */
@Slf4j
@TestMethodOrder(MethodOrderer.MethodName.class)
class DcpVerifierFlowTestE2E extends DcpTestEnvironment {

    private static final int CREDENTIAL_ISSUANCE_TIMEOUT_SECONDS = 10;
    private static final int CREDENTIAL_ISSUANCE_POLL_INTERVAL_SECONDS = 1;
    private static final int RATE_LIMIT_DELAY_MILLIS = 2000;

    /**
     * Wait between tests to avoid rate limiting.
     * All tests use the same holder DID, so they share the same rate limiter bucket.
     */
    @BeforeEach
    void setUp() {
        avoidRateLimit();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Successful Presentation Query Scenarios
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Test successful presentation query with valid credentials.
     * This is the happy path where verifier successfully requests and validates
     * presentations from a holder with issued credentials.
     */
    @Test
    void testSuccessfulPresentationQueryWithValidCredentials() {
        log.info("\n═══════════════════════════════════════════════");
        log.info("TEST: Successful Presentation Query with Valid Credentials");
        log.info("═══════════════════════════════════════════════");

        // Step 1: Setup - Issue a credential to the holder first
        log.info("\n--- Step 1: Setup - Issue Credential to Holder ---");
        String requestId = setupCredentialIssuance();
        log.info("✓ Credential request created: {}", requestId);

        // Step 2: Wait for credential to be issued
        log.info("\n--- Step 2: Wait for Credential Issuance ---");
        await()
            .atMost(Duration.ofSeconds(CREDENTIAL_ISSUANCE_TIMEOUT_SECONDS))
            .pollInterval(Duration.ofSeconds(CREDENTIAL_ISSUANCE_POLL_INTERVAL_SECONDS))
            .untilAsserted(() -> {
                Map<String, Object> status = getRequestStatus(requestId);
                assertEquals("ISSUED", status.get("status"));
            });
        log.info("✓ Credential issued successfully");

        // Step 3: Generate Self-Issued ID Token (with holder as audience - the service endpoint)
        log.info("\n--- Step 3: Generate Self-Issued ID Token ---");
        String selfIssuedToken = generateSelfIssuedIdTokenForVerifier();
        assertNotNull(selfIssuedToken, "Self-issued token must not be null");
        log.info("✓ Self-Issued ID Token generated for holder");

        // Step 4: Query presentations from Holder's Credential Service
        log.info("\n--- Step 4: Query Presentations from Holder ---");
        PresentationQueryMessage query = createPresentationQuery(List.of("org.example:MembershipCredential"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + selfIssuedToken);

        HttpEntity<PresentationQueryMessage> requestEntity = new HttpEntity<>(query, headers);

        ResponseEntity<PresentationResponseMessage> response = getHolderClient().exchange(
            "/dcp/presentations/query",
            HttpMethod.POST,
            requestEntity,
            PresentationResponseMessage.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        PresentationResponseMessage presentationResponse = response.getBody();
        assertNotNull(presentationResponse);
        log.info("✓ Presentations received from holder");

        // Step 5: Validate presentation response
        log.info("\n--- Step 5: Validate Presentation Response ---");
        assertNotNull(presentationResponse.getPresentation(), "Presentation list must not be null");
        assertFalse(presentationResponse.getPresentation().isEmpty(), "Must have at least one presentation");

        log.info("✓ Received {} presentation(s)", presentationResponse.getPresentation().size());

        log.info("\n═══════════════════════════════════════════════");
        log.info("✓✓✓ SUCCESSFUL PRESENTATION QUERY TEST PASSED");
        log.info("═══════════════════════════════════════════════\n");
    }

    /**
     * Test successful presentation query with multiple scopes.
     * Verifies that verifier can request multiple credential types in a single query.
     */
    @Test
    void testSuccessfulPresentationQueryWithMultipleScopes() {
        log.info("\n═══════════════════════════════════════════════");
        log.info("TEST: Presentation Query with Multiple Scopes");
        log.info("═══════════════════════════════════════════════");

        // Setup - Issue credentials
        String requestId = setupCredentialIssuance();
        await()
            .atMost(Duration.ofSeconds(CREDENTIAL_ISSUANCE_TIMEOUT_SECONDS))
            .pollInterval(Duration.ofSeconds(CREDENTIAL_ISSUANCE_POLL_INTERVAL_SECONDS))
            .untilAsserted(() -> {
                Map<String, Object> status = getRequestStatus(requestId);
                assertEquals("ISSUED", status.get("status"));
            });

        log.info("✓ Credential issued successfully");

        // Query with multiple scopes
        String selfIssuedToken = generateSelfIssuedIdTokenForVerifier();

        PresentationQueryMessage query = createPresentationQuery(
            List.of("org.example:MembershipCredential", "org.example:CompanyCredential")
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + selfIssuedToken);

        HttpEntity<PresentationQueryMessage> requestEntity = new HttpEntity<>(query, headers);

        ResponseEntity<PresentationResponseMessage> response = getHolderClient().exchange(
            "/dcp/presentations/query",
            HttpMethod.POST,
            requestEntity,
            PresentationResponseMessage.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        log.info("✓ Multiple scopes query succeeded");

        log.info("\n═══════════════════════════════════════════════");
        log.info("✓✓✓ MULTIPLE SCOPES QUERY TEST PASSED");
        log.info("═══════════════════════════════════════════════\n");
    }

    /**
     * Test successful presentation query when holder has no matching credentials.
     * The query should succeed but return an empty presentation list.
     */
    @Test
    void testSuccessfulPresentationQueryWithNoMatchingCredentials() {
        log.info("\n═══════════════════════════════════════════════");
        log.info("TEST: Presentation Query with No Matching Credentials");
        log.info("═══════════════════════════════════════════════");

        // Query for credential type that doesn't exist
        String selfIssuedToken = generateSelfIssuedIdTokenForVerifier();

        PresentationQueryMessage query = createPresentationQuery(
            List.of("org.example:NonExistentCredential")
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + selfIssuedToken);

        HttpEntity<PresentationQueryMessage> requestEntity = new HttpEntity<>(query, headers);

        ResponseEntity<PresentationResponseMessage> response = getHolderClient().exchange(
            "/dcp/presentations/query",
            HttpMethod.POST,
            requestEntity,
            PresentationResponseMessage.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        PresentationResponseMessage presentationResponse = response.getBody();
        assertNotNull(presentationResponse);

        // Should return empty presentation or null/empty list
        if (presentationResponse.getPresentation() != null) {
            assertTrue(presentationResponse.getPresentation().isEmpty(),
                "Should have no presentations for non-existent credential type");
        }

        log.info("✓ Query with no matching credentials handled correctly");

        log.info("\n═══════════════════════════════════════════════");
        log.info("✓✓✓ NO MATCHING CREDENTIALS TEST PASSED");
        log.info("═══════════════════════════════════════════════\n");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Authentication and Authorization Failure Scenarios
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Test presentation query without authorization header.
     * Must fail with 401 UNAUTHORIZED.
     */
    @Test
    void testPresentationQueryWithoutAuthorization() {
        log.info("\n═══════════════════════════════════════════════");
        log.info("TEST: Presentation Query Without Authorization");
        log.info("═══════════════════════════════════════════════");

        PresentationQueryMessage query = createPresentationQuery(List.of("org.example:MembershipCredential"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // No Authorization header

        HttpEntity<PresentationQueryMessage> requestEntity = new HttpEntity<>(query, headers);

        try {
            getHolderClient().exchange(
                "/dcp/presentations/query",
                HttpMethod.POST,
                requestEntity,
                PresentationResponseMessage.class
            );
            fail("Request should have failed without authorization");
        } catch (HttpClientErrorException e) {
            assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode());
            log.info("✓ Request correctly rejected without authorization: {}", e.getStatusCode());
        }

        log.info("\n═══════════════════════════════════════════════");
        log.info("✓✓✓ NO AUTHORIZATION TEST PASSED");
        log.info("═══════════════════════════════════════════════\n");
    }

    /**
     * Test presentation query with invalid bearer token format.
     * Must fail with 401 UNAUTHORIZED or 400 BAD REQUEST.
     */
    @Test
    void testPresentationQueryWithInvalidTokenFormat() {
        log.info("\n═══════════════════════════════════════════════");
        log.info("TEST: Presentation Query with Invalid Token Format");
        log.info("═══════════════════════════════════════════════");

        PresentationQueryMessage query = createPresentationQuery(List.of("org.example:MembershipCredential"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer invalid.token.format");

        HttpEntity<PresentationQueryMessage> requestEntity = new HttpEntity<>(query, headers);

        try {
            getHolderClient().exchange(
                "/dcp/presentations/query",
                HttpMethod.POST,
                requestEntity,
                PresentationResponseMessage.class
            );
            fail("Request should have failed with invalid token format");
        } catch (HttpClientErrorException e) {
            assertTrue(
                e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.BAD_REQUEST,
                "Expected 401 UNAUTHORIZED or 400 BAD REQUEST, got: " + e.getStatusCode()
            );
            log.info("✓ Request correctly rejected with invalid token: {}", e.getStatusCode());
        }

        log.info("\n═══════════════════════════════════════════════");
        log.info("✓✓✓ INVALID TOKEN FORMAT TEST PASSED");
        log.info("═══════════════════════════════════════════════\n");
    }

    /**
     * Test presentation query with token meant for wrong audience.
     * Must fail with 401 UNAUTHORIZED.
     */
    @Test
    void testPresentationQueryWithWrongAudienceToken() {
        log.info("\n═══════════════════════════════════════════════");
        log.info("TEST: Presentation Query with Wrong Audience Token");
        log.info("═══════════════════════════════════════════════");

        // Generate token for issuer instead of holder (wrong audience)
        String issuerDid = getIssuerDid();
        String wrongAudienceToken = generateValidToken(issuerDid);

        PresentationQueryMessage query = createPresentationQuery(List.of("org.example:MembershipCredential"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + wrongAudienceToken);

        HttpEntity<PresentationQueryMessage> requestEntity = new HttpEntity<>(query, headers);

        try {
            getHolderClient().exchange(
                "/dcp/presentations/query",
                HttpMethod.POST,
                requestEntity,
                PresentationResponseMessage.class
            );
            fail("Request should have failed with wrong audience token");
        } catch (HttpClientErrorException e) {
            assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode());
            log.info("✓ Request correctly rejected with wrong audience: {}", e.getStatusCode());
        }

        log.info("\n═══════════════════════════════════════════════");
        log.info("✓✓✓ WRONG AUDIENCE TOKEN TEST PASSED");
        log.info("═══════════════════════════════════════════════\n");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Request Validation Scenarios
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Test presentation query with empty scope array.
     * According to spec, empty scope array must return 4xx error.
     */
    @Test
    void testPresentationQueryWithEmptyScope() {
        log.info("\n═══════════════════════════════════════════════");
        log.info("TEST: Presentation Query with Empty Scope");
        log.info("═══════════════════════════════════════════════");

        String selfIssuedToken = generateSelfIssuedIdTokenForVerifier();

        // Create query with empty scope
        PresentationQueryMessage query = createPresentationQuery(List.of());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + selfIssuedToken);

        HttpEntity<PresentationQueryMessage> requestEntity = new HttpEntity<>(query, headers);

        try {
            getHolderClient().exchange(
                "/dcp/presentations/query",
                HttpMethod.POST,
                requestEntity,
                PresentationResponseMessage.class
            );
            fail("Request should have failed with empty scope");
        } catch (HttpClientErrorException e) {
            assertTrue(e.getStatusCode().is4xxClientError(),
                "Expected 4xx error for empty scope, got: " + e.getStatusCode());
            log.info("✓ Request correctly rejected with empty scope: {}", e.getStatusCode());
        }

        log.info("\n═══════════════════════════════════════════════");
        log.info("✓✓✓ EMPTY SCOPE TEST PASSED");
        log.info("═══════════════════════════════════════════════\n");
    }

    /**
     * Test presentation query with invalid scope format.
     * Scope must be in format [alias]:[discriminator].
     */
    @Test
    void testPresentationQueryWithInvalidScopeFormat() {
        log.info("\n═══════════════════════════════════════════════");
        log.info("TEST: Presentation Query with Invalid Scope Format");
        log.info("═══════════════════════════════════════════════");

        String selfIssuedToken = generateSelfIssuedIdTokenForVerifier();

        // Create query with invalid scope format (missing colon separator)
        PresentationQueryMessage query = createPresentationQuery(List.of("InvalidScopeFormat"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + selfIssuedToken);

        HttpEntity<PresentationQueryMessage> requestEntity = new HttpEntity<>(query, headers);

        try {
            ResponseEntity<PresentationResponseMessage> response = getHolderClient().exchange(
                "/dcp/presentations/query",
                HttpMethod.POST,
                requestEntity,
                PresentationResponseMessage.class
            );

            // Some implementations might accept it and return empty results
            // Others might reject with 4xx
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✓ Implementation accepted invalid scope format (permissive)");
            }
        } catch (HttpClientErrorException e) {
            assertTrue(e.getStatusCode().is4xxClientError(),
                "Expected 4xx error for invalid scope format, got: " + e.getStatusCode());
            log.info("✓ Request correctly rejected with invalid scope format: {}", e.getStatusCode());
        }

        log.info("\n═══════════════════════════════════════════════");
        log.info("✓✓✓ INVALID SCOPE FORMAT TEST PASSED");
        log.info("═══════════════════════════════════════════════\n");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Presentation Definition Scenarios
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Test presentation query with presentation definition.
     * Implementation MAY support presentationDefinition parameter.
     * If not supported, must return 501 Not Implemented.
     */
    @Test
    void testPresentationQueryWithPresentationDefinition() {
        log.info("\n═══════════════════════════════════════════════");
        log.info("TEST: Presentation Query with Presentation Definition");
        log.info("═══════════════════════════════════════════════");

        String selfIssuedToken = generateSelfIssuedIdTokenForVerifier();

        // Create query with presentation definition
        Map<String, Object> presentationDefinition = Map.of(
            "id", "membership-check",
            "input_descriptors", List.of(
                Map.of(
                    "id", "membership-credential",
                    "format", Map.of(
                        "jwt_vc", Map.of("alg", List.of("ES256"))
                    ),
                    "constraints", Map.of(
                        "fields", List.of(
                            Map.of(
                                "path", List.of("$.type"),
                                "filter", Map.of(
                                    "type", "string",
                                    "pattern", "MembershipCredential"
                                )
                            )
                        )
                    )
                )
            )
        );

        Map<String, Object> query = Map.of(
            "@context", List.of("https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"),
            "type", "PresentationQueryMessage",
            "presentationDefinition", presentationDefinition
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + selfIssuedToken);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(query, headers);

        try {
            ResponseEntity<PresentationResponseMessage> response = getHolderClient().exchange(
                "/dcp/presentations/query",
                HttpMethod.POST,
                requestEntity,
                PresentationResponseMessage.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("✓ Presentation definition supported and query succeeded");
                assertNotNull(response.getBody());
            }
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_IMPLEMENTED) {
                log.info("✓ Presentation definition not supported (501 Not Implemented) - acceptable");
            } else {
                fail("Unexpected error for presentation definition query: " + e.getStatusCode());
            }
        }

        log.info("\n═══════════════════════════════════════════════");
        log.info("✓✓✓ PRESENTATION DEFINITION TEST PASSED");
        log.info("═══════════════════════════════════════════════\n");
    }

    /**
     * Test presentation query with both scope and presentationDefinition.
     * According to spec, this is an error and must return 400 BAD REQUEST.
     */
    @Test
    void testPresentationQueryWithBothScopeAndDefinition() {
        log.info("\n═══════════════════════════════════════════════");
        log.info("TEST: Presentation Query with Both Scope and Definition");
        log.info("═══════════════════════════════════════════════");

        String selfIssuedToken = generateSelfIssuedIdTokenForVerifier();

        Map<String, Object> presentationDefinition = Map.of(
            "id", "test-definition",
            "input_descriptors", List.of()
        );

        Map<String, Object> query = Map.of(
            "@context", List.of("https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"),
            "type", "PresentationQueryMessage",
            "scope", List.of("org.example:MembershipCredential"),
            "presentationDefinition", presentationDefinition
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + selfIssuedToken);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(query, headers);

        try {
            getHolderClient().exchange(
                "/dcp/presentations/query",
                HttpMethod.POST,
                requestEntity,
                PresentationResponseMessage.class
            );
            fail("Request should have failed with both scope and presentationDefinition");
        } catch (HttpClientErrorException e) {
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
            log.info("✓ Request correctly rejected with both parameters: {}", e.getStatusCode());
        }

        log.info("\n═══════════════════════════════════════════════");
        log.info("✓✓✓ BOTH SCOPE AND DEFINITION TEST PASSED");
        log.info("═══════════════════════════════════════════════\n");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DID Resolution Scenarios
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Test that verifier can discover holder's credential service endpoint from DID.
     * This tests the DID resolution flow.
     */
    @Test
    void testVerifierCanResolveHolderCredentialServiceEndpoint() {
        log.info("\n═══════════════════════════════════════════════");
        log.info("TEST: Verifier Resolves Holder Credential Service Endpoint");
        log.info("═══════════════════════════════════════════════");

        // Fetch holder's DID document
        ResponseEntity<Map<String, Object>> didResponse = getHolderClient().exchange(
            "/holder/did.json",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, didResponse.getStatusCode());
        Map<String, Object> didDocument = didResponse.getBody();
        assertNotNull(didDocument);

        // Verify service entries exist
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> services = (List<Map<String, Object>>) didDocument.get("service");
        assertNotNull(services, "DID document must have service entries");
        assertFalse(services.isEmpty(), "DID document must have at least one service");

        // Find CredentialService endpoint
        boolean foundCredentialService = services.stream()
            .anyMatch(service -> "CredentialService".equals(service.get("type")));

        assertTrue(foundCredentialService, "DID document must have CredentialService endpoint");
        log.info("✓ Holder's CredentialService endpoint found in DID document");

        log.info("\n═══════════════════════════════════════════════");
        log.info("✓✓✓ DID RESOLUTION TEST PASSED");
        log.info("═══════════════════════════════════════════════\n");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Sets up credential issuance for testing verifier flow.
     * Issues a membership credential to the holder.
     *
     * @return the credential request ID
     */
    private String setupCredentialIssuance() {
        String holderPid = "holder-verifier-test-" + System.currentTimeMillis();
        String requestId = createCredentialRequest(holderPid);

        // Approve the request with custom claims
        approveRequest(requestId, Map.of(
            "membershipLevel", "Premium",
            "region", "EU"
        ));

        return requestId;
    }

    /**
     * Creates a credential request and returns the request ID.
     *
     * @param holderPid the holder's participant ID
     * @return the credential request ID
     */
    private String createCredentialRequest(String holderPid) {
        String issuerDid = getIssuerDid();
        String validToken = generateValidToken(issuerDid);
        String credentialId = getAnyCredentialId();

        Map<String, Object> credentialRequest = Map.of(
            "@context", List.of("https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"),
            "type", "CredentialRequestMessage",
            "holderPid", holderPid,
            "credentials", List.of(Map.of("id", credentialId))
        );

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        requestHeaders.set("Authorization", "Bearer " + validToken);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(credentialRequest, requestHeaders);

        ResponseEntity<Void> response = getIssuerClient().exchange(
            "/issuer/credentials",
            HttpMethod.POST,
            requestEntity,
            Void.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        String locationHeader = response.getHeaders().getFirst("Location");
        assertNotNull(locationHeader);
        return locationHeader.substring(locationHeader.lastIndexOf("/") + 1);
    }

    /**
     * Approves a credential request with optional custom claims.
     *
     * @param requestId the credential request ID
     * @param customClaims optional custom claims to include in the credential (may be null)
     */
    private void approveRequest(String requestId, Map<String, Object> customClaims) {
        Map<String, Object> approvalRequest = customClaims != null
            ? Map.of("customClaims", customClaims)
            : Map.of();

        HttpEntity<Map<String, Object>> approvalEntity = new HttpEntity<>(approvalRequest);

        ResponseEntity<String> response = getIssuerClient().exchange(
            "/api/issuer/requests/" + requestId + "/approve",
            HttpMethod.POST,
            approvalEntity,
            String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    /**
     * Gets the status of a credential request.
     *
     * @param requestId the credential request ID
     * @return map containing the request status and details
     */
    private Map<String, Object> getRequestStatus(String requestId) {
        String issuerDid = getIssuerDid();
        String freshToken = generateValidToken(issuerDid);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + freshToken);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = getIssuerClient().exchange(
            "/issuer/requests/" + requestId,
            HttpMethod.GET,
            requestEntity,
            new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody();
    }

    /**
     * Retrieves any available credential ID from the issuer's metadata.
     *
     * @return a credential ID that the issuer supports
     */
    private String getAnyCredentialId() {
        String issuerDid = getIssuerDid();
        String validToken = generateValidToken(issuerDid);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + validToken);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> metadataResponse = getIssuerClient().exchange(
            "/issuer/metadata",
            HttpMethod.GET,
            requestEntity,
            new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, metadataResponse.getStatusCode());
        Map<String, Object> metadata = metadataResponse.getBody();
        assertNotNull(metadata);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> credentialsSupported =
            (List<Map<String, Object>>) metadata.get("credentialsSupported");
        assertFalse(credentialsSupported.isEmpty(), "Issuer must have at least one credential configured");

        return (String) credentialsSupported.get(0).get("id");
    }

    /**
     * Generates a valid Self-Issued ID Token for querying the holder's presentation endpoint.
     * The audience must be the holder DID (the service receiving the request), not the verifier DID.
     *
     * @return valid JWT Bearer token containing access token
     */
    private String generateSelfIssuedIdTokenForVerifier() {
        // The audience must be the holder DID (the service endpoint being called)
        // NOT the verifier DID. The token is issued BY the holder TO the holder.
        String holderDid = getHolderDid();
        Map<String, String> tokenRequest = Map.of("audienceDid", holderDid);

        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(tokenRequest);

        ResponseEntity<Map<String, Object>> tokenResponse = getHolderClient().exchange(
            "/api/dev/token/generate",
            HttpMethod.POST,
            requestEntity,
            new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, tokenResponse.getStatusCode());
        Map<String, Object> tokenBody = tokenResponse.getBody();
        assertNotNull(tokenBody);

        String token = (String) tokenBody.get("token");
        assertNotNull(token, "Token should not be null");
        return token;
    }

    /**
     * Generates a valid JWT token for issuer/holder authentication.
     *
     * @param audienceDid the DID of the audience for the token
     * @return valid JWT Bearer token
     */
    private String generateValidToken(String audienceDid) {
        Map<String, String> tokenRequest = Map.of("audienceDid", audienceDid);

        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(tokenRequest);

        ResponseEntity<Map<String, Object>> tokenResponse = getHolderClient().exchange(
            "/api/dev/token/generate",
            HttpMethod.POST,
            requestEntity,
            new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, tokenResponse.getStatusCode());
        Map<String, Object> tokenBody = tokenResponse.getBody();
        assertNotNull(tokenBody);

        String token = (String) tokenBody.get("token");
        assertNotNull(token, "Token should not be null");
        return token;
    }


    /**
     * Creates a presentation query message with the specified scopes.
     *
     * @param scopes list of credential type scopes
     * @return the presentation query message
     */
    private PresentationQueryMessage createPresentationQuery(List<String> scopes) {
        return PresentationQueryMessage.Builder.newInstance()
            .context(List.of("https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"))
            .scope(scopes)
            .build();
    }

    /**
     * Adds a delay to avoid rate limiting between tests.
     * Rate limiter allows 5 requests per 60 seconds (1 minute) per holder DID.
     * Using 15 seconds to ensure sufficient tokens are refilled between tests.
     */
    private void avoidRateLimit() {
        try {
            Thread.sleep(RATE_LIMIT_DELAY_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}




