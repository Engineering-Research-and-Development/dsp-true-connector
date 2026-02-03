package it.eng.dcp.e2e;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Test application configuration for Holder+Verifier E2E tests.
 *
 * <p>This is a minimal Spring Boot application used for testing purposes.
 * It scans only holder and verifier packages to avoid bean conflicts with issuer.
 *
 * <p>Note: dcp-common beans are automatically included through holder and verifier
 * auto-configuration dependencies.
 */

@SpringBootApplication
public class HolderVerifierTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(HolderVerifierTestApplication.class, args);
    }
}
