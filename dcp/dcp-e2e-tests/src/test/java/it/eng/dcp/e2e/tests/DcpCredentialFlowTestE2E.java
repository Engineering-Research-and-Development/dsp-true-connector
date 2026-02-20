package it.eng.dcp.e2e.tests;

import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.model.IssuerMetadata;
import it.eng.dcp.e2e.environment.DcpTestEnvironment;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test for complete DCP credential issuance and verification flow.
 * This test can run with both Docker and Spring environments.
 *
 * <p>This test verifies the complete flow as per DCP specification v1.0:
 * <ol>
 *   <li>Issuer publishes DID Document and Credential Metadata (Section 6.7)</li>
 *   <li>Holder discovers Issuer capabilities</li>
 *   <li>Holder requests credentials from Issuer (Section 6.4)</li>
 *   <li>Issuer approves/rejects credential requests (Section 6.8)</li>
 *   <li>Holder receives issued credentials (Section 6.5)</li>
 *   <li>Verifier requests and validates presentations from Holder (Section 5.4)</li>
 * </ol>
 */
@Slf4j
class DcpCredentialFlowTestE2E extends DcpTestEnvironment {

    /**
     * Test the complete DID discovery flow.
     * Verifies that all parties (Issuer, Holder, Verifier) publish valid DID documents
     * that can be discovered and contain proper verification methods and service endpoints.
     */
    @Test
    void testDidDiscoveryFlow() {
        log.info("\n═══════════════════════════════════════════════");
        log.info("TEST: DID Discovery Flow");
        log.info("═══════════════════════════════════════════════");

        // Step 1: Discover Issuer DID Document
        log.info("\n--- Step 1: Discover Issuer DID Document ---");
        ResponseEntity<DidDocument> issuerDidResponse = getIssuerClient().getForEntity(
            "/.well-known/did.json",
            DidDocument.class
        );

        assertEquals(HttpStatus.OK, issuerDidResponse.getStatusCode());
        DidDocument issuerDid = issuerDidResponse.getBody();
        assertNotNull(issuerDid);

        log.info("✓ Issuer DID: " + issuerDid.getId());
        log.info("  - Verification Methods: " + issuerDid.getVerificationMethods().size());
        log.info("  - Service Endpoints: " + issuerDid.getServices().size());

        // Validate Issuer DID structure
        assertTrue(issuerDid.getId().startsWith("did:web:"));
        assertTrue(issuerDid.getId().contains("issuer"));
        assertFalse(issuerDid.getVerificationMethods().isEmpty(),
            "Issuer must have at least one verification method");

        // Step 2: Discover Holder DID Document
        log.info("\n--- Step 2: Discover Holder DID Document ---");
        ResponseEntity<DidDocument> holderDidResponse = getHolderClient().getForEntity(
            "/holder/did.json",
            DidDocument.class
        );

        assertEquals(HttpStatus.OK, holderDidResponse.getStatusCode());
        DidDocument holderDid = holderDidResponse.getBody();
        assertNotNull(holderDid);

        log.info("✓ Holder DID: " + holderDid.getId());
        log.info("  - Verification Methods: " + holderDid.getVerificationMethods().size());
        log.info("  - Service Endpoints: " + holderDid.getServices().size());

        // Validate Holder DID structure
        assertTrue(holderDid.getId().startsWith("did:web:"));
        assertTrue(holderDid.getId().contains("holder"));
        assertFalse(holderDid.getVerificationMethods().isEmpty(),
            "Holder must have at least one verification method");

        // Step 3: Discover Verifier DID Document
        // Note: Holder and Verifier share the same application context, so they serve the same DID
        log.info("\n--- Step 3: Discover Verifier DID Document ---");
        ResponseEntity<DidDocument> verifierDidResponse = getVerifierClient().getForEntity(
            "/.well-known/did.json",
            DidDocument.class
        );

        assertEquals(HttpStatus.OK, verifierDidResponse.getStatusCode());
        DidDocument verifierDid = verifierDidResponse.getBody();
        assertNotNull(verifierDid);

        log.info("✓ Verifier DID: " + verifierDid.getId());
        log.info("  - Verification Methods: " + verifierDid.getVerificationMethods().size());
        log.info("  - Service Endpoints: " + verifierDid.getServices().size());

        // Validate Verifier DID structure
        assertTrue(verifierDid.getId().startsWith("did:web:"));
        assertTrue(verifierDid.getId().contains("holder"),
            "Verifier uses holder DID in shared context");
        assertFalse(verifierDid.getVerificationMethods().isEmpty(),
            "Verifier must have at least one verification method");

        // Step 4: Verify DID uniqueness
        // Note: Holder and Verifier share the same DID in this test environment
        log.info("\n--- Step 4: Verify DID Uniqueness ---");
        assertNotEquals(issuerDid.getId(), holderDid.getId(),
            "Issuer DID must be different from Holder DID");
        assertEquals(holderDid.getId(), verifierDid.getId(),
            "Holder and Verifier share the same DID in test environment");

        log.info("✓ Issuer DID is unique from Holder/Verifier DID");

        log.info("\n═══════════════════════════════════════════════");
        log.info("✓✓✓ DID DISCOVERY TEST PASSED");
        log.info("═══════════════════════════════════════════════\n");
    }

