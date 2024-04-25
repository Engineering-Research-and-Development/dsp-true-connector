package it.eng.negotiation.rest.protocol;

import java.util.concurrent.ExecutionException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.model.ContractAgreementMessage;
import it.eng.negotiation.model.ContractNegotiationEventMessage;
import it.eng.negotiation.model.ContractNegotiationTerminationMessage;
import it.eng.negotiation.model.ContractOfferMessage;
import it.eng.negotiation.model.Serializer;
import it.eng.negotiation.service.ContractNegotiationConsumerService;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class ConsumerContractNegotiationCallbackController {

    private ContractNegotiationConsumerService contractNegotiationConsumerService;

    public ConsumerContractNegotiationCallbackController(ContractNegotiationConsumerService contractNegotiationConsumerService) {
        super();
        this.contractNegotiationConsumerService = contractNegotiationConsumerService;
    }

    //	https://consumer.com/negotiations/offers	POST	ContractOfferMessage
    // returns 201 with body ContractNegotiation - OFFERED
    @PostMapping("/negotiations/offers")
    public ResponseEntity<JsonNode> handleNegotiationOffers(@RequestBody JsonNode contractOfferMessageJsonNode) {
        ContractOfferMessage contractOfferMessage = Serializer.deserializeProtocol(contractOfferMessageJsonNode,
                ContractOfferMessage.class);

        String callbackAddress = contractOfferMessage.getCallbackAddress();
        JsonNode responseNode = contractNegotiationConsumerService.processContractOffer(contractOfferMessage);

//		callbackAddress = callbackAddress.endsWith("/") ? callbackAddress : callbackAddress + "/";
//		String finalCallback = callbackAddress + ContactNegotiationCallback.getProviderNegotiationOfferCallback(callbackAddress);
        // send OK 200
        log.info("Sending response OK in callback case");
        return ResponseEntity.ok().build();
    }

    // https://consumer.com/:callback/negotiations/:consumerPid/offers	POST	ContractOfferMessage
    // process message - if OK return 200; The response body is not specified and clients are not required to process it.
    @PostMapping("/consumer/negotiations/{consumerPid}/offers")
    public ResponseEntity<JsonNode> handleNegotiationOfferConsumerPid(@PathVariable String consumerPid,
                                                                      @RequestBody JsonNode contractOfferMessageJsonNode) throws InterruptedException, ExecutionException {
        ContractOfferMessage contractOfferMessage = Serializer.deserializeProtocol(contractOfferMessageJsonNode,
                ContractOfferMessage.class);

        String callbackAddress = contractOfferMessage.getCallbackAddress();
        JsonNode responseNode =
                contractNegotiationConsumerService.handleNegotiationOfferConsumer(consumerPid, contractOfferMessage);

//		callbackAddress = callbackAddress.endsWith("/") ? callbackAddress : callbackAddress + "/";
//		String finalCallback = callbackAddress + ContactNegotiationCallback.getNegotiationOfferConsumer(callbackAddress);
        log.info("Sending response OK in callback case");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).build();
    }

    // https://consumer.com/:callback/negotiations/:consumerPid/agreement	POST	ContractAgreementMessage
    // after successful processing - 200 ok; body not specified
    @PostMapping("/consumer/negotiations/{consumerPid}/agreement")
    public ResponseEntity<JsonNode> handleAgreement(@PathVariable String consumerPid,
                                                    @RequestBody JsonNode contractAgreementMessageJsonNode) throws InterruptedException, ExecutionException {

    	log.info("Received agreement from provider !!!!!, consumerPid - {}", consumerPid);
        ContractAgreementMessage contractAgreementMessage = Serializer.deserializeProtocol(contractAgreementMessageJsonNode,
                ContractAgreementMessage.class);

        String callbackAddress = contractAgreementMessage.getCallbackAddress();
        contractNegotiationConsumerService.handleAgreement(callbackAddress, contractAgreementMessage);

//		callbackAddress = callbackAddress.endsWith("/") ? callbackAddress : callbackAddress + "/";
        log.info("CONSUMER - Sending response OK - agreementMessage received");
        return ResponseEntity.ok().build();
    }

    // https://consumer.com/:callback/negotiations/:consumerPid/events	POST	ContractNegotiationEventMessage
    // No callbackAddress
    @PostMapping("/consumer/negotiations/{consumerPid}/events")
    public ResponseEntity<JsonNode> handleEventsMessage(@PathVariable String consumerPid,
                                                         @RequestBody JsonNode contractNegotiationEventMessageJsonNode) throws InterruptedException, ExecutionException {

        ContractNegotiationEventMessage contractNegotiationEventMessage =
                Serializer.deserializeProtocol(contractNegotiationEventMessageJsonNode, ContractNegotiationEventMessage.class);
        log.info("Event message received, status {}, consumerPid {}, providerPid", contractNegotiationEventMessage.getEventType(),
        		contractNegotiationEventMessage.getConsumerPid(), contractNegotiationEventMessage.getProviderPid());
        JsonNode responseNode =
                contractNegotiationConsumerService.handleEventsResponse(consumerPid, contractNegotiationEventMessage);

        // ACK or ERROR
        //If the CN's state is successfully transitioned, the Consumer must return HTTP code 200 (OK).
        // The response body is not specified and clients are not required to process it.
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString())
                .body(responseNode);
    }

    // https://consumer.com/:callback/negotiations/:consumerPid/termination POST	ContractNegotiationTerminationMessage
    // No callbackAddress
    @PostMapping("/consumer/negotiations/{consumerPid}/termination")
    public ResponseEntity<JsonNode> handleTerminationResponse(@PathVariable String consumerPid,
                                                              @RequestBody JsonNode contractNegotiationTerminationMessageJsonNode) throws InterruptedException, ExecutionException {

        ContractNegotiationTerminationMessage contractNegotiationTerminationMessage =
                Serializer.deserializeProtocol(contractNegotiationTerminationMessageJsonNode, ContractNegotiationTerminationMessage.class);

        JsonNode responseNode =
                contractNegotiationConsumerService.handleTerminationResponse(consumerPid, contractNegotiationTerminationMessage);

        // ACK or ERROR
        // If the CN's state is successfully transitioned, the Consumer must return HTTP code 200 (OK).
        // The response body is not specified and clients are not required to process it.
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString())
                .body(responseNode);
    }
}
