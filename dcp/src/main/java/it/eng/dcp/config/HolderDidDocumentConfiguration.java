package it.eng.dcp.config;

import it.eng.dcp.common.config.BaseDidDocumentConfiguration;
import it.eng.dcp.common.config.DidDocumentConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration for DID document in the holder (dcp) module.
 */
@Configuration
public class HolderDidDocumentConfiguration implements BaseDidDocumentConfiguration {

    @Value("${holder.did:did:web:localhost%3A8083:holder}")
    private String holderDid;

    @Value("${holder.base-url:}")
    private String holderBaseUrl;

    @Value("${server.ssl.enabled:false}")
    private boolean sslEnabled;

    @Value("${holder.host:localhost}")
    private String host;

    @Value("${server.port:8083}")
    private String port;

    @Value("${holder.verification-method.controller:}")
    private String verificationMethodController;

    @Value("${holder.keystore.path:eckey.p12}")
    private String keystorePath;

    @Value("${holder.keystore.password:password}")
    private String keystorePassword;

    @Value("${holder.keystore.alias:dsptrueconnector}")
    private String keystoreAlias;

    /**
     * Create the DID document configuration bean for the holder.
     *
     * @return DidDocumentConfig configured for the holder
     */
    @Bean(name = "holderDidDocumentConfig")
    public DidDocumentConfig holderDidDocumentConfig() {
        String protocol = sslEnabled ? "https" : "http";
        return DidDocumentConfig.builder()
                .did(holderDid)
                .baseUrl(holderBaseUrl != null && !holderBaseUrl.isBlank() ? holderBaseUrl : null)
                .protocol(protocol)
                .host(host)
                .port(port)
                .verificationMethodController(verificationMethodController)
                .keystorePath(keystorePath)
                .keystorePassword(keystorePassword)
                .keystoreAlias(keystoreAlias)
                .serviceEntries(List.of(
                        DidDocumentConfig.ServiceEntryConfig.builder()
                                .id("TRUEConnector-Credential-Service")
                                .type("CredentialService")
                                .endpointPath("")
                                .build(),
                        DidDocumentConfig.ServiceEntryConfig.builder()
                                .id("TRUEConnector-Issuer-Service")
                                .type("IssuerService")
                                .endpointPath("/issuer")
                                .build()
                ))
                .build();
    }

    @Override
    public DidDocumentConfig getDidDocumentConfig() {
        return holderDidDocumentConfig();
    }
}
