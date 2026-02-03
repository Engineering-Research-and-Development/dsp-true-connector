package it.eng.dcp.e2e.docker;

import it.eng.dcp.common.model.DidDocument;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test for complete DCP flow - verifies application containers start successfully.
 */
class DcpCompleteFlowTestE2E extends BaseDcpE2ETest {

    /**
     * Test that verifies all Docker containers start successfully and can serve DID documents.
     * This is a smoke test to ensure the E2E testing infrastructure is working.
     */
    @Test
    void testApplicationContextsStartSuccessfully() {
        System.out.println("═══════════════════════════════════════════════");
        System.out.println("SMOKE TEST: Verifying Application Containers");
        System.out.println("═══════════════════════════════════════════════");

        // Verify Issuer container
        assertNotNull(issuerContainer, "Issuer container should be initialized");
        assertTrue(issuerContainer.isRunning(), "Issuer container should be running");
        assertTrue(issuerContainer.getMappedPort(8084) > 0, "Issuer port should be mapped");

        System.out.println("✓ Issuer container is running on port: " + issuerContainer.getMappedPort(8084));

        // Verify combined Holder+Verifier container
        assertNotNull(holderVerifierContainer, "Holder+Verifier container should be initialized");
        assertTrue(holderVerifierContainer.isRunning(), "Holder+Verifier container should be running");
        assertTrue(holderVerifierContainer.getMappedPort(8087) > 0, "Holder+Verifier port should be mapped");

        System.out.println("✓ Holder+Verifier container is running on port: " + holderVerifierContainer.getMappedPort(8087));

        // Verify REST clients are initialized
        assertNotNull(issuerClient, "Issuer REST client should be initialized");
        assertNotNull(holderClient, "Holder REST client should be initialized");
        assertNotNull(verifierClient, "Verifier REST client should be initialized");

        System.out.println("✓ REST clients are initialized");

        System.out.println("\n--- Fetching DID Documents from Applications ---");

        // Fetch and verify Issuer DID document
        ResponseEntity<DidDocument> issuerDidResponse = issuerClient.getForEntity("/.well-known/did.json", DidDocument.class);
        assertEquals(HttpStatus.OK, issuerDidResponse.getStatusCode(), "Issuer DID endpoint should return 200 OK");
        assertNotNull(issuerDidResponse.getBody(), "Issuer DID document should not be null");

        DidDocument issuerDidDocument = issuerDidResponse.getBody();
        assertNotNull(issuerDidDocument.getId(), "Issuer DID should have an ID");
        assertTrue(issuerDidDocument.getId().startsWith("did:web:"), "Issuer DID should use did:web method");
        assertNotNull(issuerDidDocument.getVerificationMethods(), "Issuer should have verification methods");
        assertFalse(issuerDidDocument.getVerificationMethods().isEmpty(), "Issuer should have at least one verification method");

        System.out.println("✓ Issuer DID Document fetched:");
        System.out.println("  - DID:                 " + issuerDidDocument.getId());
        System.out.println("  - Verification Methods: " + issuerDidDocument.getVerificationMethods().size());
        System.out.println("  - Services:            " + issuerDidDocument.getServices().size());

        // Fetch and verify Holder DID document
        ResponseEntity<DidDocument> holderDidResponse = holderClient.getForEntity("/holder/did.json", DidDocument.class);
        assertEquals(HttpStatus.OK, holderDidResponse.getStatusCode(), "Holder DID endpoint should return 200 OK");
        assertNotNull(holderDidResponse.getBody(), "Holder DID document should not be null");

        DidDocument holderDidDocument = holderDidResponse.getBody();
        assertNotNull(holderDidDocument.getId(), "Holder DID should have an ID");
        assertTrue(holderDidDocument.getId().startsWith("did:web:"), "Holder DID should use did:web method");
        assertNotNull(holderDidDocument.getVerificationMethods(), "Holder should have verification methods");
        assertFalse(holderDidDocument.getVerificationMethods().isEmpty(), "Holder should have at least one verification method");

        System.out.println("✓ Holder DID Document fetched:");
        System.out.println("  - DID:                 " + holderDidDocument.getId());
        System.out.println("  - Verification Methods: " + holderDidDocument.getVerificationMethods().size());
        System.out.println("  - Services:            " + holderDidDocument.getServices().size());

        // Fetch and verify Verifier DID document
        ResponseEntity<DidDocument> verifierDidResponse = verifierClient.getForEntity("/verifier/did.json", DidDocument.class);
        assertEquals(HttpStatus.OK, verifierDidResponse.getStatusCode(), "Verifier DID endpoint should return 200 OK");
        assertNotNull(verifierDidResponse.getBody(), "Verifier DID document should not be null");

        DidDocument verifierDidDocument = verifierDidResponse.getBody();
        assertNotNull(verifierDidDocument.getId(), "Verifier DID should have an ID");
        assertTrue(verifierDidDocument.getId().startsWith("did:web:"), "Verifier DID should use did:web method");
        assertNotNull(verifierDidDocument.getVerificationMethods(), "Verifier should have verification methods");
        assertFalse(verifierDidDocument.getVerificationMethods().isEmpty(), "Verifier should have at least one verification method");

        System.out.println("✓ Verifier DID Document fetched:");
        System.out.println("  - DID:                 " + verifierDidDocument.getId());
        System.out.println("  - Verification Methods: " + verifierDidDocument.getVerificationMethods().size());
        System.out.println("  - Services:            " + verifierDidDocument.getServices().size());

        // Verify DIDs are unique
        assertNotEquals(issuerDidDocument.getId(), holderDidDocument.getId(), "Issuer and Holder should have different DIDs");
        assertNotEquals(issuerDidDocument.getId(), verifierDidDocument.getId(), "Issuer and Verifier should have different DIDs");
        assertNotEquals(holderDidDocument.getId(), verifierDidDocument.getId(), "Holder and Verifier should have different DIDs");

        System.out.println("\n✓ All DIDs are unique and properly configured");

        // Verify DID structure matches expected format
        String expectedIssuerDid = getIssuerDid();
        String expectedHolderDid = getHolderDid();
        String expectedVerifierDid = getVerifierDid();

        assertTrue(issuerDidDocument.getId().contains("issuer"), "Issuer DID should contain 'issuer'");
        assertTrue(holderDidDocument.getId().contains("holder"), "Holder DID should contain 'holder'");
        assertTrue(verifierDidDocument.getId().contains("verifier"), "Verifier DID should contain 'verifier'");

        System.out.println("\n✓ DID structure validation passed");
        System.out.println("  - Expected Issuer DID:   " + expectedIssuerDid);
        System.out.println("  - Actual Issuer DID:     " + issuerDidDocument.getId());
        System.out.println("  - Expected Holder DID:   " + expectedHolderDid);
        System.out.println("  - Actual Holder DID:     " + holderDidDocument.getId());
        System.out.println("  - Expected Verifier DID: " + expectedVerifierDid);
        System.out.println("  - Actual Verifier DID:   " + verifierDidDocument.getId());

        System.out.println("\n═══════════════════════════════════════════════");
        System.out.println("✓✓✓ SMOKE TEST PASSED - E2E Infrastructure OK");
        System.out.println("═══════════════════════════════════════════════");
    }
}
