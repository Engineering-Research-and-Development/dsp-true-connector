package it.eng.negotiation.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.negotiation.exception.ContractNegotiationNotFoundException;
import it.eng.negotiation.listener.ContractNegotiationPublisher;
import it.eng.negotiation.model.ContractAgreementMessage;
import it.eng.negotiation.model.ContractAgreementVerificationMessage;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationEventMessage;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.ContractNegotiationTerminationMessage;
import it.eng.negotiation.model.ContractOfferMessage;
import it.eng.negotiation.model.Offer;
import it.eng.negotiation.model.Serializer;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ContractNegotiationConsumerService {

	private ContractNegotiationProperties properties;
    private ContractNegotiationPublisher publisher;
	private ContractNegotiationRepository repository;
	
    public ContractNegotiationConsumerService(ContractNegotiationProperties properties,
			ContractNegotiationPublisher publisher, ContractNegotiationRepository repository) {
		this.properties = properties;
		this.publisher = publisher;
		this.repository = repository;
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
     * @param consumerPid
     * @param contractAgreementMessage
     * @return
     */

    public void handleAgreement(String callbackAddress, ContractAgreementMessage contractAgreementMessage) {
    	// ends verification message to provider
    	// TODO add error handling in case not correct
    	if(properties.isAutomaticNegotiation()) {
    		log.debug("Automatic negotiation - processing sending ContractAgreementVerificationMessage");
    		ContractAgreementVerificationMessage verificationMessage = ContractAgreementVerificationMessage.Builder.newInstance()
    				.consumerPid(contractAgreementMessage.getConsumerPid())
    				.providerPid(contractAgreementMessage.getProviderPid())
    				.build();
    				publisher.publishEvent(verificationMessage);
    	} else {
    		log.debug("Must send manually ContractAgreementVerificationMessage");
    	}
    	// save callbackAddress into ContractNegotiation - used for sending ContractNegotiationEventMessage.FINALIZED 
    	ContractNegotiation contractNegotiation = repository
				.findByProviderPidAndConsumerPid(contractAgreementMessage.getProviderPid(), contractAgreementMessage.getConsumerPid())
				.orElseThrow(() -> new ContractNegotiationNotFoundException(
						"Contract negotiation with providerPid " + contractAgreementMessage.getProviderPid() + 
						" and consumerPid " + contractAgreementMessage.getConsumerPid() + " not found"));

    	ContractNegotiation contractNegtiationUpdate = ContractNegotiation.Builder.newInstance()
			.id(contractNegotiation.getId())
	        .state(ContractNegotiationState.AGREED)
	        .consumerPid(contractNegotiation.getConsumerPid())
	        .providerPid(contractNegotiation.getProviderPid())
	        .callbackAddress(contractAgreementMessage.getCallbackAddress())
	        .build();
    	log.info("CONSUMER - updating negotiation with state AGREED");
    	repository.save(contractNegtiationUpdate);
    	// TODO save agreement also
    	
    }

    /**
     * The response body is not specified and clients are not required to process it.
     *
     * @param consumerPid
     * @param contractNegotiationEventMessage
     * @return
     */

    public JsonNode handleEventsResponse(String consumerPid, ContractNegotiationEventMessage contractNegotiationEventMessage) {
    	ContractNegotiation contractNegotiation = repository
				.findByProviderPidAndConsumerPid(contractNegotiationEventMessage.getProviderPid(), contractNegotiationEventMessage.getConsumerPid())
				.orElseThrow(() -> new ContractNegotiationNotFoundException(
						"Contract negotiation with providerPid " + contractNegotiationEventMessage.getProviderPid() + 
						" and consumerPid " + contractNegotiationEventMessage.getConsumerPid() + "not found"));

    	switch (contractNegotiationEventMessage.getEventType())	{
    		case ACCEPTED: 
    			break;
		
    		case FINALIZED: {
    			debugLogContractNegotiation(contractNegotiation);
    			log.info("Updating ContractNegotiation.state to FINALIZED");
    			ContractNegotiation contractNegtiationUpdate = ContractNegotiation.Builder.newInstance()
    					.id(contractNegotiation.getId())
    					.state(ContractNegotiationState.FINALIZED)
    					.consumerPid(contractNegotiation.getConsumerPid())
    					.providerPid(contractNegotiation.getProviderPid())
    					.callbackAddress(contractNegotiation.getCallbackAddress())
    					.build();
    			log.info("CONSUMER - updating negotiation with state FINALIZED");
    			repository.save(contractNegtiationUpdate);
    			break;
    		}
		default: 
			throw new IllegalArgumentException("Unexpected value: " + contractNegotiationEventMessage.getEventType());
		}
    	
    	return null;
    }

	private void debugLogContractNegotiation(ContractNegotiation contractNegotiation) {
		log.debug("ContractNegotiation consumerPid {}, providerPid {}, state {}", contractNegotiation.getConsumerPid(), contractNegotiation.getProviderPid(),
				contractNegotiation.getState());
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
}
