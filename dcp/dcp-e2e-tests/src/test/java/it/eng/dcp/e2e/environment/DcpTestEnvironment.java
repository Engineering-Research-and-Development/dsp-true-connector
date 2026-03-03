package it.eng.dcp.e2e.environment;

import it.eng.dcp.e2e.HolderVerifierTestApplication;
import it.eng.dcp.issuer.IssuerApplication;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for DCP E2E test environments.
 * Provides base URLs and REST clients for Issuer, Holder, and Verifier services.
 *
 * <p>Supports two test environments:
 * <ul>
 *     <li><strong>Docker</strong>: Uses Testcontainers to run services in Docker containers
 *         (activated with {@code -Dtest.environment=docker})</li>
 *     <li><strong>Spring</strong>: Uses Spring Boot Test with embedded services
 *         (default when test.environment is not set or is set to anything other than "docker")</li>
 * </ul>
 *
 * <p>Test classes should extend this class to get access to the configured environment.</p>
 *
 * <p><strong>Example usage:</strong>
 * <pre>{@code
 * class MyE2ETest extends DcpTestEnvironment {
 *     @Test
 *     void testDidDiscovery() {
 *         RestTemplate issuerClient = getIssuerClient();
 *         String didDoc = issuerClient.getForObject("/.well-known/did.json", String.class);
 *         assertNotNull(didDoc);
 *     }
 * }
 * }</pre>
 */
@Slf4j
@Testcontainers
@Getter
public abstract class DcpTestEnvironment {

    // ═══════════════════════════════════════════════════════════════════════════
    //  Shared Docker Infrastructure (when running in docker mode)
    // ═══════════════════════════════════════════════════════════════════════════

    private static Network network;
    private static MongoDBContainer mongoDBContainer;
    private static GenericContainer<?> issuerContainer;
    private static GenericContainer<?> holderContainer;
    private static GenericContainer<?> verifierContainer;

    // ═══════════════════════════════════════════════════════════════════════════
    //  Spring Boot Application Contexts (when running in spring mode)
    // ═══════════════════════════════════════════════════════════════════════════

    private static ConfigurableApplicationContext holderVerifierContext;
    private static ConfigurableApplicationContext issuerContext;

    // ═══════════════════════════════════════════════════════════════════════════
    //  REST Clients and URLs
    // ═══════════════════════════════════════════════════════════════════════════

    protected static String issuerBaseUrl;
    protected static String holderBaseUrl;
    protected static String verifierBaseUrl;

    protected static RestTemplate issuerClient;
    protected static RestTemplate holderClient;
    protected static RestTemplate verifierClient;

    protected static String environmentName;

    // ═══════════════════════════════════════════════════════════════════════════
    //  Initialization
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Sets up the test environment based on the {@code test.environment} system property.
     * <ul>
     *   <li>{@code -Dtest.environment=docker} — starts Docker containers via Testcontainers</li>
     *   <li>{@code -Dtest.environment=spring} (default) — starts embedded Spring Boot applications</li>
     * </ul>
     */
    @BeforeAll
    public static void setupTestEnvironment() {
        if (isDockerEnvironment()) {
            setupDockerEnvironment();
        } else {
            setupSpringEnvironment();
        }
    }

    /**
     * Cleanup method to properly close resources after all tests complete.
     */
    @AfterAll
    public static void cleanupTestEnvironment() {
        if (!isDockerEnvironment()) {
            cleanupSpringEnvironment();
        }
        // Docker cleanup is handled by SharedDockerEnvironment
    }

    /**
     * Cleans up Spring Boot application contexts and MongoDB container.
     */
    private static void cleanupSpringEnvironment() {
        log.info("Cleaning up Spring environment...");

        if (issuerContext != null) {
            try {
                issuerContext.close();
                log.info("✓ Issuer context closed");
            } catch (Exception e) {
                log.error("Error closing Issuer context", e);
            }
        }

        if (holderVerifierContext != null) {
            try {
                holderVerifierContext.close();
                log.info("✓ Holder+Verifier context closed");
            } catch (Exception e) {
                log.error("Error closing Holder+Verifier context", e);
            }
        }

        if (mongoDBContainer != null) {
            try {
                mongoDBContainer.stop();
                log.info("✓ MongoDB container stopped");
            } catch (Exception e) {
                log.error("Error stopping MongoDB container", e);
            }
        }
    }

    /**
     * Sets up Docker-based test environment using Testcontainers.
     */
    private static void setupDockerEnvironment() {
        log.info("═══════════════════════════════════════════════════════════════════════════");
        log.info("Setting up DOCKER test environment");
        log.info("═══════════════════════════════════════════════════════════════════════════");

        environmentName = "Docker";

        // Configure Docker environment for Windows
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            log.info("Detected Windows OS - configuring Docker Desktop connection");
            System.setProperty("DOCKER_HOST", "tcp://localhost:2375");
        }

        // Initialize shared Docker environment
        SharedDockerEnvironment sharedEnv = SharedDockerEnvironment.getInstance();
        sharedEnv.ensureStarted();

