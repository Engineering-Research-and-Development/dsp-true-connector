package it.eng.dcp.issuer.config;

import it.eng.dcp.common.config.BaseDidDocumentConfiguration;
import it.eng.dcp.common.config.DidDocumentConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration for DID document in the issuer (dcp-issuer) module.
 */
@Configuration
@EnableConfigurationProperties(IssuerProperties.class)
@RequiredArgsConstructor
public class IssuerDidDocumentConfiguration implements BaseDidDocumentConfiguration {

    private final IssuerProperties issuerProperties;

    @Value("${server.ssl.enabled:false}")
    private boolean sslEnabled;

    @Value("${server.port:8084}")
    private String port;


    /**
     * Create the DID document configuration bean for the issuer.
     *
     * @return DidDocumentConfig configured for the issuer
     */
    @Bean(name = "issuerDidDocumentConfig")
    public DidDocumentConfig issuerDidDocumentConfig() {
        String protocol = sslEnabled ? "https" : "http";
        return DidDocumentConfig.builder()
                .did(issuerProperties.getConnectorDid())
                .baseUrl(issuerProperties.getBaseUrl() != null && !issuerProperties.getBaseUrl().isBlank()
                        ? issuerProperties.getBaseUrl() : null)
                .protocol(protocol)
                .host("localhost")
                .port(port)
                .verificationMethodController(null)
                .keystorePath(issuerProperties.getKeystore().getPath())
                .keystorePassword(issuerProperties.getKeystore().getPassword())
                .keystoreAlias(issuerProperties.getKeystore().getAlias())
                .serviceEntries(List.of(
                        DidDocumentConfig.ServiceEntryConfig.builder()
                                .id("TRUEConnector-Issuer-Service")
                                .type("IssuerService")
                                .endpointPath("")
                                .build(),
                        DidDocumentConfig.ServiceEntryConfig.builder()
                                .id("TRUEConnector-Credential-Service")
                                .type("CredentialService")
                                .endpointPath("/credentials")
                                .build()
                ))
                .build();
    }

    @Override
    public DidDocumentConfig getDidDocumentConfig() {
        return issuerDidDocumentConfig();
    }
}
