package it.eng.dcp.e2e.environment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Singleton manager for Docker test infrastructure.
 *
 * <p>This class ensures that Docker containers are created ONCE and shared
 * across ALL test classes, improving test execution performance significantly.
 *
 * <p><strong>Benefits:</strong>
 * <ul>
 *   <li>Containers built once, reused by all test classes</li>
 *   <li>Faster test execution (no rebuild per test class)</li>
 *   <li>Lower resource usage</li>
 *   <li>Automatic cleanup via shutdown hook</li>
 * </ul>
 *
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * // In your test class:
 * @BeforeAll
 * static void setup() {
 *     SharedDockerEnvironment.getInstance().ensureStarted();
 * }
 * }</pre>
 */
@Slf4j
public class SharedDockerEnvironment {

    private static final SharedDockerEnvironment INSTANCE = new SharedDockerEnvironment();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final Object lock = new Object();

    // Docker Infrastructure
    private Network network;
    private MongoDBContainer mongoDBContainer;
    private GenericContainer<?> issuerContainer;
    private GenericContainer<?> holderVerifierContainer;
    private final List<String> imagesToCleanup = new ArrayList<>();

    private SharedDockerEnvironment() {
        // Private constructor for singleton
        // Register shutdown hook for cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(this::cleanup));
    }

    /**
     * Get the singleton instance.
     *
     * @return the shared Docker environment instance
     */
    public static SharedDockerEnvironment getInstance() {
        return INSTANCE;
    }

    /**
     * Ensure containers are started. Safe to call multiple times - will only start once.
     */
    public void ensureStarted() {
        if (started.get()) {
            log.info("Docker environment already started - reusing existing containers");
            return;
        }

        synchronized (lock) {
            if (started.get()) {
                return;
            }

            log.info("═══════════════════════════════════════════════");
            log.info("Starting SHARED Docker Environment (will be reused by all tests)");
            log.info("═══════════════════════════════════════════════");

            startContainers();
            started.set(true);

            log.info("═══════════════════════════════════════════════");
            log.info("✓ Shared Docker Environment Ready");
            log.info("═══════════════════════════════════════════════");
        }
    }

    private void startContainers() {
        Path dcpRoot = Paths.get(".").toAbsolutePath().normalize().getParent();
        log.info("DCP Root Path: {}", dcpRoot);

        // Build modules if needed
        log.info("\n--- Checking and Building Maven Modules ---");
        checkAndBuildIssuer(dcpRoot);
        log.info("--- Maven Build Check Complete ---\n");

        // Ensure base image available
        ensureBaseImageAvailable("eclipse-temurin:17-jre-alpine");

        // Create network
        network = Network.newNetwork();
        log.info("✓ Created Docker network");

        // Start MongoDB
        mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:7.0.12"))
            .withNetwork(network)
            .withNetworkAliases("mongodb")
            .withReuse(false);
        mongoDBContainer.start();
        log.info("✓ MongoDB started on port: {}", mongoDBContainer.getFirstMappedPort());

        // Start Holder+Verifier
        createAndStartHolderVerifierContainer(dcpRoot);

        // Start Issuer
        createAndStartIssuerContainer(dcpRoot);
    }

    private void checkAndBuildIssuer(Path dcpRoot) {
        Path issuerPath = dcpRoot.resolve("dcp-issuer");
        Path issuerTargetPath = issuerPath.resolve("target");

        if (!jarExists(issuerTargetPath, "dcp-issuer-exec.jar")) {
            log.info("Issuer JAR not found - building module...");
            buildModule(issuerPath, "Issuer");
        } else {
            log.info("✓ Issuer JAR already exists - skipping build");
        }
    }

    private boolean jarExists(Path targetPath, String jarFileName) {
        Path jarPath = targetPath.resolve(jarFileName);
        boolean exists = java.nio.file.Files.exists(jarPath);
        if (exists) {
            log.info("  ✓ Found existing JAR: {}", jarFileName);
        } else {
            log.info("  ✗ JAR not found: {}", jarFileName);
        }
        return exists;
    }

