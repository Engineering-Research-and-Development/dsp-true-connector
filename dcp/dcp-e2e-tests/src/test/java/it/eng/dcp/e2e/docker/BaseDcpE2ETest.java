package it.eng.dcp.e2e.docker;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Testcontainers;


/**
 * Base class for DCP End-to-End tests using Testcontainers.
 *
 * <p>This class manages Docker containers running in separate instances:
 * <ol>
 *   <li><strong>Issuer Container</strong> - Runs DCP Issuer application, issues credentials</li>
 *   <li><strong>Holder+Verifier Container</strong> - Runs combined Holder and Verifier application for testing</li>
 * </ol>
 *
 * <p>The test setup includes:
 * <ul>
 *   <li>MongoDB container shared across all applications</li>
 *   <li>Docker network for inter-container communication</li>
 *   <li>Dynamic port assignment to avoid conflicts</li>
 *   <li>Automatic Docker image build and cleanup</li>
 *   <li>REST clients for interacting with all applications</li>
 * </ul>
 *
 * <p><strong>Usage:</strong>
 * <pre>
 * {@code
 * class MyE2ETest extends BaseDcpE2ETest {
 *
 *     @Test
 *     void testCredentialIssuance() {
 *         // Use issuerClient, holderClient, verifierClient
 *         String credential = issuerClient.getForObject(
 *             "/api/v1/credentials/issue",
 *             String.class
 *         );
 *
 *         assertNotNull(credential);
 *     }
 * }
 * }
 * </pre>
 *
 * @see <a href="../../../../../doc/E2E_TESTING_QUICKSTART.md">E2E Testing Quick Start Guide</a>
 */
@Testcontainers
@Slf4j
public abstract class BaseDcpE2ETest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Static Initialization - Docker Configuration
    // ═══════════════════════════════════════════════════════════════════════════

    static {
        // Configure Docker environment for Windows
        // This helps Testcontainers find Docker Desktop on Windows systems
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            log.info("Detected Windows OS - configuring Docker environment...");

            // Try to use TCP if DOCKER_HOST is not set and npipe fails
            // This can help in some Docker Desktop configurations
            if (System.getenv("DOCKER_HOST") == null) {
                log.info("DOCKER_HOST not set - Testcontainers will auto-detect");
            } else {
                log.info("DOCKER_HOST is set to: {}", System.getenv("DOCKER_HOST"));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Docker Infrastructure
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Shared network for all containers to communicate.
     */
    protected static Network network;

    /**
     * MongoDB container shared across all DCP applications.
     */
    protected static MongoDBContainer mongoDBContainer;

    /**
     * Issuer application container.
     */
    protected static GenericContainer<?> issuerContainer;

    /**
     * Combined Holder+Verifier test application container.
     */
    protected static GenericContainer<?> holderVerifierContainer;

    // ═══════════════════════════════════════════════════════════════════════════
    // REST Clients
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * REST template for making requests to Issuer application.
     */
    protected RestTemplate issuerClient;

    /**
     * REST template for making requests to Holder application.
     */
    protected RestTemplate holderClient;

    /**
     * REST template for making requests to Verifier application.
     */
    protected RestTemplate verifierClient;

    // ═══════════════════════════════════════════════════════════════════════════
    // Setup and Teardown
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Ensure shared Docker environment is started.
     * Uses singleton pattern - containers are created once and shared across ALL test classes.
     */
    @BeforeAll
    static void startContainers() {
        SharedDockerEnvironment sharedEnv = SharedDockerEnvironment.getInstance();
        sharedEnv.ensureStarted();

        // Get containers from shared environment
        issuerContainer = sharedEnv.getIssuerContainer();
        holderVerifierContainer = sharedEnv.getHolderVerifierContainer();
        mongoDBContainer = sharedEnv.getMongoDBContainer();
        network = sharedEnv.getNetwork();

        log.info("═══════════════════════════════════════════════");
        log.info("Test class using shared Docker environment");
        log.info("═══════════════════════════════════════════════");
    }

    /**
     * Cleanup is handled automatically by SharedDockerEnvironment shutdown hook.
     * No need for @AfterAll - containers are shared and cleaned up when JVM exits.
     */

    /**
     * Create HTTP clients before each test.
     * This runs before each test method.
     */
    @BeforeEach
    void setupClients() {
        int issuerPort = issuerContainer.getMappedPort(8084);
        int holderVerifierPort = holderVerifierContainer.getMappedPort(8087);

        log.info("───────────────────────────────────────────────");
        log.info("Test URLs:");
        log.info("  Issuer: http://localhost:{}", issuerPort);
        log.info("  Holder+Verifier: http://localhost:{}", holderVerifierPort);
        log.info("───────────────────────────────────────────────");

        issuerClient = new RestTemplateBuilder()
            .rootUri("http://localhost:" + issuerPort)
            .build();

        holderClient = new RestTemplateBuilder()
            .rootUri("http://localhost:" + holderVerifierPort)
            .build();

        verifierClient = new RestTemplateBuilder()
            .rootUri("http://localhost:" + holderVerifierPort)
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get the Issuer DID based on current container port.
     * Format: did:web:localhost:{port}:issuer
     */
    protected String getIssuerDid() {
        return "did:web:localhost:" + issuerContainer.getMappedPort(8084) + ":issuer";
    }

    /**
     * Get the Holder DID based on current container port.
     * Format: did:web:localhost:{port}:holder
     */
    protected String getHolderDid() {
        return "did:web:localhost:" + holderVerifierContainer.getMappedPort(8087) + ":holder";
    }

    /**
     * Get the Verifier DID based on current container port.
     * Format: did:web:localhost:{port}:verifier
     */
    protected String getVerifierDid() {
        return "did:web:localhost:" + holderVerifierContainer.getMappedPort(8087) + ":verifier";
    }

    /**
     * Get the Issuer application base URL.
     */
    protected String getIssuerBaseUrl() {
        return "http://localhost:" + issuerContainer.getMappedPort(8084);
    }

    /**
     * Get the Holder application base URL.
     */
    protected String getHolderBaseUrl() {
        return "http://localhost:" + holderVerifierContainer.getMappedPort(8087);
    }

    /**
     * Get the Verifier application base URL.
     */
    protected String getVerifierBaseUrl() {
        return "http://localhost:" + holderVerifierContainer.getMappedPort(8087);
    }

}