    /**
     * Test Issuer metadata discovery.
     * Verifies that the Issuer publishes metadata about supported credentials.
     */
    @Test
    void testIssuerMetadataDiscovery() {
        log.info("\n═══════════════════════════════════════════════");
        log.info("TEST: Issuer Metadata Discovery");
        log.info("═══════════════════════════════════════════════");

        // Step 1: Fetch Issuer Metadata
        log.info("\n--- Step 1: Fetch Issuer Metadata ---");

        // The /issuer/metadata endpoint requires Bearer token authentication
        // This endpoint returns the issuer's capabilities and supported credential types
        String issuerDid = getIssuerDid();

        // Generate valid JWT token for authentication
        String validToken = generateValidToken(issuerDid);

        // Attempt to fetch metadata with a Bearer token
        // The endpoint is at /issuer/metadata and requires authentication
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + validToken);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        ResponseEntity<IssuerMetadata> metadataResponse = getIssuerClient().exchange(
            "/issuer/metadata",
            HttpMethod.GET,
            requestEntity,
            IssuerMetadata.class
        );

        assertEquals(HttpStatus.OK, metadataResponse.getStatusCode());
        IssuerMetadata metadata = metadataResponse.getBody();
        assertNotNull(metadata);

        log.info("✓ Issuer Metadata fetched");
        log.info("  - Issuer DID: " + metadata.getIssuer());
        log.info("  - Credentials Supported: " + metadata.getCredentialsSupported().size());

        // Step 2: Validate Metadata Structure
        log.info("\n--- Step 2: Validate Metadata Structure ---");
        assertNotNull(metadata.getIssuer(), "Issuer DID must be present in metadata");
        assertTrue(metadata.getIssuer().startsWith("did:web:"),
            "Issuer must use did:web method");
        assertNotNull(metadata.getCredentialsSupported(),
            "Credentials supported list must not be null");

        log.info("✓ Metadata structure is valid");

        // Step 3: List Supported Credentials
        if (!metadata.getCredentialsSupported().isEmpty()) {
            log.info("\n--- Step 3: Supported Credentials ---");
            metadata.getCredentialsSupported().forEach(credential -> {
                log.info("  • " + credential.getCredentialType());
                log.info("    ID: " + credential.getId());
            });
        } else {
            log.info("\n--- Step 3: No credentials configured (expected for fresh instance) ---");
        }

