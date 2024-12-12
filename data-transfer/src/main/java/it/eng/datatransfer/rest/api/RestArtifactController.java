package it.eng.datatransfer.rest.api;

import java.io.IOException;

import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.eng.datatransfer.exceptions.DownloadException;
import it.eng.datatransfer.service.api.RestArtifactService;
import it.eng.tools.model.Artifact;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;

@RestController
@RequestMapping(path = "/artifacts")
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
    @GetMapping(path = "/{transactionId}")
    public void getArtifact(HttpServletResponse response,
    												@RequestHeader(required = false) String authorization,
										    		@PathVariable String transactionId) {
    
    	log.info("Starting data download");
		
    	Artifact artifact = restArtifactService.getArtifact(transactionId);
    	
    	switch (artifact.getArtifactType()) {
		case FILE:
			GridFsResource attachment = restArtifactService.streamAttachment(artifact.getValue());
			try {
				IOUtils.copy(attachment.getInputStream(), response.getOutputStream());
				response.setStatus(HttpStatus.OK.value());
				response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=\"" + attachment.getFilename() + "\"");
				response.setHeader(HttpHeaders.CONTENT_TYPE, attachment.getContentType());
				restArtifactService.publishArtifactConsumedEvent(transactionId);
			} catch (IOException e) {
				log.error("Error while sending file", e);
				throw new DownloadException("Error while downloading data", HttpStatus.INTERNAL_SERVER_ERROR);
			}
			break;
		case EXTERNAL:
			try {
				Response data = restArtifactService.getExternalData(artifact.getValue());
				IOUtils.copy(data.body().byteStream(), response.getOutputStream());
				response.setStatus(HttpStatus.OK.value());
				response.setHeader(HttpHeaders.CONTENT_TYPE, data.header(HttpHeaders.CONTENT_TYPE));
				data.close();
				restArtifactService.publishArtifactConsumedEvent(transactionId);
			} catch (IOException e) {
				log.error("Error while downloading external data", e);
				throw new DownloadException("Error while downloading data", HttpStatus.INTERNAL_SERVER_ERROR);
			}
			break;

		default:
			log.error("Wrong artifact type: {}", artifact.getArtifactType());
			throw new DownloadException("Error while downloading data", HttpStatus.INTERNAL_SERVER_ERROR);
		}
    }
}