    private void buildModule(Path modulePath, String moduleName) {
        log.info("Building {} module...", moduleName);
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String mavenCmd = os.contains("win") ? "mvn.cmd" : "mvn";

            ProcessBuilder processBuilder = new ProcessBuilder(mavenCmd, "clean", "package", "-DskipTests");
            processBuilder.directory(modulePath.toFile());
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("  [{}] {}", moduleName, line);
                }
            }

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
            log.error("Failed to build {}: {}", moduleName, e.getMessage(), e);
            throw new RuntimeException("Failed to build " + moduleName + ": " + e.getMessage(), e);
        }
    }

    private void ensureBaseImageAvailable(String imageName) {
        log.info("Ensuring base image is available: {}", imageName);
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String dockerCmd = "docker";

            ProcessBuilder processBuilder = new ProcessBuilder(dockerCmd, "pull", imageName);
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("  [Docker] {}", line);
                }
            }

            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                log.warn("⚠ Base image pull timeout - continuing anyway");
                return;
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.info("✓ Base image is ready");
            } else {
                log.warn("⚠ Base image pull returned exit code {}", exitCode);
            }
        } catch (Exception e) {
            log.warn("⚠ Base image pull failed: {}", e.getMessage());
        }
    }

    private void createAndStartHolderVerifierContainer(Path dcpRoot) {
        log.info("Building Holder+Verifier Docker image...");
        Path holderVerifierPath = dcpRoot.resolve("dcp-e2e-tests");

        Path holderVerifierJar = findJarFile(holderVerifierPath.resolve("target"), "dcp-e2e-tests-exec.jar");
        String jarRelativePath = "target/" + holderVerifierJar.getFileName().toString();

        ImageFromDockerfile holderVerifierImage = new ImageFromDockerfile("dcp-holder-verifier-test-e2e", false)
            .withDockerfileFromBuilder(builder -> builder
                .from("eclipse-temurin:17-jre-alpine")
                .workDir("/app")
                .copy(jarRelativePath, "/app/app.jar")
                .copy("src/test/resources/eckey.p12", "/app/eckey.p12")
                .copy("src/test/resources/application-holderverifier.properties", "/config/application-holderverifier.properties")
                .expose(8081)
                .env("JAVA_OPTS", "-Xmx512m -Xms256m")
                .entryPoint("sh", "-c", "java $JAVA_OPTS -jar /app/app.jar")
                .build())
            .withFileFromPath(jarRelativePath, holderVerifierJar)
            .withFileFromPath("src/test/resources/eckey.p12", holderVerifierPath.resolve("src/test/resources/eckey.p12"))
            .withFileFromPath("src/test/resources/application-holderverifier.properties", holderVerifierPath.resolve("src/test/resources/application-holderverifier.properties"));
        imagesToCleanup.add("dcp-holder-verifier-test-e2e");

        holderVerifierContainer = new GenericContainer<>(holderVerifierImage)
            .withNetwork(network)
            .withNetworkAliases("holder-verifier")
            .withExposedPorts(8081)
            .withExtraHost("localhost", "host-gateway")  // Enable access to host machine's localhost
            // MongoDB connection details are dynamic and not known at build time
            .withEnv("SPRING_DATA_MONGODB_HOST", "mongodb")
            .withEnv("SPRING_DATA_MONGODB_PORT", "27017")
            // Load static configuration from the application-holderverifier.properties file
            .withEnv("SPRING_CONFIG_ADDITIONAL_LOCATION", "optional:file:/config/")
            .withEnv("SPRING_PROFILES_ACTIVE", "holderverifier")
            .waitingFor(Wait.forLogMessage(".*Started.*Application.*", 1)
                .withStartupTimeout(Duration.ofMinutes(3)))
            .withReuse(false);

        // Bind host port 8081 to container port 8081
        holderVerifierContainer.setPortBindings(List.of("8081:8081"));
        holderVerifierContainer.start();
        log.info("✓ Holder+Verifier started on port: 8081");
    }

    private void createAndStartIssuerContainer(Path dcpRoot) {
        log.info("Building Issuer Docker image...");
        Path issuerPath = dcpRoot.resolve("dcp-issuer");
        Path e2eTestsPath = dcpRoot.resolve("dcp-e2e-tests");

        Path issuerJar = findJarFile(issuerPath.resolve("target"), "dcp-issuer-exec.jar");
        String jarRelativePath = "target/" + issuerJar.getFileName().toString();

        ImageFromDockerfile issuerImage = new ImageFromDockerfile("dcp-issuer-e2e-test", false)
            .withDockerfileFromBuilder(builder -> builder
                .from("eclipse-temurin:17-jre-alpine")
                .workDir("/app")
                .copy(jarRelativePath, "/app/dcp-issuer.jar")
                .copy("src/test/resources/eckey-issuer.p12", "/app/eckey-issuer.p12")
                .copy("src/test/resources/application-issuer.properties", "/config/application-issuer.properties")
                .copy("src/test/resources/credential-metadata-configuration.properties", "/config/credential-metadata-configuration.properties")
                .expose(8082)
                .env("JAVA_OPTS", "-Xmx512m -Xms256m")
                .entryPoint("sh", "-c", "java $JAVA_OPTS -jar /app/dcp-issuer.jar")
                .build())
            .withFileFromPath(jarRelativePath, issuerJar)
            .withFileFromPath("src/test/resources/eckey-issuer.p12", e2eTestsPath.resolve("src/test/resources/eckey-issuer.p12"))
            .withFileFromPath("src/test/resources/application-issuer.properties", e2eTestsPath.resolve("src/test/resources/application-issuer.properties"))
            .withFileFromPath("src/test/resources/credential-metadata-configuration.properties", issuerPath.resolve("src/test/resources/credential-metadata-configuration.properties"));
        imagesToCleanup.add("dcp-issuer-e2e-test");

        issuerContainer = new GenericContainer<>(issuerImage)
            .withNetwork(network)
            .withNetworkAliases("issuer")
            .withExposedPorts(8082)
            .withExtraHost("localhost", "host-gateway")  // Enable access to host machine's localhost
            // MongoDB connection details are dynamic and not known at build time
            .withEnv("SPRING_DATA_MONGODB_HOST", "mongodb")
            .withEnv("SPRING_DATA_MONGODB_PORT", "27017")
            // Load static configuration from the application-issuer.properties file
            .withEnv("SPRING_CONFIG_ADDITIONAL_LOCATION", "optional:file:/config/")
            .withEnv("SPRING_PROFILES_ACTIVE", "issuer")
            .waitingFor(Wait.forLogMessage(".*Started IssuerApplication.*", 1)
                .withStartupTimeout(Duration.ofMinutes(3)))
            .withReuse(false);

        // Bind host port 8082 to container port 8082
        issuerContainer.setPortBindings(List.of("8082:8082"));
        issuerContainer.start();
        log.info("✓ Issuer started on port: 8082");
    }

    private Path findJarFile(Path targetPath, String jarPattern) {
        try {
            List<Path> matchingJars;
            try (var stream = java.nio.file.Files.list(targetPath)) {
                matchingJars = stream
                    .filter(path -> {
                        String fileName = path.getFileName().toString();
                        String regex = jarPattern.replace("*", ".*").replace("?", ".");
                        return fileName.matches(regex);
                    })
                    .toList();
            }

            if (matchingJars.isEmpty()) {
                throw new RuntimeException("No JAR file found matching pattern: " + jarPattern + " in " + targetPath);
            }
            if (matchingJars.size() > 1) {
                throw new RuntimeException("Multiple JAR files found matching pattern: " + jarPattern);
            }

            Path jarFile = matchingJars.get(0);
            log.info("  Found JAR: {}", jarFile.getFileName());
            return jarFile;
        } catch (Exception e) {
            log.error("Failed to find JAR file: {}", jarPattern, e);
            throw new RuntimeException("Failed to find JAR file: " + jarPattern, e);
        }
    }

    /**
     * Cleanup Docker resources. Called automatically via shutdown hook.
     */
    private void cleanup() {
        if (!started.get()) {
            return;
        }

        log.info("\n═══════════════════════════════════════════════");
        log.info("Cleaning up Shared Docker Environment...");
        log.info("═══════════════════════════════════════════════");

        try {
            if (holderVerifierContainer != null) {
                holderVerifierContainer.stop();
                log.info("✓ Holder+Verifier container stopped");
            }
        } catch (Exception e) {
            log.error("⚠ Error stopping Holder+Verifier: {}", e.getMessage(), e);
        }

        try {
            if (issuerContainer != null) {
                issuerContainer.stop();
                log.info("✓ Issuer container stopped");
            }
        } catch (Exception e) {
            log.error("⚠ Error stopping Issuer: {}", e.getMessage(), e);
        }

        try {
            if (mongoDBContainer != null) {
                mongoDBContainer.stop();
                log.info("✓ MongoDB container stopped");
            }
        } catch (Exception e) {
            log.error("⚠ Error stopping MongoDB: {}", e.getMessage(), e);
        }

        try {
            if (network != null) {
                network.close();
                log.info("✓ Network closed");
            }
        } catch (Exception e) {
            log.error("⚠ Error closing network: {}", e.getMessage(), e);
        }

        // Clean up Docker images
        cleanupDockerImages();

        log.info("═══════════════════════════════════════════════");
        log.info("✓ Shared Docker Environment Cleanup Complete");
        log.info("═══════════════════════════════════════════════");
    }

    private void cleanupDockerImages() {
        if (issuerContainer == null || imagesToCleanup.isEmpty()) {
            return;
        }

        try {
            for (String imageName : imagesToCleanup) {
                try {
                    issuerContainer.getDockerClient()
                        .removeImageCmd(imageName)
                        .withForce(true)
                        .exec();
                    log.info("✓ Removed Docker image: {}", imageName);
                } catch (Exception e) {
                    log.error("⚠ Failed to remove image {}: {}", imageName, e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("⚠ Error during image cleanup: {}", e.getMessage(), e);
        }
    }

    // Getters for containers

    public GenericContainer<?> getIssuerContainer() {
        if (!started.get()) {
            throw new IllegalStateException("Docker environment not started. Call ensureStarted() first.");
        }
        return issuerContainer;
    }

    public GenericContainer<?> getHolderVerifierContainer() {
        if (!started.get()) {
            throw new IllegalStateException("Docker environment not started. Call ensureStarted() first.");
        }
        return holderVerifierContainer;
    }

    public MongoDBContainer getMongoDBContainer() {
        if (!started.get()) {
            throw new IllegalStateException("Docker environment not started. Call ensureStarted() first.");
        }
        return mongoDBContainer;
    }

    public Network getNetwork() {
        if (!started.get()) {
            throw new IllegalStateException("Docker environment not started. Call ensureStarted() first.");
        }
        return network;
    }

    /**
     * Create REST client for Issuer.
     */
    public RestTemplate createIssuerClient() {
        return new RestTemplateBuilder()
            .rootUri("http://localhost:" + issuerContainer.getMappedPort(8084))
            .build();
    }

    /**
     * Create REST client for Holder.
     */
    public RestTemplate createHolderClient() {
        return new RestTemplateBuilder()
            .rootUri("http://localhost:" + holderVerifierContainer.getMappedPort(8087))
            .build();
    }

    /**
     * Create REST client for Verifier.
     */
    public RestTemplate createVerifierClient() {
        return new RestTemplateBuilder()
            .rootUri("http://localhost:" + holderVerifierContainer.getMappedPort(8087))
            .build();
    }
}
