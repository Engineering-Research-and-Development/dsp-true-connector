package it.eng.dcp.e2e.tests;

import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.e2e.common.DcpTestEnvironment;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Abstract base class containing runtime-agnostic DCP tests.
 *
 * <p>This class contains test logic that works across different environments
 * (Spring Boot, Docker, Kubernetes, etc.). Concrete subclasses provide the
 * specific {@link DcpTestEnvironment} implementation.
 *
 * <p><strong>Design Pattern:</strong>
 * This follows the Template Method pattern where the test logic is defined
 * in the abstract base class, and subclasses provide environment-specific
 * setup through the {@link #getEnvironment()} method.
 *
 * <p><strong>Adding New Tests:</strong>
 * When you add new test methods to this class, they automatically become
 * available in all environment-specific test classes (Spring and Docker).
 *
 * <pre>{@code
 * // Example: Add a new test
 * @Test
 * void testNewFeature() {
 *     DcpTestEnvironment env = getEnvironment();
 *     // Your test logic here
 * }
 * }</pre>
 */
public abstract class AbstractDcpE2ETest {

    /**
     * Get the test environment implementation.
     * Subclasses must provide this to specify which runtime to use.
     *
     * @return the test environment (Spring, Docker, etc.)
     */
    protected abstract DcpTestEnvironment getEnvironment();

    // ═══════════════════════════════════════════════════════════════════════════
    // DID Discovery Tests
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Test that the Issuer application serves a valid DID document.
     *
     * <p>Verifies:
     * <ul>
     *   <li>DID document is accessible at /.well-known/did.json</li>
     *   <li>DID uses did:web method</li>
     *   <li>DID contains "issuer" identifier</li>
     *   <li>At least one verification method is present</li>
     * </ul>
     */
    @Test
    public void testIssuerDidDocumentIsAccessible() {
        DcpTestEnvironment env = getEnvironment();
        RestTemplate issuerClient = env.getIssuerClient();

        System.out.println("═══════════════════════════════════════════════");
        System.out.println("TEST: Issuer DID Document Discovery");
        System.out.println("Environment: " + env.getEnvironmentName());
        System.out.println("═══════════════════════════════════════════════");

        // Fetch the DID document
        ResponseEntity<DidDocument> response = issuerClient.getForEntity(
            "/.well-known/did.json",
            DidDocument.class
        );

        // Verify HTTP 200 OK
        assertEquals(HttpStatus.OK, response.getStatusCode(),
            "Issuer DID endpoint should return 200 OK");

        // Verify document structure
        DidDocument didDocument = response.getBody();
        assertNotNull(didDocument, "DID document should not be null");
        assertNotNull(didDocument.getId(), "DID should have an ID");

        System.out.println("✓ Issuer DID: " + didDocument.getId());
        System.out.println("  Base URL: " + env.getIssuerBaseUrl());
        System.out.println("  Expected DID: " + env.getIssuerDid());

        // Verify DID format
        assertTrue(didDocument.getId().startsWith("did:web:"),
            "Issuer DID should use did:web method");
        assertTrue(didDocument.getId().contains("issuer"),
            "Issuer DID should contain 'issuer' identifier");

        // Verify verification methods exist
        assertNotNull(didDocument.getVerificationMethods(),
            "Issuer should have verification methods");
        assertFalse(didDocument.getVerificationMethods().isEmpty(),
            "Issuer should have at least one verification method");

        System.out.println("  Verification Methods: " + didDocument.getVerificationMethods().size());
        System.out.println("  Services: " + didDocument.getServices().size());

        System.out.println("✓ Test passed: Issuer DID document is valid");
        System.out.println("═══════════════════════════════════════════════\n");
    }

    /**
     * Test that the Holder application serves a valid DID document.
     *
     * <p>Verifies:
     * <ul>
     *   <li>DID document is accessible at /holder/did.json</li>
     *   <li>DID uses did:web method</li>
     *   <li>DID contains "holder" identifier</li>
     *   <li>At least one verification method is present</li>
     * </ul>
     */
    @Test
    public void testHolderDidDocumentIsAccessible() {
        DcpTestEnvironment env = getEnvironment();
        RestTemplate holderClient = env.getHolderClient();

        System.out.println("═══════════════════════════════════════════════");
        System.out.println("TEST: Holder DID Document Discovery");
        System.out.println("Environment: " + env.getEnvironmentName());
        System.out.println("═══════════════════════════════════════════════");

        // Fetch the DID document
        ResponseEntity<DidDocument> response = holderClient.getForEntity(
            "/holder/did.json",
            DidDocument.class
        );

        // Verify HTTP 200 OK
        assertEquals(HttpStatus.OK, response.getStatusCode(),
            "Holder DID endpoint should return 200 OK");

        // Verify document structure
        DidDocument didDocument = response.getBody();
        assertNotNull(didDocument, "DID document should not be null");
        assertNotNull(didDocument.getId(), "DID should have an ID");

        System.out.println("✓ Holder DID: " + didDocument.getId());
        System.out.println("  Base URL: " + env.getHolderBaseUrl());
        System.out.println("  Expected DID: " + env.getHolderDid());

        // Verify DID format
        assertTrue(didDocument.getId().startsWith("did:web:"),
            "Holder DID should use did:web method");
        assertTrue(didDocument.getId().contains("holder"),
            "Holder DID should contain 'holder' identifier");

        // Verify verification methods exist
        assertNotNull(didDocument.getVerificationMethods(),
            "Holder should have verification methods");
        assertFalse(didDocument.getVerificationMethods().isEmpty(),
            "Holder should have at least one verification method");

        System.out.println("  Verification Methods: " + didDocument.getVerificationMethods().size());
        System.out.println("  Services: " + didDocument.getServices().size());

        System.out.println("✓ Test passed: Holder DID document is valid");
        System.out.println("═══════════════════════════════════════════════\n");
    }

    /**
     * Test that the Verifier application serves a valid DID document.
     *
     * <p>Verifies:
     * <ul>
     *   <li>DID document is accessible at /verifier/did.json</li>
     *   <li>DID uses did:web method</li>
     *   <li>DID contains "verifier" identifier</li>
     *   <li>At least one verification method is present</li>
     * </ul>
     */
    @Test
    public void testVerifierDidDocumentIsAccessible() {
        DcpTestEnvironment env = getEnvironment();
        RestTemplate verifierClient = env.getVerifierClient();

        System.out.println("═══════════════════════════════════════════════");
        System.out.println("TEST: Verifier DID Document Discovery");
        System.out.println("Environment: " + env.getEnvironmentName());
        System.out.println("═══════════════════════════════════════════════");

        // Fetch the DID document
        ResponseEntity<DidDocument> response = verifierClient.getForEntity(
            "/verifier/did.json",
            DidDocument.class
        );

        // Verify HTTP 200 OK
        assertEquals(HttpStatus.OK, response.getStatusCode(),
            "Verifier DID endpoint should return 200 OK");

        // Verify document structure
        DidDocument didDocument = response.getBody();
        assertNotNull(didDocument, "DID document should not be null");
        assertNotNull(didDocument.getId(), "DID should have an ID");

        System.out.println("✓ Verifier DID: " + didDocument.getId());
        System.out.println("  Base URL: " + env.getVerifierBaseUrl());
        System.out.println("  Expected DID: " + env.getVerifierDid());

        // Verify DID format
        assertTrue(didDocument.getId().startsWith("did:web:"),
            "Verifier DID should use did:web method");
        assertTrue(didDocument.getId().contains("verifier"),
            "Verifier DID should contain 'verifier' identifier");

        // Verify verification methods exist
        assertNotNull(didDocument.getVerificationMethods(),
            "Verifier should have verification methods");
        assertFalse(didDocument.getVerificationMethods().isEmpty(),
            "Verifier should have at least one verification method");

        System.out.println("  Verification Methods: " + didDocument.getVerificationMethods().size());
        System.out.println("  Services: " + didDocument.getServices().size());

        System.out.println("✓ Test passed: Verifier DID document is valid");
        System.out.println("═══════════════════════════════════════════════\n");
    }

    /**
     * Test that all three DIDs (Issuer, Holder, Verifier) are unique.
     *
     * <p>This ensures proper identity isolation between applications.
     */
    @Test
    public void testAllDidsAreUnique() {
        DcpTestEnvironment env = getEnvironment();

        System.out.println("═══════════════════════════════════════════════");
        System.out.println("TEST: DID Uniqueness");
        System.out.println("Environment: " + env.getEnvironmentName());
        System.out.println("═══════════════════════════════════════════════");

        String issuerDid = env.getIssuerDid();
        String holderDid = env.getHolderDid();
        String verifierDid = env.getVerifierDid();

        System.out.println("Issuer DID:   " + issuerDid);
        System.out.println("Holder DID:   " + holderDid);
        System.out.println("Verifier DID: " + verifierDid);

        // Verify all DIDs are different
        assertNotEquals(issuerDid, holderDid,
            "Issuer and Holder should have different DIDs");
        assertNotEquals(issuerDid, verifierDid,
            "Issuer and Verifier should have different DIDs");
        assertNotEquals(holderDid, verifierDid,
            "Holder and Verifier should have different DIDs");

        System.out.println("✓ Test passed: All DIDs are unique");
        System.out.println("═══════════════════════════════════════════════\n");
    }
}
