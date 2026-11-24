package it.eng.dcp.common.config;

import it.eng.dcp.common.participant.AllowAllAdminsParticipantAdminAuthorization;
import it.eng.dcp.common.participant.DefaultParticipantKeyProvider;
import it.eng.dcp.common.participant.FixedParticipantContextResolver;
import it.eng.dcp.common.participant.ParticipantAdminAuthorization;
import it.eng.dcp.common.participant.ParticipantContextProvider;
import it.eng.dcp.common.participant.ParticipantContextResolver;
import it.eng.dcp.common.participant.ParticipantKeyProvider;
import it.eng.dcp.common.participant.PathParticipantContextResolver;
import it.eng.dcp.common.participant.RequestParticipantContext;
import it.eng.dcp.common.participant.SingleParticipantContextProvider;
import it.eng.dcp.common.service.KeyMetadataService;
import it.eng.dcp.common.service.KeyService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Registers the default participant context SPI implementations. Each default is registered only when
 * the host does not define its own bean, so the single-participant behaviour needs no configuration.
 */
@Configuration
public class ParticipantContextConfiguration {

    /**
     * Default provider: the single participant built from the {@code dcp.*} properties.
     *
     * @param properties        the DCP properties
     * @param didDocumentConfig the DID document configuration
     * @return the provider
     */
    @Bean
    @ConditionalOnMissingBean(ParticipantContextProvider.class)
    public ParticipantContextProvider participantContextProvider(DcpProperties properties,
                                                                 DidDocumentConfig didDocumentConfig) {
        return new SingleParticipantContextProvider(properties, didDocumentConfig);
    }

    /**
     * Default resolver, selected with {@code dcp.participant.resolver} ({@code fixed} by default).
     *
     * @param properties the DCP properties
     * @return the resolver
     */
    @Bean
    @ConditionalOnMissingBean(ParticipantContextResolver.class)
    public ParticipantContextResolver participantContextResolver(DcpProperties properties) {
        if (properties.getParticipant().getResolver() == DcpProperties.ParticipantResolver.PATH) {
            Set<String> reserved = new LinkedHashSet<>(properties.getParticipant().getReservedIds());
            return new PathParticipantContextResolver(properties.basePaths(), reserved);
        }
        return new FixedParticipantContextResolver(properties.getParticipant().getId());
    }

    /**
     * Default key provider wrapping {@link KeyService} and {@link KeyMetadataService}.
     *
     * @param keyService         key loading and rotation
     * @param keyMetadataService key lifecycle metadata
     * @param didDocumentConfig  keystore configuration
     * @param properties         the DCP properties
     * @return the key provider
     */
    @Bean
    @ConditionalOnMissingBean(ParticipantKeyProvider.class)
    public ParticipantKeyProvider participantKeyProvider(KeyService keyService, KeyMetadataService keyMetadataService,
                                                         DidDocumentConfig didDocumentConfig, DcpProperties properties) {
        return new DefaultParticipantKeyProvider(keyService, keyMetadataService, didDocumentConfig, properties);
    }

    /**
     * Default authorization: any authenticated admin may act on the single participant.
     *
     * @return the authorization
     */
    @Bean
    @ConditionalOnMissingBean(ParticipantAdminAuthorization.class)
    public ParticipantAdminAuthorization participantAdminAuthorization() {
        return new AllowAllAdminsParticipantAdminAuthorization();
    }

    /**
     * Controller-edge helper resolving the participant of a request.
     *
     * @param resolver the resolver
     * @param provider the provider
     * @param adminAuthorization the admin authorization hook
     * @return the helper
     */
    @Bean
    @ConditionalOnMissingBean(RequestParticipantContext.class)
    public RequestParticipantContext requestParticipantContext(ParticipantContextResolver resolver,
                                                               ParticipantContextProvider provider,
                                                               ParticipantAdminAuthorization adminAuthorization) {
        return new RequestParticipantContext(resolver, provider, adminAuthorization);
    }
}
