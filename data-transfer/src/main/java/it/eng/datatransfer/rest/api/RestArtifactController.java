package it.eng.datatransfer.rest.api;

import java.io.IOException;

import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import it.eng.datatransfer.service.api.RestArtifactService;
import it.eng.tools.model.Artifact;
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
    protected void getArtifact(HttpServletResponse response,
    												@RequestHeader(required = false) String authorization,
										    		@PathVariable String transactionId) {
    
    	log.info("Starting data download");
		
    	Artifact artifact = restArtifactService.getArtifact(transactionId);
    	
    	switch (artifact.getArtifactType()) {
		case FILE:
			GridFsResource attachment = restArtifactService.streamAttachment(artifact.getValue());
			try {
				response.setStatus(HttpStatus.OK.value());
				response.setHeader("Content-Disposition", "attachment;filename=\"" + attachment.getFilename() + "\"");
				response.addHeader("Content-type", attachment.getContentType());
				IOUtils.copy(attachment.getInputStream(), response.getOutputStream());
				restArtifactService.publishArtifactConsumedEvent(transactionId);
			} catch (IOException e) {
				response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
			}
			break;
		case EXTERNAL:
			try {
				IOUtils.copy(restArtifactService.getExternalData(artifact.getValue()).byteStream(), response.getOutputStream());
				response.setStatus(HttpStatus.OK.value());
				restArtifactService.publishArtifactConsumedEvent(transactionId);
			} catch (IOException e) {
				response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
			}
			break;

		default:
			break;
		}
    }
}
