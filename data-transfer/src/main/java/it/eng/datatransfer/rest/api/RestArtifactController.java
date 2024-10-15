package it.eng.datatransfer.rest.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.datatransfer.service.api.RestArtifactService;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE,
	path = "/artifacts")
@Slf4j
public class RestArtifactController { 
	
	private final RestArtifactService restArtifactService;
	
	public RestArtifactController(RestArtifactService restArtifactService) {
		super();
		this.restArtifactService = restArtifactService;
	}

	/**
	 * 
	 * @param authorization
	 * @param transactionId Base64.urlEncoded(consumerPid|providerPid) from TransferProcess message
	 * @param artifactId artifactId
	 * @param jsonBody
	 * @return
	 */
//    @PostMapping(path = "/{transactionId}/{artifactId}")
	@RequestMapping(value = "/{transactionId}/{artifactId}", method = { RequestMethod.POST,  RequestMethod.GET })
    protected ResponseEntity<String> getArtifact(@RequestHeader(required = false) String authorization,
										    		@PathVariable String transactionId,                                       
										    		@PathVariable String artifactId, 
                                                  @RequestBody(required = false) JsonNode jsonBody) {
    	log.info("Accessing artifact with id {}", artifactId);
    
		String response = restArtifactService.getArtifact(transactionId, artifactId, jsonBody);
		
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);

    }
    
  
}
