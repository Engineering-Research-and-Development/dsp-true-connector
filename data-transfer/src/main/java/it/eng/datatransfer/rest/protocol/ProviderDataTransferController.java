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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.datatransfer.model.Serializer;
import it.eng.datatransfer.model.TransferCompletionMessage;
import it.eng.datatransfer.model.TransferRequestMessage;
import it.eng.datatransfer.model.TransferStartMessage;
import it.eng.datatransfer.model.TransferSuspensionMessage;
import it.eng.datatransfer.model.TransferTerminationMessage;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, path = "/transfers")
@Slf4j
public class ProviderDataTransferController {

	@GetMapping(path = "/{providerPid}")
	public ResponseEntity<JsonNode> getTransferProcessByProviderPid(@PathVariable String providerPid) {
		log.info("Fetching TransferProcess for id {}", providerPid);
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(notImplemented());
	}

	@PostMapping(path = "/request")
	public ResponseEntity<JsonNode> initiateDataTransfer(@RequestBody JsonNode transferRequestMessageJsonNode) {
		TransferRequestMessage transferRequestMessage = Serializer.deserializeProtocol(transferRequestMessageJsonNode, TransferRequestMessage.class);
		log.info("Initiating data transfer");
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(notImplemented());
	}

	@PostMapping(path = "/{providerPid}/start")
	public ResponseEntity<JsonNode> startDataTransfer(@PathVariable String providerPid,
			@RequestBody JsonNode transferStartMessageJsonNode) {
		TransferStartMessage transferStartMessage = Serializer.deserializeProtocol(transferStartMessageJsonNode, TransferStartMessage.class);
		log.info("Starting data transfer for providerPid {} and consumerPid {}", providerPid, transferStartMessage.getConsumerPid());
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(notImplemented());
	}

	@PostMapping(path = "/{providerPid}/completion")
	public ResponseEntity<JsonNode> completeDataTransfer(@PathVariable String providerPid,
			@RequestBody JsonNode transferCompletionMessageJsonNode) {
		TransferCompletionMessage transferCompletionMessage = Serializer.deserializeProtocol(transferCompletionMessageJsonNode, TransferCompletionMessage.class);
		log.info("Completing data transfer for providerPid {} and consumerPid {}", providerPid, transferCompletionMessage.getConsumerPid());
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(notImplemented());
	}

	@PostMapping(path = "/{providerPid}/termination")
	public ResponseEntity<JsonNode> terminateDataTransfer(@PathVariable String providerPid,
			@RequestBody JsonNode transferTerminationMessageJsonNode) {
		TransferTerminationMessage transferTerminationMessage = Serializer.deserializeProtocol(transferTerminationMessageJsonNode, TransferTerminationMessage.class);
		log.info("Terminating data transfer for providerPid {} and comsumerPid {}", providerPid, transferTerminationMessage.getConsumerPid());
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(notImplemented());
	}

	@PostMapping(path = "/{providerPid}/suspension")
	public ResponseEntity<JsonNode> suspenseDataTransfer(@PathVariable String providerPid,
			@RequestBody JsonNode transferSuspensionMessageJsonNode) {
		TransferSuspensionMessage transferSuspensionMessage = Serializer.deserializeProtocol(transferSuspensionMessageJsonNode, TransferSuspensionMessage.class);
		log.info("Suspending data transfer for providerPid {} and consumerPid {}", providerPid, transferSuspensionMessage.getConsumerPid());
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(notImplemented());
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
