package it.eng.negotiation.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.negotiation.exception.ContractNegotiationInvalidEventTypeException;
import it.eng.negotiation.exception.ContractNegotiationInvalidStateException;
import it.eng.negotiation.exception.ContractNegotiationNotFoundException;
import it.eng.negotiation.exception.OfferNotFoundException;
import it.eng.negotiation.listener.ContractNegotiationPublisher;
import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.ContractAgreementMessage;
import it.eng.negotiation.model.ContractAgreementVerificationMessage;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationEventMessage;
import it.eng.negotiation.model.ContractNegotiationEventType;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.ContractNegotiationTerminationMessage;
import it.eng.negotiation.model.ContractOfferMessage;
import it.eng.negotiation.model.Offer;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.OfferRepository;
import it.eng.negotiation.serializer.Serializer;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ContractNegotiationConsumerService {

	private final ContractNegotiationProperties properties;
    private final ContractNegotiationPublisher publisher;
	private final ContractNegotiationRepository repository;
	private final OfferRepository offerRepository;
	private final AgreementRepository agreementRepository;
	
    public ContractNegotiationConsumerService(ContractNegotiationProperties properties,
			ContractNegotiationPublisher publisher, ContractNegotiationRepository repository, AgreementRepository agreementRepository, OfferRepository offerRepository) {
		this.properties = properties;
		this.publisher = publisher;
		this.repository = repository;
		this.offerRepository = offerRepository;
		this.agreementRepository = agreementRepository;
	}

	/**
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

    public JsonNode processContractOffer(ContractOfferMessage contractOfferMessage) {
        //TODO consumer side only - handle consumerPid and providerPid
    	processContractOffer(contractOfferMessage.getOffer());
        ContractNegotiation contractNegotiation = ContractNegotiation.Builder.newInstance()
                .consumerPid(contractOfferMessage.getProviderPid())
                .providerPid(createNewPid())
                .state(ContractNegotiationState.OFFERED)
                .build();
        repository.save(contractNegotiation);
        return Serializer.serializeProtocolJsonNode(contractNegotiation);
    }
    
    //TODO save offer so it can be decided what to do with it
    private void processContractOffer(Offer offer) {
    	// automatic processing or manual
		
	}

	protected String createNewPid() {
        return "urn:uuid:" + UUID.randomUUID();
    }

    /**
     * The response body is not specified and clients are not required to process it.
     *
     * @param consumerPid
     * @param contractOfferMessage
     * @return
     */

    public JsonNode handleNegotiationOfferConsumer(String consumerPid, ContractOfferMessage contractOfferMessage) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode testNode = mapper.createObjectNode();
        return testNode;
    }

    /**
     * The response body is not specified and clients are not required to process it.
     *
     * @param contractAgreementMessage
     */

    public void handleAgreement(ContractAgreementMessage contractAgreementMessage) {
    	// save callbackAddress into ContractNegotiation - used for sending ContractNegotiationEventMessage.FINALIZED 
    	ContractNegotiation contractNegotiation = validateNegotiation(contractAgreementMessage.getConsumerPid(), contractAgreementMessage.getProviderPid());
    	
    	if (!contractNegotiation.getState().canTransitTo(ContractNegotiationState.AGREED)) {
			throw new ContractNegotiationInvalidStateException("Agreement aborted, wrong state " + contractNegotiation.getState().name(),
					contractAgreementMessage.getConsumerPid(), contractAgreementMessage.getProviderPid());
		}
    	
    	validateAgreementAgainstOffer(contractAgreementMessage);

    	
    	ContractNegotiation contractNegotiationUpdated = ContractNegotiation.Builder.newInstance()
    			.id(contractNegotiation.getId())
                .state(ContractNegotiationState.AGREED)
                .consumerPid(contractNegotiation.getConsumerPid())
                .providerPid(contractNegotiation.getProviderPid())
                .callbackAddress(contractAgreementMessage.getCallbackAddress())
                .build();
    	log.info("CONSUMER - updating negotiation with state AGREED");
    	repository.save(contractNegotiationUpdated);
    	log.info("CONSUMER - negotiation {} updated with state AGREED", contractNegotiation.getId());
    	Agreement agreement = Agreement.Builder.newInstance()
    			.assignee(contractAgreementMessage.getAgreement().getAssignee())
    			.assigner(contractAgreementMessage.getAgreement().getAssigner())
    			.id(contractAgreementMessage.getAgreement().getId())
    			.permission(contractAgreementMessage.getAgreement().getPermission())
    			.target(contractAgreementMessage.getAgreement().getTarget())
    			.timestamp(contractAgreementMessage.getAgreement().getTimestamp())
    			.consumerPid(contractNegotiation.getConsumerPid())
    			.providerPid(contractNegotiation.getProviderPid())
    			.build();
    	log.info("CONSUMER - saving agreement");
    	agreementRepository.save(agreement);
    	log.info("CONSUMER - agreement {} saved", contractAgreementMessage.getAgreement().getId());
    	
    	// sends verification message to provider
    	// TODO add error handling in case not correct
    	if(properties.isAutomaticNegotiation()) {
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
    	
    	ContractNegotiation contractNegotiation = validateNegotiation(contractNegotiationEventMessage.getConsumerPid(), contractNegotiationEventMessage.getProviderPid());

    	if (!contractNegotiation.getState().canTransitTo(ContractNegotiationState.FINALIZED)) {
			throw new ContractNegotiationInvalidStateException(
					"Contract negotiation with providerPid " + contractNegotiation.getProviderPid() + 
					" and consumerPid " + contractNegotiation.getConsumerPid() + " is not in VERIFIED state, aborting verification", contractNegotiation.getConsumerPid(), contractNegotiation.getProviderPid());
		}
    	
		log.info("CONSUMER - updating Contract Negotiation state to FINALIZED");
		ContractNegotiation contractNegotiationUpdated = contractNegotiation.withNewContractNegotiationState(ContractNegotiationState.FINALIZED);
		log.info("CONSUMER - saving updated contract negotiation");
		repository.save(contractNegotiationUpdated);
    }

    /**
     * The response body is not specified and clients are not required to process it.
     *
     * @param consumerPid
     * @param contractNegotiationTerminationMessage
     * @return
     */

    public JsonNode handleTerminationResponse(String consumerPid, ContractNegotiationTerminationMessage contractNegotiationTerminationMessage) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode testNode = mapper.createObjectNode();
        return testNode;
    }
    
    
    private Offer validateAgreementAgainstOffer(ContractAgreementMessage contractAgreementMessage) {
		return offerRepository
				.findByConsumerPidAndProviderPidAndTarget(contractAgreementMessage.getConsumerPid(),
						contractAgreementMessage.getProviderPid(),
						contractAgreementMessage.getAgreement().getTarget())
				.orElseThrow(() -> new OfferNotFoundException(
						"Offer with following values from Agreement not found: providerPid " + contractAgreementMessage.getProviderPid() + 
						" and consumerPid " + contractAgreementMessage.getConsumerPid() + "and target " + contractAgreementMessage.getAgreement().getTarget()));
	}

	private ContractNegotiation validateNegotiation(String consumerPid, String providerPid) {
		return repository
				.findByProviderPidAndConsumerPid(providerPid, consumerPid)
				.orElseThrow(() -> new ContractNegotiationNotFoundException(
						"Contract negotiation with providerPid " + providerPid + 
						" and consumerPid " + consumerPid + " not found"));
	}
}
