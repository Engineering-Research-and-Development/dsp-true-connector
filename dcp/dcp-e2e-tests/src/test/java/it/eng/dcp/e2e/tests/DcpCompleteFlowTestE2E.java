package it.eng.dcp.e2e.tests;

import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.e2e.environment.DcpTestEnvironment;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end smoke test for DCP infrastructure.
 * Verifies that all application containers start successfully and can serve DID documents.
 *
 * <p>This test validates:
 * <ul>
 *   <li>REST clients are properly initialized</li>
 *   <li>All services (Issuer, Holder, Verifier) respond to DID document requests</li>
 *   <li>DID documents contain valid structure and verification methods</li>
 *   <li>DIDs follow expected patterns and uniqueness constraints</li>
 * </ul>
 *
 * <p><strong>Note:</strong> In the test environment, Holder and Verifier share the same
 * application context (port 8081) and serve the same DID document. In production deployments,
 * they would have separate endpoints.
 */
@Slf4j
class DcpCompleteFlowTestE2E extends DcpTestEnvironment {

    private static final String W3C_DID_ENDPOINT = "/.well-known/did.json";
    private static final String HOLDER_LEGACY_ENDPOINT = "/holder/did.json";
    private static final String DID_WEB_METHOD = "did:web:";

    /**
     * Smoke test that verifies E2E testing infrastructure is working correctly.
     * Fetches and validates DID documents from all services.
     */
    @Test
    void testApplicationContextsStartSuccessfully() {
        logTestHeader();
        verifyRestClientsInitialized();

        log.info("\n--- Fetching DID Documents from Applications ---");

        var issuerDidDocument = fetchAndValidateIssuerDid();
        var holderDidDocument = fetchAndValidateHolderDid();
        var verifierDidDocument = fetchAndValidateVerifierDid();

        validateDidUniqueness(issuerDidDocument, holderDidDocument, verifierDidDocument);
        validateDidStructure(issuerDidDocument, holderDidDocument, verifierDidDocument);

        logTestSuccess();
    }

    /**
     * Logs the test header with environment information.
     */
    private void logTestHeader() {
        log.info("═══════════════════════════════════════════════");
        log.info("SMOKE TEST: Verifying Application Containers");
        log.info("═══════════════════════════════════════════════");
        log.info("\n--- Verifying Test Environment: {} ---", getEnvironmentName());
    }

    /**
     * Verifies that all REST clients are properly initialized.
     */
    private void verifyRestClientsInitialized() {
        assertNotNull(getIssuerClient(), "Issuer REST client should be initialized");
        assertNotNull(getHolderClient(), "Holder REST client should be initialized");
        assertNotNull(getVerifierClient(), "Verifier REST client should be initialized");

        log.info("✓ REST clients are initialized");
        log.info("  - Issuer URL: {}", getIssuerBaseUrl());
        log.info("  - Holder URL: {}", getHolderBaseUrl());
        log.info("  - Verifier URL: {}", getVerifierBaseUrl());
    }

    /**
     * Fetches and validates the Issuer DID document.
     *
     * @return the validated Issuer DID document
     */
    private DidDocument fetchAndValidateIssuerDid() {
        var didDocument = fetchDidDocument(getIssuerClient(), W3C_DID_ENDPOINT, "Issuer");
        validateDidDocument(didDocument, "Issuer");
        logDidDocument(didDocument, "Issuer");
        return didDocument;
    }

    /**
     * Fetches and validates the Holder DID document.
     *
     * @return the validated Holder DID document
     */
    private DidDocument fetchAndValidateHolderDid() {
        var didDocument = fetchDidDocument(getHolderClient(), HOLDER_LEGACY_ENDPOINT, "Holder");
        validateDidDocument(didDocument, "Holder");
        logDidDocument(didDocument, "Holder");
        return didDocument;
    }

    /**
     * Fetches and validates the Verifier DID document.
     *
     * <p><strong>Note:</strong> Verifier shares the same application context as Holder
     * (both run on port 8081), so we use the standard W3C endpoint which serves the
     * holder DID document. In production, verifier would have its own endpoint.
     *
     * @return the validated Verifier DID document
     */
    private DidDocument fetchAndValidateVerifierDid() {
        var didDocument = fetchDidDocument(getVerifierClient(), W3C_DID_ENDPOINT, "Verifier");
        validateDidDocument(didDocument, "Verifier");
        logDidDocument(didDocument, "Verifier");
        return didDocument;
    }

