package it.eng.dcp.issuer.config;

import it.eng.dcp.common.config.BaseDidDocumentConfiguration;
import it.eng.dcp.common.config.DidDocumentConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration for DID document in the issuer (dcp-issuer) module.
 */
@Configuration
public class IssuerDidDocumentConfiguration implements BaseDidDocumentConfiguration {

    @Value("${issuer.did:did:web:localhost%3A8084:issuer}")
    private String issuerDid;

    @Value("${issuer.base-url:}")
    private String issuerBaseUrl;

    @Value("${server.ssl.enabled:false}")
    private boolean sslEnabled;

    @Value("${issuer.host:localhost}")
    private String host;

    @Value("${server.port:8084}")
    private String port;

    @Value("${issuer.verification-method.controller:}")
    private String verificationMethodController;

    @Value("${issuer.keystore.path:classpath:eckey-issuer.p12}")
    private String keystorePath;

    @Value("${issuer.keystore.password:password}")
    private String keystorePassword;

    /**
     * Create the DID document configuration bean for the issuer.
     *
     * @return DidDocumentConfig configured for the issuer
     */
    @Bean(name = "issuerDidDocumentConfig")
    public DidDocumentConfig issuerDidDocumentConfig() {
        String protocol = sslEnabled ? "https" : "http";
        return DidDocumentConfig.builder()
                .did(issuerDid)
                .baseUrl(issuerBaseUrl != null && !issuerBaseUrl.isBlank() ? issuerBaseUrl : null)
                .protocol(protocol)
                .host(host)
                .port(port)
                .verificationMethodController(verificationMethodController)
                .keystorePath(keystorePath)
                .keystorePassword(keystorePassword)
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
