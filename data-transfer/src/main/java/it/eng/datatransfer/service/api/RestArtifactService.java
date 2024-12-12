package it.eng.datatransfer.service.api;

import java.nio.charset.Charset;

import org.apache.tomcat.util.codec.binary.Base64;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.gridfs.GridFsResource;
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
import it.eng.tools.repository.ArtifactRepository;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;

@Service
@Slf4j
public class RestArtifactService {

	private final DataTransferService dataTransferService;
	private final MongoTemplate mongoTemplate;
	private final OkHttpRestClient okHttpRestClient;
	private final ApplicationEventPublisher publisher;
	private final ArtifactRepository artifactRepository;
	
	public RestArtifactService(DataTransferService dataTransferService, MongoTemplate mongoTemplate,
			OkHttpRestClient okHttpRestClient, ApplicationEventPublisher publisher, ArtifactRepository artifactRepository) {
		super();
		this.dataTransferService = dataTransferService;
		this.mongoTemplate = mongoTemplate;
		this.okHttpRestClient = okHttpRestClient;
		this.publisher = publisher;
		this.artifactRepository = artifactRepository;
	}
	


	public Artifact getArtifact(String transactionId) {
		TransferProcess transferProcess = getTransferProcessForTransactionId(transactionId);
		String response = okHttpRestClient.sendInternalRequest(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + transferProcess.getDatasetId() + "/artifactId", HttpMethod.GET, null);
		
		GenericApiResponse<String> rr = Serializer.deserializePlain(response, GenericApiResponse.class);
		
		String artifactId = rr.getData();
		return artifactRepository.findById(artifactId)
				.orElseThrow(() -> new DownloadException("No such data exists", HttpStatus.NOT_FOUND));
	}

	public GridFsResource streamAttachment(String fileId) {
	 	GridFSBucket gridFSBucket = GridFSBuckets.create(mongoTemplate.getDb());
	 	ObjectId fileIdentifier = new ObjectId(fileId);
	 	// probaj da sklonis
	 	Bson query = Filters.eq("_id", fileIdentifier);
	 	GridFSFile file = gridFSBucket.find(query).first();
        if (file != null) {
            GridFSDownloadStream gridFSDownloadStream = gridFSBucket.openDownloadStream(file.getObjectId());
            GridFsResource gridFsResource = new GridFsResource(file, gridFSDownloadStream);
            return gridFsResource;
        }
        log.error("File not found in database");
		throw new DownloadException("Data not found", HttpStatus.NOT_FOUND);
    }
	
	public Response getExternalData(String value) {
		GenericApiResponse<Response> response = okHttpRestClient.downloadData(value, null);
		
		if (response.isSuccess()) {
			return response.getData();
		} else {
			log.error("Could not download external data: {}", response.getMessage());
			throw new DownloadException("Could not download external data", HttpStatus.INTERNAL_SERVER_ERROR);
		}
		
		
	}
	
	public void publishArtifactConsumedEvent(String transactionId) {
		TransferProcess transferProcess = getTransferProcessForTransactionId(transactionId);
		publisher.publishEvent(new ArtifactConsumedEvent(transferProcess.getAgreementId()));
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
