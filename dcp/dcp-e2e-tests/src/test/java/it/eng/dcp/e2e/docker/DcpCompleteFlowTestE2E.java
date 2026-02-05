package it.eng.dcp.docker;

import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.e2e.docker.BaseDcpE2ETest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test for complete DCP flow - verifies application containers start successfully.
 */
@Slf4j
class DcpCompleteFlowTestE2E extends BaseDcpE2ETest {

    /**
     * Test that verifies all Docker containers start successfully and can serve DID documents.
     * This is a smoke test to ensure the E2E testing infrastructure is working.
     */
    @Test
    void testApplicationContextsStartSuccessfully() {
        log.info("═══════════════════════════════════════════════");
        log.info("SMOKE TEST: Verifying Application Containers");
        log.info("═══════════════════════════════════════════════");

        // Verify Issuer container
        assertNotNull(issuerContainer, "Issuer container should be initialized");
        assertTrue(issuerContainer.isRunning(), "Issuer container should be running");
        assertTrue(issuerContainer.getMappedPort(8084) > 0, "Issuer port should be mapped");

        log.info("✓ Issuer container is running on port: {}", issuerContainer.getMappedPort(8084));

        // Verify combined Holder+Verifier container
        assertNotNull(holderVerifierContainer, "Holder+Verifier container should be initialized");
        assertTrue(holderVerifierContainer.isRunning(), "Holder+Verifier container should be running");
        assertTrue(holderVerifierContainer.getMappedPort(8087) > 0, "Holder+Verifier port should be mapped");

        log.info("✓ Holder+Verifier container is running on port: {}", holderVerifierContainer.getMappedPort(8087));

        // Verify REST clients are initialized
        assertNotNull(issuerClient, "Issuer REST client should be initialized");
        assertNotNull(holderClient, "Holder REST client should be initialized");
        assertNotNull(verifierClient, "Verifier REST client should be initialized");

        log.info("✓ REST clients are initialized");

        log.info("\n--- Fetching DID Documents from Applications ---");

        // Fetch and verify Issuer DID document
        ResponseEntity<DidDocument> issuerDidResponse = issuerClient.getForEntity("/.well-known/did.json", DidDocument.class);
        assertEquals(HttpStatus.OK, issuerDidResponse.getStatusCode(), "Issuer DID endpoint should return 200 OK");
        assertNotNull(issuerDidResponse.getBody(), "Issuer DID document should not be null");

        DidDocument issuerDidDocument = issuerDidResponse.getBody();
        assertNotNull(issuerDidDocument.getId(), "Issuer DID should have an ID");
        assertTrue(issuerDidDocument.getId().startsWith("did:web:"), "Issuer DID should use did:web method");
        assertNotNull(issuerDidDocument.getVerificationMethods(), "Issuer should have verification methods");
        assertFalse(issuerDidDocument.getVerificationMethods().isEmpty(), "Issuer should have at least one verification method");

        log.info("✓ Issuer DID Document fetched:");
        log.info("  - DID:                 {}", issuerDidDocument.getId());
        log.info("  - Verification Methods: {}", issuerDidDocument.getVerificationMethods().size());
        log.info("  - Services:            {}", issuerDidDocument.getServices().size());

        // Fetch and verify Holder DID document
        ResponseEntity<DidDocument> holderDidResponse = holderClient.getForEntity("/holder/did.json", DidDocument.class);
        assertEquals(HttpStatus.OK, holderDidResponse.getStatusCode(), "Holder DID endpoint should return 200 OK");
        assertNotNull(holderDidResponse.getBody(), "Holder DID document should not be null");

        DidDocument holderDidDocument = holderDidResponse.getBody();
        assertNotNull(holderDidDocument.getId(), "Holder DID should have an ID");
        assertTrue(holderDidDocument.getId().startsWith("did:web:"), "Holder DID should use did:web method");
        assertNotNull(holderDidDocument.getVerificationMethods(), "Holder should have verification methods");
        assertFalse(holderDidDocument.getVerificationMethods().isEmpty(), "Holder should have at least one verification method");

        log.info("✓ Holder DID Document fetched:");
        log.info("  - DID:                 {}", holderDidDocument.getId());
        log.info("  - Verification Methods: {}", holderDidDocument.getVerificationMethods().size());
        log.info("  - Services:            {}", holderDidDocument.getServices().size());

        // Fetch and verify Verifier DID document
        ResponseEntity<DidDocument> verifierDidResponse = verifierClient.getForEntity("/verifier/did.json", DidDocument.class);
        assertEquals(HttpStatus.OK, verifierDidResponse.getStatusCode(), "Verifier DID endpoint should return 200 OK");
        assertNotNull(verifierDidResponse.getBody(), "Verifier DID document should not be null");

        DidDocument verifierDidDocument = verifierDidResponse.getBody();
        assertNotNull(verifierDidDocument.getId(), "Verifier DID should have an ID");
        assertTrue(verifierDidDocument.getId().startsWith("did:web:"), "Verifier DID should use did:web method");
        assertNotNull(verifierDidDocument.getVerificationMethods(), "Verifier should have verification methods");
        assertFalse(verifierDidDocument.getVerificationMethods().isEmpty(), "Verifier should have at least one verification method");

        log.info("✓ Verifier DID Document fetched:");
        log.info("  - DID:                 {}", verifierDidDocument.getId());
        log.info("  - Verification Methods: {}", verifierDidDocument.getVerificationMethods().size());
        log.info("  - Services:            {}", verifierDidDocument.getServices().size());

        // Verify DIDs are unique
        assertNotEquals(issuerDidDocument.getId(), holderDidDocument.getId(), "Issuer and Holder should have different DIDs");
        assertNotEquals(issuerDidDocument.getId(), verifierDidDocument.getId(), "Issuer and Verifier should have different DIDs");
        assertNotEquals(holderDidDocument.getId(), verifierDidDocument.getId(), "Holder and Verifier should have different DIDs");

        log.info("\n✓ All DIDs are unique and properly configured");

        // Verify DID structure matches expected format
        String expectedIssuerDid = getIssuerDid();
        String expectedHolderDid = getHolderDid();
        String expectedVerifierDid = getVerifierDid();

        assertTrue(issuerDidDocument.getId().contains("issuer"), "Issuer DID should contain 'issuer'");
        assertTrue(holderDidDocument.getId().contains("holder"), "Holder DID should contain 'holder'");
        assertTrue(verifierDidDocument.getId().contains("verifier"), "Verifier DID should contain 'verifier'");

        log.info("\n✓ DID structure validation passed");
        log.info("  - Expected Issuer DID:   {}", expectedIssuerDid);
        log.info("  - Actual Issuer DID:     {}", issuerDidDocument.getId());
        log.info("  - Expected Holder DID:   {}", expectedHolderDid);
        log.info("  - Actual Holder DID:     {}", holderDidDocument.getId());
        log.info("  - Expected Verifier DID: {}", expectedVerifierDid);
        log.info("  - Actual Verifier DID:   {}", verifierDidDocument.getId());

        log.info("\n═══════════════════════════════════════════════");
        log.info("✓✓✓ SMOKE TEST PASSED - E2E Infrastructure OK");
        log.info("═══════════════════════════════════════════════");
    }
}