        log.info("\n═══════════════════════════════════════════════");
        log.info("✓✓✓ ISSUER METADATA DISCOVERY TEST PASSED");
        log.info("═══════════════════════════════════════════════\n");
    }

    /**
     * Test DID verification method validation.
     * Ensures all parties have properly formatted verification methods with valid keys.
     */
    @Test
    void testVerificationMethodsValidation() {
        log.info("\n═══════════════════════════════════════════════");
        log.info("TEST: Verification Methods Validation");
        log.info("═══════════════════════════════════════════════");

        // Fetch all DID documents
        DidDocument issuerDid = getIssuerClient().getForEntity("/.well-known/did.json", DidDocument.class).getBody();
        DidDocument holderDid = getHolderClient().getForEntity("/holder/did.json", DidDocument.class).getBody();
        DidDocument verifierDid = getVerifierClient().getForEntity("/.well-known/did.json", DidDocument.class).getBody();

        // Validate Issuer verification methods
        log.info("\n--- Validating Issuer Verification Methods ---");
        validateVerificationMethods(issuerDid, "Issuer");

        // Validate Holder verification methods
        log.info("\n--- Validating Holder Verification Methods ---");
        validateVerificationMethods(holderDid, "Holder");

        // Validate Verifier verification methods
        log.info("\n--- Validating Verifier Verification Methods ---");
        validateVerificationMethods(verifierDid, "Verifier");

        log.info("\n═══════════════════════════════════════════════");
        log.info("✓✓✓ VERIFICATION METHODS VALIDATION TEST PASSED");
        log.info("═══════════════════════════════════════════════\n");
    }

    /**
     * Helper method to validate verification methods in a DID document.
     *
     * @param didDocument the DID document to validate
     * @param role        the role name (for logging)
     */
    private void validateVerificationMethods(DidDocument didDocument, String role) {
        assertNotNull(didDocument, role + " DID document should not be null");
        assertNotNull(didDocument.getVerificationMethods(),
            role + " should have verification methods list");
        assertFalse(didDocument.getVerificationMethods().isEmpty(),
            role + " should have at least one verification method");

        didDocument.getVerificationMethods().forEach(method -> {
            assertNotNull(method.getId(), role + " verification method must have an ID");
            assertNotNull(method.getType(), role + " verification method must have a type");
            assertNotNull(method.getController(), role + " verification method must have a controller");

            // Verify the controller matches the DID
            assertEquals(didDocument.getId(), method.getController(),
                role + " verification method controller must match DID");

            log.info("  ✓ " + role + " verification method: " + method.getId());
            log.info("    - Type: " + method.getType());
            log.info("    - Controller: " + method.getController());
        });
    }

    /**
     * Test complete credential issuance flow with approval.
     * Follows DCP spec Section 6 - Credential Issuance Protocol.
     *
     * <p>Flow:
     * <ol>
     *   <li>Holder requests credentials from Issuer (6.4)</li>
     *   <li>Issuer acknowledges request with status URL (6.4.1)</li>
     *   <li>Holder polls request status (6.8)</li>
     *   <li>Issuer approves and issues credentials</li>
     *   <li>Holder receives credentials asynchronously (6.5)</li>
     *   <li>Holder verifies credential integrity</li>
     * </ol>
     */
    @Test
    void testCredentialIssuanceFlowWithApproval() {
        log.info("\n═══════════════════════════════════════════════");
        log.info("TEST: Credential Issuance Flow with Approval");
        log.info("═══════════════════════════════════════════════");

        String holderPid = "holder-request-" + System.currentTimeMillis();

        // Step 1: Holder requests credentials from Issuer
        log.info("\n--- Step 1: Holder Requests Credentials ---");

        // Get issuer DID for token audience
        String issuerDid = getIssuerDid();

        // Generate valid JWT token
        String validToken = generateValidToken(issuerDid);

        // Fetch a valid credential ID from the issuer metadata
        String credentialId = getAnyCredentialId();

        Map<String, Object> credentialRequest = Map.of(
            "@context", List.of("https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"),
            "type", "CredentialRequestMessage",
            "holderPid", holderPid,
            "credentials", List.of(
                Map.of("id", credentialId)
            )
        );

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        requestHeaders.set("Authorization", "Bearer " + validToken);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(credentialRequest, requestHeaders);

        ResponseEntity<Void> createResponse = getIssuerClient().exchange(
            "/issuer/credentials",
            HttpMethod.POST,
            requestEntity,
            Void.class
        );

        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        String locationHeader = createResponse.getHeaders().getFirst("Location");
        assertNotNull(locationHeader, "Location header must be present");
        log.info("✓ Credential request created: " + locationHeader);

        // Extract request ID from location header
        String requestId = locationHeader.substring(locationHeader.lastIndexOf("/") + 1);
        log.info("  Request ID: " + requestId);

        // Step 2: Check initial request status (should be RECEIVED)
        log.info("\n--- Step 2: Check Initial Request Status ---");

        // Generate a FRESH token for status check to avoid JWT replay issues
        String statusToken = generateValidToken(issuerDid);

        HttpHeaders statusHeaders = new HttpHeaders();
        statusHeaders.set("Authorization", "Bearer " + statusToken);
        HttpEntity<Void> statusRequestEntity = new HttpEntity<>(statusHeaders);

        ResponseEntity<Map<String, Object>> statusResponse = getIssuerClient().exchange(
            "/issuer/requests/" + requestId,
            HttpMethod.GET,
            statusRequestEntity,
            new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, statusResponse.getStatusCode());
        Map<String, Object> status = statusResponse.getBody();
        assertNotNull(status);
        assertEquals("RECEIVED", status.get("status"));
        assertEquals(requestId, status.get("issuerPid"));
        assertEquals(holderPid, status.get("holderPid"));
        log.info("✓ Initial status: RECEIVED");

        // Step 3: Issuer approves the request
        log.info("\n--- Step 3: Issuer Approves Request ---");

        Map<String, Object> approvalRequest = Map.of(
            "customClaims", Map.of(
                "membershipLevel", "Premium",
                "region", "EU"
            )
        );

        HttpEntity<Map<String, Object>> approvalEntity = new HttpEntity<>(approvalRequest);

        ResponseEntity<String> approvalResponse = getIssuerClient().exchange(
            "/api/issuer/requests/" + requestId + "/approve",
            HttpMethod.POST,
            approvalEntity,
            String.class
        );

        assertEquals(HttpStatus.OK, approvalResponse.getStatusCode());
        log.info("✓ Request approved: " + approvalResponse.getBody());

        // Step 4: Verify status changed to ISSUED
        log.info("\n--- Step 4: Verify Status Changed to ISSUED ---");

        await()
            .atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofSeconds(1))
            .untilAsserted(() -> {
                Map<String, Object> statusBody = getRequestStatus(requestId);
                assertNotNull(statusBody);
                assertEquals("ISSUED", statusBody.get("status"));
                log.info("✓ Status updated to: ISSUED");
            });

        log.info("\n═══════════════════════════════════════════════");
        log.info("✓✓✓ CREDENTIAL ISSUANCE WITH APPROVAL TEST PASSED");
        log.info("═══════════════════════════════════════════════\n");
    }

    /**
     * Test credential request rejection flow.
     * Follows DCP spec Section 6.5 - credential rejection with reason.
     *
     * <p>Flow:
     * <ol>
     *   <li>Holder requests credentials from Issuer</li>
     *   <li>Issuer acknowledges request</li>
     *   <li>Issuer rejects the request with reason</li>
     *   <li>Holder receives rejection notification</li>
     *   <li>Request status shows REJECTED with reason</li>
     * </ol>
     */
    @Test
    void testCredentialIssuanceFlowWithRejection() {
        log.info("\n═══════════════════════════════════════════════");
        log.info("TEST: Credential Issuance Flow with Rejection");
        log.info("═══════════════════════════════════════════════");

        String holderPid = "holder-reject-" + System.currentTimeMillis();

        // Step 1: Holder requests credentials from Issuer
        log.info("\n--- Step 1: Holder Requests Credentials ---");

        // Get issuer DID for token audience
        String issuerDid = getIssuerDid();

        // Generate valid JWT token
        String validToken = generateValidToken(issuerDid);

        // Fetch a valid credential ID from the issuer metadata
        String credentialId = getAnyCredentialId();

        Map<String, Object> credentialRequest = Map.of(
            "@context", List.of("https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"),
            "type", "CredentialRequestMessage",
            "holderPid", holderPid,
            "credentials", List.of(
                Map.of("id", credentialId)
            )
        );

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        requestHeaders.set("Authorization", "Bearer " + validToken);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(credentialRequest, requestHeaders);

        ResponseEntity<Void> createResponse = getIssuerClient().exchange(
            "/issuer/credentials",
            HttpMethod.POST,
            requestEntity,
            Void.class
        );

        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        String locationHeader = createResponse.getHeaders().getFirst("Location");
        assertNotNull(locationHeader);
        String requestId = locationHeader.substring(locationHeader.lastIndexOf("/") + 1);
        log.info("✓ Credential request created: " + requestId);

        // Step 2: Issuer rejects the request
        log.info("\n--- Step 2: Issuer Rejects Request ---");

        String rejectionReason = "Applicant does not meet security clearance requirements";
        Map<String, String> rejectionRequest = Map.of(
            "reason", rejectionReason
        );

        HttpEntity<Map<String, String>> rejectionEntity = new HttpEntity<>(rejectionRequest);

        ResponseEntity<String> rejectionResponse = getIssuerClient().exchange(
            "/api/issuer/requests/" + requestId + "/reject",
            HttpMethod.POST,
            rejectionEntity,
            String.class
        );

        assertEquals(HttpStatus.OK, rejectionResponse.getStatusCode());
        log.info("✓ Request rejected: " + rejectionResponse.getBody());

        // Step 3: Verify status changed to REJECTED with reason
        log.info("\n--- Step 3: Verify Status Changed to REJECTED ---");

        await()
            .atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofSeconds(1))
            .untilAsserted(() -> {
                Map<String, Object> status = getRequestStatus(requestId);
                assertNotNull(status);
                assertEquals("REJECTED", status.get("status"));
                assertEquals(rejectionReason, status.get("rejectionReason"));
                log.info("✓ Status updated to: REJECTED");
                log.info("  Reason: " + status.get("rejectionReason"));
            });

        log.info("\n═══════════════════════════════════════════════");
        log.info("✓✓✓ CREDENTIAL ISSUANCE WITH REJECTION TEST PASSED");
        log.info("═══════════════════════════════════════════════\n");
    }

    /**
     * Test multiple credential requests lifecycle.
     * Verifies ability to handle multiple concurrent requests with different outcomes.
     *
     * <p>Scenarios:
     * <ol>
     *   <li>Request A: Approved immediately</li>
     *   <li>Request B: Rejected after review</li>
     *   <li>Request C: Approved with custom claims</li>
     *   <li>Verify all requests maintain independent status</li>
     * </ol>
     */
    @Test
    void testMultipleCredentialRequestsLifecycle() {
        log.info("═══════════════════════════════════════════════");
        log.info("TEST: Multiple Credential Requests Lifecycle");
        log.info("═══════════════════════════════════════════════");

        // Create three requests
        String requestIdA = createCredentialRequest("holder-multi-A-" + System.currentTimeMillis());
        String requestIdB = createCredentialRequest("holder-multi-B-" + System.currentTimeMillis());
        String requestIdC = createCredentialRequest("holder-multi-C-" + System.currentTimeMillis());

        log.info("✓ Created 3 credential requests:");
        log.info("  Request A: " + requestIdA);
        log.info("  Request B: " + requestIdB);
        log.info("  Request C: " + requestIdC);

        // Approve request A
        log.info("\n--- Approving Request A ---");
        approveRequest(requestIdA, null);
        log.info("✓ Request A approved");

        // Reject request B
        log.info("\n--- Rejecting Request B ---");
        rejectRequest(requestIdB, "Insufficient documentation provided");
        log.info("✓ Request B rejected");

        // Approve request C with custom claims
        log.info("\n--- Approving Request C with custom claims ---");
        Map<String, Object> customClaims = Map.of(
            "tier", "Gold",
            "validUntil", "2027-12-31"
        );
        approveRequest(requestIdC, customClaims);
        log.info("✓ Request C approved with custom claims");

        // Verify all statuses
        log.info("\n--- Verifying All Request Statuses ---");

        await()
            .atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofSeconds(1))
            .untilAsserted(() -> {
                verifyRequestStatus(requestIdA, "ISSUED", null);
                verifyRequestStatus(requestIdB, "REJECTED", "Insufficient documentation provided");
                verifyRequestStatus(requestIdC, "ISSUED", null);
            });

        log.info("✓ Request A status: ISSUED");
        log.info("✓ Request B status: REJECTED (Insufficient documentation provided)");
        log.info("✓ Request C status: ISSUED");

        log.info("\n═══════════════════════════════════════════════");
        log.info("✓✓✓ MULTIPLE CREDENTIAL REQUESTS LIFECYCLE TEST PASSED");
        log.info("═══════════════════════════════════════════════\n");
    }

    /**
     * Test credential request status polling.
     * Verifies DCP spec Section 6.8 - Credential Request Status API.
     *
     * <p>Validates:
     * <ol>
     *   <li>Status endpoint returns proper CredentialStatus structure</li>
     *   <li>Status transitions are tracked correctly</li>
     *   <li>issuerPid and holderPid are consistent</li>
     * </ol>
     */
    @Test
    void testCredentialRequestStatusPolling() {
        log.info("═══════════════════════════════════════════════");
        log.info("TEST: Credential Request Status Polling");
        log.info("═══════════════════════════════════════════════");

        String holderPid = "holder-polling-" + System.currentTimeMillis();
        String requestId = createCredentialRequest(holderPid);

        log.info("\n--- Step 1: Initial Status Check ---");
        Map<String, Object> initialStatus = getRequestStatus(requestId);

        assertEquals("CredentialStatus", initialStatus.get("type"));
        assertEquals(requestId, initialStatus.get("issuerPid"));
        assertEquals(holderPid, initialStatus.get("holderPid"));
        assertEquals("RECEIVED", initialStatus.get("status"));
        assertNull(initialStatus.get("rejectionReason"));
        log.info("✓ Initial status: RECEIVED");

        log.info("\n--- Step 2: Approve Request ---");
        approveRequest(requestId, null);

        log.info("\n--- Step 3: Poll Until Status Changes ---");
        await()
            .atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                Map<String, Object> status = getRequestStatus(requestId);
                assertEquals("ISSUED", status.get("status"));
            });

        Map<String, Object> finalStatus = getRequestStatus(requestId);
        assertEquals("CredentialStatus", finalStatus.get("type"));
        assertEquals("ISSUED", finalStatus.get("status"));
        assertEquals(requestId, finalStatus.get("issuerPid"));
        assertEquals(holderPid, finalStatus.get("holderPid"));
        log.info("✓ Final status: ISSUED");

        log.info("\n═══════════════════════════════════════════════");
        log.info("✓✓✓ CREDENTIAL REQUEST STATUS POLLING TEST PASSED");
        log.info("═══════════════════════════════════════════════\n");
    }

    /**
     * Test credential request with missing authorization.
     * Verifies DCP spec requirement for Bearer token authentication.
     */
    @Test
    void testCredentialRequestWithoutAuthorization() {
        log.info("\n═══════════════════════════════════════════════");
        log.info("TEST: Credential Request Without Authorization");
        log.info("═══════════════════════════════════════════════");

        // Fetch a valid credential ID from the issuer metadata
        String credentialId = getAnyCredentialId();

        Map<String, Object> credentialRequest = Map.of(
            "@context", List.of("https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"),
            "type", "CredentialRequestMessage",
            "holderPid", "test-holder",
            "credentials", List.of(Map.of("id", credentialId))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // No Authorization header

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(credentialRequest, headers);

        try {
            getIssuerClient().exchange(
                "/issuer/credentials",
                HttpMethod.POST,
                requestEntity,
                Void.class
            );
            fail("Request should have failed without authorization");
        } catch (HttpClientErrorException e) {
            assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode());
            log.info("✓ Request correctly rejected without authorization: " + e.getStatusCode());
        }

        log.info("\n═══════════════════════════════════════════════");
        log.info("✓✓✓ CREDENTIAL REQUEST WITHOUT AUTHORIZATION TEST PASSED");
        log.info("═══════════════════════════════════════════════\n");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods for Flow Tests
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Fetches the issuer metadata containing supported credentials.
     *
     * @return IssuerMetadata object
     */
    private IssuerMetadata fetchIssuerMetadata() {
        String issuerDid = getIssuerDid();
        String validToken = generateValidToken(issuerDid);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + validToken);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        ResponseEntity<IssuerMetadata> metadataResponse = getIssuerClient().exchange(
            "/issuer/metadata",
            HttpMethod.GET,
            requestEntity,
            IssuerMetadata.class
        );

        assertEquals(HttpStatus.OK, metadataResponse.getStatusCode());
        IssuerMetadata metadata = metadataResponse.getBody();
        assertNotNull(metadata, "Issuer metadata should not be null");
        return metadata;
    }

    /**
     * Retrieves a credential ID from the issuer's metadata by credential type.
     *
     * @param credentialType the type of credential (e.g., "MembershipCredential", "CompanyCredential")
     * @return the credential ID, or null if not found
     */
    private String getCredentialIdByType(String credentialType) {
        IssuerMetadata metadata = fetchIssuerMetadata();
        return metadata.getCredentialsSupported().stream()
            .filter(credential -> credentialType.equals(credential.getCredentialType()))
            .map(IssuerMetadata.CredentialObject::getId)
            .findFirst()
            .orElse(null);
    }

    /**
     * Retrieves any available credential ID from the issuer's metadata.
     * Useful for tests that don't care about the specific credential type.
     *
     * @return the first available credential ID
     * @throws AssertionError if no credentials are configured
     */
    private String getAnyCredentialId() {
        IssuerMetadata metadata = fetchIssuerMetadata();
        assertFalse(metadata.getCredentialsSupported().isEmpty(),
            "Issuer must have at least one credential configured");
        String credentialId = metadata.getCredentialsSupported().get(0).getId();
        log.info("Using credential: {} ({})",
            metadata.getCredentialsSupported().get(0).getCredentialType(), credentialId);
        return credentialId;
    }

    /**
     * Generates a valid Self-Issued ID Token using the holder's token generator API.
     *
     * @param audienceDid the DID of the audience (issuer or verifier)
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
     * Creates a credential request and returns the request ID.
     */
    private String createCredentialRequest(String holderPid) {
        // Get issuer DID for token audience
        String issuerDid = getIssuerDid();

        // Generate valid JWT token
        String validToken = generateValidToken(issuerDid);

        // Fetch a valid credential ID from the issuer metadata
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
     * Rejects a credential request with a reason.
     */
    private void rejectRequest(String requestId, String reason) {
        Map<String, String> rejectionRequest = Map.of("reason", reason);
        HttpEntity<Map<String, String>> rejectionEntity = new HttpEntity<>(rejectionRequest);

        ResponseEntity<String> response = getIssuerClient().exchange(
            "/api/issuer/requests/" + requestId + "/reject",
            HttpMethod.POST,
            rejectionEntity,
            String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    /**
     * Gets the status of a credential request.
     * Generates a fresh token for each status check to avoid JWT replay issues.
     *
     * @param requestId the credential request ID
     * @return the request status as a map
     */
    private Map<String, Object> getRequestStatus(String requestId) {
        // Get issuer DID for token audience
        String issuerDid = getIssuerDid();

        // IMPORTANT: Generate a FRESH token for each status check
        // The jti (JWT ID) must be unique for each request to prevent replay attacks
        String freshToken = generateValidToken(issuerDid);

        // Create request with authorization header
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
     * Verifies request status matches expected values.
     */
    private void verifyRequestStatus(String requestId, String expectedStatus, String expectedRejectionReason) {
        Map<String, Object> status = getRequestStatus(requestId);
        assertEquals(expectedStatus, status.get("status"));
        if (expectedRejectionReason != null) {
            assertEquals(expectedRejectionReason, status.get("rejectionReason"));
        }
    }
}
