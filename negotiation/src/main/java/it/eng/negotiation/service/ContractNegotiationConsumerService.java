package it.eng.negotiation.service;

import it.eng.negotiation.exception.*;
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
     * Method to get contract negotiation by consumer pid.
     *
     * @param consumerPid - provider pid
     * @return ContractNegotiation - contract negotiation from DB
     * @throws ContractNegotiationNotFoundException if no contract negotiation is found with the specified provider pid.
     */
    public ContractNegotiation getNegotiationByConsumerPid(String consumerPid) {
        log.info("Getting contract negotiation by consumer pid: {}", consumerPid);
        publisher.publishEvent(AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.PROTOCOL_NEGOTIATION_CONTRACT_NEGOTIATION)
                .description("Searching with consumer pid " + consumerPid)
                .details(Map.of("consumerPid", consumerPid, "role", IConstants.ROLE_CONSUMER))
                .build());
        return contractNegotiationRepository.findByConsumerPid(consumerPid)
                .orElseThrow(() ->
                        new ContractNegotiationNotFoundException("Contract negotiation with consumer pid " + consumerPid + " not found", consumerPid));
    }

    /**
     * Process contract offer.
     *
     * @param contractOfferMessage - contract offer message
     * @return ContractNegotiation as JsonNode
     */
    @Override
    public ContractNegotiation handleContractOfferMessage(ContractOfferMessage contractOfferMessage) {
        contractNegotiationRepository.findByProviderPid(contractOfferMessage.getProviderPid())
                .ifPresent(existingContractNegotiation -> {
                    throw new ContractNegotiationExistsException("Contract negotiation with providerPid " + contractOfferMessage.getProviderPid() +
                            " already exists");
                });

        log.info("No ContractNegotiation found with providerPid {}, creating a new one", contractOfferMessage.getProviderPid());

        ContractNegotiation contractNegotiation = ContractNegotiation.Builder.newInstance()
                .consumerPid(ToolsUtil.generateUniqueId())
                .providerPid(contractOfferMessage.getProviderPid())
                .state(ContractNegotiationState.OFFERED)
                .role(IConstants.ROLE_CONSUMER)
                .offer(contractOfferMessage.getOffer())
                .assigner(contractOfferMessage.getOffer().getAssigner())
                .callbackAddress(contractOfferMessage.getCallbackAddress())
                .build();

        offerRepository.save(contractNegotiation.getOffer());
        contractNegotiationRepository.save(contractNegotiation);

        publisher.publishEvent(AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.PROTOCOL_NEGOTIATION_REQUESTED)
                .description("Contract negotiation requested")
                .details(Map.of("consumerPid", contractNegotiation.getConsumerPid(),
                        "providerPid", contractNegotiation.getProviderPid(),
                        "contractNegotiation", contractNegotiation,
                        "offer", contractNegotiation.getOffer(),
                        "role", IConstants.ROLE_CONSUMER))
                .build());
        return contractNegotiation;
    }

    /**
     * Process contract offer.
     *
     * @param consumerPid    - consumer pid
     * @param contractOfferMessage - contract offer message
     * @return ContractNegotiation as JsonNode
     */
    @Override
    public ContractNegotiation handleContractOfferMessageAsCounteroffer(String consumerPid, ContractOfferMessage contractOfferMessage) {
        compareConsumerPids(consumerPid, contractOfferMessage.getConsumerPid());

        ContractNegotiation existingContractNegotiation = findContractNegotiationByPids(contractOfferMessage.getConsumerPid(), contractOfferMessage.getProviderPid());


        if (!existingContractNegotiation.getOffer().getOriginalId().equals(contractOfferMessage.getOffer().getId())
                || !existingContractNegotiation.getOffer().getTarget().equals(contractOfferMessage.getOffer().getTarget())) {
            throw new ContractNegotiationAPIException("New offer must have same offer id and target as the existing one in the contract negotiation with id: " + existingContractNegotiation.getId());
        }

        stateTransitionCheck(ContractNegotiationState.OFFERED, existingContractNegotiation);

        Offer updatedOffer = Offer.Builder.newInstance()
                .id(existingContractNegotiation.getOffer().getId())
                .originalId(existingContractNegotiation.getOffer().getOriginalId())
                .permission(contractOfferMessage.getOffer().getPermission())
                .assigner(contractOfferMessage.getOffer().getAssigner())
                .assignee(contractOfferMessage.getOffer().getAssignee())
                .target(contractOfferMessage.getOffer().getTarget())
                .build();
        offerRepository.save(updatedOffer);

        log.info("Provider sent a counter offer for ContractNegotiation with id {}, updating existing one", existingContractNegotiation.getId());
        ContractNegotiation contractNegotiation = ContractNegotiation.Builder.newInstance()
                .id(existingContractNegotiation.getId())
                .consumerPid(contractOfferMessage.getConsumerPid())
                .providerPid(contractOfferMessage.getProviderPid())
                .state(ContractNegotiationState.OFFERED)
                .role(IConstants.ROLE_CONSUMER)
                .offer(updatedOffer)
                .assigner(contractOfferMessage.getOffer().getAssigner())
                .callbackAddress(contractOfferMessage.getCallbackAddress() != null ? contractOfferMessage.getCallbackAddress() : existingContractNegotiation.getCallbackAddress())
                .agreement(existingContractNegotiation.getAgreement())
                .created(existingContractNegotiation.getCreated())
                .createdBy(existingContractNegotiation.getCreatedBy())
                .version(existingContractNegotiation.getVersion())
                .build();

        offerRepository.save(contractNegotiation.getOffer());
        contractNegotiationRepository.save(contractNegotiation);

        publisher.publishEvent(AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.PROTOCOL_NEGOTIATION_REQUESTED)
                .description("Contract negotiation requested - counteroffer")
                .details(Map.of("consumerPid", contractNegotiation.getConsumerPid(),
                        "providerPid", contractNegotiation.getProviderPid(),
                        "contractNegotiation", contractNegotiation,
                        "offer", contractNegotiation.getOffer(),
                        "role", IConstants.ROLE_CONSUMER))
                .build());
        return contractNegotiation;
    }

    /**
     * The response body is not specified and clients are not required to process it.
     *
     * @param contractAgreementMessage - contract agreement message
     * @return ContractNegotiation
     */

    @Override
    public ContractNegotiation handleContractAgreementMessage(String consumerPid, ContractAgreementMessage contractAgreementMessage) {
        compareConsumerPids(consumerPid, contractAgreementMessage.getConsumerPid());

        ContractNegotiation contractNegotiation = findContractNegotiationByPids(contractAgreementMessage.getConsumerPid(), contractAgreementMessage.getProviderPid());

        stateTransitionCheck(ContractNegotiationState.AGREED, contractNegotiation);

        if (contractNegotiation.getOffer() == null) {
            throw new OfferNotFoundException("For ContractNegotiation with consumerPid {} and providerPid {} Offer does not exists",
                    contractNegotiation.getConsumerPid(), contractNegotiation.getProviderPid());
        }

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
        log.info("CONSUMER - saving agreement");
        agreementRepository.save(contractAgreementMessage.getAgreement());
        log.info("CONSUMER - agreement {} saved", contractAgreementMessage.getAgreement().getId());
        log.info("CONSUMER - updating negotiation with state AGREED");
        contractNegotiationRepository.save(contractNegotiationAgreed);
        log.info("CONSUMER - negotiation {} updated with state AGREED", contractNegotiationAgreed.getId());
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
     * @param contractNegotiationEventMessage - contract negotiation event message
     */
    @Override
    public void handleContractNegotiationEventMessageFinalize(String consumerPid, ContractNegotiationEventMessage contractNegotiationEventMessage) {
        compareConsumerPids(consumerPid, contractNegotiationEventMessage.getConsumerPid());

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
    public void handleContractNegotiationTerminationMessage(String consumerPid, ContractNegotiationTerminationMessage contractNegotiationTerminationMessage) {
        compareConsumerPids(consumerPid, contractNegotiationTerminationMessage.getConsumerPid());

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

    private void compareConsumerPids(String consumerPid, String consumerPidFromMessage) {
        if (consumerPidFromMessage == null || !consumerPidFromMessage.equals(consumerPid)) {
            throw new ContractNegotiationNotFoundException(
                    "The consumerPid in the ContractOfferMessage " + consumerPidFromMessage
                            + " is different from the one in the path parameter " + consumerPid);
        }
    }

    @Override
    public ContractNegotiation processTCKRequest(TCKContractNegotiationRequest contractNegotiationRequest) {
        return null;
    }
}
