package it.eng.negotiation.service;

import it.eng.dcp.service.DcpVerifierClient;
import it.eng.dcp.service.PresentationValidationService;
import it.eng.dcp.model.PresentationResponseMessage;
import it.eng.dcp.model.ValidationReport;
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
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import it.eng.dcp.service.RemoteHolderAuthException;

@Service
@Slf4j
public class ContractNegotiationProviderService extends BaseProtocolService {

    private final ConnectorProperties connectorProperties;

    protected final CredentialUtils credentialUtils;

    private final DcpVerifierClient dcpVerifierClient;
    private final PresentationValidationService presentationValidationService;
    private final PolicyCredentialExtractor policyCredentialExtractor;

    public ContractNegotiationProviderService(AuditEventPublisher publisher, ConnectorProperties connectorProperties,
                                              ContractNegotiationRepository contractNegotiationRepository, OkHttpRestClient okHttpRestClient,
                                              ContractNegotiationProperties properties, OfferRepository offerRepository,
                                              CredentialUtils credentialUtils, DcpVerifierClient dcpVerifierClient,
                                              PresentationValidationService presentationValidationService) {
        super(publisher, contractNegotiationRepository, okHttpRestClient, properties, offerRepository);
        this.credentialUtils = credentialUtils;
        this.connectorProperties = connectorProperties;
        this.dcpVerifierClient = dcpVerifierClient;
        this.presentationValidationService = presentationValidationService;
        this.policyCredentialExtractor = new PolicyCredentialExtractor();
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
                .assignee(contractRequestMessage.getOffer().getAssignee())
                .assigner(contractRequestMessage.getOffer().getAssigner())
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
            // Before publishing the offer request event, attempt to verify holder presentations if possible
            try {
                // extract required credential types from the offer
                var required = policyCredentialExtractor.extractCredentialTypes(
                    contractRequestMessage.getOffer(),
                    contractRequestMessage.getConsumerPid(),
                    contractRequestMessage.getProviderPid()
                );

                // attempt to fetch presentations from holder callbackAddress
                String holderEndpoint = contractRequestMessage.getCallbackAddress();
                if (holderEndpoint != null && !required.isEmpty()) {
                    log.info("PROVIDER - Fetching presentations from holder {} for credential types: {}", holderEndpoint, required);

                    PresentationResponseMessage resp = dcpVerifierClient.fetchPresentations(
                        holderEndpoint,
                        List.copyOf(required),
                        null
                    );

                    ValidationReport report = presentationValidationService.validate(
                        resp,
                        List.copyOf(required),
                        null
                    );

                    if (!report.getErrors().isEmpty()) {
                        // publish presentation invalid and abort
                        log.warn("PROVIDER - Presentation validation failed: {}", report.getErrors());
                        publisher.publishEvent(AuditEvent.Builder.newInstance()
                                .eventType(AuditEventType.PRESENTATION_INVALID)
                                .description("Presentation invalid during automatic negotiation")
                                .details(Map.of(
                                    "negotiationId", contractNegotiation.getId(),
                                    "errors", report.getErrors(),
                                    "consumerPid", contractRequestMessage.getConsumerPid(),
                                    "providerPid", contractNegotiation.getProviderPid()
                                ))
                                .build());
                        throw new OfferNotValidException("Presentation validation failed", contractRequestMessage.getConsumerPid(), contractRequestMessage.getProviderPid());
                    }

                    log.info("PROVIDER - Presentation validation successful");
                } else {
                    log.debug("PROVIDER - No holder endpoint or no required credentials, skipping presentation validation");
                }

                publisher.publishEvent(new ContractNegotationOfferRequestEvent(
                        contractNegotiation.getConsumerPid(),
                        contractNegotiation.getProviderPid(),
                        NegotiationSerializer.serializeProtocolJsonNode(contractRequestMessage.getOffer())));

            } catch (RemoteHolderAuthException e) {
                log.error("PROVIDER - Remote holder authentication failed: {}", e.getMessage());
                publisher.publishEvent(AuditEvent.Builder.newInstance()
                        .eventType(AuditEventType.TOKEN_VALIDATION_FAILED)
                        .description("Remote holder authentication failed during presentation fetch")
                        .details(Map.of(
                            "negotiationId", contractNegotiation.getId(),
                            "reason", e.getMessage(),
                            "consumerPid", contractRequestMessage.getConsumerPid(),
                            "providerPid", contractNegotiation.getProviderPid()
                        ))
                        .build());
                throw new OfferNotValidException("Remote holder auth failed", contractRequestMessage.getConsumerPid(), contractRequestMessage.getProviderPid());
            } catch (it.eng.negotiation.exception.PolicyParseException e) {
                log.error("PROVIDER - Policy parsing failed: {}", e.getMessage());
                publisher.publishEvent(AuditEvent.Builder.newInstance()
                        .eventType(AuditEventType.PROTOCOL_NEGOTIATION_POLICY_EVALUATION_DENIED)
                        .description("Policy parsing failed - no credential types found")
                        .details(Map.of(
                            "negotiationId", contractNegotiation.getId(),
                            "reason", e.getMessage(),
                            "consumerPid", contractRequestMessage.getConsumerPid(),
                            "providerPid", contractNegotiation.getProviderPid()
                        ))
                        .build());
                throw new OfferNotValidException("Policy parsing failed", contractRequestMessage.getConsumerPid(), contractRequestMessage.getProviderPid());
            }
        } else {
            log.debug("PROVIDER - Offer evaluation will have to be done by human");
        }
        return contractNegotiation;
    }

    /**
     * Publish a presentation invalid event for a negotiation. This helper will be used by DCP wiring
     * when presentation validation fails during inbound negotiation acceptance.
     * @param negotiationId the negotiation id
     * @param details additional details about the invalid presentation
     */
    public void publishPresentationInvalidEvent(String negotiationId, Object details) {
        publisher.publishEvent(AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.PRESENTATION_INVALID)
                .description("Presentation invalid for negotiation " + negotiationId)
                .details(Map.of("negotiationId", negotiationId, "details", details))
                .build());
    }

    /**
     * Publish token validation failed event for a negotiation. Use when inbound token validation fails.
     * @param negotiationId the negotiation id
     * @param reason the reason for the failure
     */
    public void publishTokenValidationFailedEvent(String negotiationId, String reason) {
        publisher.publishEvent(AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.TOKEN_VALIDATION_FAILED)
                .description("Token validation failed for negotiation " + negotiationId)
                .details(Map.of("negotiationId", negotiationId, "reason", reason))
                .build());
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