    /**
     * Fetches a DID document from the specified endpoint.
     *
     * @param client the REST client to use
     * @param endpoint the endpoint path
     * @param role the role name for logging
     * @return the fetched DID document
     */
    private DidDocument fetchDidDocument(RestTemplate client, String endpoint, String role) {
        ResponseEntity<DidDocument> response = client.getForEntity(endpoint, DidDocument.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                role + " DID endpoint should return 200 OK");
        assertNotNull(response.getBody(), role + " DID document should not be null");
        return response.getBody();
    }

    /**
     * Validates the structure and content of a DID document.
     *
     * @param didDocument the DID document to validate
     * @param role the role name for error messages
     */
    private void validateDidDocument(DidDocument didDocument, String role) {
        assertNotNull(didDocument.getId(), role + " DID should have an ID");
        assertTrue(didDocument.getId().startsWith(DID_WEB_METHOD),
                role + " DID should use did:web method");
        assertNotNull(didDocument.getVerificationMethods(),
                role + " should have verification methods");
        assertFalse(didDocument.getVerificationMethods().isEmpty(),
                role + " should have at least one verification method");
    }

    /**
     * Logs DID document information.
     *
     * @param didDocument the DID document to log
     * @param role the role name for logging
     */
    private void logDidDocument(DidDocument didDocument, String role) {
        log.info("✓ {} DID Document fetched:", role);
        log.info("  - DID:                 {}", didDocument.getId());
        log.info("  - Verification Methods: {}", didDocument.getVerificationMethods().size());
        log.info("  - Services:            {}", didDocument.getServices().size());
    }

    /**
     * Validates DID uniqueness constraints.
     *
     * <p>Verifies that Issuer has a unique DID, while Holder and Verifier share
     * the same DID in the test environment.
     *
     * @param issuerDid the Issuer DID document
     * @param holderDid the Holder DID document
     * @param verifierDid the Verifier DID document
     */
    private void validateDidUniqueness(DidDocument issuerDid, DidDocument holderDid,
                                       DidDocument verifierDid) {
        assertNotEquals(issuerDid.getId(), holderDid.getId(),
                "Issuer and Holder should have different DIDs");
        assertNotEquals(issuerDid.getId(), verifierDid.getId(),
                "Issuer and Verifier should have different DIDs");
        assertEquals(holderDid.getId(), verifierDid.getId(),
                "Holder and Verifier share the same DID in test setup");

        log.info("\n✓ DID uniqueness validated (Holder and Verifier share same DID in test environment)");
    }

    /**
     * Validates that DID structures match expected patterns.
     *
     * @param issuerDid the Issuer DID document
     * @param holderDid the Holder DID document
     * @param verifierDid the Verifier DID document
     */
    private void validateDidStructure(DidDocument issuerDid, DidDocument holderDid,
                                      DidDocument verifierDid) {
        assertTrue(issuerDid.getId().contains("issuer"),
                "Issuer DID should contain 'issuer'");
        assertTrue(holderDid.getId().contains("holder"),
                "Holder DID should contain 'holder'");
        assertTrue(verifierDid.getId().contains("holder"),
                "Verifier uses holder DID in shared test context");

        logDidStructureValidation(issuerDid, holderDid, verifierDid);
    }

    /**
     * Logs DID structure validation results.
     *
     * @param issuerDid the Issuer DID document
     * @param holderDid the Holder DID document
     * @param verifierDid the Verifier DID document
     */
    private void logDidStructureValidation(DidDocument issuerDid, DidDocument holderDid,
                                           DidDocument verifierDid) {
        log.info("\n✓ DID structure validation passed");
        log.info("  - Expected Issuer DID:   {}", getIssuerDid());
        log.info("  - Actual Issuer DID:     {}", issuerDid.getId());
        log.info("  - Expected Holder DID:   {}", getHolderDid());
        log.info("  - Actual Holder DID:     {}", holderDid.getId());
        log.info("  - Expected Verifier DID: {}", getVerifierDid());
        log.info("  - Actual Verifier DID:   {} (shares Holder DID)", verifierDid.getId());
    }

    /**
     * Logs test success message.
     */
    private void logTestSuccess() {
        log.info("\n═══════════════════════════════════════════════");
        log.info("✓✓✓ SMOKE TEST PASSED - E2E Infrastructure OK");
        log.info("═══════════════════════════════════════════════");
    }
}
