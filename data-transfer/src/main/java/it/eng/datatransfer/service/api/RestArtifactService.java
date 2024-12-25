package it.eng.datatransfer.service.api;

import java.io.IOException;
import java.nio.charset.Charset;

import org.apache.tomcat.util.codec.binary.Base64;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.model.Filters;

import it.eng.datatransfer.exceptions.DownloadException;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.serializer.Serializer;
import it.eng.datatransfer.service.DataTransferService;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.event.policyenforcement.ArtifactConsumedEvent;
import it.eng.tools.model.Artifact;
import it.eng.tools.model.ExternalData;
import it.eng.tools.repository.ArtifactRepository;
import it.eng.tools.response.GenericApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RestArtifactService {

	private final DataTransferService dataTransferService;
	private final MongoTemplate mongoTemplate;
	private final OkHttpRestClient okHttpRestClient;
	private final ApplicationEventPublisher publisher;
	private final ArtifactRepository artifactRepository;
	private GridFsResource gridFsResource;
	
	public RestArtifactService(DataTransferService dataTransferService, MongoTemplate mongoTemplate,
			OkHttpRestClient okHttpRestClient, ApplicationEventPublisher publisher, ArtifactRepository artifactRepository) {
		super();
		this.dataTransferService = dataTransferService;
		this.mongoTemplate = mongoTemplate;
		this.okHttpRestClient = okHttpRestClient;
		this.publisher = publisher;
		this.artifactRepository = artifactRepository;
	}
	


	public void getArtifact(String transactionId, HttpServletResponse response) {
		TransferProcess transferProcess = getTransferProcessForTransactionId(transactionId);
		Artifact artifact = findArtifact(transferProcess);
		
		switch (artifact.getArtifactType()) {
		case FILE:
			getFile(artifact.getValue(), response);
			break;
		case EXTERNAL:
			getExternalData(artifact.getValue(), response);
			break;

		default:
			log.error("Wrong artifact type: {}", artifact.getArtifactType());
			throw new DownloadException("Error while downloading data", HttpStatus.INTERNAL_SERVER_ERROR);
		}
		publisher.publishEvent(new ArtifactConsumedEvent(transferProcess.getAgreementId()));
	}



	private Artifact findArtifact(TransferProcess transferProcess) {
		String response = okHttpRestClient.sendInternalRequest(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + transferProcess.getDatasetId() + "/artifact", HttpMethod.GET, null);
		
		GenericApiResponse<String> rr = Serializer.deserializePlain(response, GenericApiResponse.class);
		
		String artifactId = rr.getData();
		Artifact artifact = artifactRepository.findById(artifactId)
				.orElseThrow(() -> new DownloadException("No such data exists", HttpStatus.NOT_FOUND));
		return artifact;
	}

	private void getFile(String fileId, HttpServletResponse response) {
	 	GridFSBucket gridFSBucket = GridFSBuckets.create(mongoTemplate.getDb());
	 	ObjectId fileIdentifier = new ObjectId(fileId);
	 	Bson query = Filters.eq("_id", fileIdentifier);
	 	GridFSFile file = gridFSBucket.find(query).first();
        if (file != null) {
            GridFSDownloadStream gridFSDownloadStream = gridFSBucket.openDownloadStream(file.getObjectId());
            gridFsResource = new GridFsResource(file, gridFSDownloadStream);
        } else {
	        log.error("File not found in database");
			throw new DownloadException("Data not found", HttpStatus.NOT_FOUND);
        }
		
		try {
			IOUtils.copy(gridFsResource.getInputStream(), response.getOutputStream());
			response.setStatus(HttpStatus.OK.value());
			response.setContentType(gridFsResource.getContentType());
			response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=\"" + gridFsResource.getFilename() + "\"");
		} catch (IOException e) {
			log.error("Error while sending file", e);
			throw new DownloadException("Error while downloading data", HttpStatus.INTERNAL_SERVER_ERROR);
		}
    }
	
	private void getExternalData(String value, HttpServletResponse response) {
		GenericApiResponse<ExternalData> externalData = okHttpRestClient.downloadData(value, null);
		
		if (externalData.isSuccess()) {
			try {
				response.getOutputStream().write(externalData.getData().getData());
				response.setStatus(HttpStatus.OK.value());
				response.setContentType(externalData.getData().getContentType().type());
			} catch (IOException e) {
				log.error("Error while downloading external data", e);
				throw new DownloadException("Error while downloading data", HttpStatus.INTERNAL_SERVER_ERROR);
			}
		} else {
			log.error("Could not download external data: {}", externalData.getMessage());
			throw new DownloadException("Could not download external data", HttpStatus.INTERNAL_SERVER_ERROR);
		}
		
		
	}
	
	private TransferProcess getTransferProcessForTransactionId(String transactionId) {
		String[] tokens = new String(Base64.decodeBase64URLSafe(transactionId), Charset.forName("UTF-8")).split("\\|");
		if (tokens.length != 2) {
			log.error("Wrong transaction id");
			throw new DownloadException("Wrong transaction id", HttpStatus.BAD_REQUEST);
		}
		String consumerPid = tokens[0];
		String providerPid = tokens[1];
		return dataTransferService.findTransferProcess(consumerPid, providerPid);
	}
}
