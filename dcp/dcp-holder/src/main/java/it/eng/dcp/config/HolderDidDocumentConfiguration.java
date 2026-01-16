package it.eng.dcp.config;

import it.eng.dcp.common.config.BaseDidDocumentConfiguration;
import it.eng.dcp.common.config.DidDocumentConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration for DID document in the holder (dcp) module.
 */
@Configuration
@EnableConfigurationProperties(DcpProperties.class)
@RequiredArgsConstructor
public class HolderDidDocumentConfiguration implements BaseDidDocumentConfiguration {

    private final DcpProperties dcpProperties;

    @Value("${server.ssl.enabled:false}")
    private boolean sslEnabled;

    @Value("${server.port}")
    private String port;


    /**
     * Create the DID document configuration bean for the holder.
     *
     * @return DidDocumentConfig configured for the holder
     */
    @Bean(name = "holderDidDocumentConfig")
    public DidDocumentConfig holderDidDocumentConfig() {
        String protocol = sslEnabled ? "https" : "http";
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
                .serviceEntries(List.of(
                        DidDocumentConfig.ServiceEntryConfig.builder()
                                .id("TRUEConnector-Credential-Service")
                                .type("CredentialService")
                                .endpointPath("/dcp")
                                .build(),
                        DidDocumentConfig.ServiceEntryConfig.builder()
                                .id("TRUEConnector-Issuer-Service")
                                .type("IssuerService")
                                .issuerLocation(dcpProperties.getIssuer().getLocation())
                                .build()
                ))
                .build();
    }

    @Override
    public DidDocumentConfig getDidDocumentConfig() {
        return holderDidDocumentConfig();
    }
}