        // Get references to shared containers
        network = sharedEnv.getNetwork();
        mongoDBContainer = sharedEnv.getMongoDBContainer();
        issuerContainer = sharedEnv.getIssuerContainer();
        holderContainer = sharedEnv.getHolderVerifierContainer();
        verifierContainer = sharedEnv.getHolderVerifierContainer(); // Holder and Verifier are in same container
        // Configure URLs for Docker containers using container hostnames
//        issuerBaseUrl = "http://issuer:8082";
//        holderBaseUrl = "http://holderverifier:8081";
//        verifierBaseUrl = "http://holderverifier:8081";
        // need to use following URLs becuase setting up of restCLients
        issuerBaseUrl = "http://localhost:8082";
        holderBaseUrl = "http://localhost:8081";
        verifierBaseUrl = "http://localhost:8081";

        // Initialize REST clients
        initializeRestClients();

        log.info("✓ Docker environment configured successfully");
        log.info("  - Issuer URL: {}", issuerBaseUrl);
        log.info("  - Holder URL: {}", holderBaseUrl);
        log.info("  - Verifier URL: {}", verifierBaseUrl);
    }

    /**
     * Sets up Spring-based test environment using Spring Boot Test.
     * Starts MongoDB TestContainer and both Spring Boot applications (Holder+Verifier and Issuer).
     */
    private static void setupSpringEnvironment() {
        log.info("═══════════════════════════════════════════════════════════════════════════");
        log.info("Setting up SPRING test environment");
        log.info("═══════════════════════════════════════════════════════════════════════════");

        environmentName = "Spring";

        try {
            // Start MongoDB TestContainer for Spring applications
            log.info("Starting MongoDB TestContainer...");
            mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:7.0.12"))
                    .withReuse(false);
            mongoDBContainer.start();

            String mongoHost = mongoDBContainer.getHost();
            Integer mongoPort = mongoDBContainer.getFirstMappedPort();

            log.info("✓ MongoDB TestContainer started at {}:{}", mongoHost, mongoPort);

            // Start Holder+Verifier application on port 8081
            log.info("Starting Holder+Verifier Spring Boot application...");
            holderVerifierContext = new SpringApplicationBuilder(HolderVerifierTestApplication.class)
                    .run(
                            "--server.port=8081",
                            "--spring.profiles.active=holderverifier,holderverifier-spring",
                            "--spring.data.mongodb.host=" + mongoHost,
                            "--spring.data.mongodb.port=" + mongoPort,
                            "--spring.data.mongodb.authentication-database=admin",
                            "--spring.data.mongodb.database=holder_verifier_test"
                    );

            log.info("✓ Holder+Verifier application started on port 8081");

            // Start Issuer application on port 8082
            log.info("Starting Issuer Spring Boot application...");
            issuerContext = new SpringApplicationBuilder(IssuerApplication.class)
                    .run(
                            "--server.port=8082",
                            "--spring.profiles.active=issuer,issuer-spring",
                            "--spring.main.allow-bean-definition-overriding=true",
                            "--spring.data.mongodb.host=" + mongoHost,
                            "--spring.data.mongodb.port=" + mongoPort,
                            "--spring.data.mongodb.authentication-database=admin",
                            "--spring.data.mongodb.database=issuer_test"
                    );

            log.info("✓ Issuer application started on port 8082");

            // Configure URLs for Spring Boot applications
            issuerBaseUrl = "http://localhost:8082";
            holderBaseUrl = "http://localhost:8081";
            verifierBaseUrl = "http://localhost:8081";

            // Initialize REST clients
            initializeRestClients();

            log.info("✓ Spring environment configured successfully");
            log.info("  - Issuer URL: {}", issuerBaseUrl);
            log.info("  - Holder URL: {}", holderBaseUrl);
            log.info("  - Verifier URL: {}", verifierBaseUrl);

        } catch (Exception e) {
            log.error("Failed to start Spring environment", e);
            // Cleanup on failure
            cleanupSpringEnvironment();
            throw new RuntimeException("Failed to start Spring environment", e);
        }
    }

    /**
     * Initializes REST clients for all services.
     */
    private static void initializeRestClients() {
        RestTemplateBuilder builder = new RestTemplateBuilder();

        issuerClient = builder.rootUri(issuerBaseUrl).build();
        holderClient = builder.rootUri(holderBaseUrl).build();
        verifierClient = builder.rootUri(verifierBaseUrl).build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Getters
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gets the base URL for the Issuer service.
     *
     * @return the base URL as a String
     */
    public String getIssuerBaseUrl() {
        return issuerBaseUrl;
    }

    /**
     * Gets the base URL for the Holder service.
     *
     * @return the base URL as a String
     */
    public String getHolderBaseUrl() {
        return holderBaseUrl;
    }

    /**
     * Gets the base URL for the Verifier service.
     *
     * @return the base URL as a String
     */
    public String getVerifierBaseUrl() {
        return verifierBaseUrl;
    }

    /**
     * Gets the RestTemplate configured for the Issuer service.
     *
     * @return configured RestTemplate
     */
    public RestTemplate getIssuerClient() {
        return issuerClient;
    }

    /**
     * Gets the RestTemplate configured for the Holder service.
     *
     * @return configured RestTemplate
     */
    public RestTemplate getHolderClient() {
        return holderClient;
    }

    /**
     * Gets the RestTemplate configured for the Verifier service.
     *
     * @return configured RestTemplate
     */
    public RestTemplate getVerifierClient() {
        return verifierClient;
    }

    /**
     * Gets the Issuer's DID identifier.
     * The host is derived from {@code issuerBaseUrl}, so it reflects the active environment:
     * {@code localhost} in Spring mode, {@code issuer} in Docker mode.
     *
     * @return Issuer DID (e.g., "did:web:localhost%3A8082:issuer" or "did:web:issuer%3A8082:issuer")
     */
    public String getIssuerDid() {
        String host;
        if(isDockerEnvironment()) {
            host = "issuer";
        } else {
            host = extractHost(issuerBaseUrl);
        }
        int port = extractPort(issuerBaseUrl);
        return String.format("did:web:%s%%3A%d:issuer", host, port);
    }

    /**
     * Gets the Holder's DID identifier.
     * The host is derived from {@code holderBaseUrl}, so it reflects the active environment:
     * {@code localhost} in Spring mode, {@code holderverifier} in Docker mode.
     *
     * @return Holder DID (e.g., "did:web:localhost%3A8081:holder" or "did:web:holderverifier%3A8081:holder")
     */
    public String getHolderDid() {
//        String host = extractHost(holderBaseUrl);
        String host;
        if(isDockerEnvironment()) {
            host = "holderverifier";
        } else {
            host = extractHost(issuerBaseUrl);
        }
        int port = extractPort(holderBaseUrl);
        return String.format("did:web:%s%%3A%d:holder", host, port);
    }

    /**
     * Gets the Verifier's DID identifier.
     * The host is derived from {@code verifierBaseUrl}, so it reflects the active environment:
     * {@code localhost} in Spring mode, {@code holderverifier} in Docker mode.
     *
     * @return Verifier DID (e.g., "did:web:localhost%3A8081:verifier" or "did:web:holderverifier%3A8081:verifier")
     */
    public String getVerifierDid() {
//        String host = extractHost(verifierBaseUrl);
        String host;
        if(isDockerEnvironment()) {
            host = "holderverifier";
        } else {
            host = extractHost(issuerBaseUrl);
        }
        int port = extractPort(verifierBaseUrl);
        return String.format("did:web:%s%%3A%d:verifier", host, port);
    }

    /**
     * Gets the name of the current test environment.
     *
     * @return environment name (e.g., "Docker" or "Spring")
     */
    public String getEnvironmentName() {
        return environmentName;
    }

    /**
     * Determines the test environment based on the {@code test.environment} system property.
     * Defaults to Spring mode if the property is not set.
     *
     * @return {@code true} if Docker environment, {@code false} for Spring
     */
    public static boolean isDockerEnvironment() {
        String environment = System.getProperty("test.environment", "spring");
        return "docker".equalsIgnoreCase(environment);
    }

    /**
     * Gets the shared Docker network (only available in Docker mode).
     *
     * @return Docker network or null if not in Docker mode
     */
    public static Network getNetwork() {
        return network;
    }

    /**
     * Gets the MongoDB container (only available in Docker mode).
     *
     * @return MongoDB container or null if not in Docker mode
     */
    public static MongoDBContainer getMongoDBContainer() {
        return mongoDBContainer;
    }

    /**
     * Gets the Issuer container (only available in Docker mode).
     *
     * @return Issuer container or null if not in Docker mode
     */
    public static GenericContainer<?> getIssuerContainer() {
        return issuerContainer;
    }

    /**
     * Gets the Holder container (only available in Docker mode).
     *
     * @return Holder container or null if not in Docker mode
     */
    public static GenericContainer<?> getHolderContainer() {
        return holderContainer;
    }

    /**
     * Gets the Verifier container (only available in Docker mode).
     *
     * @return Verifier container or null if not in Docker mode
     */
    public static GenericContainer<?> getVerifierContainer() {
        return verifierContainer;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Extracts the host from a URL.
     *
     * @param url the URL to extract from
     * @return the host
     */
    private String extractHost(String url) {
        // Remove protocol
        var withoutProtocol = url.replaceFirst("^https?://", "");
        // Get host part (before port if present)
        var parts = withoutProtocol.split(":");
        return parts[0];
    }

    /**
     * Extracts the port from a URL.
     *
     * @param url the URL to extract from
     * @return the port number
     */
    private int extractPort(String url) {
        // Remove protocol
        var withoutProtocol = url.replaceFirst("^https?://", "");
        // Get port part
        var parts = withoutProtocol.split(":");
        if (parts.length > 1) {
            return Integer.parseInt(parts[1].replaceAll("/.*", ""));
        }
        // Default ports
        return url.startsWith("https://") ? 443 : 80;
    }
}
