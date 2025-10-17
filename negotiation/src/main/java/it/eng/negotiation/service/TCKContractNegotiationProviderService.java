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
    private boolean isFirstTimeACN0304 = true;

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
     * Get a contract negotiation by its id.
     *
     * @param id the negotiation id
     * @return the ContractNegotiation or null if not found
     */
    @Override
    public ContractNegotiation getNegotiationById(String id) {
        log.info("getNegotiationById TCK stub called, id: {}", id);
        return super.getNegotiationById(id);
    }

    /**
     * Get a contract negotiation by the provider PID.
     *
     * @param providerPid the provider PID
     * @return the ContractNegotiation or null if not found
     */
    @Override
    public ContractNegotiation getNegotiationByProviderPid(String providerPid) {
        log.info("getNegotiationByProviderPid TCK stub called, providerPid: {}", providerPid);
        return super.getNegotiationByProviderPid(providerPid);
    }

    /**
     * Start a new contract negotiation from a ContractRequestMessage.
     *
     * @param contractRequestMessage the incoming contract request message
     * @return the created ContractNegotiation
     */
    @Override
    public ContractNegotiation handleInitialContractRequestMessage(ContractRequestMessage contractRequestMessage) {
        log.info("startContractNegotiation TCK stub called, {}", NegotiationSerializer.serializeProtocol(contractRequestMessage));

        ContractNegotiation contractNegotiation = super.handleInitialContractRequestMessage(contractRequestMessage);

        log.info("TCK profile running - publishing event - {}", contractNegotiation.getState());
        log.info("ConsumerPid: {}, ProviderPid: {}", contractNegotiation.getConsumerPid(), contractNegotiation.getProviderPid());
        applicationEventPublisher.publishEvent(contractNegotiation);

        return contractNegotiation;
    }

    /**
     * Verify a contract agreement verification message.
     *
     * @param cavm the ContractAgreementVerificationMessage to verify
     * @return
     */
    @Override
    public ContractNegotiation verifyNegotiation(ContractAgreementVerificationMessage cavm) {
        log.info("verifyNegotiation TCK stub called, {}", NegotiationSerializer.serializeProtocol(cavm));
        ContractNegotiation contractNegotiation = super.verifyNegotiation(cavm);

        log.info("TCK profile running - publishing event - {}", contractNegotiation.getState());
        log.info("ConsumerPid: {}, ProviderPid: {}", contractNegotiation.getConsumerPid(), contractNegotiation.getProviderPid());
        applicationEventPublisher.publishEvent(contractNegotiation);

        return contractNegotiation;
    }

    /**
     * Handle an incoming contract negotiation event message.
     *
     * @param contractNegotiationEventMessage the event message to handle
     * @return the resulting ContractNegotiation after handling the event
     */
    @Override
    public ContractNegotiation handleContractNegotiationEventMessage(ContractNegotiationEventMessage contractNegotiationEventMessage) {
        log.info("handleContractNegotiationEventMessage TCK stub called, {}", NegotiationSerializer.serializeProtocol(contractNegotiationEventMessage));
        ContractNegotiation contractNegotiation = super.handleContractNegotiationEventMessage(contractNegotiationEventMessage);

        log.info("TCK profile running - publishing event - {}", contractNegotiation.getState());
        log.info("ConsumerPid: {}, ProviderPid: {}", contractNegotiation.getConsumerPid(), contractNegotiation.getProviderPid());
        applicationEventPublisher.publishEvent(contractNegotiation);

        return contractNegotiation;
    }

    /**
     * Handle a termination request for a negotiation.
     *
     * @param providerPid the provider PID related to the negotiation
     * @param contractNegotiationTerminationMessage the termination message
     */
    @Override
    public void handleTerminationRequest(String providerPid, ContractNegotiationTerminationMessage contractNegotiationTerminationMessage) {
        log.info("handleTerminationRequest TCK stub called, providerPid: {}", providerPid);
        super.handleTerminationRequest(providerPid, contractNegotiationTerminationMessage);
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
        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0104")
                && contractNegotiation.getState().equals(ContractNegotiationState.REQUESTED)) {
            log.info("Processing ACN0104 - REQUESTED -> AGREED : {}", contractNegotiation.getOffer().getTarget());
            ContractNegotiation result = apiService.approveContractNegotiation(contractNegotiation.getId());
            applicationEventPublisher.publishEvent(result);
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0104")
                && contractNegotiation.getState().equals(ContractNegotiationState.REQUESTED)) {
            log.info("Processing ACN0104 - REQUESTED -> TERMINATED : {}", contractNegotiation.getId());
            apiService.terminateContractNegotiation(contractNegotiation.getId());
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0201")
                && contractNegotiation.getState().equals(ContractNegotiationState.REQUESTED)) {
            log.info("Processing ACN0201 - REQUESTED -> TERMINATED : {}", contractNegotiation.getId());
            apiService.terminateContractNegotiation(contractNegotiation.getId());
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0203")
                && contractNegotiation.getState().equals(ContractNegotiationState.REQUESTED)) {
            log.info("Processing {} - REQUESTED -> AGREED : {}", contractNegotiation.getOffer().getTarget(), contractNegotiation.getId());
            ContractNegotiation result = apiService.approveContractNegotiation(contractNegotiation.getId());
            applicationEventPublisher.publishEvent(result);
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0204")
                && contractNegotiation.getState().equals(ContractNegotiationState.REQUESTED)) {
            log.info("Processing ACN0204 - REQUESTED -> OFFERED: {}", contractNegotiation.getId());
            apiService.sendContractOfferMessage(new ContractRequestMessageRequest(contractNegotiation.getId(), contractNegotiation.getCallbackAddress(), NegotiationSerializer.serializePlainJsonNode(swapOfferIdWithOriginalId(contractNegotiation.getOffer()))));
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0205")
                && contractNegotiation.getState().equals(ContractNegotiationState.REQUESTED)) {
            log.info("Processing ACN0205 - REQUESTED -> OFFERED: {}", contractNegotiation.getId());
            ContractNegotiation result = apiService.sendContractOfferMessage(new ContractRequestMessageRequest(contractNegotiation.getId(), contractNegotiation.getCallbackAddress(), NegotiationSerializer.serializePlainJsonNode(swapOfferIdWithOriginalId(contractNegotiation.getOffer()))));
            applicationEventPublisher.publishEvent(result);
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0205")
                && contractNegotiation.getState().equals(ContractNegotiationState.OFFERED)) {
            log.info("Processing ACN0205 - OFFERED -> TERMINATED: {}", contractNegotiation.getId());
            apiService.terminateContractNegotiation(contractNegotiation.getId());
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0206")
                && contractNegotiation.getState().equals(ContractNegotiationState.REQUESTED)) {
            log.info("Processing ACN0206 - REQUESTED -> OFFERED: {}", contractNegotiation.getId());
            ContractNegotiation result = apiService.sendContractOfferMessage(new ContractRequestMessageRequest(contractNegotiation.getId(), contractNegotiation.getCallbackAddress(), NegotiationSerializer.serializePlainJsonNode(swapOfferIdWithOriginalId(contractNegotiation.getOffer()))));
            applicationEventPublisher.publishEvent(result);
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0206")
                && contractNegotiation.getState().equals(ContractNegotiationState.ACCEPTED)) {
            log.info("Processing ACN0206 - ACCEPTED -> TERMINATED: {}", contractNegotiation.getId());
            apiService.terminateContractNegotiation(contractNegotiation.getId());
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0207")
                && contractNegotiation.getState().equals(ContractNegotiationState.REQUESTED)) {
            log.info("Processing {} - REQUESTED -> AGREED : {}", contractNegotiation.getOffer().getTarget(), contractNegotiation.getId());
            ContractNegotiation result = apiService.approveContractNegotiation(contractNegotiation.getId());
            applicationEventPublisher.publishEvent(result);
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0207")
                && contractNegotiation.getState().equals(ContractNegotiationState.VERIFIED)) {
            log.info("Processing {} - VERIFIED -> TERMINATED : {}", contractNegotiation.getOffer().getTarget(), contractNegotiation.getId());
            apiService.terminateContractNegotiation(contractNegotiation.getId());
        }

        if ( contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0301")
                && contractNegotiation.getState().equals(ContractNegotiationState.REQUESTED)) {
            log.info("Processing {} - REQUESTED -> AGREED : {}", contractNegotiation.getOffer().getTarget(), contractNegotiation.getId());
            ContractNegotiation result = apiService.approveContractNegotiation(contractNegotiation.getId());
            applicationEventPublisher.publishEvent(result);
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0301")
                && contractNegotiation.getState().equals(ContractNegotiationState.VERIFIED)) {
            log.info("Processing {} - VERIFIED -> FINALIZED : {}", contractNegotiation.getOffer().getTarget(), contractNegotiation.getId());
            apiService.finalizeNegotiation(contractNegotiation.getId());
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0302")
                && contractNegotiation.getState().equals(ContractNegotiationState.REQUESTED)) {
            log.info("Processing ACN0302 - REQUESTED -> OFFERED: {}", contractNegotiation.getId());
            apiService.sendContractOfferMessage(new ContractRequestMessageRequest(contractNegotiation.getId(), contractNegotiation.getCallbackAddress(), NegotiationSerializer.serializePlainJsonNode(swapOfferIdWithOriginalId(contractNegotiation.getOffer()))));
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0303")
                && contractNegotiation.getState().equals(ContractNegotiationState.REQUESTED)) {
            log.info("Processing ACN0303 - REQUESTED -> OFFERED: {}", contractNegotiation.getId());
            apiService.sendContractOfferMessage(new ContractRequestMessageRequest(contractNegotiation.getId(), contractNegotiation.getCallbackAddress(), NegotiationSerializer.serializePlainJsonNode(swapOfferIdWithOriginalId(contractNegotiation.getOffer()))));
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0304")
                && contractNegotiation.getState().equals(ContractNegotiationState.REQUESTED)) {
            log.info("Processing ACN0304 - REQUESTED -> OFFERED: {}", contractNegotiation.getId());
            isFirstTimeACN0304 = false;
            apiService.sendContractOfferMessage(new ContractRequestMessageRequest(contractNegotiation.getId(), contractNegotiation.getCallbackAddress(), NegotiationSerializer.serializePlainJsonNode(swapOfferIdWithOriginalId(contractNegotiation.getOffer()))));
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACNC0101")
                && contractNegotiation.getState().equals(ContractNegotiationState.OFFERED)) {
            log.info("Processing ACNC0101 - OFFERED -> ACCEPTED : {}", contractNegotiation.getId());
            ContractNegotiation result = apiService.sendContractNegotiationEventMessage(contractNegotiation, ContractNegotiationEventType.ACCEPTED);
            applicationEventPublisher.publishEvent(result);
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0101")
                && contractNegotiation.getState().equals(ContractNegotiationState.REQUESTED)) {
            log.info("Processing {} - REQUESTED -> OFFERED : {}", contractNegotiation.getOffer().getTarget(), contractNegotiation.getId());
            ContractNegotiation result = apiService.sendContractOfferMessage(new ContractRequestMessageRequest(contractNegotiation.getId(), contractNegotiation.getCallbackAddress(), NegotiationSerializer.serializePlainJsonNode(swapOfferIdWithOriginalId(contractNegotiation.getOffer()))));
            applicationEventPublisher.publishEvent(result);
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0102")
                && contractNegotiation.getState().equals(ContractNegotiationState.REQUESTED)) {
            log.info("Processing {} - REQUESTED -> OFFERED : {}", contractNegotiation.getOffer().getTarget(), contractNegotiation.getId());
            ContractNegotiation result = apiService.sendContractOfferMessage(new ContractRequestMessageRequest(contractNegotiation.getId(), contractNegotiation.getCallbackAddress(), NegotiationSerializer.serializePlainJsonNode(swapOfferIdWithOriginalId(contractNegotiation.getOffer()))));
            applicationEventPublisher.publishEvent(result);
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0102")
                && contractNegotiation.getState().equals(ContractNegotiationState.OFFERED)) {
            log.info("Processing {} - OFFERED -> TERMINATED : {}", contractNegotiation.getOffer().getTarget(), contractNegotiation.getId());
            ContractNegotiation result = apiService.terminateContractNegotiation(contractNegotiation.getId());
            applicationEventPublisher.publishEvent(result);
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0103")
                && contractNegotiation.getState().equals(ContractNegotiationState.REQUESTED)) {
            log.info("Processing {} - REQUESTED -> OFFERED : {}", contractNegotiation.getOffer().getTarget(), contractNegotiation.getId());
            ContractNegotiation result = apiService.sendContractOfferMessage(new ContractRequestMessageRequest(contractNegotiation.getId(), contractNegotiation.getCallbackAddress(), NegotiationSerializer.serializePlainJsonNode(swapOfferIdWithOriginalId(contractNegotiation.getOffer()))));
            applicationEventPublisher.publishEvent(result);
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0103")
                && contractNegotiation.getState().equals(ContractNegotiationState.ACCEPTED)) {
            log.info("Processing {} - ACCEPTED -> AGREED : {}", contractNegotiation.getOffer().getTarget(), contractNegotiation.getId());
            ContractNegotiation result = apiService.approveContractNegotiation(contractNegotiation.getId());
            applicationEventPublisher.publishEvent(result);
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0103")
                && contractNegotiation.getState().equals(ContractNegotiationState.VERIFIED)) {
            log.info("Processing {} - VERIFIED -> FINALIZED : {}", contractNegotiation.getOffer().getTarget(), contractNegotiation.getId());
            apiService.finalizeNegotiation(contractNegotiation.getId());
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0104")
                && contractNegotiation.getState().equals(ContractNegotiationState.VERIFIED)) {
            log.info("Processing {} - VERIFIED -> FINALIZED : {}", contractNegotiation.getOffer().getTarget(), contractNegotiation.getId());
            apiService.finalizeNegotiation(contractNegotiation.getId());
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACNC0101")
                && contractNegotiation.getState().equals(ContractNegotiationState.AGREED)) {
            log.info("Processing ACNC0101 - AGREED -> VERIFIED : {}", contractNegotiation.getId());
            apiService.verifyNegotiation(contractNegotiation.getId());
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACNC0102")
                && contractNegotiation.getState().equals(ContractNegotiationState.OFFERED)) {
            log.info("Processing {} - OFFERED -> REQUESTED : {}", contractNegotiation.getOffer().getTarget(), contractNegotiation.getId());
            apiService.sendContractRequestMessage(new ContractRequestMessageRequest(contractNegotiation.getId(), contractNegotiation.getCallbackAddress(), NegotiationSerializer.serializePlainJsonNode(swapOfferIdWithOriginalId(contractNegotiation.getOffer()))));
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACNC0103")
                && contractNegotiation.getState().equals(ContractNegotiationState.OFFERED)) {
            log.info("Processing {} - OFFERED -> TERMINATED : {}", contractNegotiation.getOffer().getTarget(), contractNegotiation.getId());
            apiService.terminateContractNegotiation(contractNegotiation.getId());
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACNC0104")
                && contractNegotiation.getState().equals(ContractNegotiationState.AGREED)) {
            log.info("Processing ACNC0104 - AGREED -> VERIFIED : {}", contractNegotiation.getId());
            apiService.verifyNegotiation(contractNegotiation.getId());
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACNC0202")
                && contractNegotiation.getState().equals(ContractNegotiationState.REQUESTED)) {
            log.info("Processing {} - REQUESTED -> TERMINATED : {}", contractNegotiation.getOffer().getTarget(), contractNegotiation.getId());
            apiService.terminateContractNegotiation(contractNegotiation.getId());
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACNC0203")
                && contractNegotiation.getState().equals(ContractNegotiationState.AGREED)) {
            log.info("Processing {} - AGREED -> TERMINATED : {}", contractNegotiation.getOffer().getTarget(), contractNegotiation.getId());
            apiService.terminateContractNegotiation(contractNegotiation.getId());
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACNC0205")
                && contractNegotiation.getState().equals(ContractNegotiationState.OFFERED)) {
            log.info("Processing {} - OFFERED -> ACCEPTED : {}", contractNegotiation.getOffer().getTarget(), contractNegotiation.getId());
            apiService.handleContractNegotiationAccepted(contractNegotiation.getId());
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACNC0206")
                && contractNegotiation.getState().equals(ContractNegotiationState.AGREED)) {
            log.info("Processing {} - AGREED -> VERIFIED : {}", contractNegotiation.getOffer().getTarget(), contractNegotiation.getId());
            apiService.verifyNegotiation(contractNegotiation.getId());
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACNC0304")
                && contractNegotiation.getState().equals(ContractNegotiationState.OFFERED)) {
            log.info("Processing {} - OFFERED -> ACCEPTED : {}", contractNegotiation.getOffer().getTarget(), contractNegotiation.getId());
            apiService.handleContractNegotiationAccepted(contractNegotiation.getId());
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACNC0305")
                && contractNegotiation.getState().equals(ContractNegotiationState.OFFERED)) {
            log.info("Processing {} - OFFERED -> ACCEPTED : {}", contractNegotiation.getOffer().getTarget(), contractNegotiation.getId());
            apiService.handleContractNegotiationAccepted(contractNegotiation.getId());
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACNC0306")
                && contractNegotiation.getState().equals(ContractNegotiationState.OFFERED)) {
            log.info("Processing {} - OFFERED -> ACCEPTED : {}", contractNegotiation.getOffer().getTarget(), contractNegotiation.getId());
            apiService.handleContractNegotiationAccepted(contractNegotiation.getId());
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
