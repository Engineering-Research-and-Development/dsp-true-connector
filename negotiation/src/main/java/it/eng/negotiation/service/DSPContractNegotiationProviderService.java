package it.eng.negotiation.service;

import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.OfferRepository;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.service.AuditEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@Profile("!tck")
public class DSPContractNegotiationProviderService extends ContractNegotiationProviderService {
    public DSPContractNegotiationProviderService(AuditEventPublisher publisher,
                                                 ContractNegotiationRepository contractNegotiationRepository, OkHttpRestClient okHttpRestClient,
                                                 ContractNegotiationProperties properties, OfferRepository offerRepository) {
        super(publisher, contractNegotiationRepository, okHttpRestClient, properties, offerRepository);
    }
}
