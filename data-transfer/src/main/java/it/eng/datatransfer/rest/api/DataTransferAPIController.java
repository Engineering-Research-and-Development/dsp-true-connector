package it.eng.datatransfer.rest.api;

import java.util.Collection;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.datatransfer.service.DataTransferAPIService;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, path = "/api/transfers")
@Slf4j
public class DataTransferAPIController {

	private DataTransferAPIService apiService;

	public DataTransferAPIController(DataTransferAPIService apiService) {
		this.apiService = apiService;
	}

	/**
	 * Find by id if present, next by state and get all
	 * @param transferProcessId
	 * @param state
	 * @return
	 */
	@GetMapping(path = { "", "/{transferProcessId}" })
	public ResponseEntity<GenericApiResponse<Collection<JsonNode>>> getTransfersProcess(
			@PathVariable(required = false) String transferProcessId, @RequestParam(required = false) String state) {
		log.info("Ferching transfer process id - {}, state {}", transferProcessId, state);
		Collection<JsonNode> contractNegotiations = apiService.findDataTransfers(transferProcessId, state);
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
				.body(GenericApiResponse.success(contractNegotiations, "Fething transfer process", HttpStatus.OK.value()));
	}

}
