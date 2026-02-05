package it.eng.dcp.e2e.spring;

import it.eng.dcp.e2e.HolderVerifierTestApplication;
import it.eng.dcp.e2e.common.DcpTestEnvironment;
import it.eng.dcp.e2e.common.SpringTestEnvironment;
import it.eng.dcp.e2e.tests.AbstractDcpE2ETest;
import it.eng.dcp.issuer.IssuerApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Runtime-agnostic DCP tests running against Spring Boot applications.
 *
 * <p>This test class runs all tests defined in {@link AbstractDcpE2ETest}
 * against applications started as Spring Boot contexts (no Docker containers
 * for the applications, only for MongoDB).
 *
 * <p><strong>Configuration:</strong>
 * <ul>
 *   <li>Holder+Verifier: port 8081</li>
 *   <li>Issuer: port 8082</li>
 *   <li>MongoDB: Testcontainer</li>
 * </ul>
 */
@Testcontainers
public class DcpSpringTestE2E extends AbstractDcpE2ETest {

    private static MongoDBContainer mongoDBContainer;
    private static ConfigurableApplicationContext holderVerifierContext;
    private static ConfigurableApplicationContext issuerContext;
    private static SpringTestEnvironment environment;

    private static final int HOLDER_VERIFIER_PORT = 8081;
    private static final int ISSUER_PORT = 8082;

    @BeforeAll
    static void startApplications() {
        System.out.println("═══════════════════════════════════════════════");
        System.out.println("Starting Spring Boot Test Environment...");
        System.out.println("═══════════════════════════════════════════════");

        // Start MongoDB container
        mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:7.0.12"))
            .withReuse(false);
        mongoDBContainer.start();

        String mongoHost = mongoDBContainer.getHost();
        Integer mongoPort = mongoDBContainer.getFirstMappedPort();

        System.out.println("✓ MongoDB started at " + mongoHost + ":" + mongoPort);

        // Start Holder+Verifier application
        holderVerifierContext = new SpringApplicationBuilder(HolderVerifierTestApplication.class)
            .run(
                "--server.port=" + HOLDER_VERIFIER_PORT,
                "--spring.profiles.active=holderverifier",
                "--spring.data.mongodb.host=" + mongoHost,
                "--spring.data.mongodb.port=" + mongoPort,
                "--spring.data.mongodb.authentication-database=admin",
                "--spring.data.mongodb.database=holder_verifier_test"
            );

        System.out.println("✓ Holder+Verifier started on port " + HOLDER_VERIFIER_PORT);

        // Start Issuer application
        issuerContext = new SpringApplicationBuilder(IssuerApplication.class)
            .run(
                "--server.port=" + ISSUER_PORT,
                "--spring.profiles.active=issuer",
                "--spring.main.allow-bean-definition-overriding=true",
                "--spring.data.mongodb.host=" + mongoHost,
                "--spring.data.mongodb.port=" + mongoPort,
                "--spring.data.mongodb.authentication-database=admin",
                "--spring.data.mongodb.database=issuer_test"
            );

        System.out.println("✓ Issuer started on port " + ISSUER_PORT);

        // Create environment wrapper
        environment = new SpringTestEnvironment(
            holderVerifierContext,
            issuerContext,
            HOLDER_VERIFIER_PORT,
            ISSUER_PORT
        );

        System.out.println("═══════════════════════════════════════════════");
        System.out.println("✓ Spring Boot Environment Ready");
        System.out.println("═══════════════════════════════════════════════\n");
    }

    @AfterAll
    static void stopApplications() {
        System.out.println("═══════════════════════════════════════════════");
        System.out.println("Stopping Spring Boot Test Environment...");
        System.out.println("═══════════════════════════════════════════════");

        if (issuerContext != null) {
            issuerContext.close();
            System.out.println("✓ Issuer stopped");
        }

        if (holderVerifierContext != null) {
            holderVerifierContext.close();
            System.out.println("✓ Holder+Verifier stopped");
        }

        if (mongoDBContainer != null) {
            mongoDBContainer.stop();
            System.out.println("✓ MongoDB stopped");
        }

        System.out.println("═══════════════════════════════════════════════");
        System.out.println("✓ Cleanup Complete");
        System.out.println("═══════════════════════════════════════════════");
    }

    @Override
    protected DcpTestEnvironment getEnvironment() {
        return environment;
    }
}
