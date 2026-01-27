package it.eng.dcp.holder.config;

import it.eng.dcp.holder.service.IssuerTrustService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Configures trusted issuers for credential verification.
 * This runs when the application starts and registers which issuers are trusted
 * for which credential types.
 */
@Component
@Slf4j
public class TrustedIssuersConfig {

    private final IssuerTrustService issuerTrustService;

    public TrustedIssuersConfig(IssuerTrustService issuerTrustService) {
        this.issuerTrustService = issuerTrustService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void configureTrustedIssuers() {
        log.info("Configuring trusted issuers...");

        // Register trusted issuers for MembershipCredential
        // The issuer DID from your JWT token is: did:web:localhost:8080
        issuerTrustService.addTrust("MembershipCredential", "did:web:localhost:8080");

        // Add more trusted issuers as needed
        // Example: Trust another issuer for MembershipCredential
        // issuerTrustService.addTrust("MembershipCredential", "did:web:example.com");

        // Example: Trust issuers for other credential types
        // issuerTrustService.addTrust("VerifiableCredential", "did:web:trusted-issuer.com");
        // issuerTrustService.addTrust("EmployeeCredential", "did:web:company.com");

        log.info("Trusted issuers configuration complete:");
        log.info("  - MembershipCredential: {}", issuerTrustService.getTrustedIssuers("MembershipCredential"));
    }
}

