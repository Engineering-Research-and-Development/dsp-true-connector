package it.eng.negotiation.rest.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.negotiation.model.*;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.negotiation.service.ContractNegotiationConsumerService;
import it.eng.tools.rest.api.TenantAwareProtocolController;
import it.eng.tools.service.TenantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE, path = "/{tenantId}")
@Slf4j
public class ContractNegotiationConsumerCallbackController extends TenantAwareProtocolController {

    private final ContractNegotiationConsumerService contractNegotiationConsumerService;
    private final ContractNegotiationProperties properties;

    /**
     * Constructs the controller with its consumer service, properties, and tenant service dependencies.
     *
     * @param contractNegotiationConsumerService the consumer service for contract negotiations
     * @param properties                         the negotiation properties
     * @param tenantService                      the tenant service used for tenant resolution
     */
    public ContractNegotiationConsumerCallbackController(ContractNegotiationConsumerService contractNegotiationConsumerService,
                                                         ContractNegotiationProperties properties,
                                                         TenantService tenantService) {
        super(tenantService);
        this.contractNegotiationConsumerService = contractNegotiationConsumerService;
        this.properties = properties;
    }

    /**
     * Returns the contract negotiation for the given consumer PID.
     *
     * @param tenantId    the tenant identifier from the path
     * @param consumerPid the consumer PID
     * @return the serialized contract negotiation
     */
    @GetMapping(path = "/consumer/negotiations/{consumerPid}")
    public ResponseEntity<JsonNode> getNegotiationByConsumerPid(@PathVariable String tenantId,
                                                                @PathVariable String consumerPid) {
        log.info("Get negotiation by consumer pid");
        resolveTenant(tenantId);
        ContractNegotiation contractNegotiation = contractNegotiationConsumerService.getNegotiationByConsumerPid(consumerPid);

        return ResponseEntity.ok()
                .body(NegotiationSerializer.serializeProtocolJsonNode(contractNegotiation));
    }

    /**
     * Handles an initial contract offer message from a provider.
     *
     * @param tenantId                   the tenant identifier from the path
     * @param contractOfferMessageJsonNode the serialized contract offer message
     * @return 201 Created with the serialized contract negotiation
     */
    @PostMapping("/negotiations/offers")
    public ResponseEntity<JsonNode> handleContractOfferMessage(@PathVariable String tenantId,
                                                               @RequestBody JsonNode contractOfferMessageJsonNode) {
        resolveTenant(tenantId);
        ContractOfferMessage contractOfferMessage = NegotiationSerializer.deserializeProtocol(contractOfferMessageJsonNode,
                ContractOfferMessage.class);

        ContractNegotiation contractNegotiation = contractNegotiationConsumerService.handleContractOfferMessage(contractOfferMessage);
        log.info("Initial contract offer message successfully processed, contract negotiation with id {} created, sending response 201 Created", contractNegotiation.getId());
        return ResponseEntity.created(createdURI(contractNegotiation))
                .contentType(MediaType.APPLICATION_JSON)
                .body(NegotiationSerializer.serializeProtocolJsonNode(contractNegotiation));
    }

    /**
     * Handles a counter-offer contract offer message from a provider for an existing negotiation.
     *
     * @param tenantId                   the tenant identifier from the path
     * @param consumerPid                the consumer PID
     * @param contractOfferMessageJsonNode the serialized contract offer message
     * @return 200 OK with the serialized contract negotiation
     */
    @PostMapping("/consumer/negotiations/{consumerPid}/offers")
    public ResponseEntity<JsonNode> handleContractOfferMessageAsCounteroffer(@PathVariable String tenantId,
                                                                             @PathVariable String consumerPid,
                                                                             @RequestBody JsonNode contractOfferMessageJsonNode) {
        resolveTenant(tenantId);
        ContractOfferMessage contractOfferMessage =
                NegotiationSerializer.deserializeProtocol(contractOfferMessageJsonNode, ContractOfferMessage.class);

        log.info("Received contractOfferMessage {}", contractOfferMessageJsonNode);

        ContractNegotiation contractNegotiation = contractNegotiationConsumerService.handleContractOfferMessageAsCounteroffer(consumerPid, contractOfferMessage);

        log.info("Contract offer message as counteroffer successfully processed for consumerPid {}, sending response 200", consumerPid);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(NegotiationSerializer.serializeProtocolJsonNode(contractNegotiation));
    }

