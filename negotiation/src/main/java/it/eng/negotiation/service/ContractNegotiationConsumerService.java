package it.eng.negotiation.service;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.negotiation.exception.ContractNegotiationInvalidEventTypeException;
import it.eng.negotiation.exception.ContractNegotiationNotFoundException;
import it.eng.negotiation.exception.OfferNotFoundException;
import it.eng.negotiation.model.*;
import it.eng.negotiation.policy.service.PolicyAdministrationPoint;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.OfferRepository;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.event.datatransfer.InitializeTransferProcess;
import it.eng.tools.model.IConstants;
import it.eng.tools.service.AuditEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class ContractNegotiationConsumerService extends BaseProtocolService {

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

    /*
     * {
     * "@context": "https://w3id.org/dspace/v0.8/context.json",
     * "@type": "dspace:ContractNegotiation",
     * "dspace:providerPid": "urn:uuid:dcbf434c-eacf-4582-9a02-f8dd50120fd3",
     * "dspace:consumerPid": "urn:uuid:32541fe6-c580-409e-85a8-8a9a32fbe833",
     * "dspace:state" :"OFFERED"
     * }
     *
     * @param contractOfferMessage
     * @return
     */

    /**
     * Process contract offer.
     *
     * @param contractOfferMessage
     * @return ContractNegotiation as JsonNode
     */
    public JsonNode processContractOffer(ContractOfferMessage contractOfferMessage) {
        checkIfContractNegotiationExists(contractOfferMessage.getConsumerPid(), contractOfferMessage.getProviderPid());

        processContractOffer(contractOfferMessage.getOffer());

        ContractNegotiation contractNegotiation = ContractNegotiation.Builder.newInstance()
                .consumerPid("urn:uuid:" + UUID.randomUUID())
                .providerPid(contractOfferMessage.getProviderPid())
                .state(ContractNegotiationState.OFFERED)
                .role(IConstants.ROLE_CONSUMER)
                .offer(contractOfferMessage.getOffer())
                .assigner(contractOfferMessage.getOffer().getAssigner())
                .callbackAddress(contractOfferMessage.getCallbackAddress())
                .build();
        contractNegotiationRepository.save(contractNegotiation);
        return NegotiationSerializer.serializeProtocolJsonNode(contractNegotiation);
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
        if (properties.isAutomaticNegotiation()) {
            log.debug("Automatic negotiation - processing sending ContractAgreementVerificationMessage");
            ContractAgreementVerificationMessage verificationMessage = ContractAgreementVerificationMessage.Builder.newInstance()
                    .consumerPid(contractAgreementMessage.getConsumerPid())
                    .providerPid(contractAgreementMessage.getProviderPid())
                    .build();
            publisher.publishEvent(verificationMessage);
        } else {
            log.debug("Sending only 200 if agreement is valid, ContractAgreementVerificationMessage must be manually sent");
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
}
