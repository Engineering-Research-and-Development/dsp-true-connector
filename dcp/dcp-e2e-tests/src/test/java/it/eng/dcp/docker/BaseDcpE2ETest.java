package it.eng.dcp.docker;

import com.github.dockerjava.api.command.RemoveImageCmd;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


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
@Slf4j
@Testcontainers
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

    /**
     * List to track Docker image names for cleanup.
     */
    protected static final List<String> imagesToCleanup = new ArrayList<>();

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
     * Executes Maven clean install for a specific module to build a fresh JAR file.
     *
     * @param modulePath the path to the Maven module
     * @param moduleName the name of the module for logging purposes
     * @throws RuntimeException if the Maven build fails
     */
    private static void buildModule(Path modulePath, String moduleName) {
        log.info("Building {} module...", moduleName);
        log.info("  Module path: {}", modulePath.toAbsolutePath());
        try {
            // Determine the Maven command based on OS
            String os = System.getProperty("os.name").toLowerCase();
            String mavenCmd = os.contains("win") ? "mvn.cmd" : "mvn";

            ProcessBuilder processBuilder = new ProcessBuilder(
                mavenCmd, "clean", "package", "-DskipTests"
            );
            processBuilder.directory(modulePath.toFile());
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            // Read and display the Maven output
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("  [{}] {}", moduleName, line);
                }
            }

            // Wait for the process to complete with a timeout
            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Maven build timeout for " + moduleName);
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new RuntimeException("Maven build failed for " + moduleName + " with exit code: " + exitCode);
            }

            log.info("✓ {} built successfully", moduleName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build " + moduleName + ": " + e.getMessage(), e);
        }
    }

    /**
     * Ensures the base Docker image is available locally by attempting to pull it.
     * This prevents Testcontainers from hanging during the pre-emptive image check.
     *
     * <p>Testcontainers performs a pre-emptive check for base images referenced in Dockerfiles.
     * If the image is not available locally, it will attempt to pull it, which can hang
     * or timeout on some systems (especially Windows). This method explicitly pulls the
     * image using the Docker CLI before Testcontainers attempts its check.
     *
     * @param imageName the Docker image name to pull (e.g., "eclipse-temurin:17-jre-alpine")
     */
    private static void ensureBaseImageAvailable(String imageName) {
        log.info("Ensuring base image is available: {}", imageName);
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String dockerCmd = os.contains("win") ? "docker" : "docker";

            ProcessBuilder processBuilder = new ProcessBuilder(dockerCmd, "pull", imageName);
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            // Read output (optional, for debugging)
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("  [Docker] {}", line);
                }
            }

            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                log.warn("⚠ Base image pull timeout - continuing anyway (may already be cached)");
                return;
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.info("✓ Base image is ready");
            } else {
                log.warn("⚠ Base image pull returned exit code {} - continuing anyway", exitCode);
            }
        } catch (Exception e) {
            log.error("⚠ Base image pull failed: {} - continuing anyway", e.getMessage());
        }
    }

    /**
     * Start all containers before running tests.
     * Builds JAR files with Maven, builds Docker images, starts MongoDB, and starts all DCP application containers.
     */
    @BeforeAll
    static void startContainers() {
        log.info("═══════════════════════════════════════════════");
        log.info("Starting DCP E2E Test Environment...");
        log.info("═══════════════════════════════════════════════");

        // Determine base path for Maven builds and Dockerfiles
        // Current directory is dcp-e2e-tests, parent is dcp (the root folder for all DCP modules)
        Path dcpRoot = Paths.get(".").toAbsolutePath().normalize().getParent();
        log.info("DCP Root Path: {}", dcpRoot);

        // Build all modules with Maven to create fresh JAR files
        log.info("\n--- Building Maven Modules ---");
        buildModule(dcpRoot.resolve("dcp-issuer"), "Issuer");
        buildModule(dcpRoot.resolve("dcp-e2e-tests"), "HolderVerifier-Test");
        log.info("--- Maven Builds Complete ---");

        // Ensure base Docker image is available before building custom images
        ensureBaseImageAvailable("eclipse-temurin:17-jre-alpine");

        // Create shared network
        network = Network.newNetwork();
        log.info("✓ Created Docker network");

        // Start MongoDB container
        mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:7.0.12"))
            .withNetwork(network)
            .withNetworkAliases("mongodb")
            .withReuse(false);
        mongoDBContainer.start();
        log.info("✓ MongoDB started on port: " + mongoDBContainer.getFirstMappedPort());

        // Build and start Issuer container
        log.info("Building Issuer Docker image (using DSL)...");
        Path issuerPath = dcpRoot.resolve("dcp-issuer");
        ImageFromDockerfile issuerImage = new ImageFromDockerfile("dcp-issuer-e2e-test", false)
            .withDockerfileFromBuilder(builder -> builder
                .from("eclipse-temurin:17-jre-alpine")
                .workDir("/app")
                .copy("target/dcp-issuer-exec.jar", "/app/dcp-issuer.jar")
                .copy("src/main/resources/eckey-issuer.p12", "/app/eckey-issuer.p12")
                .expose(8084)
                .env("JAVA_OPTS", "-Xmx512m -Xms256m")
                .entryPoint("sh", "-c", "java $JAVA_OPTS -jar /app/dcp-issuer.jar")
                .build())
            .withFileFromPath("target/dcp-issuer-exec.jar", issuerPath.resolve("target/dcp-issuer-exec.jar"))
            .withFileFromPath("src/main/resources/eckey-issuer.p12", issuerPath.resolve("src/main/resources/eckey-issuer.p12"));
        imagesToCleanup.add("dcp-issuer-e2e-test");

        issuerContainer = new GenericContainer<>(issuerImage)
            .withNetwork(network)
            .withNetworkAliases("issuer")
            .withExposedPorts(8084)
            .withEnv("SPRING_DATA_MONGODB_HOST", "mongodb")
            .withEnv("SPRING_DATA_MONGODB_PORT", "27017")
            .withEnv("SPRING_DATA_MONGODB_DATABASE", "issuer-e2e-test")
            .withEnv("SERVER_PORT", "8084")
            .waitingFor(Wait.forLogMessage(".*Started IssuerApplication.*", 1)
                .withStartupTimeout(Duration.ofMinutes(3)))
            .withReuse(false);

        issuerContainer.start();
        log.info("✓ Issuer started on port: " + issuerContainer.getMappedPort(8084));


        // Build and start combined Holder+Verifier container
        log.info("Building Holder+Verifier Docker image (using DSL)...");
        Path holderVerifierPath = dcpRoot.resolve("dcp-e2e-tests");
        ImageFromDockerfile holderVerifierImage = new ImageFromDockerfile("dcp-holder-verifier-test-e2e", false)
            .withDockerfileFromBuilder(builder -> builder
                .from("eclipse-temurin:17-jre-alpine")
                .workDir("/app")
                .copy("target/dcp-holder-verifier-test.jar", "/app/dcp-holder-verifier-test.jar")
                .expose(8087)
                .env("JAVA_OPTS", "-Xmx512m -Xms256m")
                .entryPoint("sh", "-c", "java $JAVA_OPTS -jar /app/dcp-holder-verifier-test.jar")
                .build())
            .withFileFromPath("target/dcp-holder-verifier-test.jar", holderVerifierPath.resolve("target/dcp-holder-verifier-test.jar"));
        imagesToCleanup.add("dcp-holder-verifier-test-e2e");

        holderVerifierContainer = new GenericContainer<>(holderVerifierImage)
            .withNetwork(network)
            .withNetworkAliases("holder-verifier")
            .withExposedPorts(8087)
            .withEnv("SPRING_DATA_MONGODB_HOST", "mongodb")
            .withEnv("SPRING_DATA_MONGODB_PORT", "27017")
            .withEnv("SPRING_DATA_MONGODB_DATABASE", "holder-verifier-e2e-test")
            .withEnv("SERVER_PORT", "8087")
            .waitingFor(Wait.forLogMessage(".*Started.*Application.*", 1)
                .withStartupTimeout(Duration.ofMinutes(3)))
            .withReuse(false);
        holderVerifierContainer.start();
        log.info("✓ Holder+Verifier started on port: " + holderVerifierContainer.getMappedPort(8087));

        log.info("═══════════════════════════════════════════════");
        log.info("✓ All containers started successfully");
        log.info("═══════════════════════════════════════════════");
    }

    /**
     * Stop all containers and clean up Docker images after all tests complete.
     */
    @AfterAll
    static void stopContainersAndCleanup() {
        log.info("═══════════════════════════════════════════════");
        log.info("Stopping containers and cleaning up...");

        // Stop containers
        if (holderVerifierContainer != null) {
            holderVerifierContainer.stop();
            log.info("✓ Holder+Verifier container stopped");
        }
        if (issuerContainer != null) {
            issuerContainer.stop();
            log.info("✓ Issuer container stopped");
        }
        if (mongoDBContainer != null) {
            mongoDBContainer.stop();
            log.info("✓ MongoDB container stopped");
        }
        if (network != null) {
            network.close();
            log.info("✓ Network closed");
        }

        // Clean up Docker images
        try {
            for (String imageName : imagesToCleanup) {
                try {
                    RemoveImageCmd removeImageCmd = issuerContainer.getDockerClient()
                        .removeImageCmd(imageName)
                        .withForce(true);
                    removeImageCmd.exec();
                    log.info("✓ Removed Docker image: " + imageName);
                } catch (Exception e) {
                    log.error("⚠ Failed to remove image " + imageName + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("⚠ Error during image cleanup: " + e.getMessage());
        }

        log.info("═══════════════════════════════════════════════");
        log.info("✓ Cleanup complete");
        log.info("═══════════════════════════════════════════════");
    }

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
        log.info("  Issuer: http://localhost:" + issuerPort);
        log.info("  Holder+Verifier: http://localhost:" + holderVerifierPort);
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

