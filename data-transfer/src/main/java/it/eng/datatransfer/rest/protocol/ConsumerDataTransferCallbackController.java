package it.eng.datatransfer.rest.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.datatransfer.model.*;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.datatransfer.service.TransferProcessStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
//consumes = MediaType.APPLICATION_JSON_VALUE,
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE, path = "/consumer/transfers")
@Slf4j
public class ConsumerDataTransferCallbackController {

    private final TransferProcessStrategy dataTransferService;

    public ConsumerDataTransferCallbackController(TransferProcessStrategy dataTransferService) {
        super();
        this.dataTransferService = dataTransferService;
    }

    @PostMapping("/tck")
    public ResponseEntity<JsonNode> initiateDataTransfer(@RequestBody TCKRequest tckRequest) {
        log.info("Received TCK request for agreementId {}, format {} from connector {}",
                tckRequest.getAgreementId(), tckRequest.getFormat(), tckRequest.getConnectorAddress());

        TransferProcess transferProcessRequested = dataTransferService.requestTransfer(tckRequest);

        return ResponseEntity.ok(TransferSerializer.serializeProtocolJsonNode(transferProcessRequested));
    }

    @GetMapping("/{consumerPid}")
    public ResponseEntity<String> getTransferProcessByConsumerPid(@PathVariable String consumerPid) {
        TransferProcess transferProcess = dataTransferService.findTransferProcessByConsumerPid(consumerPid);
        return ResponseEntity.ok(TransferSerializer.serializeProtocol(transferProcess));
    }

    @PostMapping(path = "/{consumerPid}/start")
    public ResponseEntity<Void> startDataTransfer(@PathVariable String consumerPid,
                                                  @RequestBody JsonNode transferStartMessageJsonNode) {
        TransferStartMessage transferStartMessage = TransferSerializer.deserializeProtocol(transferStartMessageJsonNode, TransferStartMessage.class);
        log.info("Starting data transfer for consumerPid {} and providerPid {}", consumerPid, transferStartMessage.getProviderPid());
        TransferProcess transferProcessStarted = dataTransferService.startDataTransfer(transferStartMessage, consumerPid, null);
        log.info("TransferProcess {} state changed to {}", transferProcessStarted.getId(), transferProcessStarted.getState());
        return ResponseEntity.ok().build();
    }

    @PostMapping(path = "/{consumerPid}/completion")
    public ResponseEntity<Void> completeDataTransfer(@PathVariable String consumerPid,
                                                     @RequestBody JsonNode transferCompletionMessageJsonNode) {
        TransferCompletionMessage transferCompletionMessage = TransferSerializer.deserializeProtocol(transferCompletionMessageJsonNode, TransferCompletionMessage.class);
        log.info("Completing data transfer for consumerPid {} and providerPid {}", consumerPid, transferCompletionMessage.getProviderPid());
        TransferProcess transferProcessCompleted = dataTransferService.completeDataTransfer(transferCompletionMessage, consumerPid, null);
        log.info("TransferProcess {} state changed to {}", transferProcessCompleted.getId(), transferProcessCompleted.getState());
        return ResponseEntity.ok().build();
    }

    @PostMapping(path = "/{consumerPid}/termination")
    public ResponseEntity<Void> terminateDataTransfer(@PathVariable String consumerPid,
                                                      @RequestBody JsonNode transferTerminationMessageJsonNode) {
        TransferTerminationMessage transferTerminationMessage = TransferSerializer.deserializeProtocol(transferTerminationMessageJsonNode, TransferTerminationMessage.class);
        log.info("Terminating data transfer for comsumerPid {} and providerPid {}", consumerPid, transferTerminationMessage.getProviderPid());
        TransferProcess transferProcessTerminated = dataTransferService.terminateDataTransfer(transferTerminationMessage, consumerPid, null);
        log.info("TransferProcess {} state changed to {}", transferProcessTerminated.getId(), transferProcessTerminated.getState());
        return ResponseEntity.ok().build();
    }

    @PostMapping(path = "/{consumerPid}/suspension")
    public ResponseEntity<Void> suspenseDataTransfer(@PathVariable String consumerPid,
                                                     @RequestBody JsonNode transferSuspensionMessageJsonNode) {
        TransferSuspensionMessage transferSuspensionMessage = TransferSerializer.deserializeProtocol(transferSuspensionMessageJsonNode, TransferSuspensionMessage.class);
        log.info("Suspending data transfer for comsumerPid {} and providerPid {}", consumerPid, transferSuspensionMessage.getProviderPid());
        TransferProcess transferProcessSuspended = dataTransferService.suspendDataTransfer(transferSuspensionMessage, consumerPid, null);
        log.info("TransferProcess {} state changed to {}", transferProcessSuspended.getId(), transferProcessSuspended.getState());
        return ResponseEntity.ok().build();
    }
}
