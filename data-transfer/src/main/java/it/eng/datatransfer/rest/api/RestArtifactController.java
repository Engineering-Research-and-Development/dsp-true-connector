package it.eng.datatransfer.rest.api;

import java.io.IOException;

import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
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
	 * @param jsonBody
	 * @return
	 */
    @RequestMapping(path = "/{transactionId}", method = { RequestMethod.GET, RequestMethod.POST })
    protected ResponseEntity<String> getArtifact(HttpServletResponse response,
    												@RequestHeader(required = false) String authorization,
										    		@PathVariable String transactionId,                                       
										    		@RequestBody(required = false) JsonNode jsonBody) {
    
		String rr = restArtifactService.getArtifact(transactionId, jsonBody);
		return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(rr);
		/*
		 * Uncomment this part when TransferProcess.State.INITIAL is done
		 * this logic requires to have fileId present in TransferPRocess
    	try (ServletOutputStream outputStream = response.getOutputStream()) {
    		GridFsResource attachment = restArtifactService.streamAttachment(transactionId);
    		response.setHeader("Content-Disposition", "attachment;filename=\"" + attachment.getFilename() + "\"");
    		String contentType = attachment.getGridFSFile().getMetadata().get(HttpHeaders.CONTENT_TYPE) != null ?
    				(String) attachment.getGridFSFile().getMetadata().get(HttpHeaders.CONTENT_TYPE) :
    					"text/plain";
    		response.addHeader(HttpHeaders.CONTENT_TYPE, contentType);
    		IOUtils.copy(attachment.getInputStream(), response.getOutputStream());
    		restArtifactService.publishEvent(transactionId);
    	} catch (IOException e) {
    		response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
    	} 
		 */
    }
}
