package it.eng.negotiation.rest.protocol;

import ch.qos.logback.core.testUtil.RandomUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.negotiation.model.*;
import it.eng.negotiation.service.CallbackHandler;
import it.eng.negotiation.service.ContractNegotiationService;
import it.eng.negotiation.transformer.from.JsonFromContractNegotiationErrorMessageTrasformer;
import it.eng.negotiation.transformer.to.*;
import it.eng.tools.model.DSpaceConstants;
import lombok.extern.java.Log;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, path = "/negotiations")
@Log
public class NegotiationProtocolController {

    private ContractNegotiationService contractNegotiationService;
    private CallbackHandler callbackHandler;
    private boolean isConsumer;

    public NegotiationProtocolController(ContractNegotiationService contractNegotiationService,
                                         CallbackHandler callbackHandler,
                                         @Value("${application.connectorid}") String connectroId,
                                         @Value("${application.isconsumer}") boolean isConsumer) {
        super();
        this.contractNegotiationService = contractNegotiationService;
        this.callbackHandler = callbackHandler;
        this.isConsumer = isConsumer;
    }

// 2.1 The negotiation endpoint (Provider side)
    // 2.1.1 GET

    @GetMapping(path = "/{providerPid}")
    public CompletableFuture<?> getNegotiationByProviderPid(@PathVariable String providerPid) {
        log.info("Get negotiation by provider pid");

        return contractNegotiationService.getNegotiationByProviderPid(providerPid)
                .thenApply(result -> ResponseEntity.ok()
                        .header("foo", "bar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Serializer.serializeProtocol(result)));
    }

    // 2.5 The provider negotiations/request resource
    // 2.5.1 POST

    @PostMapping(path = "/request")
    public ResponseEntity<?> createNegotiation(@RequestBody JsonNode object) throws InterruptedException, ExecutionException {
        log.info("Handling get catalog");
        JsonToContractRequestMessageTransformer jsonToContractRequestMessageTransformer = new JsonToContractRequestMessageTransformer();
        ContractRequestMessage crm = jsonToContractRequestMessageTransformer.transform(object);

        System.out.println(crm);
        String callbackAddress = crm.getCallbackAddress();

        CompletableFuture<JsonNode> responseNode = contractNegotiationService.startContractNegotiation(crm);

        if (ObjectUtils.isEmpty(callbackAddress)) {
            log.info("Sending response regular");
            //TODO check how to send callback or this negotiations location value
            URI location = URI.create("/negotiations/" + "123");// crm.getId());
            return ResponseEntity.created(location).contentType(MediaType.APPLICATION_JSON).body(responseNode.get());
        } else {
            // final callback = callback + extension base on message type
            callbackAddress = callbackAddress.endsWith("/") ? callbackAddress : callbackAddress + "/";
            String finalCallback = callbackAddress + ContactNegotiationCallback.getOffersCallback();
            callbackHandler.handleCallbackResponse(finalCallback, responseNode);
            // send OK 200
            log.info("Sending response OK in callback case");
            return ResponseEntity.ok().build();
        }
    }

    // 2.6 The provider negotiations/:id/request resource
    // 2.6.1 POST

    @PostMapping(path = "/{providerPid}/request")
    public ResponseEntity<JsonNode> consumerOfferAContractRequestMessage(@PathVariable String providerPid,
                                                                         @RequestBody JsonNode object) {
        JsonToContractRequestMessageTransformer jsonToContractRequestMessageTransformer = new JsonToContractRequestMessageTransformer();
        ContractRequestMessage crm = jsonToContractRequestMessageTransformer.transform(object);

        if (crm.getConsumerPid() == null || crm.getConsumerPid().isEmpty()) {
            Map<String, Object> map = new HashMap<>();
            map.put(DSpaceConstants.ERROR_MESSAGE, "The consumer must include the processId.");

            ObjectMapper mapper = new ObjectMapper();

            return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(mapper.convertValue(map, JsonNode.class));
        }
        if (crm.getOffer() != null) {
            Map<String, Object> map = new HashMap<>();
            map.put(DSpaceConstants.ERROR_MESSAGE, "The consumer must include either the offer or offerId property.");

            ObjectMapper mapper = new ObjectMapper();

            return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                    .body(mapper.convertValue(map, JsonNode.class));
        }

        return ResponseEntity.ok().build();/*header("foo", "bar")
				.contentType(MediaType.APPLICATION_JSON).body(jsonNode);*/
    }

    // Start path = "/:providerPid/events"

    @PostMapping(path = "/{providerPid}/events")
    public ResponseEntity<JsonNode> switchConsumerOrProvider(@PathVariable String providerPid, @RequestBody JsonNode object) {
        if (!isConsumer) {
            return acceptCurrentProviderContractOffer(providerPid, object);
        } else if (isConsumer) {
            return providerCanFinalizeContract(providerPid, object);
        }
        return ResponseEntity.internalServerError().contentType(MediaType.APPLICATION_JSON).body(object);

		/*.header("foo", "bar")
				.contentType(MediaType.APPLICATION_JSON).body(jsonNode); ;*/
    }

    // 2.7 The provider negotiations/:providerPid/events resource
    // 2.7.1 POST
    public ResponseEntity<JsonNode> acceptCurrentProviderContractOffer(String providerPid, JsonNode object) {
        JsonToContractNegotiationEventMessageTransformer jsonToContractNegotiationEventMessageTransformer = new JsonToContractNegotiationEventMessageTransformer();
        ContractNegotiationEventMessage cnem = jsonToContractNegotiationEventMessageTransformer.transform(object);

        log.info(cnem.toString());

        //TODO verify If the negotiation state is successfully transitioned
        if (RandomUtil.getPositiveInt() % 2 == 0) {
            return ResponseEntity.ok().build();
        } else {
            ContractNegotiationErrorMessage cne = ContractNegotiationErrorMessage.Builder.newInstance()
                    .providerPid(cnem.getProviderPid()).consumerPid(cnem.getConsumerPid()).build();
            JsonFromContractNegotiationErrorMessageTrasformer jsonFromContractNegotiationErrorMessageTrasformer = new JsonFromContractNegotiationErrorMessageTrasformer();
            JsonNode jsonNode = jsonFromContractNegotiationErrorMessageTrasformer.transform(cne);

            return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(jsonNode);
        }
    }

    // 3.5 The consumer negotiations/:id/events resource
    // 3.5.1 POST

    //@PostMapping(path = "/:providerPid/events")
    public ResponseEntity<JsonNode> providerCanFinalizeContract(
            /* @PathVariable */ String id,
            /* @RequestBody */ JsonNode object) {
        ContractNegotiationEventMessage cam = Serializer.deserializeProtocol(object.toString(), ContractNegotiationEventMessage.class);

        // If the negotiation state is successfully transitioned, the consumer must return HTTP code 200 (OK).
        // The response body is not specified and clients are not required to process it.
        // TODO code if(???)

        return ResponseEntity.ok().build();
    }

    // End path = "/{id}/events"

    // 2.8 The provider negotiations/:id/agreement/verification resource
    // 2.8.1 POST

    @PostMapping(path = "/{id}/agreement/verification")
    public ResponseEntity<JsonNode> consumerCanVerifyAgreement(@PathVariable String id,
                                                               @RequestBody JsonNode object) {
        ContractAgreementVerificationMessage cavm = Serializer.deserializeProtocol(object.toString(), ContractAgreementVerificationMessage.class);
        return null;
    }

    @PostMapping(path = "/{id}/termination")
    public ResponseEntity<JsonNode> switchConsumerOrProviderTermination(@PathVariable String id, @RequestBody JsonNode object) {
        if (!isConsumer) {
            return providerCanTerminateNegotiation(id, object);
        } else if (isConsumer) {
            return consumerCanTerminateNegotiation(id, object);
        }
        return ResponseEntity.internalServerError().contentType(MediaType.APPLICATION_JSON).body(object);
    }

    // 2.9 The provider negotiations/:id/termination resource
    // 2.9.1 POST

    //@PostMapping(path = "/{id}/termination")
    public ResponseEntity<JsonNode> consumerCanTerminateNegotiation(/*@PathVariable*/ String id,
            /*@RequestBody*/ JsonNode object) {

        ContractNegotiationTerminationMessage cntm = Serializer.deserializeProtocol(object.toString(), ContractNegotiationTerminationMessage.class);
        return null;
    }

    // 3.6 The consumer negotiations/:id/events resource
    // 3.6.1 POST

    //@PostMapping(path = "/{id}/events")
    public ResponseEntity<JsonNode> providerCanTerminateNegotiation(/*@PathVariable*/ String id,
            /*@RequestBody*/ JsonNode object) {

        ContractNegotiationTerminationMessage cntm = Serializer.deserializeProtocol(object.toString(), ContractNegotiationTerminationMessage.class);
        return null;
    }

    // 3.2 The consumer negotiations/offers resource
    // 3.2.1 POST
//Check in  ContractNegotiationProviderCallbackController.handleNegotiationOffers
//	@PostMapping(path = "/offers")
//	public ResponseEntity<JsonNode> provideMakeOffer(@RequestBody JsonNode object) { 
//
//		JsonToContractOfferMessageTransformer jsonToContractOfferMessageTransformer = new JsonToContractOfferMessageTransformer();
//		ContractOfferMessage com = jsonToContractOfferMessageTransformer.transform(object);
//		
//		return null;
//	}

    // 3.3 The consumer negotiations/:id/offers resource
    // 3.3.1 POST

    @PostMapping(path = "/{id}/offers")
    public ResponseEntity<JsonNode> providerMayMakeOffer(@PathVariable String id,
                                                         @RequestBody JsonNode object) {

        ContractOfferMessage com = Serializer.deserializeProtocol(object.toString(), ContractOfferMessage.class);
        return null;
    }

    // 3.4 The consumer negotiations/:id/agreement resource
    // 3.4.1 POST

    @PostMapping(path = "/{id}/agreement")
    public ResponseEntity<JsonNode> providerCanPostAContractAgreement(@PathVariable String id,
                                                                      @RequestBody JsonNode object) {

        ContractAgreementMessage com = Serializer.deserializeProtocol(object.toString(), ContractAgreementMessage.class);
        return null;
    }


}
