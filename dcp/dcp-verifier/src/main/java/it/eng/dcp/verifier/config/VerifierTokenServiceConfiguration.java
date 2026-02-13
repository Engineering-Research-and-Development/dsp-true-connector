package it.eng.dcp.verifier.config;

import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.service.KeyService;
import it.eng.dcp.common.service.did.DidResolverService;
import it.eng.dcp.common.service.sts.JtiReplayCache;
import it.eng.dcp.common.service.sts.SelfIssuedIdTokenService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for verifier-specific token services.
 *
 * <p>Creates a dedicated SelfIssuedIdTokenService bean for the verifier role.
 * This service validates tokens where the audience (aud) is the connector's DID.
 *
 * <p>Note: The verifier uses the same DID as the holder (connector identity).
 * This configuration only creates verifier-specific service beans.
 */
@Configuration
public class VerifierTokenServiceConfiguration {

    /**
     * Create a SelfIssuedIdTokenService instance specifically for the verifier role.
     *
     * <p>This instance validates tokens where the audience (aud) is the connector's DID.
     * Uses the DidDocumentConfig bean created by BaseDidDocumentConfiguration.
     *
     * @param didResolver DID resolver service
     * @param jtiCache JTI replay cache
     * @param keyService Key service for cryptographic operations
     * @param config The DidDocumentConfig bean
     * @return SelfIssuedIdTokenService configured with connector's DID
     */
    @Bean(name = "verifierTokenService")
    @Qualifier("verifierTokenService")
    public SelfIssuedIdTokenService verifierTokenService(
            DidResolverService didResolver,
            JtiReplayCache jtiCache,
            KeyService keyService,
            DidDocumentConfig config) {
        return new SelfIssuedIdTokenService(didResolver, jtiCache, keyService, config);
    }
}


