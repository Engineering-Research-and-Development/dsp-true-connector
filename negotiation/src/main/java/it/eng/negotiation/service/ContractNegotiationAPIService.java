package it.eng.negotiation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.negotiation.exception.ContractNegotiationAPIException;
import it.eng.negotiation.model.*;
import it.eng.negotiation.policy.service.PolicyAdministrationPoint;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.OfferRepository;
import it.eng.negotiation.rest.protocol.ContractNegotiationCallback;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.event.datatransfer.InitializeTransferProcess;
import it.eng.tools.model.DSpaceConstants;
import it.eng.tools.model.IConstants;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.service.AuditEventPublisher;
import it.eng.tools.util.CredentialUtils;
import it.eng.tools.util.ToolsUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class ContractNegotiationAPIService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final OkHttpRestClient okHttpRestClient;
    private final ContractNegotiationRepository contractNegotiationRepository;
    private final ContractNegotiationProperties properties;
    private final OfferRepository offerRepository;
    private final AgreementRepository agreementRepository;
    private final CredentialUtils credentialUtils;
    private final ObjectMapper mapper = new ObjectMapper();
    private final PolicyAdministrationPoint policyAdministrationPoint;
    private final AuditEventPublisher auditEventPublisher;

    public ContractNegotiationAPIService(OkHttpRestClient okHttpRestClient, ContractNegotiationRepository contractNegotiationRepository,
                                         ContractNegotiationProperties properties, OfferRepository offerRepository, AgreementRepository agreementRepository,
                                         CredentialUtils credentialUtils, PolicyAdministrationPoint policyAdministrationPoint,
                                         AuditEventPublisher auditEventPublisher) {
        this.okHttpRestClient = okHttpRestClient;
        this.contractNegotiationRepository = contractNegotiationRepository;
        this.properties = properties;
        this.offerRepository = offerRepository;
        this.agreementRepository = agreementRepository;
        this.credentialUtils = credentialUtils;
        this.policyAdministrationPoint = policyAdministrationPoint;
        this.auditEventPublisher = auditEventPublisher;
    }

    /**
     * Find contract negotiation by id.
     *
     * @param contractNegotiationId - id of the contract negotiation
     * @return ContractNegotiation
     */
    public ContractNegotiation findContractNegotiationById(String contractNegotiationId) {
        return contractNegotiationRepository.findById(contractNegotiationId)
                .orElseThrow(() -> {
                    auditEventPublisher.publishEvent(AuditEventType.PROTOCOL_NEGOTIATION_NOT_FOUND,
                            "Contract negotiation not found",
                            Map.of("contractNegotiationId", contractNegotiationId,
                                    "role", IConstants.ROLE_API));
                    return new ContractNegotiationAPIException("Contract negotiation with id " + contractNegotiationId + " not found");
                });
    }

    /**
     * Find contract negotiation by id.
     *
     * @param filters  - dynamic filters to apply
     * @param pageable - pagination information
     * @return ContractNegotiation
     */
    public Page<ContractNegotiation> findContractNegotiations(Map<String, Object> filters, Pageable pageable) {
        return contractNegotiationRepository.findWithDynamicFilters(filters, ContractNegotiation.class, pageable);
    }

    /**
     * Start negotiation as consumer.<br>
     * Contract request message will be created and sent to connector behind forwardTo URL
     *
     * @param contractRequestMessageRequest the request containing the target connector and offer details
     * @return ContractNegotiation as JsonNode
     */
    public ContractNegotiation sendContractRequestMessage(JsonNode contractRequestMessageRequest) {
        String forwardTo = contractRequestMessageRequest.get("Forward-To").asText();
        JsonNode offerNode = contractRequestMessageRequest.get(DSpaceConstants.OFFER);

        Offer offerWithoutOriginalId = NegotiationSerializer.deserializePlain(offerNode.toPrettyString(), Offer.class);

        log.info("Sending ContractRequestMessage to {} to start a new Contract Negotiation", forwardTo);
        ContractRequestMessage contractRequestMessage = ContractRequestMessage.Builder.newInstance()
                .callbackAddress(properties.consumerCallbackAddress())
                .consumerPid(ToolsUtil.generateUniqueId())
                .offer(offerWithoutOriginalId)
                .build();

        GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(ContractNegotiationCallback.getInitialNegotiationRequestURL(forwardTo),
                NegotiationSerializer.serializeProtocolJsonNode(contractRequestMessage), credentialUtils.getConnectorCredentials());
        log.info("Response received {}", response);
        ContractNegotiation contractNegotiationWithOffer;
        if (response.isSuccess()) {
            try {
                JsonNode jsonNode = mapper.readTree(response.getData());
                ContractNegotiation contractNegotiationFromResponse = NegotiationSerializer.deserializeProtocol(jsonNode, ContractNegotiation.class);

                // set originalId to the id of the offer received in the request
                Offer offerWithOriginalId = Offer.Builder.newInstance()
                        .target(offerWithoutOriginalId.getTarget())
                        .assigner(offerWithoutOriginalId.getAssigner())
                        .assignee(offerWithoutOriginalId.getAssignee())
                        .originalId(offerWithoutOriginalId.getId())
                        .permission(offerWithoutOriginalId.getPermission())
                        .build();

                offerRepository.save(offerWithOriginalId);
                log.info("Offer with id {} and original id {} saved", offerWithOriginalId.getId(), offerWithOriginalId.getOriginalId());
                contractNegotiationWithOffer = ContractNegotiation.Builder.newInstance()
                        .id(contractNegotiationFromResponse.getId())
                        .consumerPid(contractNegotiationFromResponse.getConsumerPid())
                        .providerPid(contractNegotiationFromResponse.getProviderPid())
                        .callbackAddress(forwardTo)
                        .assigner(offerWithOriginalId.getAssigner())
                        .state(contractNegotiationFromResponse.getState())
                        .role(IConstants.ROLE_CONSUMER)
                        .offer(offerWithOriginalId)
                        .build();
                contractNegotiationRepository.save(contractNegotiationWithOffer);
                log.info("Contract negotiation {} saved", contractNegotiationWithOffer.getId());
                auditEventPublisher.publishEvent(
                        AuditEventType.PROTOCOL_NEGOTIATION_REQUESTED,
                        "Contract negotiation requested",
                        Map.of("contractNegotiationFromResponse", contractNegotiationWithOffer,
                                "offer", offerWithOriginalId,
                                "callbackAddress", forwardTo,
                                "role", IConstants.ROLE_API));
            } catch (JsonProcessingException e) {
                log.error("Contract negotiation from response not valid");
                auditEventPublisher.publishEvent(
                        AuditEventType.PROTOCOL_NEGOTIATION_REJECTED,
                        "Contract negotiation request failed",
                        Map.of("contractRequestMessage", contractRequestMessage,
                                "consumerPid", contractRequestMessage.getConsumerPid(),
                                "role", IConstants.ROLE_API));
                throw new ContractNegotiationAPIException(e.getLocalizedMessage(), e);
            }
        } else {
            log.info("Error response received!");
            try {
                ContractNegotiationErrorMessage contractNegotiationErrorMessage = null;
                if (StringUtils.isNotBlank(response.getData())) {
                    JsonNode jsonNode = mapper.readTree(response.getData());
                    contractNegotiationErrorMessage = NegotiationSerializer.deserializeProtocol(jsonNode, ContractNegotiationErrorMessage.class);
                }
                auditEventPublisher.publishEvent(
                        AuditEventType.PROTOCOL_NEGOTIATION_REJECTED,
                        "Contract negotiation request failed",
                        Map.of("contractRequestMessage", contractRequestMessage,
                                "contractNegotiationErrorMessage", contractNegotiationErrorMessage != null ? contractNegotiationErrorMessage.toString() : "No message received",
                                "consumerPid", contractRequestMessage.getConsumerPid(),
                                "role", IConstants.ROLE_API));
                throw new ContractNegotiationAPIException(contractNegotiationErrorMessage, "Error making request");
            } catch (JsonProcessingException e) {
                auditEventPublisher.publishEvent(
                        AuditEventType.PROTOCOL_NEGOTIATION_REJECTED,
                        "Contract negotiation request failed",
                        Map.of("contractRequestMessage", contractRequestMessage,
                                "consumerPid", contractRequestMessage.getConsumerPid(),
                                "role", IConstants.ROLE_API));
                throw new ContractNegotiationAPIException("Error occurred");
            }
        }
        return contractNegotiationWithOffer;
    }

    /**
     * Send counteroffer as consumer.<br>
     * Contract request message will be created and sent to connector using callback address from existing negotiation
     *
     * @param contractNegotiationId the ID of the existing contract negotiation
     * @param counteroffer the counteroffer
     * @return ContractNegotiation as JsonNode
     */
    public ContractNegotiation sendContractRequestMessageAsCounteroffer(String contractNegotiationId, JsonNode counteroffer) {
        Offer offerWithoutOriginalId = NegotiationSerializer.deserializePlain(counteroffer.toPrettyString(), Offer.class);

        ContractNegotiation existingContractNegotiation = findContractNegotiationById(contractNegotiationId);

        checkOfferValidity(existingContractNegotiation, offerWithoutOriginalId);
        stateTransitionCheck(ContractNegotiationState.REQUESTED, existingContractNegotiation);

        log.info("Sending ContractRequestMessage as a counter offer to {} to continue existing Contract Negotiation {}", existingContractNegotiation.getCallbackAddress(), contractNegotiationId);

        ContractRequestMessage contractRequestMessage = ContractRequestMessage.Builder.newInstance()
                .consumerPid(existingContractNegotiation.getConsumerPid())
                .providerPid(existingContractNegotiation.getProviderPid())
                .offer(offerWithoutOriginalId)
                .build();

        GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(
                ContractNegotiationCallback.getNegotiationRequestURL(existingContractNegotiation.getCallbackAddress(), existingContractNegotiation.getProviderPid()),
                NegotiationSerializer.serializeProtocolJsonNode(contractRequestMessage),
                credentialUtils.getConnectorCredentials());
        log.info("Response received {}", response);
        ContractNegotiation contractNegotiationWithOffer;
        if (response.isSuccess()) {
            try {
                JsonNode jsonNode = mapper.readTree(response.getData());
                ContractNegotiation contractNegotiationFromResponse = NegotiationSerializer.deserializeProtocol(jsonNode, ContractNegotiation.class);

                // set originalId to the id of the offer received in the request
                Offer offerWithOriginalId = Offer.Builder.newInstance()
                        .id(existingContractNegotiation.getOffer().getId())
                        .target(offerWithoutOriginalId.getTarget())
                        .assigner(offerWithoutOriginalId.getAssigner())
                        .assignee(offerWithoutOriginalId.getAssignee())
                        .originalId(offerWithoutOriginalId.getId())
                        .permission(offerWithoutOriginalId.getPermission())
                        .build();

                offerRepository.save(offerWithOriginalId);
                log.info("Offer with id {} and original id {} saved", offerWithOriginalId.getId(), offerWithOriginalId.getOriginalId());
                contractNegotiationWithOffer = ContractNegotiation.Builder.newInstance()
                        .id(existingContractNegotiation.getId())
                        .consumerPid(contractNegotiationFromResponse.getConsumerPid())
                        .providerPid(contractNegotiationFromResponse.getProviderPid())
                        .callbackAddress(existingContractNegotiation.getCallbackAddress())
                        .assigner(offerWithOriginalId.getAssigner())
                        .state(contractNegotiationFromResponse.getState())
                        .role(IConstants.ROLE_CONSUMER)
                        .offer(offerWithOriginalId)
                        .agreement(existingContractNegotiation.getAgreement())
                        .created(existingContractNegotiation.getCreated())
                        .createdBy(existingContractNegotiation.getCreatedBy())
                        .version(existingContractNegotiation.getVersion())
                        .build();
                contractNegotiationRepository.save(contractNegotiationWithOffer);
                log.info("Contract negotiation {} saved", contractNegotiationWithOffer.getId());
                auditEventPublisher.publishEvent(
                        AuditEventType.PROTOCOL_NEGOTIATION_REQUESTED,
                        "Contract negotiation request as counteroffer successfully processed",
                        Map.of("contractNegotiationFromResponse", contractNegotiationWithOffer,
                                "offer", offerWithOriginalId,
                                "callbackAddress", existingContractNegotiation.getCallbackAddress(),
                                "role", IConstants.ROLE_API));
            } catch (JsonProcessingException e) {
                log.error("Contract negotiation from response not valid");
                auditEventPublisher.publishEvent(
                        AuditEventType.PROTOCOL_NEGOTIATION_REJECTED,
                        "Contract negotiation request as counteroffer failed",
                        Map.of("contractRequestMessage", contractRequestMessage,
                                "consumerPid", contractRequestMessage.getConsumerPid(),
                                "providerPid", contractRequestMessage.getProviderPid(),
                                "role", IConstants.ROLE_API));
                throw new ContractNegotiationAPIException(e.getLocalizedMessage(), e);
            }
        } else {
            log.info("Error response received!");
            try {
                ContractNegotiationErrorMessage contractNegotiationErrorMessage = null;
                if (StringUtils.isNotBlank(response.getData())) {
                    JsonNode jsonNode = mapper.readTree(response.getData());
                    contractNegotiationErrorMessage = NegotiationSerializer.deserializeProtocol(jsonNode, ContractNegotiationErrorMessage.class);
                }
                auditEventPublisher.publishEvent(
                        AuditEventType.PROTOCOL_NEGOTIATION_REJECTED,
                        "Contract negotiation request as counteroffer failed",
                        Map.of("contractRequestMessage", contractRequestMessage,
                                "contractNegotiationErrorMessage", contractNegotiationErrorMessage != null ? contractNegotiationErrorMessage.toString() : "No message received",
                                "consumerPid", contractRequestMessage.getConsumerPid(),
                                "providerPid", contractRequestMessage.getProviderPid(),
                                "role", IConstants.ROLE_API));
                throw new ContractNegotiationAPIException(contractNegotiationErrorMessage, "Error making request");
            } catch (JsonProcessingException e) {
                auditEventPublisher.publishEvent(
                        AuditEventType.PROTOCOL_NEGOTIATION_REJECTED,
                        "Contract negotiation request as counteroffer failed",
                        Map.of("contractRequestMessage", contractRequestMessage,
                                "consumerPid", contractRequestMessage.getConsumerPid(),
                                "providerPid", contractRequestMessage.getProviderPid(),
                                "role", IConstants.ROLE_API));
                throw new ContractNegotiationAPIException("Error occurred");
            }
        }
        return contractNegotiationWithOffer;
    }

    /**
     * Provider sends offer to consumer.
     *
     * @param contractOfferMessageRequest the request containing the target connector and offer details
     * @return ContractNegotiation
     */
    public ContractNegotiation sendContractOfferMessage(JsonNode contractOfferMessageRequest) {
        String forwardTo = contractOfferMessageRequest.get("Forward-To").asText();
        JsonNode offerNode = contractOfferMessageRequest.get(DSpaceConstants.OFFER);

        Offer offer = NegotiationSerializer.deserializePlain(offerNode.toPrettyString(), Offer.class);

        log.info("Sending ContractOfferMessage to {} to start a new Contract Negotiation", forwardTo);
        ContractOfferMessage contractOfferMessage = ContractOfferMessage.Builder.newInstance()
                .callbackAddress(properties.providerCallbackAddress())
                .providerPid(ToolsUtil.generateUniqueId())
                .offer(offer)
                .build();

        GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(ContractNegotiationCallback.getInitialOfferCallback(forwardTo),
                NegotiationSerializer.serializeProtocolJsonNode(contractOfferMessage), credentialUtils.getConnectorCredentials());
        log.info("Response received {}", response);
        ContractNegotiation contractNegotiationWithOffer;
        if (response.isSuccess()) {
            try {
                JsonNode jsonNode = mapper.readTree(response.getData());
                ContractNegotiation contractNegotiationFromResponse = NegotiationSerializer.deserializeProtocol(jsonNode, ContractNegotiation.class);

                Offer updatedOffer = Offer.Builder.newInstance()
                        .originalId(offer.getId())
                        .assignee(offer.getAssignee())
                        .assigner(offer.getAssigner())
                        .permission(offer.getPermission())
                        .target(offer.getTarget())
                        .build();
                offerRepository.save(updatedOffer);

                log.info("Offer {} saved", updatedOffer.getId());
                contractNegotiationWithOffer = ContractNegotiation.Builder.newInstance()
                        .id(contractNegotiationFromResponse.getId())
                        .consumerPid(contractNegotiationFromResponse.getConsumerPid())
                        .providerPid(contractNegotiationFromResponse.getProviderPid())
                        .callbackAddress(forwardTo)
                        .assigner(updatedOffer.getAssigner())
                        .state(contractNegotiationFromResponse.getState())
                        .role(IConstants.ROLE_PROVIDER)
                        .offer(updatedOffer)
                        .build();
                contractNegotiationRepository.save(contractNegotiationWithOffer);
                log.info("Contract negotiation {} saved", contractNegotiationWithOffer.getId());
                auditEventPublisher.publishEvent(
                        AuditEventType.PROTOCOL_NEGOTIATION_OFFERED,
                        "Contract negotiation offered",
                        Map.of("contractNegotiationFromResponse", contractNegotiationWithOffer,
                                "offer", updatedOffer,
                                "callbackAddress", forwardTo,
                                "role", IConstants.ROLE_API));
            } catch (JsonProcessingException e) {
                log.error("Contract negotiation from response not valid");
                auditEventPublisher.publishEvent(
                        AuditEventType.PROTOCOL_NEGOTIATION_REJECTED,
                        "Contract negotiation offer failed",
                        Map.of("ContractOfferMessage", contractOfferMessage,
                                "providerPid", contractOfferMessage.getProviderPid(),
                                "role", IConstants.ROLE_API));
                throw new ContractNegotiationAPIException(e.getLocalizedMessage(), e);
            }
        } else {
            log.info("Error response received!");
            try {
                ContractNegotiationErrorMessage contractNegotiationErrorMessage = null;
                if (StringUtils.isNotBlank(response.getData())) {
                    JsonNode jsonNode = mapper.readTree(response.getData());
                    contractNegotiationErrorMessage = NegotiationSerializer.deserializeProtocol(jsonNode, ContractNegotiationErrorMessage.class);
                }
                auditEventPublisher.publishEvent(
                        AuditEventType.PROTOCOL_NEGOTIATION_REJECTED,
                        "Contract negotiation offer failed",
                        Map.of("ContractOfferMessage", contractOfferMessage,
                                "contractNegotiationErrorMessage", contractNegotiationErrorMessage != null ? contractNegotiationErrorMessage.toString() : "No message received",
                                "role", IConstants.ROLE_API));
                throw new ContractNegotiationAPIException(contractNegotiationErrorMessage, "Error making request");
            } catch (JsonProcessingException e) {
                auditEventPublisher.publishEvent(
                        AuditEventType.PROTOCOL_NEGOTIATION_REJECTED,
                        "Contract negotiation offer failed",
                        Map.of("ContractOfferMessage", contractOfferMessage,
                                "providerPid", contractOfferMessage.getProviderPid(),
                                "role", IConstants.ROLE_API));
                throw new ContractNegotiationAPIException("Error occurred");
            }
        }
        return contractNegotiationWithOffer;
    }

    /**
     * Send counteroffer as provider.<br>
     * Contract offer message will be created and sent to connector using callback address from existing negotiation
     *
     * @param contractNegotiationId - id of the existing contract negotiation
     * @param offerNode - counteroffer
     * @return ContractNegotiation
     */
    public ContractNegotiation sendContractOfferMessageAsCounteroffer(String contractNegotiationId, JsonNode offerNode) {
        Offer offer = NegotiationSerializer.deserializePlain(offerNode.toPrettyString(), Offer.class);

        ContractNegotiation existingContractNegotiation = findContractNegotiationById(contractNegotiationId);

        checkOfferValidity(existingContractNegotiation, offer);
        stateTransitionCheck(ContractNegotiationState.OFFERED, existingContractNegotiation);

        log.info("Sending ContractOfferMessage as a counter offer to {} to continue existing Contract Negotiation {}", existingContractNegotiation.getCallbackAddress(), contractNegotiationId);

        ContractOfferMessage contractOfferMessage = ContractOfferMessage.Builder.newInstance()
                .consumerPid(existingContractNegotiation.getConsumerPid())
                .providerPid(existingContractNegotiation.getProviderPid())
                .offer(offer)
                .build();

        GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(
                ContractNegotiationCallback.getConsumerOffersCallback(existingContractNegotiation.getCallbackAddress(), existingContractNegotiation.getProviderPid()),
                NegotiationSerializer.serializeProtocolJsonNode(contractOfferMessage),
                credentialUtils.getConnectorCredentials());
        log.info("Response received {}", response);
        ContractNegotiation contractNegotiationWithOffer;
        if (response.isSuccess()) {
            try {
                JsonNode jsonNode = mapper.readTree(response.getData());
                ContractNegotiation contractNegotiationFromResponse = NegotiationSerializer.deserializeProtocol(jsonNode, ContractNegotiation.class);

                Offer updatedOffer = Offer.Builder.newInstance()
                        .id(existingContractNegotiation.getOffer().getId())
                        .originalId(existingContractNegotiation.getOffer().getOriginalId())
                        .assignee(offer.getAssignee())
                        .assigner(offer.getAssigner())
                        .permission(offer.getPermission())
                        .target(offer.getTarget())
                        .build();
                offerRepository.save(updatedOffer);

                log.info("Offer {} saved", updatedOffer.getId());
                contractNegotiationWithOffer = ContractNegotiation.Builder.newInstance()
                        .id(existingContractNegotiation.getId())
                        .consumerPid(existingContractNegotiation.getConsumerPid())
                        .providerPid(existingContractNegotiation.getProviderPid())
                        .callbackAddress(existingContractNegotiation.getCallbackAddress())
                        .assigner(updatedOffer.getAssigner())
                        .state(contractNegotiationFromResponse.getState())
                        .role(IConstants.ROLE_PROVIDER)
                        .offer(updatedOffer)
                        .agreement(existingContractNegotiation.getAgreement())
                        .created(existingContractNegotiation.getCreated())
                        .createdBy(existingContractNegotiation.getCreatedBy())
                        .version(existingContractNegotiation.getVersion())
                        .build();
                contractNegotiationRepository.save(contractNegotiationWithOffer);
                log.info("Contract negotiation {} saved", contractNegotiationWithOffer.getId());
                auditEventPublisher.publishEvent(
                        AuditEventType.PROTOCOL_NEGOTIATION_OFFERED,
                        "Contract negotiation offer as counteroffer successfully processed",
                        Map.of("contractNegotiationFromResponse", contractNegotiationWithOffer,
                                "offer", updatedOffer,
                                "callbackAddress", existingContractNegotiation.getCallbackAddress(),
                                "role", IConstants.ROLE_API));
            } catch (JsonProcessingException e) {
                log.error("Contract negotiation from response not valid");
                auditEventPublisher.publishEvent(
                        AuditEventType.PROTOCOL_NEGOTIATION_REJECTED,
                        "Contract negotiation offer as counteroffer failed",
                        Map.of("ContractOfferMessage", contractOfferMessage,
                                "consumerPid", contractOfferMessage.getConsumerPid(),
                                "providerPid", contractOfferMessage.getProviderPid(),
                                "role", IConstants.ROLE_API));
                throw new ContractNegotiationAPIException(e.getLocalizedMessage(), e);
            }
        } else {
            log.info("Error response received!");
            try {
                ContractNegotiationErrorMessage contractNegotiationErrorMessage = null;
                if (StringUtils.isNotBlank(response.getData())) {
                    JsonNode jsonNode = mapper.readTree(response.getData());
                    contractNegotiationErrorMessage = NegotiationSerializer.deserializeProtocol(jsonNode, ContractNegotiationErrorMessage.class);
                }
                auditEventPublisher.publishEvent(
                        AuditEventType.PROTOCOL_NEGOTIATION_REJECTED,
                        "Contract negotiation offer as counteroffer failed",
                        Map.of("ContractOfferMessage", contractOfferMessage,
                                "contractNegotiationErrorMessage", contractNegotiationErrorMessage != null ? contractNegotiationErrorMessage.toString() : "No message received",
                                "consumerPid", contractOfferMessage.getConsumerPid(),
                                "providerPid", contractOfferMessage.getProviderPid(),
                                "role", IConstants.ROLE_API));
                throw new ContractNegotiationAPIException(contractNegotiationErrorMessage, "Error making request");
            } catch (JsonProcessingException e) {
                auditEventPublisher.publishEvent(
                        AuditEventType.PROTOCOL_NEGOTIATION_REJECTED,
                        "Contract negotiation offer as counteroffer failed",
                        Map.of("ContractOfferMessage", contractOfferMessage,
                                "consumerPid", contractOfferMessage.getConsumerPid(),
                                "providerPid", contractOfferMessage.getProviderPid(),
                                "role", IConstants.ROLE_API));
                throw new ContractNegotiationAPIException("Error occurred");
            }
        }
        return contractNegotiationWithOffer;
    }

    /**
     * Finalize negotiation.
     *
     * @param contractNegotiationId - id of the contract negotiation
     */
    public void sendContractNegotiationEventMessageFinalize(String contractNegotiationId) {
        ContractNegotiation contractNegotiation = findContractNegotiationById(contractNegotiationId);

        stateTransitionCheck(ContractNegotiationState.FINALIZED, contractNegotiation);

        ContractNegotiationEventMessage contractNegotiationEventMessage = ContractNegotiationEventMessage.Builder.newInstance()
                .consumerPid(contractNegotiation.getConsumerPid())
                .providerPid(contractNegotiation.getProviderPid())
                .eventType(ContractNegotiationEventType.FINALIZED)
                .build();

        //	https://consumer.com/:callback/negotiations/:consumerPid/events
        String callbackAddress = ContractNegotiationCallback.getContractEventsCallback(contractNegotiation.getCallbackAddress(), contractNegotiation.getConsumerPid());

        log.info("Sending ContractNegotiationEventMessage.FINALIZED to {}", callbackAddress);
        GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(callbackAddress,
                NegotiationSerializer.serializeProtocolJsonNode(contractNegotiationEventMessage), credentialUtils.getConnectorCredentials());

        if (response.isSuccess()) {
            ContractNegotiation contractNegotiationFinalized = contractNegotiation.withNewContractNegotiationState(ContractNegotiationState.FINALIZED);
            contractNegotiationRepository.save(contractNegotiationFinalized);
            // TODO remove this line once api/getArtifact is implemented on consumer side
            policyAdministrationPoint.createPolicyEnforcement(contractNegotiation.getAgreement().getId());
            auditEventPublisher.publishEvent(new InitializeTransferProcess(
                    contractNegotiationFinalized.getCallbackAddress(),
                    contractNegotiationFinalized.getAgreement().getId(),
                    contractNegotiationFinalized.getAgreement().getTarget(),
                    contractNegotiationFinalized.getRole()
            ));
            auditEventPublisher.publishEvent(
                    AuditEventType.PROTOCOL_NEGOTIATION_FINALIZED,
                    "Contract negotiation finalized",
                    Map.of("contractNegotiation", contractNegotiationFinalized,
                            "consumerPid", contractNegotiationFinalized.getConsumerPid(),
                            "providerPid", contractNegotiationFinalized.getProviderPid(),
                            "role", IConstants.ROLE_API));
        } else {
            log.error("Error response received!");
            auditEventPublisher.publishEvent(
                    AuditEventType.PROTOCOL_NEGOTIATION_REJECTED,
                    "Contract negotiation finalized",
                    Map.of("contractNegotiation", contractNegotiation,
                            "errorMessage", response.getMessage(),
                            "consumerPid", contractNegotiation.getConsumerPid(),
                            "providerPid", contractNegotiation.getProviderPid(),
                            "role", IConstants.ROLE_API));
            throw new ContractNegotiationAPIException(response.getMessage());
        }
    }

    /**
     * Consumer sends ContractNegotiationEventMessage with state ACCEPTED.<br>
     * Updates state Contract Negotiation upon successful response to ACCEPTED
     *
     * @param contractNegotiationId - id of the contract negotiation
     * @return ContractNegotiation
     */
    public ContractNegotiation sendContractNegotiationEventMessageAccepted(String contractNegotiationId) {
        ContractNegotiation contractNegotiation = findContractNegotiationById(contractNegotiationId);

        stateTransitionCheck(ContractNegotiationState.ACCEPTED, contractNegotiation);

        ContractNegotiationEventMessage eventMessageAccepted = ContractNegotiationEventMessage.Builder.newInstance()
                .consumerPid(contractNegotiation.getConsumerPid())
                .providerPid(contractNegotiation.getProviderPid())
                .eventType(ContractNegotiationEventType.ACCEPTED)
                .build();

        log.info("Sending ContractNegotiationEventMessage.ACCEPTED as consumer to {}", contractNegotiation.getCallbackAddress());
        GenericApiResponse<String> response = okHttpRestClient
                .sendRequestProtocol(ContractNegotiationCallback.getContractEventsCallback(contractNegotiation.getCallbackAddress(), contractNegotiation.getProviderPid()),
                        NegotiationSerializer.serializeProtocolJsonNode(eventMessageAccepted),
                        credentialUtils.getConnectorCredentials());
        log.info("Response received {}", response);
        if (response.isSuccess()) {
            ContractNegotiation contractNegotiationAccepted = contractNegotiation.withNewContractNegotiationState(ContractNegotiationState.ACCEPTED);
            contractNegotiationRepository.save(contractNegotiationAccepted);
            log.info("Contract negotiation {} saved", contractNegotiation.getId());
            // Create and publish audit event with request information
            auditEventPublisher.publishEvent(
                    AuditEventType.PROTOCOL_NEGOTIATION_ACCEPTED,
                    "Contract negotiation accepted",
                    Map.of("contractNegotiation", contractNegotiationAccepted,
                            "consumerPid", contractNegotiation.getConsumerPid(),
                            "providerPid", contractNegotiation.getProviderPid(),
                            "role", IConstants.ROLE_API));
            return contractNegotiationAccepted;
        } else {
            log.error("Error response received!");
            auditEventPublisher.publishEvent(
                    AuditEventType.PROTOCOL_NEGOTIATION_REJECTED,
                    "Contract negotiation accepted failed",
                    Map.of("contractNegotiation", contractNegotiation,
                            "errorMessage", response.getMessage(),
                            "consumerPid", contractNegotiation.getConsumerPid(),
                            "providerPid", contractNegotiation.getProviderPid(),
                            "role", IConstants.ROLE_API));
            throw new ContractNegotiationAPIException(response.getMessage());
        }
    }

    /**
     * Negotiate status to AGREED after successful response from connector.
     *
     * @param contractNegotiationId contract negotiation ID
     * @return ContractNegotiation
     */
    public ContractNegotiation sendContractAgreementMessage(String contractNegotiationId) {
        ContractNegotiation contractNegotiation = findContractNegotiationById(contractNegotiationId);

        stateTransitionCheck(ContractNegotiationState.AGREED, contractNegotiation);

        ContractAgreementMessage agreementMessage = ContractAgreementMessage.Builder.newInstance()
                .consumerPid(contractNegotiation.getConsumerPid())
                .providerPid(contractNegotiation.getProviderPid())
                .agreement(agreementFromOffer(contractNegotiation.getOffer()))
                .build();

        GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(
                ContractNegotiationCallback.getContractAgreementCallback(contractNegotiation.getCallbackAddress(),contractNegotiation.getConsumerPid()),
                NegotiationSerializer.serializeProtocolJsonNode(agreementMessage),
                credentialUtils.getConnectorCredentials());
        if (response.isSuccess()) {
            log.info("Updating status for negotiation {} to agreed", contractNegotiation.getId());
            log.info("Saving agreement...{}", agreementMessage.getAgreement().getId());
            agreementRepository.save(agreementMessage.getAgreement());

            ContractNegotiation contractNegotiationAgreed = ContractNegotiation.Builder.newInstance()
                    .id(contractNegotiation.getId())
                    .consumerPid(contractNegotiation.getConsumerPid())
                    .providerPid(contractNegotiation.getProviderPid())
                    .callbackAddress(contractNegotiation.getCallbackAddress())
                    .assigner(contractNegotiation.getAssigner())
                    .state(ContractNegotiationState.AGREED)
                    .role(contractNegotiation.getRole())
                    .offer(contractNegotiation.getOffer())
                    .agreement(agreementMessage.getAgreement())
                    .created(contractNegotiation.getCreated())
                    .createdBy(contractNegotiation.getCreatedBy())
                    .modified(contractNegotiation.getModified())
                    .lastModifiedBy(contractNegotiation.getLastModifiedBy())
                    .version(contractNegotiation.getVersion())
                    .build();

            contractNegotiationRepository.save(contractNegotiationAgreed);

            // Create and publish audit event with request information
            auditEventPublisher.publishEvent(
                    AuditEventType.PROTOCOL_NEGOTIATION_AGREED,
                    "Contract negotiation agreed",
                    Map.of("contractNegotiation", contractNegotiationAgreed,
                            "agreement", agreementMessage.getAgreement(),
                            "consumerPid", contractNegotiationAgreed.getConsumerPid(),
                            "providerPid", contractNegotiationAgreed.getProviderPid(),
                            "role", IConstants.ROLE_API));
            return contractNegotiationAgreed;
        } else {
            log.error("Response status not 200 - consumer did not process AgreementMessage correct");
            auditEventPublisher.publishEvent(
                    AuditEventType.PROTOCOL_NEGOTIATION_REJECTED,
                    "Contract negotiation approval failed",
                    Map.of("contractNegotiation", contractNegotiation,
                            "errorMessage", response.getMessage(),
                            "consumerPid", contractNegotiation.getConsumerPid(),
                            "providerPid", contractNegotiation.getProviderPid(),
                            "role", IConstants.ROLE_API));
            throw new ContractNegotiationAPIException("consumer did not process AgreementMessage correct");
        }
    }

    /**
     * Verify negotiation.<br>
     * If all ok state is transitioned to VERIFIED
     *
     * @param contractNegotiationId contract negotiation ID
     */
    public void sendContractAgreementVerificationMessage(String contractNegotiationId) {
        ContractNegotiation contractNegotiation = findContractNegotiationById(contractNegotiationId);

        stateTransitionCheck(ContractNegotiationState.VERIFIED, contractNegotiation);

        ContractAgreementVerificationMessage verificationMessage = ContractAgreementVerificationMessage.Builder.newInstance()
                .consumerPid(contractNegotiation.getConsumerPid())
                .providerPid(contractNegotiation.getProviderPid())
                .build();

        log.info("Found initial negotiation {} - CallbackAddress ", contractNegotiation.getCallbackAddress());

        String callbackAddress = ContractNegotiationCallback.getProviderAgreementVerificationCallback(contractNegotiation.getCallbackAddress(), contractNegotiation.getProviderPid());
        log.info("Sending verification message to provider to {}", callbackAddress);
        GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(callbackAddress,
                NegotiationSerializer.serializeProtocolJsonNode(verificationMessage),
                credentialUtils.getConnectorCredentials());

        if (response.isSuccess()) {
            log.info("Updating status for negotiation {} to verified", contractNegotiation.getId());
            ContractNegotiation contractNegotiationVerified = contractNegotiation.withNewContractNegotiationState(ContractNegotiationState.VERIFIED);
            contractNegotiationRepository.save(contractNegotiationVerified);
            auditEventPublisher.publishEvent(
                    AuditEventType.PROTOCOL_NEGOTIATION_VERIFIED,
                    "Contract negotiation verified",
                    Map.of("contractNegotiation", contractNegotiationVerified,
                            "consumerPid", contractNegotiationVerified.getConsumerPid(),
                            "providerPid", contractNegotiationVerified.getProviderPid(),
                            "role", IConstants.ROLE_API));
        } else {
            log.error("Response status not 200 - provider did not process Verification message correct");
            try {
                ContractNegotiationErrorMessage contractNegotiationErrorMessage = null;
                if (StringUtils.isNotBlank(response.getData())) {
                    JsonNode jsonNode = mapper.readTree(response.getData());
                    contractNegotiationErrorMessage = NegotiationSerializer.deserializeProtocol(jsonNode, ContractNegotiationErrorMessage.class);
                }
                auditEventPublisher.publishEvent(
                        AuditEventType.PROTOCOL_NEGOTIATION_REJECTED,
                        "Contract negotiation verification failed",
                        Map.of("contractNegotiation", contractNegotiation,
                                "errorMessage", contractNegotiationErrorMessage != null ? contractNegotiationErrorMessage.toString() : "No message received",
                                "consumerPid", contractNegotiation.getConsumerPid(),
                                "providerPid", contractNegotiation.getProviderPid(),
                                "role", IConstants.ROLE_API));
                throw new ContractNegotiationAPIException(contractNegotiationErrorMessage, "Provider did not process Verification message correct");
            } catch (JsonProcessingException e) {
                auditEventPublisher.publishEvent(
                        AuditEventType.PROTOCOL_NEGOTIATION_REJECTED,
                        "Contract negotiation verification failed",
                        Map.of("contractNegotiation", contractNegotiation,
                                "consumerPid", contractNegotiation.getConsumerPid(),
                                "providerPid", contractNegotiation.getProviderPid(),
                                "role", IConstants.ROLE_API));
                throw new ContractNegotiationAPIException("Provider did not process Verification message correct");
            }
        }
    }

    /**
     * Terminate contract negotiation.
     * Transition state to TERMINATED
     *
     * @param contractNegotiationId - id of the contract negotiation
     * @return ContractNegotiation
     */
    public ContractNegotiation sendContractNegotiationTerminationMessage(String contractNegotiationId) {
        ContractNegotiation contractNegotiation = findContractNegotiationById(contractNegotiationId);
        // for now, log it; maybe we can publish event?
        log.info("Contract negotiation with consumerPid {} and providerPid {} declined", contractNegotiation.getConsumerPid(), contractNegotiation.getProviderPid());
        String reason;
        String address;
        if (IConstants.ROLE_PROVIDER.equals(contractNegotiation.getRole())) {
            log.info("Terminating negotiation by provider");
            reason = "Contract negotiation terminated by provider";
            // send request to consumer callback address
            address = ContractNegotiationCallback.getContractTerminationCallback(contractNegotiation.getCallbackAddress(), contractNegotiation.getConsumerPid());
        } else {
            log.info("Terminating negotiation by consumer");
            reason = "Contract negotiation terminated by consumer";
            // send request to protocol address
            address = ContractNegotiationCallback.getContractTerminationProvider(contractNegotiation.getCallbackAddress(), contractNegotiation.getProviderPid());
        }
        ContractNegotiationTerminationMessage negotiationTerminatedEventMessage = ContractNegotiationTerminationMessage.Builder.newInstance()
                .consumerPid(contractNegotiation.getConsumerPid())
                .providerPid(contractNegotiation.getProviderPid())
                .code(contractNegotiationId)
                .reason(Collections.singletonList(reason))
                .build();
        GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(
                address,
                NegotiationSerializer.serializeProtocolJsonNode(negotiationTerminatedEventMessage),
                credentialUtils.getConnectorCredentials());
        if (response.isSuccess()) {
            log.info("Updating status for negotiation {} to terminated", contractNegotiation.getId());
            ContractNegotiation contractNegotiationTerminated = contractNegotiation.withNewContractNegotiationState(ContractNegotiationState.TERMINATED);
            contractNegotiationRepository.save(contractNegotiationTerminated);
            auditEventPublisher.publishEvent(
                    AuditEventType.PROTOCOL_NEGOTIATION_TERMINATED,
                    "Contract negotiation terminated",
                    Map.of("contractNegotiation", contractNegotiationTerminated,
                            "address", address,
                            "consumerPid", contractNegotiationTerminated.getConsumerPid(),
                            "providerPid", contractNegotiationTerminated.getProviderPid(),
                            "role", IConstants.ROLE_API));
            return contractNegotiationTerminated;
        } else {
            log.error("Response status not 200 - {} did not process ContractNegotiationTerminationMessage correct", contractNegotiation.getRole());
            try {
                ContractNegotiationErrorMessage contractNegotiationErrorMessage = null;
                if (StringUtils.isNotBlank(response.getData())) {
                    JsonNode jsonNode = mapper.readTree(response.getData());
                    contractNegotiationErrorMessage = NegotiationSerializer.deserializeProtocol(jsonNode, ContractNegotiationErrorMessage.class);
                }
                auditEventPublisher.publishEvent(
                        AuditEventType.PROTOCOL_NEGOTIATION_REJECTED,
                        "Contract negotiation termination failed",
                        Map.of("contractNegotiation", contractNegotiation,
                                "errorMessage", contractNegotiationErrorMessage != null ? contractNegotiationErrorMessage.toString() : "No message received",
                                "consumerPid", contractNegotiation.getConsumerPid(),
                                "providerPid", contractNegotiation.getProviderPid(),
                                "role", IConstants.ROLE_API));

                if (contractNegotiationErrorMessage != null) {
                    throw new ContractNegotiationAPIException(contractNegotiationErrorMessage, contractNegotiation.getRole() + " did not process Terminate message correct");
                } else {
                    throw new ContractNegotiationAPIException(contractNegotiation.getRole() + " did not process Terminate message correct");
                }
            } catch (JsonProcessingException e) {
                auditEventPublisher.publishEvent(
                        AuditEventType.PROTOCOL_NEGOTIATION_REJECTED,
                        "Contract negotiation termination failed",
                        Map.of("contractNegotiation", contractNegotiation,
                                "consumerPid", contractNegotiation.getConsumerPid(),
                                "providerPid", contractNegotiation.getProviderPid(),
                                "role", IConstants.ROLE_API));
                throw new ContractNegotiationAPIException(contractNegotiation.getRole() + " did not process Terminate message correct");
            }
        }
    }

    private Agreement agreementFromOffer(Offer offer) {
        return Agreement.Builder.newInstance()
                .assignee(offer.getAssignee())
                .assigner(offer.getAssigner())
                .target(offer.getTarget())
                .timestamp(FORMATTER.format(ZonedDateTime.now()))
                .permission(offer.getPermission())
                .build();
    }

    private void stateTransitionCheck(ContractNegotiationState newState, ContractNegotiation contractNegotiation) {
        if (!contractNegotiation.getState().canTransitTo(newState)) {
            auditEventPublisher.publishEvent(
                    AuditEventType.PROTOCOL_NEGOTIATION_STATE_TRANSITION_ERROR,
                    "Contract negotiation state transition error",
                    Map.of("contractNegotiation", contractNegotiation,
                            "currentState", contractNegotiation.getState(),
                            "newState", newState,
                            "consumerPid", contractNegotiation.getConsumerPid(),
                            "providerPid", contractNegotiation.getProviderPid(),
                            "role", IConstants.ROLE_API));
            throw new ContractNegotiationAPIException("State transition aborted, " + contractNegotiation.getState().name()
                    + " state can not transition to " + newState.name());
        }
    }

    private void checkOfferValidity(ContractNegotiation contractNegotiation, Offer newOffer) {
        if (!contractNegotiation.getOffer().getOriginalId().equals(newOffer.getId()) ||
                !contractNegotiation.getOffer().getTarget().equals(newOffer.getTarget())) {
            auditEventPublisher.publishEvent(
                    AuditEventType.PROTOCOL_NEGOTIATION_INVALID_OFFER,
                    "Contract negotiation offer not valid error",
                    Map.of("contractNegotiation", contractNegotiation,
                            "consumerPid", contractNegotiation.getConsumerPid(),
                            "providerPid", contractNegotiation.getProviderPid(),
                            "role", IConstants.ROLE_API));
            throw new ContractNegotiationAPIException("New offer must have same offer id and target as the existing one in the contract negotiation with id: " + contractNegotiation.getId());
        }
    }
}
