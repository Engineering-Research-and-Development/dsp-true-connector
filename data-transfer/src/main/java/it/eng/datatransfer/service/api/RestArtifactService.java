package it.eng.datatransfer.service.api;

import java.nio.charset.Charset;

import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.codec.binary.Base64;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.model.Filters;

import it.eng.datatransfer.exceptions.TransferProcessArtifactNotFoundException;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.serializer.Serializer;
import it.eng.datatransfer.service.DataTransferService;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.event.policyenforcement.ArtifactConsumedEvent;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RestArtifactService {

	private final DataTransferService dataTransferService;
	private final MongoTemplate mongoTemplate;
	private final OkHttpRestClient okHttpRestClient;
	private final ApplicationEventPublisher publisher;
	
	public RestArtifactService(DataTransferService dataTransferService, MongoTemplate mongoTemplate,
			OkHttpRestClient okHttpRestClient, ApplicationEventPublisher publisher) {
		super();
		this.dataTransferService = dataTransferService;
		this.mongoTemplate = mongoTemplate;
		this.okHttpRestClient = okHttpRestClient;
		this.publisher = publisher;
	}

	public GridFsResource streamAttachment(String transactionId) {
		TransferProcess transferProcess = getTransferProcessForTransactionId(transactionId);
		String response = okHttpRestClient.sendInternalRequest(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + transferProcess.getDatasetId() + "/fileid", HttpMethod.GET, null);
		
		GenericApiResponse<String> rr = Serializer.deserializePlain(response, GenericApiResponse.class);
		
		String fileId = rr.getData();
		if(StringUtils.isBlank(fileId)) {
			log.error("NO file attached to dataset");
			throw new TransferProcessArtifactNotFoundException("Artifact not found for agreement " + transferProcess.getAgreementId(), 
					transferProcess.getConsumerPid(), transferProcess.getProviderPid());
		}
	 	GridFSBucket gridFSBucket = GridFSBuckets.create(mongoTemplate.getDb());
	 	ObjectId fileIdentifier = new ObjectId(fileId);
	 	Bson query = Filters.eq("_id", fileIdentifier);
	 	GridFSFile file = gridFSBucket.find(query).first();
        if (file != null) {
            GridFSDownloadStream gridFSDownloadStream = gridFSBucket.openDownloadStream(file.getObjectId());
            GridFsResource gridFsResource = new GridFsResource(file, gridFSDownloadStream);
    		publisher.publishEvent(new ArtifactConsumedEvent(transferProcess.getAgreementId()));
            return gridFsResource;
        }
        return null;
    }

	private TransferProcess getTransferProcessForTransactionId(String transactionId) {
		String[] tokens = new String(Base64.decodeBase64URLSafe(transactionId), Charset.forName("UTF-8")).split("\\|");
		String consumerPid = tokens[0];
		String providerPid = tokens[1];
		return dataTransferService.findTransferProcess(consumerPid, providerPid);
	}
	
}
