package it.eng.datatransfer.rest.api;

import java.io.UnsupportedEncodingException;
import java.util.Collection;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.datatransfer.model.DataTransferRequest;
import it.eng.datatransfer.service.api.DataTransferAPIService;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(path = ApiEndpoints.TRANSFER_DATATRANSFER_V1)
@Slf4j
public class DataTransferAPIController {

	private DataTransferAPIService apiService;

	public DataTransferAPIController(DataTransferAPIService apiService) {
		this.apiService = apiService;
	}

	/********* CONSUMER ***********/
	
	/**
	 * Consumer requests (initiates) data transfer.
	 * @param dataTransferRequest
	 * @return GenericApiResponse
	 */
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<GenericApiResponse<JsonNode>> requestTransfer(@RequestBody DataTransferRequest dataTransferRequest) {
		log.info("Consumer sends transfer request {}", dataTransferRequest.getTransferProcessId());
		JsonNode response = apiService.requestTransfer(dataTransferRequest);
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
				.body(GenericApiResponse.success(response, "Data transfer requested"));
	}
	
	/**
	 * Consumer download artifact.
	 * @param transferProcessId
	 * @return GenericApiResponse
	 */
	@GetMapping(path = { "/{transferProcessId}/download" })
	public ResponseEntity<GenericApiResponse<Void>> downloadData(
			@PathVariable(required = true) String transferProcessId) {
		log.info("Downloading transfer process id - {} data", transferProcessId);
		apiService.downloadData(transferProcessId);
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
				.body(GenericApiResponse.success(null, "Data successfully downloaded"));
	}
	
	/**
	 * Consumer view downloaded artifact.<br>
	 * Before "viewing" artifact, policy will be enforced to check if agreement is still valid.
	 * In response artifact will be sent, even that method is void.
	 * @param transferProcessId
	 * @param response
	 */
	@GetMapping(path = { "/{transferProcessId}/view" })
	public void viewData(
			@PathVariable(required = true) String transferProcessId,
			HttpServletResponse response) {
		log.info("Accessing transfer process id - {} data", transferProcessId);
		apiService.viewData(transferProcessId, response);
	}
	
	/********* CONSUMER & PROVIDER ***********/
	
	/**
	 * Find by id if present, next by state and get all.
	 * @param transferProcessId
	 * @param state
	 * @param role
	 * @return GenericApiResponse
	 */
	@GetMapping(path = { "", "/{transferProcessId}" })
	public ResponseEntity<GenericApiResponse<Collection<JsonNode>>> getTransfersProcess(
			@PathVariable(required = false) String transferProcessId,
			@RequestParam(required = false) String state,
			@RequestParam(required = false) String role) {
		log.info("Fetching transfer process id - {}, state {}", transferProcessId, state);
		Collection<JsonNode> response = apiService.findDataTransfers(transferProcessId, state, role);
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
				.body(GenericApiResponse.success(response, "Fetching transfer process"));
	}
	
	/**
	 * Start transfer process.
	 * @param transferProcessId
	 * @return GenericApiResponse
	 * @throws UnsupportedEncodingException
	 */
	@PutMapping(path = "/{transferProcessId}/start")
    public ResponseEntity<GenericApiResponse<JsonNode>> startTransfer(@PathVariable String transferProcessId) throws UnsupportedEncodingException {
		log.info("Starting data transfer {}", transferProcessId);
    	JsonNode response = apiService.startTransfer(transferProcessId);
    	return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
    			.body(GenericApiResponse.success(response, "Data transfer started"));
    }
	
	/**
	 * Complete transfer process.
	 * @param transferProcessId
	 * @return GenericApiResponse
	 */
	@PutMapping(path = "/{transferProcessId}/complete")
    public ResponseEntity<GenericApiResponse<JsonNode>> completeTransfer(@PathVariable String transferProcessId) {
		log.info("Compliting data transfer {}", transferProcessId);
    	JsonNode response = apiService.completeTransfer(transferProcessId);
    	return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
    			.body(GenericApiResponse.success(response, "Data transfer completed"));
    }
	
	/**
	 * Suspend transfer process.
	 * @param transferProcessId
	 * @return GenericApiResponse
	 */
	@PutMapping(path = "/{transferProcessId}/suspend")
    public ResponseEntity<GenericApiResponse<JsonNode>> suspendTransfer(@PathVariable String transferProcessId) {
		log.info("Suspending data transfer {}", transferProcessId);
    	JsonNode response = apiService.suspendTransfer(transferProcessId);
    	return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
    			.body(GenericApiResponse.success(response, "Data transfer suspended"));
    }
	
	/**
	 * Terminate transfer process.
	 * @param transferProcessId
	 * @return GenericApiResponse
	 */
	@PutMapping(path = "/{transferProcessId}/terminate")
    public ResponseEntity<GenericApiResponse<JsonNode>> terminateTransfer(@PathVariable String transferProcessId) {
		log.info("Terminating data transfer {}", transferProcessId);
    	JsonNode response = apiService.terminateTransfer(transferProcessId);
    	return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
    			.body(GenericApiResponse.success(response, "Data transfer terminated"));
    }

}
