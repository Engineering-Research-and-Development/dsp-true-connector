package it.eng.datatransfer.rest.protocol;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.datatransfer.model.TransferCompletionMessage;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferRequestMessage;
import it.eng.datatransfer.model.TransferStartMessage;
import it.eng.datatransfer.model.TransferSuspensionMessage;
import it.eng.datatransfer.model.TransferTerminationMessage;
import it.eng.datatransfer.service.DataTransferService;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, path = "/transfers")
@Slf4j
public class ProviderDataTransferController {

	private DataTransferService dataTransferService;
	
	public ProviderDataTransferController(DataTransferService dataTransferService) {
		super();
		this.dataTransferService = dataTransferService;
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
