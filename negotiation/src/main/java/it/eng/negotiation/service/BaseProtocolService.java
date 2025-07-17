package it.eng.negotiation.service;

import it.eng.negotiation.exception.ContractNegotiationExistsException;
import it.eng.negotiation.exception.ContractNegotiationInvalidStateException;
import it.eng.negotiation.exception.ContractNegotiationNotFoundException;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.OfferRepository;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashMap;
import java.util.Map;

public abstract class BaseProtocolService {

    protected final ApplicationEventPublisher publisher;
    protected final ContractNegotiationRepository contractNegotiationRepository;
    protected final OkHttpRestClient okHttpRestClient;
    protected final ContractNegotiationProperties properties;
    protected final OfferRepository offerRepository;

    public BaseProtocolService(ApplicationEventPublisher publisher,
                               ContractNegotiationRepository contractNegotiationRepository, OkHttpRestClient okHttpRestClient,
                               ContractNegotiationProperties properties, OfferRepository offerRepository) {
        super();
        this.publisher = publisher;
        this.contractNegotiationRepository = contractNegotiationRepository;
        this.okHttpRestClient = okHttpRestClient;
        this.properties = properties;
        this.offerRepository = offerRepository;
    }

    protected ContractNegotiation findContractNegotiationByPids(String consumerPid, String providerPid) {
        return contractNegotiationRepository.findByProviderPidAndConsumerPid(providerPid, consumerPid)
                .orElseThrow(() -> {
                    publisher.publishEvent(AuditEvent.Builder.newInstance()
                            .eventType(AuditEventType.PROTOCOL_NEGOTIATION_NOT_FOUND)
                            .description("Contract negotiation not found")
                            .details(Map.of("consumerPid", consumerPid,
                                    "providerPid", providerPid))
                            .build());
                    return new ContractNegotiationNotFoundException(
                            "Contract negotiation with providerPid " + providerPid +
                                    " and consumerPid " + consumerPid + " not found", consumerPid, providerPid);
                });
    }

    protected void stateTransitionCheck(ContractNegotiationState newState, ContractNegotiation contractNegotiation) {
        if (!contractNegotiation.getState().canTransitTo(newState)) {
            publisher.publishEvent(AuditEvent.Builder.newInstance()
                    .eventType(AuditEventType.PROTOCOL_NEGOTIATION_STATE_TRANSITION_ERROR)
                    .description("Contract negotiation state transition error")
                    .details(Map.of("contractNegotiation", contractNegotiation,
                            "currentState", contractNegotiation.getState(),
                            "newState", newState))
                    .build());
            throw new ContractNegotiationInvalidStateException("State transition aborted, " + contractNegotiation.getState().name()
                    + " state can not transition to " + newState.name(),
                    contractNegotiation.getConsumerPid(), contractNegotiation.getProviderPid());
        }
    }

    protected void checkIfContractNegotiationExists(String consumerPid, String providerPid) {
        contractNegotiationRepository
                .findByProviderPidAndConsumerPid(providerPid, consumerPid)
                .ifPresent(cn -> {
                    Map<String, Object> details = new HashMap<>();
                    if (providerPid != null) {
                        details.put("providerPid", providerPid);
                    }
                    if (consumerPid != null) {
                        details.put("consumerPid", consumerPid);
                    }
                    publisher.publishEvent(AuditEvent.Builder.newInstance()
                            .eventType(AuditEventType.PROTOCOL_NEGOTIATION_CONTRACT_NEGOTIATION)
                            .description("Contract negotiation already exists")
                            .details(details)
                            .build());
                    throw new ContractNegotiationExistsException("Contract negotiation with providerPid " + cn.getProviderPid() +
                            " and consumerPid " + cn.getConsumerPid() + " already exists");
                });
    }

    protected ContractNegotiation findContractNegotiationById(String contractNegotiationId) {
        return contractNegotiationRepository.findById(contractNegotiationId)
                .orElseThrow(() -> {
                    publisher.publishEvent(AuditEvent.Builder.newInstance()
                            .eventType(AuditEventType.PROTOCOL_NEGOTIATION_NOT_FOUND)
                            .description("Contract negotiation not found")
                            .details(Map.of("contractNegotiationId", contractNegotiationId))
                            .build());
                    return new ContractNegotiationNotFoundException("Contract negotiation with id " + contractNegotiationId + " not found");
                });
    }

}
