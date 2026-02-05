package it.eng.dcp.e2e.common;

import org.springframework.web.client.RestTemplate;

/**
 * Abstraction for DCP test environment that can run on different runtimes.
 *
 * <p>This interface provides a runtime-agnostic way to access DCP applications
 * (Issuer, Holder, Verifier) during end-to-end testing. Implementations can
 * provide access to applications running as:
 * <ul>
 *   <li>Spring Boot applications (in-memory, embedded)</li>
 *   <li>Docker containers (via Testcontainers)</li>
 *   <li>Remote deployments (Kubernetes, cloud, etc.)</li>
 * </ul>
 *
 * <p>Tests written against this interface are portable across different
 * deployment scenarios, making them reusable for local development,
 * CI/CD pipelines, and production verification.
 *
 * <p><strong>Example usage:</strong>
 * <pre>{@code
 * @Test
 * void testDidDiscovery(DcpTestEnvironment env) {
 *     RestTemplate issuerClient = env.getIssuerClient();
 *     String didDoc = issuerClient.getForObject("/.well-known/did.json", String.class);
 *     assertNotNull(didDoc);
 * }
 * }</pre>
 */
public interface DcpTestEnvironment {

    /**
     * Get a REST client configured to communicate with the Issuer application.
     * The client is pre-configured with the correct base URL.
     *
     * @return RestTemplate configured for Issuer endpoints
     */
    RestTemplate getIssuerClient();

    /**
     * Get a REST client configured to communicate with the Holder application.
     * The client is pre-configured with the correct base URL.
     *
     * @return RestTemplate configured for Holder endpoints
     */
    RestTemplate getHolderClient();

    /**
     * Get a REST client configured to communicate with the Verifier application.
     * The client is pre-configured with the correct base URL.
     *
     * @return RestTemplate configured for Verifier endpoints
     */
    RestTemplate getVerifierClient();

    /**
     * Get the Issuer application's base URL.
     *
     * @return Base URL (e.g., "http://localhost:8082")
     */
    String getIssuerBaseUrl();

    /**
     * Get the Holder application's base URL.
     *
     * @return Base URL (e.g., "http://localhost:8081")
     */
    String getHolderBaseUrl();

    /**
     * Get the Verifier application's base URL.
     *
     * @return Base URL (e.g., "http://localhost:8081")
     */
    String getVerifierBaseUrl();

    /**
     * Get the Issuer's DID identifier.
     * Format: did:web:{host}:{port}:issuer
     *
     * @return Issuer DID (e.g., "did:web:localhost:8082:issuer")
     */
    String getIssuerDid();

    /**
     * Get the Holder's DID identifier.
     * Format: did:web:{host}:{port}:holder
     *
     * @return Holder DID (e.g., "did:web:localhost:8081:holder")
     */
    String getHolderDid();

    /**
     * Get the Verifier's DID identifier.
     * Format: did:web:{host}:{port}:verifier
     *
     * @return Verifier DID (e.g., "did:web:localhost:8081:verifier")
     */
    String getVerifierDid();

    /**
     * Get a human-readable name describing this environment.
     * Useful for logging and debugging.
     *
     * @return Environment name (e.g., "Docker", "Spring Boot", "Kubernetes")
     */
    String getEnvironmentName();
}
