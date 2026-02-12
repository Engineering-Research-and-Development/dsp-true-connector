package it.eng.dcp.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralized DID document configuration for all DCP modules.
 *
 * <p>Creates a single DID document configuration using dcp.connector-did property.
 * This eliminates the need for separate holder/issuer/verifier configurations.
 *
 * <p>All modules (holder, issuer, verifier) use the same connector DID identity,
 * with module-specific service entries added to the DID document.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class BaseDidDocumentConfiguration {

    private final DcpProperties dcpProperties;

    @Value("${server.ssl.enabled:false}")
    private boolean sslEnabled;

    @Value("${server.port}")
    private String port;

    /**
     * Create the DID document configuration bean.
     *
     * <p>This single configuration is used by all DCP modules (holder, issuer, verifier).
     * Service entries are added based on which modules are active in the deployment.
     *
     * @return DidDocumentConfig configured with connector's DID
     */
    @Bean
    public DidDocumentConfig didDocumentConfig() {
        String protocol = sslEnabled ? "https" : "http";

        List<DidDocumentConfig.ServiceEntryConfig> serviceEntries = new ArrayList<>();

        // Add holder/verifier service entry (credential service)
        serviceEntries.add(
                DidDocumentConfig.ServiceEntryConfig.builder()
                        .id("TRUEConnector-Credential-Service")
                        .type("CredentialService")
                        .endpointPath("/dcp")
                        .build()
        );

        // Add issuer service entry if issuer location is configured
        if (dcpProperties.getIssuer() != null &&
            dcpProperties.getIssuer().getLocation() != null &&
            !dcpProperties.getIssuer().getLocation().isBlank()) {
            serviceEntries.add(
                    DidDocumentConfig.ServiceEntryConfig.builder()
                            .id("TRUEConnector-Issuer-Service")
                            .type("IssuerService")
                            .issuerLocation(dcpProperties.getIssuer().getLocation())
                            .build()
            );
        }

        // Add verifier service entry
        serviceEntries.add(
                DidDocumentConfig.ServiceEntryConfig.builder()
                        .id("TRUEConnector-Verifier-Service")
                        .type("VerifierService")
                        .endpointPath("/dcp/verifier")
                        .build()
        );

        log.info("Creating DID document configuration for: {}", dcpProperties.getConnectorDid());

        return DidDocumentConfig.builder()
                .did(dcpProperties.getConnectorDid())
                .baseUrl(dcpProperties.getBaseUrl() != null && !dcpProperties.getBaseUrl().isBlank()
                        ? dcpProperties.getBaseUrl() : null)
                .protocol(protocol)
                .host(dcpProperties.getHost())
                .port(port)
                .verificationMethodController(null)
                .keystorePath(dcpProperties.getKeystore().getPath())
                .keystorePassword(dcpProperties.getKeystore().getPassword())
                .keystoreAlias(dcpProperties.getKeystore().getAlias())
                .serviceEntries(serviceEntries)
                .build();
    }

    /**
     * Get the DID document configuration.
     *
     * @return DidDocumentConfig instance
     */
    public DidDocumentConfig getDidDocumentConfig() {
        return didDocumentConfig();
    }
}

