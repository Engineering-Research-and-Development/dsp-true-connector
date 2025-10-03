package it.eng.negotiation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import it.eng.negotiation.rest.protocol.ContractNegotiationCallback;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.event.datatransfer.InitializeTransferProcess;
import it.eng.tools.model.IConstants;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.service.AuditEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class ContractNegotiationConsumerService extends BaseProtocolService {

    private final AgreementRepository agreementRepository;
    private final PolicyAdministrationPoint policyAdministrationPoint;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Environment environment;

    public ContractNegotiationConsumerService(AuditEventPublisher publisher,
                                              ContractNegotiationRepository contractNegotiationRepository, OkHttpRestClient okHttpRestClient,
                                              ContractNegotiationProperties properties, OfferRepository offerRepository,
                                              AgreementRepository agreementRepository, PolicyAdministrationPoint policyAdministrationPoint, Environment environment) {
        super(publisher, contractNegotiationRepository, okHttpRestClient, properties, offerRepository);
        this.agreementRepository = agreementRepository;
        this.policyAdministrationPoint = policyAdministrationPoint;
        this.environment = environment;
    }

    /**
     * Process contract offer.
     *
     * @param contractOfferMessage
     * @return ContractNegotiation as JsonNode
     */
    public ContractNegotiation processContractOffer(ContractOfferMessage contractOfferMessage) {
//        checkIfContractNegotiationExists(contractOfferMessage.getConsumerPid(), contractOfferMessage.getProviderPid());

//        processContractOffer(contractOfferMessage.getOffer());
        ContractNegotiation contractNegotiationInitial =
                contractNegotiationRepository.findByConsumerPid(contractOfferMessage.getConsumerPid())
                        .orElseThrow(() -> new ContractNegotiationNotFoundException(
                                "Contract negotiation with consumerPid " + contractOfferMessage.getConsumerPid() +
                                        " not found", contractOfferMessage.getConsumerPid(), contractOfferMessage.getProviderPid()));

        log.info("CONSUMER - saving negotiation with state OFFERED");
        ContractNegotiation contractNegotiation = ContractNegotiation.Builder.newInstance()
                .consumerPid(contractNegotiationInitial.getConsumerPid())
                .providerPid(contractNegotiationInitial.getProviderPid())
                .state(ContractNegotiationState.OFFERED)
                .role(IConstants.ROLE_CONSUMER)
                .offer(contractOfferMessage.getOffer())
                .assigner(contractOfferMessage.getOffer().getAssigner())
                .callbackAddress(contractNegotiationInitial.getCallbackAddress())
                .build();
        contractNegotiationRepository.save(contractNegotiation);

        if (Arrays.asList(environment.getActiveProfiles()).contains("tck")) {
            log.info("TCK profile running - publishing event - {}", contractNegotiation.getState());
            log.info("ConsumerPid: {}, ProviderPid: {}", contractNegotiation.getConsumerPid(), contractNegotiation.getProviderPid());
            publisher.publishEvent(contractNegotiation);
        }
        return contractNegotiation;
    }

    private void processContractOffer(Offer offer) {
        offerRepository.findById(offer.getId()).ifPresentOrElse(
                o -> log.info("Offer already exists"), () -> offerRepository.save(offer));
        log.info("CONSUMER - Offer {} saved", offer.getId());
    }

    protected String createNewPid() {
        return "urn:uuid:" + UUID.randomUUID();
    }

    /**
     * The response body is not specified and clients are not required to process it.
     *
     * @param contractAgreementMessage
     */

    public void handleAgreement(ContractAgreementMessage contractAgreementMessage) {
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
                .callbackAddress(contractAgreementMessage.getCallbackAddress())
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
        if (environment.matchesProfiles("tck")) {
            log.info("TCK profile running - publishing event - {}", contractNegotiationAgreed.getState());
            log.info("ConsumerPid: {}, ProviderPid: {}", contractNegotiationAgreed.getConsumerPid(), contractNegotiationAgreed.getProviderPid());
            publisher.publishEvent(contractNegotiationAgreed);
        }
    }

    /**
     * The response body is not specified and clients are not required to process it.
     *
     * @param contractNegotiationEventMessage
     */
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

        log.debug("Creating polcyEnforcement for agreementId {}", contractNegotiation.getAgreement().getId());
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

    public ContractNegotiation processTCKRequest(TCKContractNegotiationRequest contractNegotiationRequest) {

        Offer offer = Offer.Builder.newInstance()
                .id(contractNegotiationRequest.getOfferId())
                .permission(List.of(Permission.Builder.newInstance().action(Action.USE).build()))
                .target(contractNegotiationRequest.getDatasetId())
                .build();

        ContractRequestMessage contractRequestMessage = ContractRequestMessage.Builder.newInstance()
                .callbackAddress(properties.consumerCallbackAddress())
                .consumerPid("urn:uuid:" + UUID.randomUUID())
                .offer(offer)
                .build();

        String connectorAddress = ContractNegotiationCallback.getNegotiationRequestURL(contractNegotiationRequest.getConnectorAddress());
        log.info("Sending contract request to {}", connectorAddress);
        GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(connectorAddress,
                NegotiationSerializer.serializeProtocolJsonNode(contractRequestMessage),
                null);
        log.info("Response received {}", response);

        if (response.isSuccess()) {
            JsonNode jsonNode = null;
            try {
                jsonNode = mapper.readTree(response.getData());
                ContractNegotiation contractNegotiationResponse = NegotiationSerializer.deserializeProtocol(jsonNode, ContractNegotiation.class);
                ContractNegotiation contractNegotiationUpdated = ContractNegotiation.Builder.newInstance()
                        .consumerPid(contractRequestMessage.getConsumerPid())
                        .providerPid(contractNegotiationResponse.getProviderPid())
                        .callbackAddress(contractNegotiationResponse.getCallbackAddress() != null ?
                                contractNegotiationResponse.getCallbackAddress() : contractNegotiationRequest.getConnectorAddress())
                        .assigner(offer.getAssigner())
                        .state(contractNegotiationResponse.getState())
                        .role(IConstants.ROLE_CONSUMER)
                        .offer(offer)
//                        .created(contractNegotiation.getCreated())
//                        .createdBy(contractNegotiation.getCreatedBy())
//                        .modified(contractNegotiation.getModified())
//                        .lastModifiedBy(contractNegotiation.getLastModifiedBy())
//                        .version(contractNegotiation.getVersion())
                        .build();
                contractNegotiationRepository.save(contractNegotiationUpdated);
                log.info("Contract negotiation saved with id {}", contractNegotiationUpdated.getId());
                return contractNegotiationUpdated;
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        throw new ContractNegotiationAPIException("Error occurred");
    }
}
