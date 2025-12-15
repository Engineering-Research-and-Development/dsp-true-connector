package it.eng.dcp.config;

import it.eng.dcp.service.IssuerTrustService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * Properties-based configuration for trusted issuers.
 * Allows configuring trusted issuers via application.properties
 *
 * Example in application.properties:
 * dcp.trusted-issuers.MembershipCredential=did:web:localhost:8080,did:web:localhost:8090
 * dcp.trusted-issuers.EmployeeCredential=did:web:company.com
 */
@Configuration
@EnableConfigurationProperties(TrustedIssuersProperties.DcpTrustConfig.class)
@Slf4j
public class TrustedIssuersProperties {

    private final DcpTrustConfig config;
    private final IssuerTrustService issuerTrustService;

    public TrustedIssuersProperties(DcpTrustConfig config, IssuerTrustService issuerTrustService) {
        this.config = config;
        this.issuerTrustService = issuerTrustService;
    }

    @PostConstruct
    public void loadTrustedIssuers() {
        if (config.getTrustedIssuers() == null || config.getTrustedIssuers().isEmpty()) {
            log.warn("No trusted issuers configured in properties. Using default configuration from TrustedIssuersConfig.");
            return;
        }

        log.info("Loading trusted issuers from configuration...");
        config.getTrustedIssuers().forEach((credentialType, issuersString) -> {
            // Split comma-separated list of issuers
            String[] issuers = issuersString.split(",");
            for (String issuerDid : issuers) {
                String trimmedIssuer = issuerDid.trim();
                if (!trimmedIssuer.isEmpty()) {
                    issuerTrustService.addTrust(credentialType, trimmedIssuer);
                    log.info("  - Added trust: credentialType='{}', issuer='{}'", credentialType, trimmedIssuer);
                }
            }
        });
        log.info("Loaded {} credential types with trusted issuers", config.getTrustedIssuers().size());
    }

    /**
     * Configuration properties for trusted issuers.
     * Binds to dcp.trusted-issuers.* properties.
     */
    @ConfigurationProperties(prefix = "dcp.trusted-issuers")
    @Getter
    @Setter
    public static class DcpTrustConfig {
        /**
         * Map of credential types to comma-separated list of trusted issuer DIDs.
         * Example: MembershipCredential=did:web:localhost:8080,did:web:localhost:8090
         */
        private Map<String, String> trustedIssuers = new HashMap<>();
    }
}

