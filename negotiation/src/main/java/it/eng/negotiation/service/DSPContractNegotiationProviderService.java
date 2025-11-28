package it.eng.negotiation.service;

import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractRequestMessage;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.OfferRepository;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.property.ConnectorProperties;
import it.eng.tools.service.AuditEventPublisher;
import it.eng.tools.util.CredentialUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@Profile("!tck")
public class DSPContractNegotiationProviderService extends ContractNegotiationProviderService {
    public DSPContractNegotiationProviderService(AuditEventPublisher publisher, ConnectorProperties connectorProperties,
                                                 ContractNegotiationRepository contractNegotiationRepository, OkHttpRestClient okHttpRestClient,
                                                 ContractNegotiationProperties properties, OfferRepository offerRepository,
                                                 CredentialUtils credentialUtils) {
        super(publisher, connectorProperties, contractNegotiationRepository, okHttpRestClient, properties, offerRepository, credentialUtils);
    }
}
