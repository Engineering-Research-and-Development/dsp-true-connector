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
import it.eng.datatransfer.service.DataTransferAPIService;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.DSpaceConstants;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, 
	path = ApiEndpoints.TRANSFER_DATATRANSFER_V1)
@Slf4j
public class DataTransferAPIController {

	private DataTransferAPIService apiService;

	public DataTransferAPIController(DataTransferAPIService apiService) {
		this.apiService = apiService;
	}

	/********* CONSUMER ***********/
	
	
	@PostMapping
	public ResponseEntity<GenericApiResponse<JsonNode>> requestTransfer(@RequestBody DataTransferRequest dataTransferRequest ) {
		log.info("Consumer sends transfer request");
		JsonNode response = apiService.requestTransfer(dataTransferRequest);
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
				.body(GenericApiResponse.success(response, "Data transfer requested"));
	}
	
	/********* CONSUMER & PROVIDER ***********/
	
	/**
	 * Find by id if present, next by state and get all
	 * @param transferProcessId
	 * @param state
	 * @return
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
	
	@PutMapping(path = "/{transferProcessId}/start")
    public ResponseEntity<GenericApiResponse<JsonNode>> startTransfer(@PathVariable String transferProcessId) throws UnsupportedEncodingException {
		log.info("Starting data transfer");
    	JsonNode response = apiService.startTransfer(transferProcessId);
    	return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
    			.body(GenericApiResponse.success(response, "Data transfer started"));
    }
	
	@PutMapping(path = "/{transferProcessId}/complete")
    public ResponseEntity<GenericApiResponse<JsonNode>> completeTransfer(@PathVariable String transferProcessId) {
		log.info("Compliting data transfer");
    	JsonNode response = apiService.completeTransfer(transferProcessId);
    	return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
    			.body(GenericApiResponse.success(response, "Data transfer completed"));
    }
	
	@PutMapping(path = "/{transferProcessId}/suspend")
    public ResponseEntity<GenericApiResponse<JsonNode>> suspendTransfer(@PathVariable String transferProcessId) {
		log.info("Compliting data transfer");
    	JsonNode response = apiService.suspendTransfer(transferProcessId);
    	return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
    			.body(GenericApiResponse.success(response, "Data transfer suspended"));
    }
	
	@PutMapping(path = "/{transferProcessId}/terminate")
    public ResponseEntity<GenericApiResponse<JsonNode>> terminateTransfer(@PathVariable String transferProcessId) {
		log.info("Compliting data transfer");
    	JsonNode response = apiService.terminateTransfer(transferProcessId);
    	return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
    			.body(GenericApiResponse.success(response, "Data transfer terminated"));
    }

}
