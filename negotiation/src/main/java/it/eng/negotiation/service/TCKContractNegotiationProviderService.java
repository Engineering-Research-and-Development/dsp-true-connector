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
public class TCKContractNegotiationProviderService extends ContractNegotiationProviderService implements ContractNegotiationProviderStrategy {

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
     * @param id
     * @return
     */
    @Override
    public ContractNegotiation getNegotiationById(String id) {
        log.info("getNegotiationById TCK stub called, id: {}", id);
        return super.getNegotiationById(id);
    }

    /**
     * @param providerPid
     * @return
     */
    @Override
    public ContractNegotiation getNegotiationByProviderPid(String providerPid) {
        log.info("getNegotiationByProviderPid TCK stub called, providerPid: {}", providerPid);
        return super.getNegotiationByProviderPid(providerPid);
    }

    /**
     * @param contractRequestMessage
     * @return
     */
    @Override
    public ContractNegotiation startContractNegotiation(ContractRequestMessage contractRequestMessage) {
        log.info("startContractNegotiation TCK stub called, {}", NegotiationSerializer.serializeProtocol(contractRequestMessage));

        return super.startContractNegotiation(contractRequestMessage);
    }

    /**
     * @param cavm
     */
    @Override
    public void verifyNegotiation(ContractAgreementVerificationMessage cavm) {
        log.info("verifyNegotiation TCK stub called, {}", NegotiationSerializer.serializeProtocol(cavm));
        super.verifyNegotiation(cavm);
    }

    /**
     * @param contractNegotiationEventMessage
     * @return
     */
    @Override
    public ContractNegotiation handleContractNegotiationEventMessage(ContractNegotiationEventMessage contractNegotiationEventMessage) {
        log.info("handleContractNegotiationEventMessage TCK stub called, {}", NegotiationSerializer.serializeProtocol(contractNegotiationEventMessage));
        return super.handleContractNegotiationEventMessage(contractNegotiationEventMessage);
    }

    /**
     * @param providerPid
     * @param contractNegotiationTerminationMessage
     */
    @Override
    public void handleTerminationRequest(String providerPid, ContractNegotiationTerminationMessage contractNegotiationTerminationMessage) {
        log.info("handleTerminationRequest TCK stub called, providerPid: {}", providerPid);
        super.handleTerminationRequest(providerPid, contractNegotiationTerminationMessage);
    }

    @EventListener(classes = ContractNegotiation.class)
    public void onTransferProcessEvent(ContractNegotiation contractNegotiation) {
        log.info("TCKDataTransferService received event for Agreement id: {} with state {}", contractNegotiation.getOffer().getTarget(), contractNegotiation.getState());
        log.info("ConsumerPid: {}, ProviderPid: {}", contractNegotiation.getConsumerPid(), contractNegotiation.getProviderPid());
        try {
            Thread.sleep(2000);
            log.info("sleep over");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if ((
//                contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0101")
//                || contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0102")
//                || contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0103")
//                || contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0104")
                 contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0203")
                || contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0207")
//                || contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0303")
//                || contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0304")
//                || contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0305")
//                || contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0306")
    )
                && contractNegotiation.getState().equals(ContractNegotiationState.REQUESTED)) {
            log.info("Processing {} - REQUESTED -> AGREED : {}", contractNegotiation.getOffer().getTarget(), contractNegotiation.getId());
            ContractNegotiation result = apiService.approveContractNegotiation(contractNegotiation.getId());
            applicationEventPublisher.publishEvent(result);
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0201")
                && contractNegotiation.getState().equals(ContractNegotiationState.REQUESTED)) {
            log.info("Processing {} - REQUESTED -> TERMINATED : {}", contractNegotiation.getOffer().getTarget(), contractNegotiation.getId());
            apiService.terminateContractNegotiation(contractNegotiation.getId());
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0207")
                && contractNegotiation.getState().equals(ContractNegotiationState.VERIFIED)) {
            log.info("Processing {} - VERIFIED -> TERMINATED : {}", contractNegotiation.getOffer().getTarget(), contractNegotiation.getId());
            apiService.terminateContractNegotiation(contractNegotiation.getId());
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0204")
                && contractNegotiation.getState().equals(ContractNegotiationState.REQUESTED)) {
            log.info("Processing ACN0203 - REQUESTED -> AGREED: {}", contractNegotiation.getId());
            apiService.terminateContractNegotiation(contractNegotiation.getId());
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0205")
                && contractNegotiation.getState().equals(ContractNegotiationState.REQUESTED)) {
            log.info("Processing ACN0203 - REQUESTED -> AGREED: {}", contractNegotiation.getId());
            apiService.terminateContractNegotiation(contractNegotiation.getId());
        }

        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0206")
                && contractNegotiation.getState().equals(ContractNegotiationState.REQUESTED)) {
            log.info("Processing ACN0203 - REQUESTED -> AGREED: {}", contractNegotiation.getId());
            apiService.terminateContractNegotiation(contractNegotiation.getId());
        }
//        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0102") && contractNegotiation.getState().equals(ContractNegotiationState.STARTED)) {
//            log.info("Processing ACN0101 - STARTED -> TERMINATED: {}", contractNegotiation.getId());
//            apiService.completeTransfer(contractNegotiation.getId());
//        }
//        if ((contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0103") ||
//                contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0104"))
//                && contractNegotiation.getState().equals(ContractNegotiationState.STARTED)) {
//            log.info("Processing {} - STARTED -> SUSPENDED", contractNegotiation.getOffer().getTarget());
//            JsonNode jsonNode = apiService.suspendTransfer(contractNegotiation.getId());
//            // need to transit to TERMINATED state
//            applicationEventPublisher.publishEvent(NegotiationSerializer.deserializePlain(jsonNode, contractNegotiation.class));
//        }
//
//        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0104")
//                && contractNegotiation.getState().equals(ContractNegotiationState.SUSPENDED)) {
//            apiService.startTransfer(contractNegotiation.getId());
//            Thread.sleep(2000);
//            apiService.completeTransfer(contractNegotiation.getId());
//
//        }
//        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0103")
//                && contractNegotiation.getState().equals(ContractNegotiationState.SUSPENDED)) {
//            log.info("Processing ACN0101 - SUSPENDED -> TERMINATE: {}", contractNegotiation.getId());
//            apiService.terminateTransfer(contractNegotiation.getId());
//        }
//        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0105") && contractNegotiation.getState().equals(ContractNegotiationState.REQUESTED)) {
//            log.info("Processing ACN0105 - REQUESTED -> TERMINATED: {}", contractNegotiation.getId());
//            apiService.terminateTransfer(contractNegotiation.getId());
//        }

//        if (contractNegotiation.getOffer().getTarget().equalsIgnoreCase("ACN0303") && transferProcess.getState().equals(ContractNegotiationState.STARTED)) {
//            log.info("Processing ACN0303 - STARTED -> SUSPENDED: {}", transferProcess.getId());
//            apiService.suspendTransfer(transferProcess.getId());
//        }
    }
}