    /**
     * Handles a contract agreement message from a provider.
     *
     * @param tenantId                         the tenant identifier from the path
     * @param consumerPid                      the consumer PID
     * @param contractAgreementMessageJsonNode the serialized contract agreement message
     * @return 200 OK
     */
    @PostMapping("/consumer/negotiations/{consumerPid}/agreement")
    public ResponseEntity<Void> handleContractAgreementMessage(@PathVariable String tenantId,
                                                               @PathVariable String consumerPid,
                                                               @RequestBody JsonNode contractAgreementMessageJsonNode) {

        log.info("Received agreement from provider, consumerPid - {}", consumerPid);
        resolveTenant(tenantId);
        ContractAgreementMessage contractAgreementMessage = NegotiationSerializer.deserializeProtocol(contractAgreementMessageJsonNode,
                ContractAgreementMessage.class);

        contractNegotiationConsumerService.handleContractAgreementMessage(consumerPid, contractAgreementMessage);

        log.info("Contract agreement message successfully processed for consumerPid {}, sending response 200", consumerPid);
        return ResponseEntity.ok()
                .build();
    }

    /**
     * Handles a contract negotiation event message with FINALIZED state from a provider.
     *
     * @param tenantId                               the tenant identifier from the path
     * @param consumerPid                            the consumer PID
     * @param contractNegotiationEventMessageJsonNode the serialized event message
     * @return 200 OK
     */
    @PostMapping("/consumer/negotiations/{consumerPid}/events")
    public ResponseEntity<Void> handleContractNegotiationEventMessageFinalize(@PathVariable String tenantId,
                                                                              @PathVariable String consumerPid,
                                                                              @RequestBody JsonNode contractNegotiationEventMessageJsonNode) {

        resolveTenant(tenantId);
        ContractNegotiationEventMessage contractNegotiationEventMessage =
                NegotiationSerializer.deserializeProtocol(contractNegotiationEventMessageJsonNode, ContractNegotiationEventMessage.class);
        log.info("Event message received, status {}, consumerPid {}, providerPid {}", contractNegotiationEventMessage.getEventType(),
                contractNegotiationEventMessage.getConsumerPid(), contractNegotiationEventMessage.getProviderPid());
        contractNegotiationConsumerService.handleContractNegotiationEventMessageFinalize(consumerPid, contractNegotiationEventMessage);

        log.info("Contract negotiation event message finalize successfully processed for consumerPid {}, sending response 200", consumerPid);
        return ResponseEntity.ok()
                .build();
    }

    /**
     * Handles a contract negotiation termination message from a provider.
     *
     * @param tenantId                                  the tenant identifier from the path
     * @param consumerPid                               the consumer PID
     * @param contractNegotiationTerminationMessageJsonNode the serialized termination message
     * @return 200 OK
     */
    @PostMapping("/consumer/negotiations/{consumerPid}/termination")
    public ResponseEntity<JsonNode> handleContractNegotiationTerminationMessage(@PathVariable String tenantId,
                                                                                @PathVariable String consumerPid,
                                                                                @RequestBody JsonNode contractNegotiationTerminationMessageJsonNode) {

        log.info("Received terminate contract negotiation for consumerPid {}", consumerPid);
        resolveTenant(tenantId);
        ContractNegotiationTerminationMessage contractNegotiationTerminationMessage =
                NegotiationSerializer.deserializeProtocol(contractNegotiationTerminationMessageJsonNode, ContractNegotiationTerminationMessage.class);

        contractNegotiationConsumerService.handleContractNegotiationTerminationMessage(consumerPid, contractNegotiationTerminationMessage);

        log.info("Contract negotiation termination message successfully processed for consumerPid {}, sending response 200", consumerPid);
        return ResponseEntity.ok()
                .build();
    }

    /**
     * Handles TCK test contract negotiation requests.
     *
     * @param tenantId   the tenant identifier from the path
     * @param tckRequest the TCK request payload
     * @return 201 Created with the resulting contract negotiation
     */
    @PostMapping("/consumer/negotiations/tck")
    public ResponseEntity<ContractNegotiation> initiateRequestTck(@PathVariable String tenantId,
                                                                  @RequestBody TCKContractNegotiationRequest tckRequest) {
        log.info("Received TCK request {}", NegotiationSerializer.serializePlain(tckRequest));
        resolveTenant(tenantId);
        ContractNegotiation cnRequested = contractNegotiationConsumerService.processTCKRequest(tckRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(cnRequested);
    }

    private URI createdURI(ContractNegotiation responseNode) {
        String providerPid = responseNode.getProviderPid();
        return URI.create(properties.providerCallbackAddress() + "/negotiations/" + providerPid);
    }
}
