package it.eng.dcp.e2e.docker;

import com.github.dockerjava.api.command.RemoveImageCmd;
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
            System.out.println("Detected Windows OS - configuring Docker environment...");

            // Try to use TCP if DOCKER_HOST is not set and npipe fails
            // This can help in some Docker Desktop configurations
            if (System.getenv("DOCKER_HOST") == null) {
                System.out.println("DOCKER_HOST not set - Testcontainers will auto-detect");
            } else {
                System.out.println("DOCKER_HOST is set to: " + System.getenv("DOCKER_HOST"));
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
     * Finds a JAR file in the target directory matching the given pattern.
     *
     * @param targetPath the path to the target directory
     * @param jarPattern the pattern to match (e.g., "*-exec.jar", "dcp-issuer-exec.jar")
     * @return the path to the found JAR file
     * @throws RuntimeException if no matching JAR is found or multiple matches exist
     */
    private static Path findJarFile(Path targetPath, String jarPattern) {
        try {
            List<Path> matchingJars;
            try (var stream = java.nio.file.Files.list(targetPath)) {
                matchingJars = stream
                    .filter(path -> {
                        String fileName = path.getFileName().toString();
                        // Convert glob pattern to regex
                        String regex = jarPattern.replace("*", ".*").replace("?", ".");
                        return fileName.matches(regex);
                    })
                    .toList();
            }

            if (matchingJars.isEmpty()) {
                throw new RuntimeException("No JAR file found matching pattern: " + jarPattern + " in " + targetPath);
            }
            if (matchingJars.size() > 1) {
                throw new RuntimeException("Multiple JAR files found matching pattern: " + jarPattern + " in " + targetPath
                    + ". Found: " + matchingJars);
            }

            Path jarFile = matchingJars.get(0);
            System.out.println("  Found JAR: " + jarFile.getFileName());
            return jarFile;
        } catch (Exception e) {
            throw new RuntimeException("Failed to find JAR file with pattern: " + jarPattern + " in " + targetPath, e);
        }
    }

    /**
     * Checks if a JAR file exists in the target directory.
     *
     * @param targetPath the path to the target directory
     * @param jarFileName the exact JAR filename to check (e.g., "dcp-issuer-exec.jar")
     * @return true if the JAR exists, false otherwise
     */
    private static boolean jarExists(Path targetPath, String jarFileName) {
        Path jarPath = targetPath.resolve(jarFileName);
        boolean exists = java.nio.file.Files.exists(jarPath);
        if (exists) {
            System.out.println("  ✓ Found existing JAR: " + jarFileName);
        } else {
            System.out.println("  ✗ JAR not found: " + jarFileName);
        }
        return exists;
    }

    /**
     * Executes Maven clean install for a specific module to build a fresh JAR file.
     *
     * @param modulePath the path to the Maven module
     * @param moduleName the name of the module for logging purposes
     * @throws RuntimeException if the Maven build fails
     */
    private static void buildModule(Path modulePath, String moduleName) {
        System.out.println("Building " + moduleName + " module...");
        System.out.println("  Module path: " + modulePath.toAbsolutePath());
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
                    System.out.println("  [" + moduleName + "] " + line);
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

            System.out.println("✓ " + moduleName + " built successfully");
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
        System.out.println("Ensuring base image is available: " + imageName);
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
                    System.out.println("  [Docker] " + line);
                }
            }

            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                System.out.println("⚠ Base image pull timeout - continuing anyway (may already be cached)");
                return;
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                System.out.println("✓ Base image is ready");
            } else {
                System.out.println("⚠ Base image pull returned exit code " + exitCode + " - continuing anyway");
            }
        } catch (Exception e) {
            System.out.println("⚠ Base image pull failed: " + e.getMessage() + " - continuing anyway");
        }
    }

    /**
     * Start all containers before running tests.
     * Builds JAR files with Maven, builds Docker images, starts MongoDB, and starts all DCP application containers.
     */
    @BeforeAll
    static void startContainers() {
        System.out.println("═══════════════════════════════════════════════");
        System.out.println("Starting DCP E2E Test Environment...");
        System.out.println("═══════════════════════════════════════════════");

        // Determine base path for Maven builds and Dockerfiles
        // Current directory is dcp-e2e-tests, parent is dcp (the root folder for all DCP modules)
        Path dcpRoot = Paths.get(".").toAbsolutePath().normalize().getParent();
        System.out.println("DCP Root Path: " + dcpRoot);

        // Build modules with Maven only if JAR files don't exist (optimization for CI/CD)
        System.out.println("\n--- Checking and Building Maven Modules ---");

        // Check if issuer JAR exists, build only if missing
        Path issuerPath = dcpRoot.resolve("dcp-issuer");
        Path issuerTargetPath = issuerPath.resolve("target");
        if (!jarExists(issuerTargetPath, "dcp-issuer-exec.jar")) {
            System.out.println("Issuer JAR not found - building module...");
            buildModule(issuerPath, "Issuer");
        } else {
            System.out.println("✓ Issuer JAR already exists - skipping build");
        }

        // Note: dcp-e2e-tests module is always built by Maven before running this test
        // so we don't need to check/build it here

        System.out.println("--- Maven Build Check Complete ---\n");

        // Ensure base Docker image is available before building custom images
        ensureBaseImageAvailable("eclipse-temurin:17-jre-alpine");

        // Create shared network
        network = Network.newNetwork();
        System.out.println("✓ Created Docker network");

        // Start MongoDB container
        mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:7.0.12"))
            .withNetwork(network)
            .withNetworkAliases("mongodb")
            .withReuse(false);
        mongoDBContainer.start();
        System.out.println("✓ MongoDB started on port: " + mongoDBContainer.getFirstMappedPort());
        System.out.println("  - Container network alias: mongodb");
        System.out.println("  - Internal port (for containers): 27017");
        System.out.println("  - Host-mapped port (for tests): " + mongoDBContainer.getFirstMappedPort());

        createAdnRunHolderVerifierDockerEnvironment(dcpRoot);

        createAndRunIssuerDockerEnvironment(dcpRoot);

        System.out.println("═══════════════════════════════════════════════");
        System.out.println("✓ All containers started successfully");
        System.out.println("═══════════════════════════════════════════════");
    }

    private static void createAdnRunHolderVerifierDockerEnvironment(Path dcpRoot) {
        // Build and start combined Holder+Verifier container
        System.out.println("Building Holder+Verifier Docker image (using DSL)...");
        Path holderVerifierPath = dcpRoot.resolve("dcp-e2e-tests");

        // Use exact JAR name defined by <finalName> in pom.xml
        Path holderVerifierJar = findJarFile(holderVerifierPath.resolve("target"), "dcp-e2e-tests-exec.jar");
        String jarRelativePath = "target/" + holderVerifierJar.getFileName().toString();

        ImageFromDockerfile holderVerifierImage = new ImageFromDockerfile("dcp-holder-verifier-test-e2e", false)
            .withDockerfileFromBuilder(builder -> builder
                .from("eclipse-temurin:17-jre-alpine")
                .workDir("/app")
                .copy(jarRelativePath, "/app/app.jar")
                .copy("src/main/resources/eckey.p12", "/app/eckey.p12")
                .expose(8087)
                .env("JAVA_OPTS", "-Xmx512m -Xms256m")
                .entryPoint("sh", "-c", "java $JAVA_OPTS -jar /app/app.jar")
                .build())
            .withFileFromPath(jarRelativePath, holderVerifierJar)
            .withFileFromPath("src/main/resources/eckey.p12", holderVerifierPath.resolve("src/main/resources/eckey.p12"));
        imagesToCleanup.add("dcp-holder-verifier-test-e2e");

        holderVerifierContainer = new GenericContainer<>(holderVerifierImage)
            .withNetwork(network)
            .withNetworkAliases("holder-verifier")
            .withExposedPorts(8087)
            // Use network alias "mongodb" and internal port 27017 for container-to-container communication
            .withEnv("SPRING_DATA_MONGODB_HOST", "mongodb")
            .withEnv("SPRING_DATA_MONGODB_PORT", "27017")
            .withEnv("SPRING_DATA_MONGODB_DATABASE", "holder-verifier-e2e-test")
            .withEnv("SERVER_PORT", "8087")
            // Override keystore path to use filesystem location instead of classpath
            .withEnv("DCP_KEYSTORE_PATH", "/app/eckey.p12")
            .waitingFor(Wait.forLogMessage(".*Started.*Application.*", 1)
                .withStartupTimeout(Duration.ofMinutes(3)))
            .withReuse(false);
        holderVerifierContainer.start();
        System.out.println("✓ Holder+Verifier started on port: " + holderVerifierContainer.getMappedPort(8087));
    }

    private static void createAndRunIssuerDockerEnvironment(Path dcpRoot) {
        // Build and start Issuer container
        System.out.println("Building Issuer Docker image (using DSL)...");
        Path issuerPath = dcpRoot.resolve("dcp-issuer");

        // Dynamically find the executable JAR (version-agnostic)
        Path issuerJar = findJarFile(issuerPath.resolve("target"), "dcp-issuer-exec.jar");
        String jarRelativePath = "target/" + issuerJar.getFileName().toString();

        ImageFromDockerfile issuerImage = new ImageFromDockerfile("dcp-issuer-e2e-test", false)
            .withDockerfileFromBuilder(builder -> builder
                .from("eclipse-temurin:17-jre-alpine")
                .workDir("/app")
                .copy(jarRelativePath, "/app/dcp-issuer.jar")
                .copy("src/main/resources/eckey-issuer.p12", "/app/eckey-issuer.p12")
                .expose(8084)
                .env("JAVA_OPTS", "-Xmx512m -Xms256m")
                .entryPoint("sh", "-c", "java $JAVA_OPTS -jar /app/dcp-issuer.jar")
                .build())
            .withFileFromPath(jarRelativePath, issuerJar)
            .withFileFromPath("src/main/resources/eckey-issuer.p12", issuerPath.resolve("src/main/resources/eckey-issuer.p12"));
        imagesToCleanup.add("dcp-issuer-e2e-test");

        issuerContainer = new GenericContainer<>(issuerImage)
            .withNetwork(network)
            .withNetworkAliases("issuer")
            .withExposedPorts(8084)
            // Use network alias "mongodb" and internal port 27017 for container-to-container communication
            .withEnv("SPRING_DATA_MONGODB_HOST", "mongodb")
            .withEnv("SPRING_DATA_MONGODB_PORT", "27017")
            .withEnv("SPRING_DATA_MONGODB_DATABASE", "issuer-e2e-test")
            .withEnv("SERVER_PORT", "8084")
            // Override keystore path to use filesystem location instead of classpath
            .withEnv("DCP_KEYSTORE_PATH", "/app/eckey-issuer.p12")
            .waitingFor(Wait.forLogMessage(".*Started IssuerApplication.*", 1)
                .withStartupTimeout(Duration.ofMinutes(3)))
            .withReuse(false);

        issuerContainer.start();
        System.out.println("✓ Issuer started on port: " + issuerContainer.getMappedPort(8084));
    }

    /**
     * Stop all containers and clean up Docker images after all tests complete.
     */
    @AfterAll
    static void stopContainersAndCleanup() {
        System.out.println("═══════════════════════════════════════════════");
        System.out.println("Stopping containers and cleaning up...");

        // Stop containers
        if (holderVerifierContainer != null) {
            holderVerifierContainer.stop();
            System.out.println("✓ Holder+Verifier container stopped");
        }
        if (issuerContainer != null) {
            issuerContainer.stop();
            System.out.println("✓ Issuer container stopped");
        }
        if (mongoDBContainer != null) {
            mongoDBContainer.stop();
            System.out.println("✓ MongoDB container stopped");
        }
        if (network != null) {
            network.close();
            System.out.println("✓ Network closed");
        }

        // Clean up Docker images
        try {
            for (String imageName : imagesToCleanup) {
                try {
                    RemoveImageCmd removeImageCmd = issuerContainer.getDockerClient()
                        .removeImageCmd(imageName)
                        .withForce(true);
                    removeImageCmd.exec();
                    System.out.println("✓ Removed Docker image: " + imageName);
                } catch (Exception e) {
                    System.err.println("⚠ Failed to remove image " + imageName + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("⚠ Error during image cleanup: " + e.getMessage());
        }

        System.out.println("═══════════════════════════════════════════════");
        System.out.println("✓ Cleanup complete");
        System.out.println("═══════════════════════════════════════════════");
    }

    /**
     * Create HTTP clients before each test.
     * This runs before each test method.
     */
    @BeforeEach
    void setupClients() {
        int issuerPort = issuerContainer.getMappedPort(8084);
        int holderVerifierPort = holderVerifierContainer.getMappedPort(8087);

        System.out.println("───────────────────────────────────────────────");
        System.out.println("Test URLs:");
        System.out.println("  Issuer: http://localhost:" + issuerPort);
        System.out.println("  Holder+Verifier: http://localhost:" + holderVerifierPort);
        System.out.println("───────────────────────────────────────────────");

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


