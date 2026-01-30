package it.eng.dcp.e2e;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Test application configuration for Holder+Verifier E2E tests.
 *
 * <p>This Spring Boot application relies on auto-configuration to load DCP Holder
 * and Verifier beans from their respective auto-configurable JARs. The auto-configuration
 * classes ({@code DcpHolderAutoConfiguration} and {@code DcpVerifierAutoConfiguration})
 * are automatically discovered via Spring Boot's auto-configuration mechanism.
 *
 * <p>The issuer module is explicitly excluded to prevent bean conflicts, and
 * DataSourceAutoConfiguration is excluded as this test application does not require a database.
 *
 * <p>Note: Do not use {@code scanBasePackages} here, as it disables Spring Boot's
 * auto-configuration discovery mechanism.
 */
@SpringBootApplication
public class HolderVerifierTestApplication {

    /**
     * Main entry point for the Holder+Verifier test application.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(HolderVerifierTestApplication.class, args);
    }
}
