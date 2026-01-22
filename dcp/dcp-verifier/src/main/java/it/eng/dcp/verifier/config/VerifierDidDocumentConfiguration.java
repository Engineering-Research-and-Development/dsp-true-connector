package it.eng.dcp.verifier.config;

import it.eng.dcp.common.config.BaseDidDocumentConfiguration;
import it.eng.dcp.common.config.DcpProperties;
import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.service.KeyService;
import it.eng.dcp.common.service.did.DidResolverService;
import it.eng.dcp.common.service.sts.JtiReplayCache;
import it.eng.dcp.common.service.sts.SelfIssuedIdTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration for DID document in the verifier role.
 * Uses the same DcpProperties as holder but with a different DID identifier (connectorDidVerifier).
 */
@Configuration
@RequiredArgsConstructor
public class VerifierDidDocumentConfiguration implements BaseDidDocumentConfiguration {

    private final DcpProperties dcpProperties;

    @Value("${server.ssl.enabled:false}")
    private boolean sslEnabled;

    @Value("${server.port}")
    private String port;

    /**
     * Create the DID document configuration bean for the verifier.
     * Uses connectorDidVerifier if set, otherwise falls back to connectorDid.
     *
     * @return DidDocumentConfig configured for the verifier
     */
    @Bean(name = "verifierDidDocumentConfig")
    public DidDocumentConfig verifierDidDocumentConfig() {
        String protocol = sslEnabled ? "https" : "http";

        // Use connectorDidVerifier if set, otherwise fall back to connectorDid
        String verifierDid = dcpProperties.getConnectorDidVerifier() != null
            ? dcpProperties.getConnectorDidVerifier()
            : dcpProperties.getConnectorDid();

        return DidDocumentConfig.builder()
                .did(verifierDid)
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
                                .id("TRUEConnector-Verifier-Service")
                                .type("VerifierService")
                                .endpointPath("/dcp/verifier")
                                .build()
                ))
                .build();
    }

    @Override
    public DidDocumentConfig getDidDocumentConfig() {
        return verifierDidDocumentConfig();
    }

    /**
     * Register this configuration as 'verifier' for the generic DID document controller.
     *
     * @return This configuration instance
     */
    @Bean(name = "verifier")
    @Qualifier("verifier")
    public BaseDidDocumentConfiguration verifierConfiguration() {
        return this;
    }

    /**
     * Create a SelfIssuedIdTokenService instance specifically for the verifier role.
     * This instance validates tokens where the audience (aud) is the verifier's DID.
     *
     * @param didResolver DID resolver service
     * @param jtiCache JTI replay cache
     * @param keyService Key service for cryptographic operations
     * @return SelfIssuedIdTokenService configured with verifier's DID
     */
    @Bean(name = "verifierTokenService")
    @Qualifier("verifierTokenService")
    public SelfIssuedIdTokenService verifierTokenService(
            DidResolverService didResolver,
            JtiReplayCache jtiCache,
            KeyService keyService) {
        return new SelfIssuedIdTokenService(didResolver, jtiCache, keyService, this);
    }
}
