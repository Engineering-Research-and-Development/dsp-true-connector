package it.eng.dcp.issuer;

import it.eng.dcp.common.config.DcpProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot application class for the DCP Issuer service.
 * This service handles verifiable credential issuance.
 * Scans issuer-specific and common packages for components.
 * HTTP client configuration is provided by IssuerHttpClientConfiguration.
 */
@SpringBootApplication
@ComponentScan(
    basePackages = {"it.eng.dcp.issuer", "it.eng.dcp.common"}
)
@EnableMongoRepositories(basePackages = {
    "it.eng.dcp.issuer.repository",
    "it.eng.dcp.common.repository"
})
@EnableScheduling
@EnableConfigurationProperties(DcpProperties.class)
public class IssuerApplication {

    /**
     * Main entry point for the DCP Issuer application.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(IssuerApplication.class, args);
    }
}
