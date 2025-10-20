package it.eng.negotiation.service;

import it.eng.negotiation.exception.ContractNegotiationAPIException;
import it.eng.negotiation.exception.ContractNegotiationInvalidEventTypeException;
import it.eng.negotiation.exception.ContractNegotiationNotFoundException;
import it.eng.negotiation.exception.OfferNotFoundException;
import it.eng.negotiation.model.*;
import it.eng.negotiation.policy.service.PolicyAdministrationPoint;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.OfferRepository;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.event.datatransfer.InitializeTransferProcess;
import it.eng.tools.model.IConstants;
import it.eng.tools.service.AuditEventPublisher;
import it.eng.tools.util.ToolsUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public abstract class ContractNegotiationConsumerService extends BaseProtocolService implements ContractNegotiationConsumerStrategy {

    private final AgreementRepository agreementRepository;
    private final PolicyAdministrationPoint policyAdministrationPoint;

    public ContractNegotiationConsumerService(AuditEventPublisher publisher,
                                              ContractNegotiationRepository contractNegotiationRepository, OkHttpRestClient okHttpRestClient,
                                              ContractNegotiationProperties properties, OfferRepository offerRepository,
                                              AgreementRepository agreementRepository, PolicyAdministrationPoint policyAdministrationPoint) {
        super(publisher, contractNegotiationRepository, okHttpRestClient, properties, offerRepository);
        this.agreementRepository = agreementRepository;
        this.policyAdministrationPoint = policyAdministrationPoint;
    }

    /**
     * Process contract offer.
     *
     * @param consumerPid
     * @param contractOfferMessage
     * @return ContractNegotiation as JsonNode
     */
    @Override
    public ContractNegotiation handleContractOfferMessage(String consumerPid, ContractOfferMessage contractOfferMessage) {
        Optional<ContractNegotiation> existingContractNegotiation =
                contractNegotiationRepository.findByConsumerPid(consumerPid);

        ContractNegotiation contractNegotiation = null;
        if (existingContractNegotiation.isPresent()) {
            if (!existingContractNegotiation.get().getConsumerPid().equals(contractOfferMessage.getConsumerPid())) {
            throw new ContractNegotiationNotFoundException(
                                "Contract negotiation with providerPid " + contractOfferMessage.getProviderPid() +
                                        " has a different consumerPid " + existingContractNegotiation.get().getConsumerPid() +
                                        "than the one received in the message", contractOfferMessage.getConsumerPid());
            }

            if (!existingContractNegotiation.get().getOffer().getOriginalId().equals(contractOfferMessage.getOffer().getId()) || !existingContractNegotiation.get().getOffer().getTarget().equals(contractOfferMessage.getOffer().getTarget())) {
                throw new ContractNegotiationAPIException("New offer must have same offer id and target as the existing one in the contract negotiation with id: " + existingContractNegotiation.get().getId());
            }

            stateTransitionCheck(ContractNegotiationState.OFFERED, existingContractNegotiation.get());

            Offer updatedOffer = Offer.Builder.newInstance()
                    .id(existingContractNegotiation.get().getOffer().getId())
                    .originalId(existingContractNegotiation.get().getOffer().getOriginalId())
                    .permission(contractOfferMessage.getOffer().getPermission())
                    .assigner(contractOfferMessage.getOffer().getAssigner())
                    .assignee(contractOfferMessage.getOffer().getAssignee())
                    .target(contractOfferMessage.getOffer().getTarget())
                    .build();
            offerRepository.save(updatedOffer);

            log.info("Provider sent a counter offer for ContractNegotiation with id {}, updating existing one", existingContractNegotiation.get().getId());
            contractNegotiation = ContractNegotiation.Builder.newInstance()
                    .id(existingContractNegotiation.get().getId())
                    .consumerPid(contractOfferMessage.getConsumerPid())
                    .providerPid(contractOfferMessage.getProviderPid())
                    .state(ContractNegotiationState.OFFERED)
                    .role(IConstants.ROLE_CONSUMER)
                    .offer(updatedOffer)
                    .assigner(contractOfferMessage.getOffer().getAssigner())
                    .callbackAddress(contractOfferMessage.getCallbackAddress() != null ? contractOfferMessage.getCallbackAddress() : existingContractNegotiation.get().getCallbackAddress())
                    .agreement(existingContractNegotiation.get().getAgreement())
                    .created(existingContractNegotiation.get().getCreated())
                    .createdBy(existingContractNegotiation.get().getCreatedBy())
                    .version(existingContractNegotiation.get().getVersion())
                    .build();
        }


        if (existingContractNegotiation.isEmpty()) {
            log.info("No ContractNegotiation found with providerPid {}, creating a new one", contractOfferMessage.getProviderPid());
            contractNegotiation = ContractNegotiation.Builder.newInstance()
                    .consumerPid(ToolsUtil.generateUniqueId())
                    .providerPid(contractOfferMessage.getProviderPid())
                    .state(ContractNegotiationState.OFFERED)
                    .role(IConstants.ROLE_CONSUMER)
                    .offer(contractOfferMessage.getOffer())
                    .assigner(contractOfferMessage.getOffer().getAssigner())
                    .callbackAddress(contractOfferMessage.getCallbackAddress())
                    .build();
        }

        offerRepository.save(contractNegotiation.getOffer());
        contractNegotiationRepository.save(contractNegotiation);
        return contractNegotiation;
    }

    /**
     * The response body is not specified and clients are not required to process it.
     *
     * @param contractAgreementMessage
     * @return
     */

    @Override
    public ContractNegotiation handleContractAgreementMessage(ContractAgreementMessage contractAgreementMessage) {
        // save callbackAddress into ContractNegotiation - used for sending ContractNegotiationEventMessage.FINALIZED
        ContractNegotiation contractNegotiation = findContractNegotiationByPids(contractAgreementMessage.getConsumerPid(), contractAgreementMessage.getProviderPid());

        stateTransitionCheck(ContractNegotiationState.AGREED, contractNegotiation);

        if (contractNegotiation.getOffer() == null) {
            throw new OfferNotFoundException("For ContractNegotiation with consumerPid {} and providerPid {} Offer does not exists",
                    contractNegotiation.getConsumerPid(), contractNegotiation.getProviderPid());
        }

//    	Must do like this since callbackAddress might be null
        ContractNegotiation contractNegotiationAgreed = ContractNegotiation.Builder.newInstance()
                .id(contractNegotiation.getId())
                .consumerPid(contractNegotiation.getConsumerPid())
                .providerPid(contractNegotiation.getProviderPid())
                .callbackAddress(contractNegotiation.getCallbackAddress())
                .assigner(contractNegotiation.getAssigner())
                .state(ContractNegotiationState.AGREED)
                .role(contractNegotiation.getRole())
                .offer(contractNegotiation.getOffer())
                .agreement(contractAgreementMessage.getAgreement())
                .created(contractNegotiation.getCreated())
                .createdBy(contractNegotiation.getCreatedBy())
                .modified(contractNegotiation.getModified())
                .lastModifiedBy(contractNegotiation.getLastModifiedBy())
                .version(contractNegotiation.getVersion())
                .build();
        log.info("CONSUMER - updating negotiation with state AGREED");
        contractNegotiationRepository.save(contractNegotiationAgreed);
        log.info("CONSUMER - negotiation {} updated with state AGREED", contractNegotiationAgreed.getId());
        log.info("CONSUMER - saving agreement");
        agreementRepository.save(contractAgreementMessage.getAgreement());
        log.info("CONSUMER - agreement {} saved", contractAgreementMessage.getAgreement().getId());
        publisher.publishEvent(AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.PROTOCOL_NEGOTIATION_AGREED)
                .description("Contract negotiation agreed")
                .details(Map.of("consumerPid", contractNegotiationAgreed.getConsumerPid(),
                        "providerPid", contractNegotiationAgreed.getProviderPid(),
                        "contractNegotiation", contractNegotiationAgreed,
                        "agreement", contractAgreementMessage.getAgreement(),
                        "role", IConstants.ROLE_CONSUMER))
                .build());

        return contractNegotiationAgreed;
        // sends verification message to provider
        // TODO add error handling in case not correct
//        if (properties.isAutomaticNegotiation()) {
//            log.debug("Automatic negotiation - processing sending ContractAgreementVerificationMessage");
//            ContractAgreementVerificationMessage verificationMessage = ContractAgreementVerificationMessage.Builder.newInstance()
//                    .consumerPid(contractAgreementMessage.getConsumerPid())
//                    .providerPid(contractAgreementMessage.getProviderPid())
//                    .build();
//            publisher.publishEvent(verificationMessage);
//        } else {
//            log.debug("Sending only 200 if agreement is valid, ContractAgreementVerificationMessage must be manually sent");
//        }
    }

    /**
     * The response body is not specified and clients are not required to process it.
     *
     * @param contractNegotiationEventMessage
     */
    @Override
    public void handleFinalizeEvent(ContractNegotiationEventMessage contractNegotiationEventMessage) {
        if (!contractNegotiationEventMessage.getEventType().equals(ContractNegotiationEventType.FINALIZED)) {
            throw new ContractNegotiationInvalidEventTypeException(
                    "Contract negotiation event message with providerPid " + contractNegotiationEventMessage.getProviderPid() +
                            " and consumerPid " + contractNegotiationEventMessage.getConsumerPid() + " event type is not FINALIZED, aborting state transition", contractNegotiationEventMessage.getConsumerPid(), contractNegotiationEventMessage.getProviderPid());
        }

        ContractNegotiation contractNegotiation = findContractNegotiationByPids(contractNegotiationEventMessage.getConsumerPid(), contractNegotiationEventMessage.getProviderPid());

        stateTransitionCheck(ContractNegotiationState.FINALIZED, contractNegotiation);

        log.info("CONSUMER - updating Contract Negotiation state to FINALIZED");
        ContractNegotiation contractNegotiationUpdated = contractNegotiation.withNewContractNegotiationState(ContractNegotiationState.FINALIZED);
        log.info("CONSUMER - saving updated contract negotiation");
        contractNegotiationRepository.save(contractNegotiationUpdated);

        log.debug("Creating policy enforcement for agreementId {}", contractNegotiation.getAgreement().getId());
        policyAdministrationPoint.createPolicyEnforcement(contractNegotiation.getAgreement().getId());
        publisher.publishEvent(new InitializeTransferProcess(
                contractNegotiationUpdated.getCallbackAddress(),
                contractNegotiationUpdated.getAgreement().getId(),
                contractNegotiationUpdated.getAgreement().getTarget(),
                contractNegotiationUpdated.getRole()
        ));
        publisher.publishEvent(AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.PROTOCOL_NEGOTIATION_FINALIZED)
                .description("Contract negotiation finalized")
                .details(Map.of("consumerPid", contractNegotiationUpdated.getConsumerPid(),
                        "providerPid", contractNegotiationUpdated.getProviderPid(),
                        "contractNegotiation", contractNegotiationUpdated,
                        "role", IConstants.ROLE_CONSUMER))
                .build());
    }

    /**
     * The response body is not specified and clients are not required to process it.
     *
     * @param consumerPid                           consumer PID
     * @param contractNegotiationTerminationMessage contract negotiation termination message
     */
    @Override
    public void handleTerminationRequest(String consumerPid, ContractNegotiationTerminationMessage contractNegotiationTerminationMessage) {
        ContractNegotiation contractNegotiation = contractNegotiationRepository.findByConsumerPid(consumerPid)
                .orElseThrow(() -> new ContractNegotiationNotFoundException(
                        "Contract negotiation with providerPid " + contractNegotiationTerminationMessage.getProviderPid() +
                                " and consumerPid " + consumerPid + " not found", consumerPid, contractNegotiationTerminationMessage.getProviderPid()));

        stateTransitionCheck(ContractNegotiationState.TERMINATED, contractNegotiation);

        ContractNegotiation contractNegotiationTerminated = contractNegotiation.withNewContractNegotiationState(ContractNegotiationState.TERMINATED);
        contractNegotiationRepository.save(contractNegotiationTerminated);
        log.info("Contract Negotiation with id {} set to TERMINATED state", contractNegotiation.getId());

        publisher.publishEvent(contractNegotiationTerminationMessage);
        publisher.publishEvent(AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.PROTOCOL_NEGOTIATION_TERMINATED)
                .description("Contract negotiation terminated")
                .details(Map.of("consumerPid", contractNegotiationTerminated.getConsumerPid(),
                        "providerPid", contractNegotiationTerminated.getProviderPid(),
                        "contractNegotiation", contractNegotiationTerminated,
                        "role", IConstants.ROLE_CONSUMER))
                .build());
    }

    @Override
    public ContractNegotiation processTCKRequest(TCKContractNegotiationRequest contractNegotiationRequest) {
        return null;
    }

    /**
     * Method to get contract negotiation by consumer pid.
     *
     * @param consumerPid - provider pid
     * @return ContractNegotiation - contract negotiation from DB
     * @throws ContractNegotiationNotFoundException if no contract negotiation is found with the specified provider pid.
     */
    public ContractNegotiation getNegotiationByConsumerPid(String consumerPid) {
        log.info("Getting contract negotiation by consumer pid: {}", consumerPid);
//        publisher.publishEvent(ContractNegotiationEvent.builder().action("Find by consumer pid").description("Searching with consumer pid ").build());
        publisher.publishEvent(AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.PROTOCOL_NEGOTIATION_CONTRACT_NEGOTIATION)
                .description("Searching with consumer pid " + consumerPid)
                .details(Map.of("consumerPid", consumerPid, "role", IConstants.ROLE_CONSUMER))
                .build());
        return contractNegotiationRepository.findByConsumerPid(consumerPid)
                .orElseThrow(() ->
                        new ContractNegotiationNotFoundException("Contract negotiation with consumer pid " + consumerPid + " not found", consumerPid));
    }
}
