package it.eng.datatransfer.service.api;

import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

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
	
	public RestArtifactService(ApplicationEventPublisher publisher, DataTransferService dataTransferService) {
		super();
		this.publisher = publisher;
		this.dataTransferService = dataTransferService;
	}

	public String getArtifact(String transactionId, String artifactId, JsonNode jsonBody) {
		String[] tokens = new String(Base64.decodeBase64URLSafe(transactionId), Charset.forName("UTF-8")).split("\\|");
		String consumerPid = tokens[0];
		String providerPid = tokens[1];
		TransferProcess tp = dataTransferService.findTransferProcess(consumerPid, providerPid);
		log.info("Publishing event to increase counter for agreementId {}",tp.getAgreementId());
		publisher.publishEvent(new ArtifactConsumedEvent(tp.getAgreementId()));
		return getJohnDoe();
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
