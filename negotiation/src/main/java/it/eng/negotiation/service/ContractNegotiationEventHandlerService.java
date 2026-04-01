package it.eng.negotiation.service;

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
import it.eng.tools.event.policyenforcement.ArtifactConsumedEvent;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.service.AuditEventPublisher;
import it.eng.tools.util.CredentialUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.UUID;

@Service
@Slf4j
public class ContractNegotiationEventHandlerService extends BaseProtocolService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final AgreementRepository agreementRepository;
    protected final CredentialUtils credentialUtils;
    private final PolicyAdministrationPoint policyAdministrationPoint;

    public ContractNegotiationEventHandlerService(AuditEventPublisher publisher,
                                                  ContractNegotiationRepository contractNegotiationRepository, OkHttpRestClient okHttpRestClient,
                                                  ContractNegotiationProperties properties, OfferRepository offerRepository,
                                                  AgreementRepository agreementRepository, CredentialUtils credentialUtils,
                                                  PolicyAdministrationPoint policyAdministrationPoint) {
        super(publisher, contractNegotiationRepository, okHttpRestClient, properties, offerRepository);
        this.agreementRepository = agreementRepository;
        this.credentialUtils = credentialUtils;
        this.policyAdministrationPoint = policyAdministrationPoint;
    }


    public ContractNegotiation handleContractNegotiationTerminated(String contractNegotiationId) {
        ContractNegotiation contractNegotiation = findContractNegotiationById(contractNegotiationId);
        // for now, log it; maybe we can publish event?
        log.info("Contract negotiation with consumerPid {} and providerPid {} declined", contractNegotiation.getConsumerPid(), contractNegotiation.getProviderPid());
        ContractNegotiationTerminationMessage negotiationTerminatedEventMessage = ContractNegotiationTerminationMessage.Builder.newInstance()
                .consumerPid(contractNegotiation.getConsumerPid())
                .providerPid(contractNegotiation.getProviderPid())
                .code(contractNegotiationId)
                .reason(Collections.singletonList("Contract negotiation terminated by provider"))
                .build();

        GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(
                ContractNegotiationCallback.getContractTerminationCallback(contractNegotiation.getCallbackAddress(), contractNegotiation.getConsumerPid()),
                NegotiationSerializer.serializeProtocolJsonNode(negotiationTerminatedEventMessage),
                credentialUtils.getConnectorCredentials());
        if (response.isSuccess()) {
            log.info("Updating status for negotiation {} to terminated", contractNegotiation.getId());
            ContractNegotiation contractNegotiationTerminated = contractNegotiation.withNewContractNegotiationState(ContractNegotiationState.TERMINATED);
            contractNegotiationRepository.save(contractNegotiationTerminated);
            return contractNegotiationTerminated;
        } else {
            log.error("Response status not 200 - consumer did not process AgreementMessage correct");
            throw new ContractNegotiationAPIException("consumer did not process AgreementMessage correct");
        }
    }

    private Agreement agreementFromOffer(Offer offer, String assigner) {
        return Agreement.Builder.newInstance()
                .id(UUID.randomUUID().toString())
                .assignee(properties.getAssignee())
                .assigner(assigner)
                .target(offer.getTarget())
                .timestamp(FORMATTER.format(ZonedDateTime.now()))
                .permission(offer.getPermission())
                .build();
    }

    public void verifyNegotiation(String consumerPid, String providerPid) {
        log.info("ConsumerPid - {} , providerPid - {}", consumerPid, providerPid);
        ContractNegotiation contractNegotiation = findContractNegotiationByPids(consumerPid, providerPid);

        stateTransitionCheck(ContractNegotiationState.VERIFIED, contractNegotiation);

        ContractAgreementVerificationMessage verificationMessage = ContractAgreementVerificationMessage.Builder.newInstance()
                .consumerPid(consumerPid)
                .providerPid(providerPid)
                .build();

        log.info("Found initial negotiation - CallbackAddress {}", contractNegotiation.getCallbackAddress());

        String callbackAddress = ContractNegotiationCallback.getProviderAgreementVerificationCallback(contractNegotiation.getCallbackAddress(), providerPid);
        log.info("Sending verification message to provider to {}", callbackAddress);
        GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(callbackAddress,
                NegotiationSerializer.serializeProtocolJsonNode(verificationMessage),
                credentialUtils.getConnectorCredentials());

        if (response.isSuccess()) {
            log.info("Updating status for negotiation {} to verified", contractNegotiation.getId());
            ContractNegotiation contractNegotiationUpdate = ContractNegotiation.Builder.newInstance()
                    .id(contractNegotiation.getId())
                    .callbackAddress(contractNegotiation.getCallbackAddress())
                    .consumerPid(contractNegotiation.getConsumerPid())
                    .providerPid(contractNegotiation.getProviderPid())
                    .state(ContractNegotiationState.VERIFIED)
                    .build();
            contractNegotiationRepository.save(contractNegotiationUpdate);
        } else {
            log.error("Response status not 200 - provider did not process Verification message correct");
            throw new ContractNegotiationAPIException("provider did not process Verification message correct");
        }
    }

    public void artifactConsumedEvent(ArtifactConsumedEvent artifactConsumedEvent) {
        log.info("Increasing access count for artifactId {}", artifactConsumedEvent.getAgreementId());
        policyAdministrationPoint.updateAccessCount(artifactConsumedEvent.getAgreementId());
    }
}
