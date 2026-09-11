package it.eng.datatransfer.rest.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.datatransfer.model.*;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.datatransfer.service.TransferProcessStrategy;
import it.eng.tools.rest.api.TenantAwareProtocolController;
import it.eng.tools.service.TenantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE, path = "/{tenantId}/consumer/transfers")
@Slf4j
public class ConsumerDataTransferCallbackController extends TenantAwareProtocolController {

    private final TransferProcessStrategy dataTransferService;

    /**
     * Constructs the controller with its transfer strategy and tenant service dependencies.
     *
     * @param dataTransferService the transfer process strategy
     * @param tenantService       the tenant service used for tenant resolution
     */
    public ConsumerDataTransferCallbackController(TransferProcessStrategy dataTransferService,
                                                  TenantService tenantService) {
        super(tenantService);
        this.dataTransferService = dataTransferService;
    }

    /**
     * Handles a TCK-initiated transfer request.
     *
     * @param tenantId   the tenant identifier from the path
     * @param tckRequest the TCK request payload
     * @return the serialized transfer process
     */
    @PostMapping("/tck")
    public ResponseEntity<JsonNode> initiateDataTransfer(@PathVariable String tenantId,
                                                         @RequestBody TCKRequest tckRequest) {
        log.info("Received TCK request for agreementId {}, format {} from connector {}",
                tckRequest.getAgreementId(), tckRequest.getFormat(), tckRequest.getConnectorAddress());
        resolveTenant(tenantId);
        TransferProcess transferProcessRequested = dataTransferService.requestTransfer(tckRequest);

        return ResponseEntity.ok(TransferSerializer.serializeProtocolJsonNode(transferProcessRequested));
    }

    /**
     * Returns the transfer process for the given consumer PID.
     *
     * @param tenantId    the tenant identifier from the path
     * @param consumerPid the consumer PID
     * @return the serialized transfer process
     */
    @GetMapping("/{consumerPid}")
    public ResponseEntity<String> getTransferProcessByConsumerPid(@PathVariable String tenantId,
                                                                  @PathVariable String consumerPid) {
        resolveTenant(tenantId);
        TransferProcess transferProcess = dataTransferService.findTransferProcessByConsumerPid(consumerPid);
        return ResponseEntity.ok(TransferSerializer.serializeProtocol(transferProcess));
    }

    /**
     * Starts the data transfer for the given consumer PID.
     *
     * @param tenantId                    the tenant identifier from the path
     * @param consumerPid                 the consumer PID
     * @param transferStartMessageJsonNode the transfer start message
     * @return empty response on success
     */
    @PostMapping(path = "/{consumerPid}/start")
    public ResponseEntity<Void> startDataTransfer(@PathVariable String tenantId,
                                                  @PathVariable String consumerPid,
                                                  @RequestBody JsonNode transferStartMessageJsonNode) {
        resolveTenant(tenantId);
        TransferStartMessage transferStartMessage = TransferSerializer.deserializeProtocol(transferStartMessageJsonNode, TransferStartMessage.class);
        log.info("Starting data transfer for consumerPid {} and providerPid {}", consumerPid, transferStartMessage.getProviderPid());
        TransferProcess transferProcessStarted = dataTransferService.startDataTransfer(transferStartMessage, consumerPid, null);
        log.info("TransferProcess {} state changed to {}", transferProcessStarted.getId(), transferProcessStarted.getState());
        return ResponseEntity.ok().build();
    }

    /**
     * Completes the data transfer for the given consumer PID.
     *
     * @param tenantId                        the tenant identifier from the path
     * @param consumerPid                     the consumer PID
     * @param transferCompletionMessageJsonNode the transfer completion message
     * @return empty response on success
     */
    @PostMapping(path = "/{consumerPid}/completion")
    public ResponseEntity<Void> completeDataTransfer(@PathVariable String tenantId,
                                                     @PathVariable String consumerPid,
                                                     @RequestBody JsonNode transferCompletionMessageJsonNode) {
        resolveTenant(tenantId);
        TransferCompletionMessage transferCompletionMessage = TransferSerializer.deserializeProtocol(transferCompletionMessageJsonNode, TransferCompletionMessage.class);
        log.info("Completing data transfer for consumerPid {} and providerPid {}", consumerPid, transferCompletionMessage.getProviderPid());
        TransferProcess transferProcessCompleted = dataTransferService.completeDataTransfer(transferCompletionMessage, consumerPid, null);
        log.info("TransferProcess {} state changed to {}", transferProcessCompleted.getId(), transferProcessCompleted.getState());
        return ResponseEntity.ok().build();
    }

    /**
     * Terminates the data transfer for the given consumer PID.
     *
     * @param tenantId                         the tenant identifier from the path
     * @param consumerPid                      the consumer PID
     * @param transferTerminationMessageJsonNode the transfer termination message
     * @return empty response on success
     */
    @PostMapping(path = "/{consumerPid}/termination")
    public ResponseEntity<Void> terminateDataTransfer(@PathVariable String tenantId,
                                                      @PathVariable String consumerPid,
                                                      @RequestBody JsonNode transferTerminationMessageJsonNode) {
        resolveTenant(tenantId);
        TransferTerminationMessage transferTerminationMessage = TransferSerializer.deserializeProtocol(transferTerminationMessageJsonNode, TransferTerminationMessage.class);
        log.info("Terminating data transfer for consumerPid {} and providerPid {}", consumerPid, transferTerminationMessage.getProviderPid());
        TransferProcess transferProcessTerminated = dataTransferService.terminateDataTransfer(transferTerminationMessage, consumerPid, null);
        log.info("TransferProcess {} state changed to {}", transferProcessTerminated.getId(), transferProcessTerminated.getState());
        return ResponseEntity.ok().build();
    }

    /**
     * Suspends the data transfer for the given consumer PID.
     *
     * @param tenantId                        the tenant identifier from the path
     * @param consumerPid                     the consumer PID
     * @param transferSuspensionMessageJsonNode the transfer suspension message
     * @return empty response on success
     */
    @PostMapping(path = "/{consumerPid}/suspension")
    public ResponseEntity<Void> suspenseDataTransfer(@PathVariable String tenantId,
                                                     @PathVariable String consumerPid,
                                                     @RequestBody JsonNode transferSuspensionMessageJsonNode) {
        resolveTenant(tenantId);
        TransferSuspensionMessage transferSuspensionMessage = TransferSerializer.deserializeProtocol(transferSuspensionMessageJsonNode, TransferSuspensionMessage.class);
        log.info("Suspending data transfer for consumerPid {} and providerPid {}", consumerPid, transferSuspensionMessage.getProviderPid());
        TransferProcess transferProcessSuspended = dataTransferService.suspendDataTransfer(transferSuspensionMessage, consumerPid, null);
        log.info("TransferProcess {} state changed to {}", transferProcessSuspended.getId(), transferProcessSuspended.getState());
        return ResponseEntity.ok().build();
    }
}
