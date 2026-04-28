package it.eng.datatransfer.rest.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.datatransfer.model.*;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.datatransfer.service.TransferProcessStrategy;
import it.eng.tools.rest.api.TenantAwareProtocolController;
import it.eng.tools.service.TenantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@RequestMapping(
        produces = MediaType.APPLICATION_JSON_VALUE, path = "/{tenantId}/transfers")
@Slf4j
public class ProviderDataTransferController extends TenantAwareProtocolController {

    private final Environment environment;
    private final TransferProcessStrategy dataTransferService;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Constructs the controller with its transfer strategy, tenant service, and supporting dependencies.
     *
     * @param environment              provides access to active Spring profiles
     * @param dataTransferService      the transfer process strategy
     * @param applicationEventPublisher used to publish application events
     * @param tenantService            the tenant service used for tenant resolution
     */
    public ProviderDataTransferController(Environment environment,
                                          TransferProcessStrategy dataTransferService,
                                          ApplicationEventPublisher applicationEventPublisher,
                                          TenantService tenantService) {
        super(tenantService);
        this.environment = environment;
        this.dataTransferService = dataTransferService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * Returns the transfer process for the given provider PID.
     *
     * @param tenantId    the tenant identifier from the path
     * @param providerPid the provider PID
     * @return the serialized transfer process
     */
    @GetMapping(path = "/{providerPid}")
    public ResponseEntity<JsonNode> getTransferProcessByProviderPid(@PathVariable("tenantId") String tenantId,
                                                                    @PathVariable("providerPid") String providerPid) {
        log.info("Fetching TransferProcess for id {}", providerPid);
        resolveTenant(tenantId);
        TransferProcess transferProcess = dataTransferService.findTransferProcessByProviderPid(providerPid);
        return ResponseEntity.ok(TransferSerializer.serializeProtocolJsonNode(transferProcess));
    }

    /**
     * Initiates a data transfer from a consumer's transfer request message.
     *
     * @param tenantId                       the tenant identifier from the path
     * @param transferRequestMessageJsonNode the transfer request message
     * @return the created transfer process
     */
    @PostMapping(path = "/request")
    public ResponseEntity<JsonNode> initiateDataTransfer(@PathVariable("tenantId") String tenantId,
                                                         @RequestBody JsonNode transferRequestMessageJsonNode) {
        resolveTenant(tenantId);
        TransferRequestMessage transferRequestMessage = TransferSerializer.deserializeProtocol(transferRequestMessageJsonNode, TransferRequestMessage.class);
        log.info("Initiating data transfer");
        TransferProcess transferProcessRequested = dataTransferService.initiateDataTransfer(transferRequestMessage);
        if (Arrays.stream(environment.getActiveProfiles()).toList().contains("tck")) {
            log.info("TCK profile running - publishing event - {}", transferProcessRequested.getState());
            log.info("ConsumerPid: {}, ProviderPid: {}", transferProcessRequested.getConsumerPid(), transferProcessRequested.getProviderPid());
            applicationEventPublisher.publishEvent(transferProcessRequested);
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TransferSerializer.serializeProtocolJsonNode(transferProcessRequested));
    }

    /**
     * Starts the data transfer for the given provider PID.
     *
     * @param tenantId                    the tenant identifier from the path
     * @param providerPid                 the provider PID
     * @param transferStartMessageJsonNode the transfer start message
     * @return empty response on success
     */
    @PostMapping(path = "/{providerPid}/start")
    public ResponseEntity<Void> startDataTransfer(@PathVariable("tenantId") String tenantId,
                                                  @PathVariable("providerPid") String providerPid,
                                                  @RequestBody JsonNode transferStartMessageJsonNode) {
        resolveTenant(tenantId);
        TransferStartMessage transferStartMessage = TransferSerializer.deserializeProtocol(transferStartMessageJsonNode, TransferStartMessage.class);
        log.info("Starting data transfer for providerPid {} and consumerPid {}", providerPid, transferStartMessage.getConsumerPid());
        TransferProcess transferProcessStarted = dataTransferService.startDataTransfer(transferStartMessage, null, providerPid);
        log.info("TransferProcess {} state changed to {}", transferProcessStarted.getId(), transferProcessStarted.getState());
        return ResponseEntity.ok().build();
    }

    /**
     * Completes the data transfer for the given provider PID.
     *
     * @param tenantId                        the tenant identifier from the path
     * @param providerPid                     the provider PID
     * @param transferCompletionMessageJsonNode the transfer completion message
     * @return empty response on success
     */
    @PostMapping(path = "/{providerPid}/completion")
    public ResponseEntity<Void> completeDataTransfer(@PathVariable("tenantId") String tenantId,
                                                     @PathVariable("providerPid") String providerPid,
                                                     @RequestBody JsonNode transferCompletionMessageJsonNode) {
        resolveTenant(tenantId);
        TransferCompletionMessage transferCompletionMessage = TransferSerializer.deserializeProtocol(transferCompletionMessageJsonNode, TransferCompletionMessage.class);
        log.info("Completing data transfer for providerPid {} and consumerPid {}", providerPid, transferCompletionMessage.getConsumerPid());
        TransferProcess transferProcessCompleted = dataTransferService.completeDataTransfer(transferCompletionMessage, null, providerPid);
        log.info("TransferProcess {} state changed to {}", transferProcessCompleted.getId(), transferProcessCompleted.getState());
        return ResponseEntity.ok().build();
    }

    /**
     * Terminates the data transfer for the given provider PID.
     *
     * @param tenantId                         the tenant identifier from the path
     * @param providerPid                      the provider PID
     * @param transferTerminationMessageJsonNode the transfer termination message
     * @return empty response on success
     */
    @PostMapping(path = "/{providerPid}/termination")
    public ResponseEntity<Void> terminateDataTransfer(@PathVariable("tenantId") String tenantId,
                                                      @PathVariable("providerPid") String providerPid,
                                                      @RequestBody JsonNode transferTerminationMessageJsonNode) {
        resolveTenant(tenantId);
        TransferTerminationMessage transferTerminationMessage = TransferSerializer.deserializeProtocol(transferTerminationMessageJsonNode, TransferTerminationMessage.class);
        log.info("Terminating data transfer for providerPid {} and consumerPid {}", providerPid, transferTerminationMessage.getConsumerPid());
        TransferProcess transferProcessTerminated = dataTransferService.terminateDataTransfer(transferTerminationMessage, null, providerPid);
        log.info("TransferProcess {} state changed to {}", transferProcessTerminated.getId(), transferProcessTerminated.getState());
        return ResponseEntity.ok().build();
    }

    /**
     * Suspends the data transfer for the given provider PID.
     *
     * @param tenantId                        the tenant identifier from the path
     * @param providerPid                     the provider PID
     * @param transferSuspensionMessageJsonNode the transfer suspension message
     * @return empty response on success
     */
    @PostMapping(path = "/{providerPid}/suspension")
    public ResponseEntity<Void> suspenseDataTransfer(@PathVariable("tenantId") String tenantId,
                                                     @PathVariable("providerPid") String providerPid,
                                                     @RequestBody JsonNode transferSuspensionMessageJsonNode) {
        resolveTenant(tenantId);
        TransferSuspensionMessage transferSuspensionMessage = TransferSerializer.deserializeProtocol(transferSuspensionMessageJsonNode, TransferSuspensionMessage.class);
        log.info("Suspending data transfer for providerPid {} and consumerPid {}", providerPid, transferSuspensionMessage.getConsumerPid());
        TransferProcess transferProcessSuspended = dataTransferService.suspendDataTransfer(transferSuspensionMessage, null, providerPid);
        log.info("TransferProcess {} state changed to {}", transferProcessSuspended.getId(), transferProcessSuspended.getState());
        return ResponseEntity.ok().build();
    }
}
