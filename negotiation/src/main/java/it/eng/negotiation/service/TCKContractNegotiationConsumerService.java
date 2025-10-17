package it.eng.negotiation.service;

import it.eng.negotiation.model.*;
import it.eng.negotiation.policy.service.PolicyAdministrationPoint;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.OfferRepository;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.service.AuditEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@Profile("tck")
public class TCKContractNegotiationConsumerService extends ContractNegotiationConsumerService {

    private final ContractNegotiationAPIService contractNegotiationAPIService;

    public TCKContractNegotiationConsumerService(AuditEventPublisher publisher,
                                                 ContractNegotiationRepository contractNegotiationRepository,
                                                 OkHttpRestClient okHttpRestClient,
                                                 ContractNegotiationProperties properties,
                                                 OfferRepository offerRepository,
                                                 AgreementRepository agreementRepository,
                                                 PolicyAdministrationPoint policyAdministrationPoint, ContractNegotiationAPIService contractNegotiationAPIService) {
        super(publisher, contractNegotiationRepository, okHttpRestClient, properties, offerRepository, agreementRepository, policyAdministrationPoint);
        this.contractNegotiationAPIService = contractNegotiationAPIService;
    }

    /**
     * @param contractNegotiationRequest
     * @return
     */
    @Override
    public ContractNegotiation processTCKRequest(TCKContractNegotiationRequest contractNegotiationRequest) {
        Offer offer = Offer.Builder.newInstance()
                .id(contractNegotiationRequest.getOfferId())
                .permission(List.of(Permission.Builder.newInstance().action(Action.USE).build()))
                .target(contractNegotiationRequest.getDatasetId())
                .assigner(contractNegotiationRequest.getProviderId())
                .build();

        ContractRequestMessageRequest request = new ContractRequestMessageRequest(null, contractNegotiationRequest.getConnectorAddress(), NegotiationSerializer.serializePlainJsonNode(offer));

        ContractNegotiation contractNegotiation = contractNegotiationAPIService.sendContractRequestMessage(request);

        log.info("TCK profile running - publishing event after processTCKRequest");
        log.info("ConsumerPid: {}, ProviderPid: {}", contractNegotiation.getConsumerPid(), contractNegotiation.getProviderPid());
        publisher.publishEvent(contractNegotiation);

        return contractNegotiation;
    }

    /**
     * @param consumerPid                           consumer PID
     * @param contractNegotiationTerminationMessage contract negotiation termination message
     */
    @Override
    public void handleTerminationRequest(String consumerPid, ContractNegotiationTerminationMessage contractNegotiationTerminationMessage) {
        log.info("handleTerminationRequest TCK stub called, id: {}",  NegotiationSerializer.serializeProtocol(contractNegotiationTerminationMessage));
        super.handleTerminationRequest(consumerPid, contractNegotiationTerminationMessage);
    }

    /**
     * @param contractNegotiationEventMessage
     */
    @Override
    public void handleFinalizeEvent(ContractNegotiationEventMessage contractNegotiationEventMessage) {
        log.info("handleFinalizeEvent TCK stub called, id: {}",  NegotiationSerializer.serializeProtocol(contractNegotiationEventMessage));

        super.handleFinalizeEvent(contractNegotiationEventMessage);
    }

    /**
     * @param contractAgreementMessage
     * @return
     */
    @Override
    public ContractNegotiation handleContractAgreementMessage(ContractAgreementMessage contractAgreementMessage) {
        log.info("handleAgreement TCK stub called, id: {}",  NegotiationSerializer.serializeProtocol(contractAgreementMessage));

        ContractNegotiation contractNegotiation = super.handleContractAgreementMessage(contractAgreementMessage);

        log.info("TCK profile running - publishing event after handleAgreement");
        log.info("ConsumerPid: {}, ProviderPid: {}", contractNegotiation.getConsumerPid(), contractNegotiation.getProviderPid());
        publisher.publishEvent(contractNegotiation);

        return contractNegotiation;
    }

    /**
     * @param consumerPid
     * @param contractOfferMessage
     * @return
     */
    @Override
    public ContractNegotiation handleContractOfferMessage(String consumerPid, ContractOfferMessage contractOfferMessage) {
        log.info("handleContractOffer TCK stub called, id: {}",  NegotiationSerializer.serializeProtocol(contractOfferMessage));

        ContractNegotiation contractNegotiation = super.handleContractOfferMessage(consumerPid, contractOfferMessage);

        log.info("TCK profile running - publishing event after handleContractOffer");
        log.info("ConsumerPid: {}, ProviderPid: {}", contractNegotiation.getConsumerPid(), contractNegotiation.getProviderPid());
        publisher.publishEvent(contractNegotiation);

        return contractNegotiation;
    }
}
