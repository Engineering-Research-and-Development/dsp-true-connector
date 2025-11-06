package it.eng.negotiation.service;

import it.eng.negotiation.model.*;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.OfferRepository;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.property.ConnectorProperties;
import it.eng.tools.service.AuditEventPublisher;
import it.eng.tools.util.CredentialUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@Profile("tck")
public class TCKContractNegotiationProviderService extends ContractNegotiationProviderService {

    private final ContractNegotiationAPIService apiService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public TCKContractNegotiationProviderService(ContractNegotiationAPIService apiService, ApplicationEventPublisher applicationEventPublisher,
                                                 AuditEventPublisher publisher, ConnectorProperties connectorProperties,
                                                 ContractNegotiationRepository contractNegotiationRepository, OkHttpRestClient okHttpRestClient,
                                                 ContractNegotiationProperties properties, OfferRepository offerRepository,
                                                 CredentialUtils credentialUtils) {
        super(publisher, connectorProperties, contractNegotiationRepository, okHttpRestClient, properties, offerRepository, credentialUtils);
        this.apiService = apiService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * Start a new contract negotiation from a ContractRequestMessage.
     *
     * @param contractRequestMessage the incoming contract request message
     * @return the created ContractNegotiation
     */
    @Override
    public ContractNegotiation handleContractRequestMessage(ContractRequestMessage contractRequestMessage) {
        log.info("startContractNegotiation TCK stub called, {}", NegotiationSerializer.serializeProtocol(contractRequestMessage));

        ContractNegotiation contractNegotiation = super.handleContractRequestMessage(contractRequestMessage);

        log.info("TCK profile running - publishing event - {}", contractNegotiation.getState());
        log.info("ConsumerPid: {}, ProviderPid: {}", contractNegotiation.getConsumerPid(), contractNegotiation.getProviderPid());
        applicationEventPublisher.publishEvent(contractNegotiation);

        return contractNegotiation;
    }

    /**
     * Handle a counteroffer ContractRequestMessage.
     *
     * @param providerPid the provider PID
     * @param crm         the ContractRequestMessage representing the counteroffer
     * @return the resulting ContractNegotiation after handling the counteroffer
     */
    @Override
    public ContractNegotiation handleContractRequestMessageAsCounteroffer(String providerPid, ContractRequestMessage crm) {
        log.info("handleContractRequestMessageAsCounteroffer TCK stub called, {}", NegotiationSerializer.serializeProtocol(crm));

        ContractNegotiation contractNegotiation = super.handleContractRequestMessageAsCounteroffer(providerPid, crm);

        log.info("TCK profile running - publishing event - {}", contractNegotiation.getState());
        log.info("ConsumerPid: {}, ProviderPid: {}", contractNegotiation.getConsumerPid(), contractNegotiation.getProviderPid());
        applicationEventPublisher.publishEvent(contractNegotiation);

        return contractNegotiation;
    }

    /**
     * Verify a contract agreement verification message.
     *
     * @param providerPid the provider PID
     * @param cavm        the ContractAgreementVerificationMessage to verify
     * @return the resulting ContractNegotiation after verification
     */
    @Override
    public ContractNegotiation handleContractAgreementVerificationMessage(String providerPid, ContractAgreementVerificationMessage cavm) {
        log.info("verifyNegotiation TCK stub called, {}", NegotiationSerializer.serializeProtocol(cavm));
        ContractNegotiation contractNegotiation = super.handleContractAgreementVerificationMessage(providerPid, cavm);

        log.info("TCK profile running - publishing event - {}", contractNegotiation.getState());
        log.info("ConsumerPid: {}, ProviderPid: {}", contractNegotiation.getConsumerPid(), contractNegotiation.getProviderPid());
        applicationEventPublisher.publishEvent(contractNegotiation);

        return contractNegotiation;
    }

    /**
     * Handle an incoming contract negotiation event message.
     *
     * @param providerPid the provider PID
     * @param contractNegotiationEventMessage the event message to handle
     * @return the resulting ContractNegotiation after handling the event
     */
    @Override
    public ContractNegotiation handleContractNegotiationEventMessageAccepted(String providerPid, ContractNegotiationEventMessage contractNegotiationEventMessage) {
        log.info("handleContractNegotiationEventMessageAccepted TCK stub called, {}", NegotiationSerializer.serializeProtocol(contractNegotiationEventMessage));
        ContractNegotiation contractNegotiation = super.handleContractNegotiationEventMessageAccepted(providerPid, contractNegotiationEventMessage);

        log.info("TCK profile running - publishing event - {}", contractNegotiation.getState());
        log.info("ConsumerPid: {}, ProviderPid: {}", contractNegotiation.getConsumerPid(), contractNegotiation.getProviderPid());
        applicationEventPublisher.publishEvent(contractNegotiation);

        return contractNegotiation;
    }

    @EventListener(classes = ContractNegotiation.class)
    public void onTransferProcessEvent(ContractNegotiation contractNegotiation) {
        log.info("TCKDataTransferService received event for Dataset id: {} with state {}", contractNegotiation.getOffer().getTarget(), contractNegotiation.getState());
        log.info("ConsumerPid: {}, ProviderPid: {}", contractNegotiation.getConsumerPid(), contractNegotiation.getProviderPid());
        try {
            Thread.sleep(2000);
            log.info("sleep over");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        String target = contractNegotiation.getOffer().getTarget();

        if (target.equalsIgnoreCase("ACN0101")) {
            switch (contractNegotiation.getState()) {
                case REQUESTED:
                    log.info("Processing ACN0101 - REQUESTED -> OFFERED : {}", contractNegotiation.getId());
                    ContractNegotiation result = apiService.sendContractOfferMessageAsCounteroffer(contractNegotiation.getId(), NegotiationSerializer.serializePlainJsonNode(swapOfferIdWithOriginalId(contractNegotiation.getOffer())));
                    applicationEventPublisher.publishEvent(result);
                    break;
                default:
                    log.info("No action for ACN0101 in state: {}", contractNegotiation.getState());
            }
        }

        if (target.equalsIgnoreCase("ACN0102")) {
//            since the same state REQUESTED is used for two different transitions in ACN0102 scenario,
//            we need to check the version to distinguish them
            if (contractNegotiation.getVersion() == 0L) {
                log.info("Processing ACN0102 - REQUESTED -> OFFERED : {}", contractNegotiation.getId());
                ContractNegotiation result = apiService.sendContractOfferMessageAsCounteroffer(contractNegotiation.getId(), NegotiationSerializer.serializePlainJsonNode(swapOfferIdWithOriginalId(contractNegotiation.getOffer())));
                applicationEventPublisher.publishEvent(result);
            } else if (contractNegotiation.getVersion() == 1L) {
                log.info("Processing ACN0102 - REQUESTED -> TERMINATED : {}", contractNegotiation.getId());
                ContractNegotiation result2 = apiService.sendContractNegotiationTerminationMessage(contractNegotiation.getId());
                applicationEventPublisher.publishEvent(result2);
            } else {
                log.info("No action for ACN0102 in state: {}", contractNegotiation.getState());
            }
        }

//        if (target.equalsIgnoreCase("ACN0103")) {
//            switch (contractNegotiation.getState()) {
//                case REQUESTED:
//                    log.info("Processing ACN0103 - REQUESTED -> OFFERED : {}", contractNegotiation.getId());
//                    ContractNegotiation result = apiService.sendContractOfferMessageAsCounteroffer(contractNegotiation.getId(), NegotiationSerializer.serializePlainJsonNode(swapOfferIdWithOriginalId(contractNegotiation.getOffer())));
//                    applicationEventPublisher.publishEvent(result);
//                    break;
//                case ACCEPTED:
//                    log.info("Processing ACN0103 - ACCEPTED -> AGREED : {}", contractNegotiation.getId());
//                    ContractNegotiation result2 = apiService.sendContractAgreementMessage(contractNegotiation.getId());
//                    applicationEventPublisher.publishEvent(result2);
//                    break;
//                case VERIFIED:
//                    log.info("Processing ACN0103 - VERIFIED -> FINALIZED : {}", contractNegotiation.getId());
//                    apiService.sendContractNegotiationEventMessageFinalize(contractNegotiation.getId());
//                    break;
//                default:
//                    log.info("No action for ACN0103 in state: {}", contractNegotiation.getState());
//            }
//        }

        if (target.equalsIgnoreCase("ACN0104")) {
            switch (contractNegotiation.getState()) {
                case REQUESTED:
                    log.info("Processing ACN0104 - REQUESTED -> AGREED : {}", contractNegotiation.getOffer().getTarget());
                    ContractNegotiation result = apiService.sendContractAgreementMessage(contractNegotiation.getId());
                    applicationEventPublisher.publishEvent(result);
                    break;
                case VERIFIED:
                    log.info("Processing ACN0104 - VERIFIED -> FINALIZED : {}", contractNegotiation.getId());
                    apiService.sendContractNegotiationEventMessageFinalize(contractNegotiation.getId());
                    break;
                default:
                    log.info("No action for ACN0104 in state: {}", contractNegotiation.getState());
            }
        }

        if (target.equalsIgnoreCase("ACN0201")) {
            switch (contractNegotiation.getState()) {
                case REQUESTED:
                    log.info("Processing ACN0201 - REQUESTED -> TERMINATED : {}", contractNegotiation.getId());
                    apiService.sendContractNegotiationTerminationMessage(contractNegotiation.getId());
                    break;
                default:
                    log.info("No action for ACN0201 in state: {}", contractNegotiation.getState());
            }
        }

        if (target.equalsIgnoreCase("ACN0203")) {
            switch (contractNegotiation.getState()) {
                case REQUESTED:
                    log.info("Processing ACN0203 - REQUESTED -> AGREED : {}", contractNegotiation.getId());
                    ContractNegotiation result = apiService.sendContractAgreementMessage(contractNegotiation.getId());
                    applicationEventPublisher.publishEvent(result);
                    break;
                default:
                    log.info("No action for ACN0203 in state: {}", contractNegotiation.getState());
            }
        }

        if (target.equalsIgnoreCase("ACN0204")) {
            switch (contractNegotiation.getState()) {
                case REQUESTED:
                    log.info("Processing ACN0204 - REQUESTED -> OFFERED: {}", contractNegotiation.getId());
                    apiService.sendContractOfferMessageAsCounteroffer(contractNegotiation.getId(), NegotiationSerializer.serializePlainJsonNode(swapOfferIdWithOriginalId(contractNegotiation.getOffer())));
                    break;
                default:
                    log.info("No action for ACN0204 in state: {}", contractNegotiation.getState());
            }
        }

        if (target.equalsIgnoreCase("ACN0205")) {
            switch (contractNegotiation.getState()) {
                case REQUESTED:
                    log.info("Processing ACN0205 - REQUESTED -> OFFERED: {}", contractNegotiation.getId());
                    ContractNegotiation result = apiService.sendContractOfferMessageAsCounteroffer(contractNegotiation.getId(), NegotiationSerializer.serializePlainJsonNode(swapOfferIdWithOriginalId(contractNegotiation.getOffer())));
                    applicationEventPublisher.publishEvent(result);
                    break;
                case OFFERED:
                    log.info("Processing ACN0205 - OFFERED -> TERMINATED: {}", contractNegotiation.getId());
                    apiService.sendContractNegotiationTerminationMessage(contractNegotiation.getId());
                    break;
                default:
                    log.info("No action for ACN0205 in state: {}", contractNegotiation.getState());
            }
        }

        if (target.equalsIgnoreCase("ACN0206")) {
            switch (contractNegotiation.getState()) {
                case REQUESTED:
                    log.info("Processing ACN0206 - REQUESTED -> OFFERED: {}", contractNegotiation.getId());
                    ContractNegotiation result = apiService.sendContractOfferMessageAsCounteroffer(contractNegotiation.getId(), NegotiationSerializer.serializePlainJsonNode(swapOfferIdWithOriginalId(contractNegotiation.getOffer())));
                    applicationEventPublisher.publishEvent(result);
                    break;
                case ACCEPTED:
                    log.info("Processing ACN0206 - ACCEPTED -> TERMINATED: {}", contractNegotiation.getId());
                    apiService.sendContractNegotiationTerminationMessage(contractNegotiation.getId());
                    break;
                default:
                    log.info("No action for ACN0206 in state: {}", contractNegotiation.getState());
            }
        }

        if (target.equalsIgnoreCase("ACN0207")) {
            switch (contractNegotiation.getState()) {
                case REQUESTED:
                    log.info("Processing ACN0207 - REQUESTED -> AGREED : {}", contractNegotiation.getId());
                    ContractNegotiation result = apiService.sendContractAgreementMessage(contractNegotiation.getId());
                    applicationEventPublisher.publishEvent(result);
                    break;
                case VERIFIED:
                    log.info("Processing ACN0207 - VERIFIED -> TERMINATED : {}", contractNegotiation.getId());
                    apiService.sendContractNegotiationTerminationMessage(contractNegotiation.getId());
                    break;
                default:
                    log.info("No action for ACN0207 in state: {}", contractNegotiation.getState());
            }
        }

        if (target.equalsIgnoreCase("ACN0301")) {
            switch (contractNegotiation.getState()) {
                case REQUESTED:
                    log.info("Processing ACN0301 - REQUESTED -> AGREED : {}", contractNegotiation.getId());
                    ContractNegotiation result = apiService.sendContractAgreementMessage(contractNegotiation.getId());
                    applicationEventPublisher.publishEvent(result);
                    break;
                case VERIFIED:
                    log.info("Processing ACN0301 - VERIFIED -> FINALIZED : {}", contractNegotiation.getId());
                    apiService.sendContractNegotiationEventMessageFinalize(contractNegotiation.getId());
                    break;
                default:
                    log.info("No action for ACN0301 in state: {}", contractNegotiation.getState());
            }
        }

        if (target.equalsIgnoreCase("ACN0302")) {
            switch (contractNegotiation.getState()) {
                case REQUESTED:
                    log.info("Processing ACN0302 - REQUESTED -> OFFERED: {}", contractNegotiation.getId());
                    apiService.sendContractOfferMessageAsCounteroffer(contractNegotiation.getId(), NegotiationSerializer.serializePlainJsonNode(swapOfferIdWithOriginalId(contractNegotiation.getOffer())));
                    break;
                default:
                    log.info("No action for ACN0302 in state: {}", contractNegotiation.getState());
            }
        }

        if (target.equalsIgnoreCase("ACN0303")) {
            switch (contractNegotiation.getState()) {
                case REQUESTED:
                    log.info("Processing ACN0303 - REQUESTED -> OFFERED: {}", contractNegotiation.getId());
                    apiService.sendContractOfferMessageAsCounteroffer(contractNegotiation.getId(), NegotiationSerializer.serializePlainJsonNode(swapOfferIdWithOriginalId(contractNegotiation.getOffer())));
                    break;
                default:
                    log.info("No action for ACN0303 in state: {}", contractNegotiation.getState());
            }
        }

        if (target.equalsIgnoreCase("ACN0304")) {
            switch (contractNegotiation.getState()) {
                case REQUESTED:
                    log.info("Processing ACN0304 - REQUESTED -> OFFERED: {}", contractNegotiation.getId());
                    apiService.sendContractOfferMessageAsCounteroffer(contractNegotiation.getId(), NegotiationSerializer.serializePlainJsonNode(swapOfferIdWithOriginalId(contractNegotiation.getOffer())));
                    break;
                default:
                    log.info("No action for ACN0304 in state: {}", contractNegotiation.getState());
            }
        }

        if (target.equalsIgnoreCase("ACNC0101")) {
            if (contractNegotiation.getState() == ContractNegotiationState.OFFERED) {
                log.info("Processing ACNC0101 - OFFERED -> ACCEPTED : {}", contractNegotiation.getId());
                ContractNegotiation result = apiService.sendContractNegotiationEventMessageAccepted(contractNegotiation.getId());
                applicationEventPublisher.publishEvent(result);
            }
            if (contractNegotiation.getState() == ContractNegotiationState.AGREED) {
                log.info("Processing ACNC0101 - AGREED -> VERIFIED : {}", contractNegotiation.getId());
                apiService.sendContractAgreementVerificationMessage(contractNegotiation.getId());
            }
        }

        if (target.equalsIgnoreCase("ACNC0102")) {
            switch (contractNegotiation.getState()) {
                case OFFERED:
                    log.info("Processing ACNC0102 - OFFERED -> REQUESTED : {}", contractNegotiation.getId());
                    apiService.sendContractRequestMessageAsCounteroffer(contractNegotiation.getId(), NegotiationSerializer.serializePlainJsonNode(swapOfferIdWithOriginalId(contractNegotiation.getOffer())));
                    break;
                default:
                    log.info("No action for ACNC0102 in state: {}", contractNegotiation.getState());
            }
        }

        if (target.equalsIgnoreCase("ACNC0103")) {
            switch (contractNegotiation.getState()) {
                case OFFERED:
                    log.info("Processing ACNC0103 - OFFERED -> TERMINATED : {}", contractNegotiation.getId());
                    apiService.sendContractNegotiationTerminationMessage(contractNegotiation.getId());
                    break;
                default:
                    log.info("No action for ACNC0103 in state: {}", contractNegotiation.getState());
            }
        }

        if (target.equalsIgnoreCase("ACNC0104")) {
            switch (contractNegotiation.getState()) {
                case AGREED:
                    log.info("Processing ACNC0104 - AGREED -> VERIFIED : {}", contractNegotiation.getId());
                    apiService.sendContractAgreementVerificationMessage(contractNegotiation.getId());
                    break;
                default:
                    log.info("No action for ACNC0104 in state: {}", contractNegotiation.getState());
            }
        }

        if (target.equalsIgnoreCase("ACNC0202")) {
            switch (contractNegotiation.getState()) {
                case REQUESTED:
                    log.info("Processing ACNC0202 - REQUESTED -> TERMINATED : {}", contractNegotiation.getId());
                    apiService.sendContractNegotiationTerminationMessage(contractNegotiation.getId());
                    break;
                default:
                    log.info("No action for ACNC0202 in state: {}", contractNegotiation.getState());
            }
        }

        if (target.equalsIgnoreCase("ACNC0203")) {
            switch (contractNegotiation.getState()) {
                case AGREED:
                    log.info("Processing ACNC0203 - AGREED -> TERMINATED : {}", contractNegotiation.getId());
                    apiService.sendContractNegotiationTerminationMessage(contractNegotiation.getId());
                    break;
                default:
                    log.info("No action for ACNC0203 in state: {}", contractNegotiation.getState());
            }
        }

        if (target.equalsIgnoreCase("ACNC0205")) {
            switch (contractNegotiation.getState()) {
                case OFFERED:
                    log.info("Processing ACNC0205 - OFFERED -> ACCEPTED : {}", contractNegotiation.getId());
                    apiService.sendContractNegotiationEventMessageAccepted(contractNegotiation.getId());
                    break;
                default:
                    log.info("No action for ACNC0205 in state: {}", contractNegotiation.getState());
            }
        }

        if (target.equalsIgnoreCase("ACNC0206")) {
            switch (contractNegotiation.getState()) {
                case AGREED:
                    log.info("Processing ACNC0206 - AGREED -> VERIFIED : {}", contractNegotiation.getId());
                    apiService.sendContractAgreementVerificationMessage(contractNegotiation.getId());
                    break;
                default:
                    log.info("No action for ACNC0206 in state: {}", contractNegotiation.getState());
            }
        }

        if (target.equalsIgnoreCase("ACNC0304")) {
            switch (contractNegotiation.getState()) {
                case OFFERED:
                    log.info("Processing ACNC0304 - OFFERED -> ACCEPTED : {}", contractNegotiation.getId());
                    apiService.sendContractNegotiationEventMessageAccepted(contractNegotiation.getId());
                    break;
                default:
                    log.info("No action for ACNC0304 in state: {}", contractNegotiation.getState());
            }
        }

        if (target.equalsIgnoreCase("ACNC0305")) {
            switch (contractNegotiation.getState()) {
                case OFFERED:
                    log.info("Processing ACNC0305 - OFFERED -> ACCEPTED : {}", contractNegotiation.getId());
                    apiService.sendContractNegotiationEventMessageAccepted(contractNegotiation.getId());
                    break;
                default:
                    log.info("No action for ACNC0305 in state: {}", contractNegotiation.getState());
            }
        }

        if (target.equalsIgnoreCase("ACNC0306")) {
            switch (contractNegotiation.getState()) {
                case OFFERED:
                    log.info("Processing ACNC0306 - OFFERED -> ACCEPTED : {}", contractNegotiation.getId());
                    apiService.sendContractNegotiationEventMessageAccepted(contractNegotiation.getId());
                    break;
                default:
                    log.info("No action for ACNC0306 in state: {}", contractNegotiation.getState());
            }
        }

    }

    private Offer swapOfferIdWithOriginalId(Offer offer) {
        return Offer.Builder.newInstance()
                .id(offer.getOriginalId())
                .assignee(offer.getAssignee())
                .assigner(offer.getAssigner())
                .permission(offer.getPermission())
                .target(offer.getTarget())
                .build();
    }
}
