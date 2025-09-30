package it.eng.datatransfer.rest.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.datatransfer.model.*;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.datatransfer.service.TransferProcessStrategy;
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
        produces = MediaType.APPLICATION_JSON_VALUE, path = "/transfers")
//consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_JSON_UTF8_VALUE, "application/json;charset=utf-8"},
@Slf4j
public class ProviderDataTransferController {

    //    @Autowired
    private final Environment environment;

    private final TransferProcessStrategy dataTransferService;
    //    @Autowired
    private final ApplicationEventPublisher applicationEventPublisher;

    public ProviderDataTransferController(Environment environment, TransferProcessStrategy dataTransferService, ApplicationEventPublisher applicationEventPublisher) {
        super();
        this.environment = environment;
        this.dataTransferService = dataTransferService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @GetMapping(path = "/{providerPid}")
    public ResponseEntity<JsonNode> getTransferProcessByProviderPid(@PathVariable String providerPid) {
        log.info("Fetching TransferProcess for id {}", providerPid);
        TransferProcess transferProcess = dataTransferService.findTransferProcessByProviderPid(providerPid);
        return ResponseEntity.ok(TransferSerializer.serializeProtocolJsonNode(transferProcess));
    }

    @PostMapping(path = "/request")
    public ResponseEntity<JsonNode> initiateDataTransfer(@RequestBody JsonNode transferRequestMessageJsonNode) {
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

    @PostMapping(path = "/{providerPid}/start")
    public ResponseEntity<Void> startDataTransfer(@PathVariable String providerPid,
                                                  @RequestBody JsonNode transferStartMessageJsonNode) {
        TransferStartMessage transferStartMessage = TransferSerializer.deserializeProtocol(transferStartMessageJsonNode, TransferStartMessage.class);
        log.info("Starting data transfer for providerPid {} and consumerPid {}", providerPid, transferStartMessage.getConsumerPid());
        TransferProcess transferProcessStarted = dataTransferService.startDataTransfer(transferStartMessage, null, providerPid);
        log.info("TransferProcess {} state changed to {}", transferProcessStarted.getId(), transferProcessStarted.getState());
        return ResponseEntity.ok().build();
    }

    @PostMapping(path = "/{providerPid}/completion")
    public ResponseEntity<Void> completeDataTransfer(@PathVariable String providerPid,
                                                     @RequestBody JsonNode transferCompletionMessageJsonNode) {
        TransferCompletionMessage transferCompletionMessage = TransferSerializer.deserializeProtocol(transferCompletionMessageJsonNode, TransferCompletionMessage.class);
        log.info("Completing data transfer for providerPid {} and consumerPid {}", providerPid, transferCompletionMessage.getConsumerPid());
        TransferProcess transferProcessCompleted = dataTransferService.completeDataTransfer(transferCompletionMessage, null, providerPid);
        log.info("TransferProcess {} state changed to {}", transferProcessCompleted.getId(), transferProcessCompleted.getState());
        return ResponseEntity.ok().build();
    }

    @PostMapping(path = "/{providerPid}/termination")
    public ResponseEntity<Void> terminateDataTransfer(@PathVariable String providerPid,
                                                      @RequestBody JsonNode transferTerminationMessageJsonNode) {
        TransferTerminationMessage transferTerminationMessage = TransferSerializer.deserializeProtocol(transferTerminationMessageJsonNode, TransferTerminationMessage.class);
        log.info("Terminating data transfer for providerPid {} and comsumerPid {}", providerPid, transferTerminationMessage.getConsumerPid());
        TransferProcess transferProcessTerminated = dataTransferService.terminateDataTransfer(transferTerminationMessage, null, providerPid);
        log.info("TransferProcess {} state changed to {}", transferProcessTerminated.getId(), transferProcessTerminated.getState());
        return ResponseEntity.ok().build();
    }

    @PostMapping(path = "/{providerPid}/suspension")
    public ResponseEntity<Void> suspenseDataTransfer(@PathVariable String providerPid,
                                                     @RequestBody JsonNode transferSuspensionMessageJsonNode) {
        TransferSuspensionMessage transferSuspensionMessage = TransferSerializer.deserializeProtocol(transferSuspensionMessageJsonNode, TransferSuspensionMessage.class);
        log.info("Suspending data transfer for providerPid {} and consumerPid {}", providerPid, transferSuspensionMessage.getConsumerPid());
        TransferProcess transferProcessSuspended = dataTransferService.suspendDataTransfer(transferSuspensionMessage, null, providerPid);
        log.info("TransferProcess {} state changed to {}", transferProcessSuspended.getId(), transferProcessSuspended.getState());
        return ResponseEntity.ok().build();
    }
}
