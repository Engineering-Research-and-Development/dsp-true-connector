package it.eng.negotiation.rest.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.negotiation.model.*;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.negotiation.service.ContractNegotiationConsumerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class ContractNegotiationConsumerCallbackController {

    private final ContractNegotiationConsumerService contractNegotiationConsumerService;
    private final ContractNegotiationProperties properties;

    public ContractNegotiationConsumerCallbackController(ContractNegotiationConsumerService contractNegotiationConsumerService,
                                                         ContractNegotiationProperties properties) {
        super();
        this.contractNegotiationConsumerService = contractNegotiationConsumerService;
        this.properties = properties;
    }
    // https://eclipse-dataspace-protocol-base.github.io/DataspaceProtocol/2025-1/#negotiations-get-consumer
    // GET
    // Provider must return an HTTP 200 (OK) response and a body containing the Contract Negotiation
    @GetMapping(path = "/consumer/negotiations/{consumerPid}")
    public ResponseEntity<JsonNode> getNegotiationByConsumerPid(@PathVariable String consumerPid) {
        log.info("Get negotiation by consumer pid");

        ContractNegotiation contractNegotiation = contractNegotiationConsumerService.getNegotiationByConsumerPid(consumerPid);

        return ResponseEntity.ok()
                .body(NegotiationSerializer.serializeProtocolJsonNode(contractNegotiation));
    }

    //	https://consumer.com/negotiations/offers	POST	ContractOfferMessage
    // returns 201 with body ContractNegotiation - OFFERED
    @PostMapping("/negotiations/offers")
    public ResponseEntity<JsonNode> handleNegotiationOffers(@RequestBody JsonNode contractOfferMessageJsonNode) {
        ContractOfferMessage contractOfferMessage = NegotiationSerializer.deserializeProtocol(contractOfferMessageJsonNode,
                ContractOfferMessage.class);

        ContractNegotiation responseNode = contractNegotiationConsumerService.handleContractOfferMessage(null, contractOfferMessage);
        // send OK 201
        log.info("Sending response OK in callback case");
        return ResponseEntity.created(createdURI(responseNode))
                .body(NegotiationSerializer.serializeProtocolJsonNode(responseNode));
    }

    private URI createdURI(ContractNegotiation responseNode) {
        // "https://provider.com/negotiations/:providerPid"
        String providerPid = responseNode.getProviderPid();
        return URI.create(properties.providerCallbackAddress() + "/negotiations/" + providerPid);
    }

    @PostMapping("/consumer/negotiations/tck")
    public ResponseEntity<ContractNegotiation> initiateRequestTck(@RequestBody TCKContractNegotiationRequest tckRequest) {
        log.info("Received TCK request {}", NegotiationSerializer.serializePlain(tckRequest));
        ContractNegotiation cnRequested = contractNegotiationConsumerService.processTCKRequest(tckRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(cnRequested);
    }

    // https://consumer.com/:callback/negotiations/:consumerPid/offers	POST	ContractOfferMessage
    // process message - if OK return 200; The response body is not specified and clients are not required to process it.
    @PostMapping("/consumer/negotiations/{consumerPid}/offers")
    public ResponseEntity<JsonNode> handleNegotiationOfferConsumerPid(@PathVariable String consumerPid,
                                                                      @RequestBody JsonNode contractOfferMessageJsonNode) {
        ContractOfferMessage contractOfferMessage =
                NegotiationSerializer.deserializeProtocol(contractOfferMessageJsonNode, ContractOfferMessage.class);

        log.info("Received contractOfferMessage {}", contractOfferMessageJsonNode);

        ContractNegotiation contractNegotiation = contractNegotiationConsumerService.handleContractOfferMessage(consumerPid, contractOfferMessage);

        return ResponseEntity.ok()
                .body(NegotiationSerializer.serializeProtocolJsonNode(contractNegotiation));
    }

    // https://consumer.com/:callback/negotiations/:consumerPid/agreement	POST	ContractAgreementMessage
    // after successful processing - 200 ok; body not specified
    @PostMapping("/consumer/negotiations/{consumerPid}/agreement")
    public ResponseEntity<Void> handleAgreement(@PathVariable String consumerPid,
                                                @RequestBody JsonNode contractAgreementMessageJsonNode) throws InterruptedException, ExecutionException {

        log.info("Received agreement from provider, consumerPid - {}", consumerPid);
        ContractAgreementMessage contractAgreementMessage = NegotiationSerializer.deserializeProtocol(contractAgreementMessageJsonNode,
                ContractAgreementMessage.class);

        contractNegotiationConsumerService.handleContractAgreementMessage(contractAgreementMessage);

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

        return ContractNegotiationErrorMessage.Builder.newInstance()
                .code("1")
                .consumerPid("NOT IMPLEMENTED")
                .providerPid("NOT IMPLEMENTED")
                .reason(Collections.singletonList("Not implemented"))
                .build();
    }
}
