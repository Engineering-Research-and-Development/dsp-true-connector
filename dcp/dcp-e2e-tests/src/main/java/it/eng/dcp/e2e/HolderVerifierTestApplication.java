package it.eng.dcp.e2e;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * Test application configuration for Holder+Verifier E2E tests.
 *
 * <p>This is a minimal Spring Boot application used for testing purposes.
 * It scans only holder and verifier packages to avoid bean conflicts with issuer.
 *
 * <p>Note: dcp-common beans are automatically included through holder and verifier
 * auto-configuration dependencies.
 *
 * <p>Spring Security is on the classpath only because the e2e module also runs the Issuer, which needs it
 * for its administration API. The Holder and Verifier keep their own DCP token checks, so the Spring Boot
 * security auto-configuration is excluded here instead of protecting every endpoint with a generated user.
 */

@SpringBootApplication(exclude = {
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class,
        ManagementWebSecurityAutoConfiguration.class
})
public class HolderVerifierTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(HolderVerifierTestApplication.class, args);
    }
}
