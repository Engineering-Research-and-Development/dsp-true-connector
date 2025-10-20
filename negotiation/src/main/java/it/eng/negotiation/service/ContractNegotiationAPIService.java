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
     * @param contractRequestMessageRequest - offer
     * @return ContractNegotiation as JsonNode
     */
    public ContractNegotiation sendContractRequestMessage(ContractRequestMessageRequest contractRequestMessageRequest) {
        Offer offerWithoutOriginalId = NegotiationSerializer.deserializePlain(contractRequestMessageRequest.getOffer().toPrettyString(), Offer.class);
        ContractRequestMessage contractRequestMessage;
        String address;

        ContractNegotiation existingContractNegotiation = null;
        if (StringUtils.isNotBlank(contractRequestMessageRequest.getContractNegotiationId())) {
            existingContractNegotiation = contractNegotiationRepository.findById(contractRequestMessageRequest.getContractNegotiationId())
                    .orElseThrow(() -> new ContractNegotiationAPIException("Contract negotiation with id: " + contractRequestMessageRequest.getContractNegotiationId() + " not found"));

            if (!existingContractNegotiation.getOffer().getOriginalId().equals(offerWithoutOriginalId.getId()) || !existingContractNegotiation.getOffer().getTarget().equals(offerWithoutOriginalId.getTarget())) {
                throw new ContractNegotiationAPIException("New offer must have same offer id and target as the existing one in the contract negotiation with id: " + contractRequestMessageRequest.getContractNegotiationId());
            }

            stateTransitionCheck(ContractNegotiationState.REQUESTED, existingContractNegotiation);

            log.info("Sending ContractRequestMessage as a counter offer to {} to continue existing Contract Negotiation {}", contractRequestMessageRequest.getForwardTo(), contractRequestMessageRequest.getContractNegotiationId());

            contractRequestMessage = ContractRequestMessage.Builder.newInstance()
                    .consumerPid(existingContractNegotiation.getConsumerPid())
                    .providerPid(existingContractNegotiation.getProviderPid())
                    .offer(offerWithoutOriginalId)
                    .build();
            address = ContractNegotiationCallback.getNegotiationRequestURL(existingContractNegotiation.getCallbackAddress(), existingContractNegotiation.getProviderPid());
        } else {
            log.info("Sending ContractRequestMessage to {} to start a new Contract Negotiation", contractRequestMessageRequest.getForwardTo());
            contractRequestMessage = ContractRequestMessage.Builder.newInstance()
                    .callbackAddress(properties.consumerCallbackAddress())
                    .consumerPid(ToolsUtil.generateUniqueId())
                    .offer(offerWithoutOriginalId)
                    .build();
            address = ContractNegotiationCallback.getInitialNegotiationRequestURL(contractRequestMessageRequest.getForwardTo());
        }

        GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(address,
                NegotiationSerializer.serializeProtocolJsonNode(contractRequestMessage), credentialUtils.getConnectorCredentials());
        log.info("Response received {}", response);
        ContractNegotiation contractNegotiationWithOffer;
        if (response.isSuccess()) {
            try {
                JsonNode jsonNode = mapper.readTree(response.getData());
                ContractNegotiation contractNegotiationFromResponse = NegotiationSerializer.deserializeProtocol(jsonNode, ContractNegotiation.class);

                // set originalId to the id of the offer received in the request
                Offer offerWithOriginalId = Offer.Builder.newInstance()
                        .id(existingContractNegotiation != null ? existingContractNegotiation.getOffer().getId() : null)
                        .target(offerWithoutOriginalId.getTarget())
                        .assigner(offerWithoutOriginalId.getAssigner())
                        .assignee(offerWithoutOriginalId.getAssignee())
                        .originalId(offerWithoutOriginalId.getId())
                        .permission(offerWithoutOriginalId.getPermission())
                        .build();

                offerRepository.save(offerWithOriginalId);
                log.info("Offer with id {} and original id {} saved", offerWithOriginalId.getId(), offerWithOriginalId.getOriginalId());
                contractNegotiationWithOffer = ContractNegotiation.Builder.newInstance()
                        .id(existingContractNegotiation != null ? existingContractNegotiation.getId() : contractNegotiationFromResponse.getId())
                        .consumerPid(contractNegotiationFromResponse.getConsumerPid())
                        .providerPid(contractNegotiationFromResponse.getProviderPid())
                        .callbackAddress(existingContractNegotiation != null ? existingContractNegotiation.getCallbackAddress() : contractRequestMessageRequest.getForwardTo())
                        .assigner(offerWithOriginalId.getAssigner())
                        .state(contractNegotiationFromResponse.getState())
                        .role(IConstants.ROLE_CONSUMER)
                        .offer(offerWithOriginalId)
                        .agreement(existingContractNegotiation != null ? existingContractNegotiation.getAgreement() : null)
                        .created(existingContractNegotiation != null ? existingContractNegotiation.getCreated() : null)
                        .createdBy(existingContractNegotiation != null ? existingContractNegotiation.getCreatedBy() : null)
                        .version(existingContractNegotiation != null ? existingContractNegotiation.getVersion() : null)
                        .build();
                contractNegotiationRepository.save(contractNegotiationWithOffer);
                log.info("Contract negotiation {} saved", contractNegotiationWithOffer.getId());
                auditEventPublisher.publishEvent(
                        AuditEventType.PROTOCOL_NEGOTIATION_REQUESTED,
                        "Contract negotiation requested",
                        Map.of("contractNegotiationFromResponse", contractNegotiationWithOffer,
                                "offer", offerWithOriginalId,
                                "callbackAddress", address,
                                "role", IConstants.ROLE_API));
            } catch (JsonProcessingException e) {
                log.error("Contract negotiation from response not valid");
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
                        AuditEventType.PROTOCOL_NEGOTIATION_REQUESTED,
                        "Contract negotiation request failed",
                        Map.of("contractRequestMessage", contractRequestMessage,
                                "contractNegotiationErrorMessage", contractNegotiationErrorMessage != null ? contractNegotiationErrorMessage : "No message received",
                                "role", IConstants.ROLE_API));
                throw new ContractNegotiationAPIException(contractNegotiationErrorMessage, "Error making request");
            } catch (JsonProcessingException e) {
                throw new ContractNegotiationAPIException("Error occurred");
            }
        }
        return contractNegotiationWithOffer;
    }

    /**
     * Provider sends offer to consumer.
     *
     * @param contractRequestMessageRequest
     * @return ContractNegotiation
     */
    public ContractNegotiation sendContractOfferMessage(ContractRequestMessageRequest contractRequestMessageRequest) {
        Offer offer = NegotiationSerializer.deserializePlain(contractRequestMessageRequest.getOffer().toPrettyString(), Offer.class);
        ContractOfferMessage contractOfferMessage;
        String address;

        ContractNegotiation existingContractNegotiation = null;
        if (StringUtils.isNotBlank(contractRequestMessageRequest.getContractNegotiationId())) {
            existingContractNegotiation = contractNegotiationRepository.findById(contractRequestMessageRequest.getContractNegotiationId())
                    .orElseThrow(() -> new ContractNegotiationAPIException("Contract negotiation with id: " + contractRequestMessageRequest.getContractNegotiationId() + " not found"));

            if (!existingContractNegotiation.getOffer().getOriginalId().equals(offer.getId()) || !existingContractNegotiation.getOffer().getTarget().equals(offer.getTarget())) {
                throw new ContractNegotiationAPIException("New offer must have same offer id and target as the existing one in the contract negotiation with id: " + contractRequestMessageRequest.getContractNegotiationId());
            }

            stateTransitionCheck(ContractNegotiationState.OFFERED, existingContractNegotiation);

            log.info("Sending ContractOfferMessage as a counter offer to {} to continue existing Contract Negotiation {}", contractRequestMessageRequest.getForwardTo(), contractRequestMessageRequest.getContractNegotiationId());

            contractOfferMessage = ContractOfferMessage.Builder.newInstance()
                    .consumerPid(existingContractNegotiation.getConsumerPid())
                    .providerPid(existingContractNegotiation.getProviderPid())
                    .offer(offer)
                    .build();
            address = ContractNegotiationCallback.getConsumerOffersCallback(existingContractNegotiation.getCallbackAddress(), existingContractNegotiation.getProviderPid());
        } else {
            log.info("Sending ContractOfferMessage to {} to start a new Contract Negotiation", contractRequestMessageRequest.getForwardTo());
            contractOfferMessage = ContractOfferMessage.Builder.newInstance()
                    .callbackAddress(properties.consumerCallbackAddress())
                    .providerPid(ToolsUtil.generateUniqueId())
                    .offer(offer)
                    .build();
            address = ContractNegotiationCallback.getInitialOfferCallback(contractRequestMessageRequest.getForwardTo());
        }

        GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(address,
                NegotiationSerializer.serializeProtocolJsonNode(contractOfferMessage), credentialUtils.getConnectorCredentials());
        log.info("Response received {}", response);
        ContractNegotiation contractNegotiationWithOffer;
        if (response.isSuccess()) {
            try {
                JsonNode jsonNode = mapper.readTree(response.getData());
                ContractNegotiation contractNegotiationFromResponse = NegotiationSerializer.deserializeProtocol(jsonNode, ContractNegotiation.class);

                Offer updatedOffer = Offer.Builder.newInstance()
                        .id(existingContractNegotiation.getOffer().getId())
                        .originalId(existingContractNegotiation.getOffer().getOriginalId())
                        .assignee(contractOfferMessage.getOffer().getAssignee())
                        .assigner(contractOfferMessage.getOffer().getAssigner())
                        .permission(contractOfferMessage.getOffer().getPermission())
                        .target(contractOfferMessage.getOffer().getTarget())
                        .build();
                offerRepository.save(updatedOffer);

                log.info("Offer {} saved", updatedOffer.getId());
                contractNegotiationWithOffer = ContractNegotiation.Builder.newInstance()
                        .id(existingContractNegotiation != null ? existingContractNegotiation.getId() : contractNegotiationFromResponse.getId())
                        .consumerPid(contractNegotiationFromResponse.getConsumerPid())
                        .providerPid(contractNegotiationFromResponse.getProviderPid())
                        .callbackAddress(existingContractNegotiation != null ? existingContractNegotiation.getCallbackAddress() : contractRequestMessageRequest.getForwardTo())
                        .assigner(updatedOffer.getAssigner())
                        .state(contractNegotiationFromResponse.getState())
                        .role(IConstants.ROLE_PROVIDER)
                        .offer(updatedOffer)
                        .agreement(existingContractNegotiation != null ? existingContractNegotiation.getAgreement() : null)
                        .created(existingContractNegotiation != null ? existingContractNegotiation.getCreated() : null)
                        .createdBy(existingContractNegotiation != null ? existingContractNegotiation.getCreatedBy() : null)
                        .version(existingContractNegotiation != null ? existingContractNegotiation.getVersion() : null)
                        .build();
                contractNegotiationRepository.save(contractNegotiationWithOffer);
                log.info("Contract negotiation {} saved", contractNegotiationWithOffer.getId());
                auditEventPublisher.publishEvent(
                        AuditEventType.PROTOCOL_NEGOTIATION_OFFERED,
                        "Contract negotiation offered",
                        Map.of("contractNegotiationFromResponse", contractNegotiationWithOffer,
                                "offer", updatedOffer,
                                "callbackAddress", address,
                                "role", IConstants.ROLE_API));
            } catch (JsonProcessingException e) {
                log.error("Contract negotiation from response not valid");
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
                        AuditEventType.PROTOCOL_NEGOTIATION_OFFERED,
                        "Contract negotiation offer failed",
                        Map.of("ContractOfferMessage", contractOfferMessage,
                                "contractNegotiationErrorMessage", contractNegotiationErrorMessage != null ? contractNegotiationErrorMessage : "No message received",
                                "role", IConstants.ROLE_API));
                throw new ContractNegotiationAPIException(contractNegotiationErrorMessage, "Error making request");
            } catch (JsonProcessingException e) {
                throw new ContractNegotiationAPIException("Error occurred");
            }
        }
        return contractNegotiationWithOffer;
    }

    @Deprecated
    public void sendAgreement(String consumerPid, String providerPid, JsonNode agreementNode) {
        ContractNegotiation contractNegotiation = findContractNegotiationByPids(consumerPid, providerPid);

        stateTransitionCheck(ContractNegotiationState.AGREED, contractNegotiation);

        Agreement agreement = NegotiationSerializer.deserializePlain(agreementNode.toPrettyString(), Agreement.class);
        ContractAgreementMessage agreementMessage = ContractAgreementMessage.Builder.newInstance()
                .consumerPid(consumerPid)
                .providerPid(providerPid)
                .agreement(agreement)
                .build();

        log.info("Sending agreement as provider to {}", contractNegotiation.getCallbackAddress());
        GenericApiResponse<String> response = okHttpRestClient
                .sendRequestProtocol(ContractNegotiationCallback.getContractAgreementCallback(contractNegotiation.getCallbackAddress(), consumerPid),
                        NegotiationSerializer.serializeProtocolJsonNode(agreementMessage),
                        credentialUtils.getConnectorCredentials());
        log.info("Response received {}", response);
        if (response.isSuccess()) {
            ContractNegotiation contractNegotiationUpdated = contractNegotiation.withNewContractNegotiationState(ContractNegotiationState.AGREED);
            contractNegotiationRepository.save(contractNegotiationUpdated);
            log.info("Contract negotiation {} saved", contractNegotiation.getId());
            agreementRepository.save(agreement);
            log.info("Agreement {} saved", agreement.getId());
        } else {
            log.error("Error response received!");
            throw new ContractNegotiationAPIException(response.getMessage());
        }
    }

    /**
     * Finalize negotiation.
     *
     * @param contractNegotiationId - id of the contract negotiation
     */
    public void finalizeNegotiation(String contractNegotiationId) {
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
                    AuditEventType.PROTOCOL_NEGOTIATION_FINALIZED,
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
    public ContractNegotiation handleContractNegotiationAccepted(String contractNegotiationId) {
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
            // Create and publish audit event with request information
            auditEventPublisher.publishEvent(
                    AuditEventType.PROTOCOL_NEGOTIATION_ACCEPTED,
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
    public ContractNegotiation approveContractNegotiation(String contractNegotiationId) {
        ContractNegotiation contractNegotiation = findContractNegotiationById(contractNegotiationId);

        stateTransitionCheck(ContractNegotiationState.AGREED, contractNegotiation);

        ContractAgreementMessage agreementMessage = ContractAgreementMessage.Builder.newInstance()
                .consumerPid(contractNegotiation.getConsumerPid())
                .providerPid(contractNegotiation.getProviderPid())
                .agreement(agreementFromOffer(contractNegotiation.getOffer()))
                .build();
        // TODO this one will fail because provider does not have consumer callbackAddress for sending agreement
        GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(
                ContractNegotiationCallback.getContractAgreementCallback(contractNegotiation.getCallbackAddress(), contractNegotiation.getConsumerPid()),
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
            // Create and publish audit event with request information
            auditEventPublisher.publishEvent(
                    AuditEventType.PROTOCOL_NEGOTIATION_AGREED,
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
    public void verifyNegotiation(String contractNegotiationId) {
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
                        AuditEventType.PROTOCOL_NEGOTIATION_VERIFIED,
                        "Contract negotiation verification failed",
                        Map.of("contractNegotiation", contractNegotiation,
                                "errorMessage", contractNegotiationErrorMessage != null ? contractNegotiationErrorMessage : "No message received",
                                "consumerPid", contractNegotiation.getConsumerPid(),
                                "providerPid", contractNegotiation.getProviderPid(),
                                "role", IConstants.ROLE_API));
                throw new ContractNegotiationAPIException(contractNegotiationErrorMessage, "Provider did not process Verification message correct");
            } catch (JsonProcessingException e) {
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
    public ContractNegotiation terminateContractNegotiation(String contractNegotiationId) {
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
            address = ContractNegotiationCallback.getContractTerminationProvider(contractNegotiation.getCallbackAddress(),
                    contractNegotiation.getProviderPid());
        }
        ContractNegotiationTerminationMessage negotiationTerminatedEventMessage = ContractNegotiationTerminationMessage.Builder.newInstance()
                .consumerPid(contractNegotiation.getConsumerPid())
                .providerPid(contractNegotiation.getProviderPid())
//                .code(contractNegotiationId)
//                .reason(Collections.singletonList(reason))
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
                        AuditEventType.PROTOCOL_NEGOTIATION_TERMINATED,
                        "Contract negotiation termination failed",
                        Map.of("contractNegotiation", contractNegotiation,
                                "errorMessage", contractNegotiationErrorMessage != null ? contractNegotiationErrorMessage : "No message received",
                                "consumerPid", contractNegotiation.getConsumerPid(),
                                "providerPid", contractNegotiation.getProviderPid(),
                                "role", IConstants.ROLE_API));

                if (contractNegotiationErrorMessage != null) {
                    throw new ContractNegotiationAPIException(contractNegotiationErrorMessage, contractNegotiation.getRole() + " did not process Terminate message correct");
                } else {
                    throw new ContractNegotiationAPIException(contractNegotiation.getRole() + " did not process Terminate message correct");
                }
            } catch (JsonProcessingException e) {
                throw new ContractNegotiationAPIException(contractNegotiation.getRole() + " did not process Terminate message correct");
            }
        }
    }

    /**
     * Validate if agreement is valid.
     *
     * @param agreementId - id of the agreement to validate
     */
    public void validateAgreement(String agreementId) {
        log.info("Validating agreement {}", agreementId);
        contractNegotiationRepository.findByAgreement(agreementId)
                .ifPresentOrElse((cn) -> {
                            if (!cn.getState().equals(ContractNegotiationState.FINALIZED)) {
                                throw new ContractNegotiationAPIException("Contract negotiation with Agreement Id " + agreementId + " is not finalized.");
                            }
                        },
                        () -> {
                            throw new ContractNegotiationAPIException("Contract negotiation with Agreement Id " + agreementId + " not found.");
                        });
        // TODO add additional checks like contract dates or else
        //		LocalDateTime agreementStartDate = LocalDateTime.parse(agreement.getTimestamp(), FORMATTER);
        //		agreementStartDate.isBefore(LocalDateTime.now());
        if (!policyAdministrationPoint.policyEnforcementExists(agreementId)) {
            log.warn("Policy enforcement not created, cannot enforce properly");
            throw new ContractNegotiationAPIException("Policy enforcement not found for agreement with Id "
                    + agreementId + " not found.");
        }
    }

    private Agreement agreementFromOffer(Offer offer) {
        return Agreement.Builder.newInstance()
                .id("urn:uuid:" + UUID.randomUUID())
                .assignee(offer.getAssignee())
                .assigner(offer.getAssigner())
                .target(offer.getTarget())
                .timestamp(FORMATTER.format(ZonedDateTime.now()))
                .permission(offer.getPermission())
                .build();
    }

    private void processContractOffer(Offer offer) {
        offerRepository.findById(offer.getId()).ifPresentOrElse(
                o -> log.info("Offer already exists"), () -> offerRepository.save(offer));
        log.info("PROVIDER - Offer {} saved", offer.getId());
    }

    private ContractNegotiation findContractNegotiationByPids(String consumerPid, String providerPid) {
        return contractNegotiationRepository.findByProviderPidAndConsumerPid(providerPid, consumerPid)
                .orElseThrow(() -> {
                    auditEventPublisher.publishEvent(
                            AuditEventType.PROTOCOL_NEGOTIATION_NOT_FOUND,
                            "Contract negotiation not found",
                            Map.of("providerPid", providerPid,
                                    "consumerPid", consumerPid,
                                    "role", IConstants.ROLE_API));
                    return new ContractNegotiationAPIException("Contract negotiation with providerPid " + providerPid +
                            " and consumerPid " + consumerPid + " not found");
                });
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

    public ContractNegotiation sendContractNegotiationEventMessage(ContractNegotiation contractNegotiation, ContractNegotiationEventType eventType) {
        ContractNegotiationEventMessage negotiationEventMessage = ContractNegotiationEventMessage.Builder.newInstance()
                .consumerPid(contractNegotiation.getConsumerPid())
                .providerPid(contractNegotiation.getProviderPid())
                .eventType(eventType)
                .build();
        String address;
        if (IConstants.ROLE_PROVIDER.equals(contractNegotiation.getRole())) {
            address = ContractNegotiationCallback.getContractEventsCallback(contractNegotiation.getCallbackAddress(),
                    contractNegotiation.getConsumerPid());
        } else {
            address = contractNegotiation.getCallbackAddress() + ContractNegotiationCallback.getProviderEventVerificationCallback(contractNegotiation.getCallbackAddress(),
                    contractNegotiation.getProviderPid());
        }

        log.info("Sending ContractNegotiationEventMessage.{} to {}", eventType, address);

        GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(
                address,
                NegotiationSerializer.serializeProtocolJsonNode(negotiationEventMessage),
                null);  // TCK does not have authentication

        ContractNegotiation contractNegotiationUpdated;
        if (response.isSuccess()) {
            // update state to accepted
            contractNegotiationUpdated = contractNegotiation.withNewContractNegotiationState(ContractNegotiationState.ACCEPTED);
            contractNegotiationRepository.save(contractNegotiationUpdated);
            log.info("Contract negotiation {} saved", contractNegotiation.getId());
            return contractNegotiationUpdated;
        } else {
            log.error("Response status not 200 - {} did not process ContractNegotiationEventMessage correct", contractNegotiation.getRole());
            try {
                ContractNegotiationErrorMessage contractNegotiationErrorMessage = null;
                if (StringUtils.isNotBlank(response.getData())) {
                    JsonNode jsonNode = mapper.readTree(response.getData());
                    contractNegotiationErrorMessage = NegotiationSerializer.deserializeProtocol(jsonNode, ContractNegotiationErrorMessage.class);
                }
                throw new ContractNegotiationAPIException(contractNegotiationErrorMessage, contractNegotiation.getRole() + " did not process Verification message correct");
            } catch (JsonProcessingException e) {
                throw new ContractNegotiationAPIException(contractNegotiation.getRole() + " did not process Verification message correct");
            }
        }
    }

    public ContractNegotiation sendContractNegotiationRequest(ContractNegotiation contractNegotiation) {
        ContractRequestMessage contractRequestMessage = ContractRequestMessage.Builder.newInstance()
                .callbackAddress(properties.consumerCallbackAddress())
                .consumerPid(contractNegotiation.getConsumerPid())
                .providerPid(contractNegotiation.getProviderPid())
                .offer(contractNegotiation.getOffer())
                .build();

        String address = null;
        if (IConstants.ROLE_PROVIDER.equals(contractNegotiation.getRole())) {
            address = ContractNegotiationCallback.getInitialNegotiationRequestURL(contractNegotiation.getCallbackAddress());
        } else {
            address = ContractNegotiationCallback.getNegotiationRequestURL(contractNegotiation.getCallbackAddress(),
                    contractNegotiation.getProviderPid());
        }

        log.info("Sending ContractRequestMessage to {}", address);

        GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(address,
                NegotiationSerializer.serializeProtocolJsonNode(contractRequestMessage),
                null);

        log.info("Response received {}", response);
        ContractNegotiation contractNegotiationWithOffer;
        if (response.isSuccess()) {
            try {
                JsonNode jsonNode = mapper.readTree(response.getData());
                ContractNegotiation contractNegotiationResponse = NegotiationSerializer.deserializeProtocol(jsonNode, ContractNegotiation.class);

                contractNegotiationWithOffer = ContractNegotiation.Builder.newInstance()
                        .id(contractNegotiation.getId())
                        .consumerPid(contractNegotiationResponse.getConsumerPid())
                        .providerPid(contractNegotiationResponse.getProviderPid())
                        .callbackAddress(contractNegotiation.getCallbackAddress())
                        .assigner(contractNegotiation.getAssigner())
                        .state(contractNegotiation.getState())
                        .role(IConstants.ROLE_CONSUMER)
                        .offer(contractNegotiation.getOffer())
                        .created(contractNegotiation.getCreated())
                        .createdBy(contractNegotiation.getCreatedBy())
                        .modified(contractNegotiation.getModified())
                        .lastModifiedBy(contractNegotiation.getLastModifiedBy())
                        .version(contractNegotiation.getVersion())
                        .build();
                contractNegotiationRepository.save(contractNegotiationWithOffer);
                log.info("Contract negotiation {} saved", contractNegotiationWithOffer.getId());
                auditEventPublisher.publishEvent(
                        AuditEventType.PROTOCOL_NEGOTIATION_REQUESTED,
                        "Contract negotiation requested",
                        Map.of("contractNegotiation", contractNegotiationWithOffer,
                                "offer", contractNegotiationResponse.getOffer(),
                                "callbackAddress", contractNegotiation.getCallbackAddress(),
                                "role", IConstants.ROLE_API));
                return contractNegotiationWithOffer;
            } catch (JsonProcessingException e) {
                log.error("Contract negotiation from response not valid");
                throw new ContractNegotiationAPIException(e.getLocalizedMessage(), e);
            }
        } else {
            log.info("Error response received! {}", response.getMessage());
            try {
                ContractNegotiationErrorMessage contractNegotiationErrorMessage = null;
                if (StringUtils.isNotBlank(response.getData())) {
                    JsonNode jsonNode = mapper.readTree(response.getData());
                    contractNegotiationErrorMessage = NegotiationSerializer.deserializeProtocol(jsonNode, ContractNegotiationErrorMessage.class);
                }
                auditEventPublisher.publishEvent(
                        AuditEventType.PROTOCOL_NEGOTIATION_REQUESTED,
                        "Contract negotiation request failed",
                        Map.of("contractRequestMessage", contractRequestMessage,
                                "contractNegotiationErrorMessage", contractNegotiationErrorMessage != null ? contractNegotiationErrorMessage : "No message received",
                                "role", IConstants.ROLE_API));
                throw new ContractNegotiationAPIException(contractNegotiationErrorMessage, "Error making request");
            } catch (JsonProcessingException e) {
                throw new ContractNegotiationAPIException("Error occurred");
            }
        }
    }
}
