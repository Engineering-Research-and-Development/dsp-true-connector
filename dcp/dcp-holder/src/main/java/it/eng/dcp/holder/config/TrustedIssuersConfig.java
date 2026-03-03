package it.eng.dcp.holder.config;

import it.eng.dcp.common.config.DcpProperties;
import it.eng.dcp.common.service.IssuerTrustService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Configures trusted issuers for credential verification.
 * This runs when the application starts and registers which issuers are trusted
 * for which credential types based on the properties defined in application.properties.
 */
@Component
@Slf4j
public class TrustedIssuersConfig {

    private final IssuerTrustService issuerTrustService;
    private final DcpProperties dcpProperties;

    public TrustedIssuersConfig(IssuerTrustService issuerTrustService, DcpProperties dcpProperties) {
        this.issuerTrustService = issuerTrustService;
        this.dcpProperties = dcpProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void configureTrustedIssuers() {
        log.info("Configuring trusted issuers from properties...");

        Map<String, String> trustedIssuersMap = dcpProperties.getTrustedIssuers();

        if (trustedIssuersMap == null || trustedIssuersMap.isEmpty()) {
            log.warn("No trusted issuers configured in properties (dcp.trusted-issuers.*). " +
                    "Credentials from all issuers will be rejected unless trust is configured programmatically.");
            return;
        }

        // Process each credential type and its comma-separated list of trusted issuer DIDs
        trustedIssuersMap.forEach((credentialType, issuersString) -> {
            if (issuersString == null || issuersString.trim().isEmpty()) {
                log.warn("Empty issuer list for credential type: {}", credentialType);
                return;
            }

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

        log.info("Trusted issuers configuration complete. Configured {} credential type(s):", trustedIssuersMap.size());
        trustedIssuersMap.keySet().forEach(credentialType ->
            log.info("  - {}: {}", credentialType, issuerTrustService.getTrustedIssuers(credentialType))
        );
    }
}
