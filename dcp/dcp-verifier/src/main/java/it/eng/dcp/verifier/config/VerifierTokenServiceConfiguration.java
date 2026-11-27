package it.eng.dcp.verifier.config;

import it.eng.dcp.common.participant.ParticipantKeyProvider;
import it.eng.dcp.common.service.audit.DcpAuditEventPublisher;
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
     * Signs through the participant key provider.
     *
     * @param didResolver DID resolver service
     * @param jtiCache JTI replay cache
     * @param keyProvider Provider of the participant signing key
     * @param auditEventPublisher DCP audit event publisher
     * @return SelfIssuedIdTokenService configured with connector's DID
     */
    @Bean(name = "verifierTokenService")
    @Qualifier("verifierTokenService")
    public SelfIssuedIdTokenService verifierTokenService(
            DidResolverService didResolver,
            JtiReplayCache jtiCache,
            ParticipantKeyProvider keyProvider,
            DcpAuditEventPublisher auditEventPublisher) {
        return new SelfIssuedIdTokenService(didResolver, jtiCache, keyProvider, auditEventPublisher);
    }
}


