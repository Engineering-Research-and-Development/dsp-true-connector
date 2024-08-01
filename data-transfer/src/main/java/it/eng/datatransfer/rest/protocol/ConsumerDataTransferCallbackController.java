package it.eng.datatransfer.rest.protocol;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.datatransfer.model.Serializer;
import it.eng.datatransfer.model.TransferCompletionMessage;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferStartMessage;
import it.eng.datatransfer.model.TransferSuspensionMessage;
import it.eng.datatransfer.model.TransferTerminationMessage;
import it.eng.datatransfer.service.DataTransferService;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, path = "/consumer/transfers")
@Slf4j
public class ConsumerDataTransferCallbackController {

	private DataTransferService dataTransferService;
	
	public ConsumerDataTransferCallbackController(DataTransferService dataTransferService) {
		super();
		this.dataTransferService = dataTransferService;
	}

	@PostMapping(path = "/{consumerPid}/start")
	public ResponseEntity<JsonNode> startDataTransfer(@PathVariable String consumerPid,
			@RequestBody JsonNode transferStartMessageJsonNode) {
		TransferStartMessage transferStartMessage = Serializer.deserializeProtocol(transferStartMessageJsonNode, TransferStartMessage.class);
		log.info("Starting data transfer for consumerPid {} and providerPid {}", consumerPid, transferStartMessage.getProviderPid());
		TransferProcess transferProcessStarted = dataTransferService.startDataTransfer(transferStartMessage, consumerPid, null);
		log.info("TransferProcess {} state changed to {}", transferProcessStarted.getId(), transferProcessStarted.getState());
		return ResponseEntity.ok(null);
	}

	@PostMapping(path = "/{consumerPid}/completion")
	public ResponseEntity<JsonNode> completeDataTransfer(@PathVariable String consumerPid,
			@RequestBody JsonNode transferCompletionMessageJsonNode) {
		TransferCompletionMessage transferCompletionMessage = Serializer.deserializeProtocol(transferCompletionMessageJsonNode, TransferCompletionMessage.class);
		log.info("Completing data transfer for consumerPid {} and providerPid {}", consumerPid, transferCompletionMessage.getProviderPid());
		TransferProcess transferProcessCompleted = dataTransferService.completeDataTransfer(transferCompletionMessage, consumerPid, null);
		log.info("TransferProcess {} state changed to {}", transferProcessCompleted.getId(), transferProcessCompleted.getState());
		return ResponseEntity.ok(null);
	}

	@PostMapping(path = "/{consumerPid}/termination")
	public ResponseEntity<JsonNode> terminateDataTransfer(@PathVariable String consumerPid,
			@RequestBody JsonNode transferTerminationMessageJsonNode) {
		TransferTerminationMessage transferTerminationMessage = Serializer.deserializeProtocol(transferTerminationMessageJsonNode, TransferTerminationMessage.class);
		log.info("Terminating data transfer for comsumerPid {} and providerPid {}", consumerPid, transferTerminationMessage.getProviderPid());
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(notImplemented());
	}

	@PostMapping(path = "/{consumerPid}/suspension")
	public ResponseEntity<JsonNode> suspenseDataTransfer(@PathVariable String consumerPid,
			@RequestBody JsonNode transferSuspensionMessageJsonNode) {
		TransferSuspensionMessage transferSuspensionMessage = Serializer.deserializeProtocol(transferSuspensionMessageJsonNode, TransferSuspensionMessage.class);
		log.info("Suspending data transfer for comsumerPid {} and providerPid {}", consumerPid, transferSuspensionMessage.getProviderPid());
		TransferProcess transferProcessSuspended = dataTransferService.suspendDataTransfer(transferSuspensionMessage, consumerPid, null);
		log.info("TransferProcess {} state changed to {}", transferProcessSuspended.getId(), transferProcessSuspended.getState());
		return ResponseEntity.ok(null);
	}
	
	private JsonNode notImplemented() {
		ObjectMapper mapper = new ObjectMapper();
		String newString = "{\"response\": \"Method not implemented\"}";
	    try {
			return mapper.readTree(newString);
		} catch (JsonProcessingException e) {
		}
	    return null;
	}
}
