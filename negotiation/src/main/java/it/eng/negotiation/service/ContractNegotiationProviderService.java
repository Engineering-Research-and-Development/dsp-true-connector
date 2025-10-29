package it.eng.negotiation.service;

import it.eng.negotiation.event.ContractNegotiationEvent;
import it.eng.negotiation.exception.*;
import it.eng.negotiation.model.*;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.OfferRepository;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.event.contractnegotiation.ContractNegotationOfferRequestEvent;
import it.eng.tools.model.IConstants;
import it.eng.tools.property.ConnectorProperties;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.service.AuditEventPublisher;
import it.eng.tools.util.CredentialUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public abstract class ContractNegotiationProviderService extends BaseProtocolService implements ContractNegotiationProviderStrategy {

    private final ConnectorProperties connectorProperties;

    protected final CredentialUtils credentialUtils;

    public ContractNegotiationProviderService(AuditEventPublisher publisher, ConnectorProperties connectorProperties,
                                              ContractNegotiationRepository contractNegotiationRepository, OkHttpRestClient okHttpRestClient,
                                              ContractNegotiationProperties properties, OfferRepository offerRepository,
                                              CredentialUtils credentialUtils) {
        super(publisher, contractNegotiationRepository, okHttpRestClient, properties, offerRepository);
        this.credentialUtils = credentialUtils;
        this.connectorProperties = connectorProperties;
    }

    /**
     * Method to get a contract negotiation by its unique identifier.
     * If no contract negotiation is found with the given ID, it throws a not found exception.
     *
     * @param id - provider pid
     * @return ContractNegotiation - contract negotiation from DB
     * @throws ContractNegotiationNotFoundException if no contract negotiation is found with the specified ID.
     */
    public ContractNegotiation getNegotiationById(String id) {
        publisher.publishEvent(ContractNegotiationEvent.builder().action("Find by id").description("Searching with id").build());
        return findContractNegotiationById(id);
    }

    /**
     * Method to get contract negotiation by provider pid, without callback address.
     *
     * @param providerPid - provider pid
     * @return ContractNegotiation - contract negotiation from DB
     * @throws ContractNegotiationNotFoundException if no contract negotiation is found with the specified provider pid.
     */
    public ContractNegotiation getNegotiationByProviderPid(String providerPid) {
        log.info("Getting contract negotiation by provider pid: {}", providerPid);
//        publisher.publishEvent(ContractNegotiationEvent.builder().action("Find by provider pid").description("Searching with provider pid ").build());
        publisher.publishEvent(AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.PROTOCOL_NEGOTIATION_CONTRACT_NEGOTIATION)
                .description("Searching with provider pid " + providerPid)
                .details(Map.of("providerPid", providerPid, "role", IConstants.ROLE_PROVIDER))
                .build());
        return contractNegotiationRepository.findByProviderPid(providerPid)
                .orElseThrow(() ->
                        new ContractNegotiationNotFoundException("Contract negotiation with provider pid " + providerPid + " not found", providerPid));
    }

    /**
     * Instantiates a new contract negotiation based on the consumer contract request and saves it in the database.
     * This method ensures that no existing contract negotiation between the same provider and consumer is active.
     * If a negotiation already exists, an exception is thrown to prevent duplication.
     *
     * @param contractRequestMessage - the contract request message containing details about the provider and consumer involved in the negotiation.
     * @return ContractNegotiation - the newly created contract negotiation record.
     * @throws ContractNegotiationExistsException if a contract negotiation already exists for the given provider and consumer PID combination.
     */
    public ContractNegotiation handleContractRequestMessage(ContractRequestMessage contractRequestMessage) {
        log.info("PROVIDER - Starting contract negotiation...");

        if (StringUtils.isNotBlank(contractRequestMessage.getProviderPid())) {
            throw new ProviderPidNotBlankException("Contract negotiation failed - providerPid has to be blank", contractRequestMessage.getConsumerPid());
        }

        checkIfContractNegotiationExists(contractRequestMessage.getConsumerPid(), contractRequestMessage.getProviderPid());

        GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(connectorProperties.getConnectorURL()
                        + ApiEndpoints.CATALOG_OFFERS_V1 + "/validate",
                NegotiationSerializer.serializePlainJsonNode(contractRequestMessage.getOffer()),
                credentialUtils.getAPICredentials());

        if (!response.isSuccess()) {
            publisher.publishEvent(AuditEvent.Builder.newInstance()
                    .description("Contract negotiation request - validation failed")
                    .eventType(AuditEventType.PROTOCOL_NEGOTIATION_POLICY_EVALUATION_DENIED)
                    .details(Map.of("contractRequestMessage", contractRequestMessage,
                            "consumerPid", contractRequestMessage.getConsumerPid(),
                            "response", response,
                            "role", IConstants.ROLE_PROVIDER))
                    .build());
            throw new OfferNotValidException("Contract offer is not valid", contractRequestMessage.getConsumerPid(), contractRequestMessage.getProviderPid());
        }

        Offer offerToBeInserted = Offer.Builder.newInstance()
                .assignee(contractRequestMessage.getOffer().getAssignee() == null ? contractRequestMessage.getCallbackAddress() : contractRequestMessage.getOffer().getAssignee())
                .assigner(contractRequestMessage.getOffer().getAssigner() == null ? properties.connectorId() : contractRequestMessage.getOffer().getAssigner())
                .originalId(contractRequestMessage.getOffer().getId())
                .permission(contractRequestMessage.getOffer().getPermission())
                .target(contractRequestMessage.getOffer().getTarget())
                .build();

        offerRepository.save(offerToBeInserted);
        log.info("PROVIDER - Offer {} saved", offerToBeInserted.getId());

        ContractNegotiation contractNegotiation = ContractNegotiation.Builder.newInstance()
                .state(ContractNegotiationState.REQUESTED)
                .consumerPid(contractRequestMessage.getConsumerPid())
                .callbackAddress(contractRequestMessage.getCallbackAddress())
                .assigner(contractRequestMessage.getOffer().getAssigner())
                .role(IConstants.ROLE_PROVIDER)
                .offer(offerToBeInserted)
                .build();

        contractNegotiationRepository.save(contractNegotiation);
        log.info("PROVIDER - Contract negotiation {} saved", contractNegotiation.getId());
        publisher.publishEvent(AuditEvent.Builder.newInstance()
                .description("Contract negotiation requested")
                .eventType(AuditEventType.PROTOCOL_NEGOTIATION_REQUESTED)
                .details(Map.of("contractNegotiation", contractNegotiation,
                        "offer", offerToBeInserted,
                        "role", IConstants.ROLE_PROVIDER))
                .build());

        if (properties.isAutomaticNegotiation()) {
            log.debug("PROVIDER - Performing automatic negotiation");
            publisher.publishEvent(new ContractNegotationOfferRequestEvent(
                    contractNegotiation.getConsumerPid(),
                    contractNegotiation.getProviderPid(),
                    NegotiationSerializer.serializeProtocolJsonNode(contractRequestMessage.getOffer())));
        } else {
            log.debug("PROVIDER - Offer evaluation will have to be done by human");
        }
        return contractNegotiation;
    }

    /**
     * Handles a counteroffer received from the consumer in an existing contract negotiation.
     * If the counteroffer is valid, the existing contract negotiation is updated with the new offer details.
     * @param providerPid            - the provider PID to validate against the contract request message.
     * @param contractRequestMessage - the contract request message containing the counteroffer details.
     * @return ContractNegotiation - the updated contract negotiation record.
     * @throws ContractNegotiationNotFoundException if the provider PID is null, does not match,
     * or if the existing contract negotiation cannot be found or validated.
     */
    public ContractNegotiation handleContractRequestMessageAsCounteroffer(String providerPid, ContractRequestMessage contractRequestMessage) {
        compareProviderPids(providerPid, contractRequestMessage.getProviderPid(), contractRequestMessage.getConsumerPid());

        ContractNegotiation contractNegotiation = findContractNegotiationByPids(contractRequestMessage.getConsumerPid(), contractRequestMessage.getProviderPid());

        if (!contractNegotiation.getOffer().getOriginalId().equals(contractRequestMessage.getOffer().getId()) ||
                !contractNegotiation.getOffer().getTarget().equals(contractRequestMessage.getOffer().getTarget())) {
            publisher.publishEvent(
                    AuditEventType.PROTOCOL_NEGOTIATION_INVALID_OFFER,
                    "Contract negotiation offer not valid error",
                    Map.of("contractNegotiation", contractNegotiation,
                            "consumerPid", contractNegotiation.getConsumerPid(),
                            "providerPid", contractNegotiation.getProviderPid(),
                            "role", IConstants.ROLE_API));
            throw new ContractNegotiationNotFoundException("New offer must have same offer id and target" +
                    " as the existing one in the contract negotiation with id: " + contractNegotiation.getId());
        }

        stateTransitionCheck(ContractNegotiationState.REQUESTED, contractNegotiation);
        log.info("Consumer sent a counteroffer for ContractNegotiation with id {}, updating existing one", contractNegotiation.getId());

        Offer updatedOffer = Offer.Builder.newInstance()
                .id(contractNegotiation.getOffer().getId())
                .originalId(contractNegotiation.getOffer().getOriginalId())
                .assignee(contractRequestMessage.getOffer().getAssignee())
                .assigner(contractRequestMessage.getOffer().getAssigner())
                .permission(contractRequestMessage.getOffer().getPermission())
                .target(contractRequestMessage.getOffer().getTarget())
                .build();
        offerRepository.save(updatedOffer);

        ContractNegotiation updatedContractNegotiation = ContractNegotiation.Builder.newInstance()
                .id(contractNegotiation.getId())
                .consumerPid(contractNegotiation.getConsumerPid())
                .providerPid(contractNegotiation.getProviderPid())
                .state(ContractNegotiationState.REQUESTED)
                .role(IConstants.ROLE_PROVIDER)
                .offer(updatedOffer)
                .assigner(contractRequestMessage.getOffer().getAssigner())
                .callbackAddress(contractNegotiation.getCallbackAddress())
                .agreement(contractNegotiation.getAgreement())
                .created(contractNegotiation.getCreated())
                .createdBy(contractNegotiation.getCreatedBy())
                .version(contractNegotiation.getVersion())
                .build();

        contractNegotiationRepository.save(updatedContractNegotiation);
        log.info("PROVIDER - Contract negotiation {} saved", contractNegotiation.getId());
        publisher.publishEvent(AuditEvent.Builder.newInstance()
                .description("Contract negotiation requested - counteroffer")
                .eventType(AuditEventType.PROTOCOL_NEGOTIATION_REQUESTED)
                .details(Map.of("contractNegotiation", contractNegotiation,
                        "offer", updatedOffer,
                        "role", IConstants.ROLE_PROVIDER))
                .build());

        return updatedContractNegotiation;
    }

    public ContractNegotiation handleContractNegotiationEventMessageAccepted(String providerPid,
                                                                             ContractNegotiationEventMessage contractNegotiationEventMessage) {
        compareProviderPids(providerPid, contractNegotiationEventMessage.getProviderPid(), contractNegotiationEventMessage.getConsumerPid());

        ContractNegotiation contractNegotiation = findContractNegotiationByPids(contractNegotiationEventMessage.getConsumerPid(), contractNegotiationEventMessage.getProviderPid());

        stateTransitionCheck(ContractNegotiationState.ACCEPTED, contractNegotiation);

        log.info("Updating state to ACCEPTED for contract negotiation id {}", contractNegotiation.getId());
        ContractNegotiation contractNegotiationAccepted = contractNegotiation.withNewContractNegotiationState(ContractNegotiationState.ACCEPTED);

        publisher.publishEvent(AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.PROTOCOL_NEGOTIATION_ACCEPTED)
                .description("Contract negotiation accepted")
                .details(Map.of("contractNegotiation", contractNegotiationAccepted,
                        "consumerPid", contractNegotiationAccepted.getConsumerPid(),
                        "providerPid", contractNegotiationAccepted.getProviderPid(),
                        "role", IConstants.ROLE_PROVIDER))
                .build());
        return contractNegotiationRepository.save(contractNegotiationAccepted);
    }

    public ContractNegotiation handleContractAgreementVerificationMessage(String providerPid, ContractAgreementVerificationMessage cavm) {
        compareProviderPids(providerPid, cavm.getProviderPid(), cavm.getConsumerPid());

        ContractNegotiation contractNegotiation = findContractNegotiationByPids(cavm.getConsumerPid(), cavm.getProviderPid());

        stateTransitionCheck(ContractNegotiationState.VERIFIED, contractNegotiation);

        ContractNegotiation contractNegotiationUpdated = contractNegotiation.withNewContractNegotiationState(ContractNegotiationState.VERIFIED);
        contractNegotiationRepository.save(contractNegotiationUpdated);
        publisher.publishEvent(AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.PROTOCOL_NEGOTIATION_VERIFIED)
                .description("Contract negotiation verified")
                .details(Map.of("contractNegotiation", contractNegotiationUpdated,
                        "role", IConstants.ROLE_PROVIDER,
                        "consumerPid", contractNegotiationUpdated.getConsumerPid(),
                        "providerPid", contractNegotiationUpdated.getProviderPid()))
                .build());
        log.info("Contract negotiation with providerPid {} and consumerPid {} changed state to VERIFIED and saved", cavm.getProviderPid(), cavm.getConsumerPid());

        return contractNegotiationUpdated;
    }

    public void handleContractNegotiationTerminationMessage(String providerPid,
                                                            ContractNegotiationTerminationMessage contractNegotiationTerminationMessage) {
        compareProviderPids(providerPid, contractNegotiationTerminationMessage.getProviderPid(), contractNegotiationTerminationMessage.getConsumerPid());

        ContractNegotiation contractNegotiation = contractNegotiationRepository.findByProviderPid(providerPid)
                .orElseThrow(() -> new ContractNegotiationNotFoundException(
                        "Contract negotiation with providerPid " + providerPid +
                                " and consumerPid " + contractNegotiationTerminationMessage.getConsumerPid() + " not found",
                        contractNegotiationTerminationMessage.getConsumerPid(), providerPid));
        stateTransitionCheck(ContractNegotiationState.TERMINATED, contractNegotiation);

        ContractNegotiation contractNegotiationTerminated = contractNegotiation.withNewContractNegotiationState(ContractNegotiationState.TERMINATED);
        contractNegotiationRepository.save(contractNegotiationTerminated);

        publisher.publishEvent(AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.PROTOCOL_NEGOTIATION_TERMINATED)
                .description("Contract negotiation terminated")
                .details(Map.of("contractNegotiation", contractNegotiationTerminated,
                        "consumerPid", contractNegotiationTerminated.getConsumerPid(),
                        "providerPid", contractNegotiationTerminated.getProviderPid(),
                        "role", IConstants.ROLE_PROVIDER))
                .build());

        log.info("Contract Negotiation with id {} set to TERMINATED state", contractNegotiation.getId());

        publisher.publishEvent(contractNegotiationTerminationMessage);
    }

    private void compareProviderPids(String providerPid, String providerPidFromMessage, String consumerPidFromMessage) {
        if (providerPidFromMessage == null || !providerPidFromMessage.equals(providerPid)) {
            publisher.publishEvent(
                    AuditEventType.PROTOCOL_NEGOTIATION_INVALID_OFFER,
                    "Contract negotiation offer not valid error",
                    Map.of("consumerPid", consumerPidFromMessage,
                            "providerPid", providerPidFromMessage,
                            "role", IConstants.ROLE_API));
            throw new ContractNegotiationNotFoundException(
                    "The providerPid from the message " + providerPidFromMessage
                            + " is different from the one in the path parameter " + providerPid, consumerPidFromMessage, providerPidFromMessage);
        }
    }
}
