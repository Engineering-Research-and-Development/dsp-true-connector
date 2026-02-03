package it.eng.dcp.e2e.spring;

import it.eng.dcp.e2e.HolderVerifierTestApplication;
import it.eng.dcp.issuer.IssuerApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for running multiple DCP applications together.
 * Tests that Holder+Verifier and Issuer applications can coexist without bean conflicts.
 */
@Testcontainers
public class SpringContextTestE2E {

    /**
     * MongoDB TestContainer - shared across all tests in this class.
     * Docker must be running for this test to work.
     * Started in static block to ensure availability before Spring contexts are created.
     */
    protected static final MongoDBContainer mongoDBContainer =
            new MongoDBContainer(DockerImageName.parse("mongo:7.0.12"))
                    .withReuse(false);

    static {
        // Start container in static block like BaseIntegrationTest
        mongoDBContainer.start();
        System.out.println("MongoDB TestContainer started on " +
                mongoDBContainer.getHost() + ":" + mongoDBContainer.getFirstMappedPort());
    }

    @Test
    public void runTests() {
        ConfigurableApplicationContext app1Context = null;
        ConfigurableApplicationContext app2Context = null;

        // Get MongoDB connection details from TestContainer
        String mongoHost = mongoDBContainer.getHost();
        Integer mongoPort = mongoDBContainer.getFirstMappedPort();

        System.out.println("Using MongoDB TestContainer at " + mongoHost + ":" + mongoPort);

        try {
            // Start first application (Holder+Verifier) on port 8081
            // Use command-line arguments (--key=value) instead of properties()
            // to ensure they override properties packaged in JAR files
            app1Context = new SpringApplicationBuilder(HolderVerifierTestApplication.class)
                    .run(
                            "--server.port=8081",
                            "--spring.profiles.active=holderverifier",
                            "--spring.data.mongodb.host=" + mongoHost,
                            "--spring.data.mongodb.port=" + mongoPort,
                            "--spring.data.mongodb.authentication-database=admin",
                            "--spring.data.mongodb.database=holder_verifier_test"
                    );

            System.out.println("✅ App1 (Holder+Verifier) started on port 8081");

            // Start second application (Issuer) on port 8082
            app2Context = new SpringApplicationBuilder(IssuerApplication.class)
                    .run(
                            "--server.port=8082",
                            "--spring.profiles.active=issuer",
                            "--spring.main.allow-bean-definition-overriding=true",
                            "--spring.data.mongodb.host=" + mongoHost,
                            "--spring.data.mongodb.port=" + mongoPort,
                            "--spring.data.mongodb.authentication-database=admin",
                            "--spring.data.mongodb.database=issuer_test"
                    );

            System.out.println("✅ App2 (Issuer) started on port 8082");

            // Perform interaction tests using RestTemplate
            RestTemplate restTemplate = new RestTemplate();

            // Test that app1 serves the DID document on its well-known endpoint
            String responseApp1 = restTemplate.getForObject("http://localhost:8081/.well-known/did.json", String.class);
            System.out.println("App1 (Holder+Verifier) DID document: " + responseApp1);

            // Verify the response is not null and contains expected DID structure
            assert responseApp1 != null && responseApp1.contains("\"id\":");
            assertTrue(responseApp1.contains("holder"));

            // Test that app2 serves the DID document on its well-known endpoint
            String responseApp2 = restTemplate.getForObject("http://localhost:8082/.well-known/did.json", String.class);
            System.out.println("App2 (Issuer) DID document: " + responseApp2);

            // Verify the response is not null and contains expected DID structure
            assert responseApp2 != null && responseApp2.contains("\"id\":");
            assertTrue(responseApp2.contains("issuer"));

            System.out.println("✅ Both applications started successfully without bean conflicts!");
            System.out.println("✅ Both applications serve DID documents on /.well-known/did.json endpoint!");
        } finally {
            if (app2Context != null) {
                app2Context.close();
            }
            if (app1Context != null) {
                app1Context.close();
            }
        }
    }
}
