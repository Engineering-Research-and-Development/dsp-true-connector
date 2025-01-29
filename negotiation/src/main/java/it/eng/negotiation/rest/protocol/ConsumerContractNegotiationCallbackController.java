package it.eng.negotiation.rest.protocol;

import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.model.ContractAgreementMessage;
import it.eng.negotiation.model.ContractNegotiationErrorMessage;
import it.eng.negotiation.model.ContractNegotiationEventMessage;
import it.eng.negotiation.model.ContractNegotiationTerminationMessage;
import it.eng.negotiation.model.ContractOfferMessage;
import it.eng.negotiation.model.Description;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.negotiation.service.ContractNegotiationConsumerService;
import it.eng.tools.model.DSpaceConstants;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class ConsumerContractNegotiationCallbackController {

    private ContractNegotiationConsumerService contractNegotiationConsumerService;
    private ContractNegotiationProperties properties;

    public ConsumerContractNegotiationCallbackController(ContractNegotiationConsumerService contractNegotiationConsumerService, 
    		ContractNegotiationProperties properties) {
        super();
        this.contractNegotiationConsumerService = contractNegotiationConsumerService;
        this.properties = properties;
    }

    //	https://consumer.com/negotiations/offers	POST	ContractOfferMessage
    // returns 201 with body ContractNegotiation - OFFERED
    @PostMapping("/negotiations/offers")
    public ResponseEntity<JsonNode> handleNegotiationOffers(@RequestBody JsonNode contractOfferMessageJsonNode) {
        ContractOfferMessage contractOfferMessage = NegotiationSerializer.deserializeProtocol(contractOfferMessageJsonNode,
                ContractOfferMessage.class);

        JsonNode responseNode = contractNegotiationConsumerService.processContractOffer(contractOfferMessage);
        // send OK 201
        log.info("Sending response OK in callback case");
        return ResponseEntity.created(createdURI(responseNode))
        		.body(NegotiationSerializer.serializeProtocolJsonNode(responseNode));
    }
    
    private URI createdURI(JsonNode responseNode) {
    	// "https://provider.com/negotiations/:providerPid"
    	String providerPid = responseNode.get(DSpaceConstants.DSPACE_PROVIDER_PID).asText();
    	return URI.create(properties.providerCallbackAddress() + "/negotiations/" + providerPid);
    }

    // https://consumer.com/:callback/negotiations/:consumerPid/offers	POST	ContractOfferMessage
    // process message - if OK return 200; The response body is not specified and clients are not required to process it.
    @PostMapping("/consumer/negotiations/{consumerPid}/offers")
    public ResponseEntity<JsonNode> handleNegotiationOfferConsumerPid(@PathVariable String consumerPid,
                                                                      @RequestBody JsonNode contractOfferMessageJsonNode) throws InterruptedException, ExecutionException {
        ContractOfferMessage contractOfferMessage = 
        		NegotiationSerializer.deserializeProtocol(contractOfferMessageJsonNode, ContractOfferMessage.class);

//		callbackAddress = callbackAddress.endsWith("/") ? callbackAddress : callbackAddress + "/";
//		String finalCallback = callbackAddress + ContactNegotiationCallback.getNegotiationOfferConsumer(callbackAddress);
        log.info("NOT IPLEMENTED YET!!!");
//        return ResponseEntity.of().contentType(MediaType.APPLICATION_JSON).build();
        ContractNegotiationErrorMessage error = methodNotYetImplemented();
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
        		.body(NegotiationSerializer.serializeProtocolJsonNode(error));
    }

    // https://consumer.com/:callback/negotiations/:consumerPid/agreement	POST	ContractAgreementMessage
    // after successful processing - 200 ok; body not specified
    @PostMapping("/consumer/negotiations/{consumerPid}/agreement")
    public ResponseEntity<Void> handleAgreement(@PathVariable String consumerPid,
                                                    @RequestBody JsonNode contractAgreementMessageJsonNode) throws InterruptedException, ExecutionException {

    	log.info("Received agreement from provider, consumerPid - {}", consumerPid);
        ContractAgreementMessage contractAgreementMessage = NegotiationSerializer.deserializeProtocol(contractAgreementMessageJsonNode,
                ContractAgreementMessage.class);

        contractNegotiationConsumerService.handleAgreement(contractAgreementMessage);

        log.info("CONSUMER - Sending response OK - agreementMessage received");
        return ResponseEntity.ok()
//        		.contentType(MediaType.APPLICATION_JSON)
        		.build();
    }

    // https://consumer.com/:callback/negotiations/:consumerPid/events	POST	ContractNegotiationEventMessage
    // No callbackAddress
    @PostMapping("/consumer/negotiations/{consumerPid}/events")
    public ResponseEntity<Void> handleFinalizeEvent(@PathVariable String consumerPid,
                                                         @RequestBody JsonNode contractNegotiationEventMessageJsonNode) throws InterruptedException, ExecutionException {

        ContractNegotiationEventMessage contractNegotiationEventMessage =
                NegotiationSerializer.deserializeProtocol(contractNegotiationEventMessageJsonNode, ContractNegotiationEventMessage.class);
        log.info("Event message received, status {}, consumerPid {}, providerPid {}", contractNegotiationEventMessage.getEventType(),
        		contractNegotiationEventMessage.getConsumerPid(), contractNegotiationEventMessage.getProviderPid());
		contractNegotiationConsumerService.handleFinalizeEvent(contractNegotiationEventMessage);

        // ACK or ERROR
        //If the CN's state is successfully transitioned, the Consumer must return HTTP code 200 (OK).
        // The response body is not specified and clients are not required to process it.
        return ResponseEntity.ok()
//        		.contentType(MediaType.APPLICATION_JSON)
                .build();
    }

    // https://consumer.com/:callback/negotiations/:consumerPid/termination POST	ContractNegotiationTerminationMessage
    // No callbackAddress
    @PostMapping("/consumer/negotiations/{consumerPid}/termination")
    public ResponseEntity<JsonNode> handleTerminationResponse(@PathVariable String consumerPid,
                                                              @RequestBody JsonNode contractNegotiationTerminationMessageJsonNode) {
    	
    	log.info("Received terminate contract negotiation for consumerPid {}", consumerPid);
        ContractNegotiationTerminationMessage contractNegotiationTerminationMessage =
                NegotiationSerializer.deserializeProtocol(contractNegotiationTerminationMessageJsonNode, ContractNegotiationTerminationMessage.class);

        contractNegotiationConsumerService.handleTerminationRequest(consumerPid, contractNegotiationTerminationMessage);

        // ACK or ERROR
        // If the CN's state is successfully transitioned, the Consumer must return HTTP code 200 (OK).
        // The response body is not specified and clients are not required to process it.
        return ResponseEntity.ok()
//        		.contentType(MediaType.APPLICATION_JSON)
        		.build();
    }

	private ContractNegotiationErrorMessage methodNotYetImplemented() {
		ContractNegotiationErrorMessage cnem = ContractNegotiationErrorMessage.Builder.newInstance()
        		.code("1")
        		.consumerPid("NOT IMPLEMENTED")
        		.providerPid("NOT IMPLEMENTED")
        		.description(Arrays.asList(Description.Builder.newInstance().language("en").value("Not implemented").build()))
        		.build();
		return cnem;
	}
}
