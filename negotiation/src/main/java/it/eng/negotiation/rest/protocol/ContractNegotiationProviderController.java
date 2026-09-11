package it.eng.negotiation.rest.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.negotiation.model.*;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.negotiation.service.ContractNegotiationProviderStrategy;
import it.eng.tools.rest.api.TenantAwareProtocolController;
import it.eng.tools.service.TenantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE, path = "/{tenantId}/negotiations")
@Slf4j
public class ContractNegotiationProviderController extends TenantAwareProtocolController {

    private final ContractNegotiationProviderStrategy providerService;

    /**
     * Constructs the controller with its provider strategy and tenant service dependencies.
     *
     * @param providerService the contract negotiation provider strategy
     * @param tenantService   the tenant service used for tenant resolution
     */
    public ContractNegotiationProviderController(ContractNegotiationProviderStrategy providerService,
                                                 TenantService tenantService) {
        super(tenantService);
        this.providerService = providerService;
    }

    /**
     * Returns the contract negotiation for the given provider PID.
     *
     * @param tenantId    the tenant identifier from the path
     * @param providerPid the provider PID
     * @return the serialized contract negotiation
     */
    @GetMapping(path = "/{providerPid}")
    public ResponseEntity<JsonNode> getNegotiationByProviderPid(@PathVariable String tenantId,
                                                                @PathVariable String providerPid) {
        log.info("Get negotiation by provider pid");
        resolveTenant(tenantId);
        ContractNegotiation contractNegotiation = providerService.getNegotiationByProviderPid(providerPid);

        return ResponseEntity.ok()
                .body(NegotiationSerializer.serializeProtocolJsonNode(contractNegotiation));
    }

    /**
     * Handles an initial contract request message from a consumer and creates a new negotiation.
     *
     * @param tenantId                       the tenant identifier from the path
     * @param contractRequestMessageJsonNode the serialized contract request message
     * @return 201 Created with the serialized contract negotiation
     */
    @PostMapping(path = "/request")
    public ResponseEntity<JsonNode> handleContractRequestMessage(@PathVariable String tenantId,
                                                                 @RequestBody JsonNode contractRequestMessageJsonNode) {
        log.info("Creating negotiation");
        resolveTenant(tenantId);
        ContractRequestMessage crm = NegotiationSerializer.deserializeProtocol(contractRequestMessageJsonNode, ContractRequestMessage.class);
        ContractNegotiation cn = providerService.handleContractRequestMessage(crm);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest().path("/{id}")
                .buildAndExpand(cn.getProviderPid()).toUri();

        log.info("Initial contract request message successfully processed, contract negotiation with id {} created, sending response 201 Created", cn.getId());

        return ResponseEntity.created(location)
                .body(NegotiationSerializer.serializeProtocolJsonNode(cn));
    }

    /**
     * Handles a counter-offer contract request message from a consumer for an existing negotiation.
     *
     * @param tenantId                       the tenant identifier from the path
     * @param providerPid                    the provider PID
     * @param contractRequestMessageJsonNode the serialized contract request message
     * @return 200 OK with the serialized contract negotiation
     */
    @PostMapping(path = "/{providerPid}/request")
    public ResponseEntity<JsonNode> handleContractRequestMessageAsCounteroffer(@PathVariable String tenantId,
                                                                               @PathVariable String providerPid,
                                                                               @RequestBody JsonNode contractRequestMessageJsonNode) {
        log.info("Processing consumer counter-offer");
        resolveTenant(tenantId);
        ContractRequestMessage crm = NegotiationSerializer.deserializeProtocol(contractRequestMessageJsonNode, ContractRequestMessage.class);
        ContractNegotiation cn = providerService.handleContractRequestMessageAsCounteroffer(providerPid, crm);

        log.info("Contract request message as counteroffer successfully processed for providerPid {}, sending response 200", providerPid);

        return ResponseEntity.ok()
                .body(NegotiationSerializer.serializeProtocolJsonNode(cn));
    }

    /**
     * Handles a contract negotiation event message with ACCEPTED state from a consumer.
     *
     * @param tenantId                               the tenant identifier from the path
     * @param providerPid                            the provider PID
     * @param contractNegotiationEventMessageJsonNode the serialized event message
     * @return 200 OK with the serialized contract negotiation
     */
    @PostMapping(path = "/{providerPid}/events")
    public ResponseEntity<JsonNode> handleContractNegotiationEventMessageAccepted(@PathVariable String tenantId,
                                                                                  @PathVariable String providerPid,
                                                                                  @RequestBody JsonNode contractNegotiationEventMessageJsonNode) {
        resolveTenant(tenantId);
        ContractNegotiationEventMessage contractNegotiationEventMessage = NegotiationSerializer.deserializeProtocol(contractNegotiationEventMessageJsonNode, ContractNegotiationEventMessage.class);
        log.info(contractNegotiationEventMessage.toString());

        ContractNegotiation contractNegotiation = providerService.handleContractNegotiationEventMessageAccepted(providerPid, contractNegotiationEventMessage);

        log.info("Contract negotiation event message accepted successfully processed for providerPid {}, sending response 200", providerPid);

        return ResponseEntity.ok()
                .body(NegotiationSerializer.serializeProtocolJsonNode(contractNegotiation));
    }

    /**
     * Handles a contract agreement verification message from a consumer.
     *
     * @param tenantId                                    the tenant identifier from the path
     * @param providerPid                                 the provider PID
     * @param contractAgreementVerificationMessageJsonNode the serialized verification message
     * @return 200 OK
     */
    @PostMapping(path = "/{providerPid}/agreement/verification")
    public ResponseEntity<Void> handleContractAgreementVerificationMessage(@PathVariable String tenantId,
                                                                           @PathVariable String providerPid,
                                                                           @RequestBody JsonNode contractAgreementVerificationMessageJsonNode) {
        resolveTenant(tenantId);
        ContractAgreementVerificationMessage cavm =
                NegotiationSerializer.deserializeProtocol(contractAgreementVerificationMessageJsonNode, ContractAgreementVerificationMessage.class);
        log.info("Verification message received");

        providerService.handleContractAgreementVerificationMessage(providerPid, cavm);

        log.info("Contract agreement verification message successfully processed for providerPid {}", providerPid);

        return ResponseEntity.ok()
                .build();
    }

    /**
     * Handles a contract negotiation termination message from a consumer.
     *
     * @param tenantId                                  the tenant identifier from the path
     * @param providerPid                               the provider PID
     * @param contractNegotiationTerminationMessageJsonNode the serialized termination message
     * @return 200 OK
     */
    @PostMapping(path = "/{providerPid}/termination")
    public ResponseEntity<Void> handleContractNegotiationTerminationMessage(@PathVariable String tenantId,
                                                                            @PathVariable String providerPid,
                                                                            @RequestBody JsonNode contractNegotiationTerminationMessageJsonNode) {
        resolveTenant(tenantId);
        ContractNegotiationTerminationMessage contractNegotiationTerminationMessage =
                NegotiationSerializer.deserializeProtocol(contractNegotiationTerminationMessageJsonNode, ContractNegotiationTerminationMessage.class);

        providerService.handleContractNegotiationTerminationMessage(providerPid, contractNegotiationTerminationMessage);

        log.info("Contract negotiation termination message successfully processed for providerPid {}, sending response 200", providerPid);

        return ResponseEntity.ok()
                .build();
    }

}
