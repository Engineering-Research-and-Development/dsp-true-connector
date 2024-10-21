package it.eng.datatransfer.service.api;

import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.codec.binary.Base64;
import org.bson.conversions.Bson;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.model.Filters;

import it.eng.datatransfer.exceptions.TransferProcessArtifactNotFoundException;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.serializer.Serializer;
import it.eng.datatransfer.service.DataTransferService;
import it.eng.tools.event.policyenforcement.ArtifactConsumedEvent;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RestArtifactService {

	private final ApplicationEventPublisher publisher;
	private final DataTransferService dataTransferService;
	private final MongoTemplate mongoTemplate;
	
	public RestArtifactService(ApplicationEventPublisher publisher, DataTransferService dataTransferService, MongoTemplate mongoTemplate) {
		super();
		this.publisher = publisher;
		this.dataTransferService = dataTransferService;
		this.mongoTemplate = mongoTemplate;
	}

	public String getArtifact(String transactionId, JsonNode jsonBody) {
		TransferProcess transferProcess = getTransferProcessForTransactionId(transactionId);
		log.info("Publishing event to increase counter for agreementId {}", transferProcess.getAgreementId());
		publisher.publishEvent(new ArtifactConsumedEvent(transferProcess.getAgreementId()));
		return getJohnDoe();
	}
	
	public GridFsResource streamAttachment(String transactionId) {
		TransferProcess transferProcess = getTransferProcessForTransactionId(transactionId);
		String fileId = transferProcess.getFileId();
		if(StringUtils.isBlank(fileId)) {
			log.error("NO file attached to dataset");
			throw new TransferProcessArtifactNotFoundException("Artifact not found for agreement " + transferProcess.getAgreementId(), 
					transferProcess.getConsumerPid(), transferProcess.getProviderPid());
		}
	 	GridFSBucket gridFSBucket = GridFSBuckets.create(mongoTemplate.getDb());
	 	Bson query = Filters.eq("_id", fileId);
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
	
	  // TODO change to get data from repository instead hardcoded
    private String getJohnDoe() {
    	DateFormat dateFormat = new SimpleDateFormat();
		Date date = new Date();
		String formattedDate = dateFormat.format(date);

		Map<String, String> jsonObject = new HashMap<>();
		jsonObject.put("firstName", "John");
		jsonObject.put("lastName", "Doe");
		jsonObject.put("dateOfBirth", formattedDate);
		jsonObject.put("address", "591  Franklin Street, Pennsylvania");
		jsonObject.put("checksum", "ABC123 " + formattedDate);
		return Serializer.serializePlain(jsonObject);
    }
}
