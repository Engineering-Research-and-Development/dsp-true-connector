package it.eng.negotiation.service;

import it.eng.negotiation.event.ContractNegotiationEvent;
import it.eng.negotiation.exception.ContractNegotiationExistsException;
import it.eng.negotiation.exception.ContractNegotiationNotFoundException;
import it.eng.negotiation.exception.OfferNotValidException;
import it.eng.negotiation.exception.ProviderPidNotBlankException;
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
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Map;

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
        publisher.publishEvent(ContractNegotiationEvent.builder().action("Find by provider pid").description("Searching with provider pid ").build());
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
    public ContractNegotiation startContractNegotiation(ContractRequestMessage contractRequestMessage) {
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

        Offer savedOffer = offerRepository.save(offerToBeInserted);
        log.info("PROVIDER - Offer {} saved", savedOffer.getId());

        ContractNegotiation contractNegotiation = ContractNegotiation.Builder.newInstance()
                .state(ContractNegotiationState.REQUESTED)
                .consumerPid(contractRequestMessage.getConsumerPid())
                .callbackAddress(contractRequestMessage.getCallbackAddress())
                .assigner(contractRequestMessage.getOffer().getAssigner())
                .role(IConstants.ROLE_PROVIDER)
                .offer(savedOffer)
                .build();

        contractNegotiationRepository.save(contractNegotiation);
        log.info("PROVIDER - Contract negotiation {} saved", contractNegotiation.getId());
//        offerRepository.findById(contractRequestMessage.getOffer().getId())
//        	.ifPresentOrElse(o -> log.info("Offer already exists"),
//        		() -> offerRepository.save(contractRequestMessage.getOffer()));
        publisher.publishEvent(AuditEvent.Builder.newInstance()
                .description("Contract negotiation requested")
                .eventType(AuditEventType.PROTOCOL_NEGOTIATION_REQUESTED)
                .details(Map.of("contractNegotiation", contractNegotiation,
                        "offer", savedOffer,
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

    public void verifyNegotiation(ContractAgreementVerificationMessage cavm) {
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
    }

    public ContractNegotiation handleContractNegotiationEventMessage(
            ContractNegotiationEventMessage contractNegotiationEventMessage) {
        return switch (contractNegotiationEventMessage.getEventType()) {
            case ACCEPTED -> processAccepted(contractNegotiationEventMessage);
            case FINALIZED -> null;
        };
    }

    private ContractNegotiation processAccepted(ContractNegotiationEventMessage contractNegotiationEventMessage) {
        ContractNegotiation contractNegotiation = findContractNegotiationByPids(contractNegotiationEventMessage.getConsumerPid(), contractNegotiationEventMessage.getProviderPid());

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

    public void handleTerminationRequest(String providerPid,
                                         ContractNegotiationTerminationMessage contractNegotiationTerminationMessage) {
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
}
