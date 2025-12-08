package it.eng.negotiation.service;

import it.eng.negotiation.policy.service.PolicyAdministrationPoint;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.AgreementRepository;
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
public class DSPContractNegotiationConsumerService extends ContractNegotiationConsumerService {
    public DSPContractNegotiationConsumerService(AuditEventPublisher publisher,
                                                 ContractNegotiationRepository contractNegotiationRepository,
                                                 OkHttpRestClient okHttpRestClient,
                                                 ContractNegotiationProperties properties,
                                                 OfferRepository offerRepository,
                                                 AgreementRepository agreementRepository,
                                                 PolicyAdministrationPoint policyAdministrationPoint) {
        super(publisher, contractNegotiationRepository, okHttpRestClient, properties, offerRepository, agreementRepository, policyAdministrationPoint);
    }
}
