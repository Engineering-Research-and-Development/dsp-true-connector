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
     * Service entries MUST be explicitly configured in application.properties as they
     * differ between connector and issuer deployments.
     *
     * @return DidDocumentConfig configured with connector's DID
     * @throws IllegalStateException if no service entries are configured
     */
    @Bean
    public DidDocumentConfig didDocumentConfig() {
        String protocol = sslEnabled ? "https" : "http";

        List<DidDocumentConfig.ServiceEntryConfig> serviceEntries = new ArrayList<>();

        // Validate that service entries are configured
        if (dcpProperties.getServiceEntries() == null || dcpProperties.getServiceEntries().isEmpty()) {
            throw new IllegalStateException(
                    "No service entries configured for DID document. " +
                    "Please configure dcp.service-entries in application.properties. " +
                    "Connector and issuer require different service entries - see documentation."
            );
        }

        log.info("Using {} configured service entries", dcpProperties.getServiceEntries().size());
        for (DcpProperties.ServiceEntry entry : dcpProperties.getServiceEntries()) {
            serviceEntries.add(
                    DidDocumentConfig.ServiceEntryConfig.builder()
                            .id(entry.getId())
                            .type(entry.getType())
                            .endpointPath(entry.getEndpointPath() != null ? entry.getEndpointPath() : "")
                            .issuerLocation(entry.getIssuerLocation() != null ? entry.getIssuerLocation() : "")
                            .build()
            );
        }

        log.info("Creating DID document configuration for: {} with {} service entries",
                dcpProperties.getConnectorDid(), serviceEntries.size());

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

