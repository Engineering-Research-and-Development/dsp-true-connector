package it.eng.datatransfer.service.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.codec.binary.Base64;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import com.mongodb.client.model.Filters;

import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.model.DataAddress;
import it.eng.datatransfer.model.DataTransferRequest;
import it.eng.datatransfer.model.EndpointProperty;
import it.eng.datatransfer.model.TransferCompletionMessage;
import it.eng.datatransfer.model.TransferError;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferRequestMessage;
import it.eng.datatransfer.model.TransferStartMessage;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.model.TransferSuspensionMessage;
import it.eng.datatransfer.model.TransferTerminationMessage;
import it.eng.datatransfer.properties.DataTransferProperties;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.rest.protocol.DataTransferCallback;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.event.policyenforcement.ArtifactConsumedEvent;
import it.eng.tools.model.ExternalData;
import it.eng.tools.model.IConstants;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.serializer.ToolsSerializer;
import it.eng.tools.usagecontrol.UsageControlProperties;
import it.eng.tools.util.CredentialUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DataTransferAPIService {
	
	private static final String CONTENT_TYPE_FIELD = "_contentType";
	private static final String DATASET_ID_METADATA = "datasetId";
	private static final String ATTACHMENT_FILENAME = "attachment;filename=";

	private final TransferProcessRepository transferProcessRepository;
	private final OkHttpRestClient okHttpRestClient;
	private final CredentialUtils credentialUtils;
	private final DataTransferProperties dataTransferProperties;
	private final ObjectMapper mapper = new ObjectMapper();
	private final UsageControlProperties usageControlProperties;
	private final MongoTemplate mongoTemplate;
	private final ApplicationEventPublisher publisher;
	
	public DataTransferAPIService(TransferProcessRepository transferProcessRepository,
			OkHttpRestClient okHttpRestClient,
			CredentialUtils credentialUtils,
			DataTransferProperties dataTransferProperties, UsageControlProperties usageControlProperties, MongoTemplate mongoTemplate, ApplicationEventPublisher publisher) {
		super();
		this.transferProcessRepository = transferProcessRepository;
		this.okHttpRestClient = okHttpRestClient;
		this.credentialUtils = credentialUtils;
		this.dataTransferProperties = dataTransferProperties;
		this.usageControlProperties = usageControlProperties;
		this.mongoTemplate = mongoTemplate;
		this.publisher = publisher;
	}

	/**
	 * Find dataTransfer based on criteria.<br> 
	 * Find by transferProcessId, filter by state, filter by role or find all
	 * @param transferProcessId
	 * @param state
	 * @param role
	 * @return List of JsonNode representations for data transfers
	 */
	public Collection<JsonNode> findDataTransfers(String transferProcessId, String state, String role) {
		  if(StringUtils.isNotBlank(transferProcessId)) {
		   return transferProcessRepository.findById(transferProcessId)
		     .stream()
		     .map(dt -> TransferSerializer.serializePlainJsonNode(dt))
		     .collect(Collectors.toList());
		  } else if(StringUtils.isNotBlank(state)) {
		   return transferProcessRepository.findByStateAndRole(state, role)
		     .stream()
		     .map(dt -> TransferSerializer.serializePlainJsonNode(dt))
		     .collect(Collectors.toList());
		  }
		  return transferProcessRepository.findByRole(role)
		    .stream()
		    .map(dt -> TransferSerializer.serializePlainJsonNode(dt))
		    .collect(Collectors.toList());
	}

	/********* CONSUMER ***********/
	
	/**
	 * Request transfer service method.<br>
	 * Check if state transition is OK; sends TransferRequestMessage to provider and based on response update state to REQUESTED.
	 * @param dataTransferRequest
	 * @return JsonNode representation of DataTransfer (should be requested if all OK)
	 */
	public JsonNode requestTransfer(DataTransferRequest dataTransferRequest) {
		TransferProcess transferProcessInitialized = findTransferProcessById(dataTransferRequest.getTransferProcessId());
		
		stateTransitionCheck(TransferState.REQUESTED, transferProcessInitialized.getState());
		DataAddress dataAddressForMessage = null;
		if (StringUtils.isNotBlank(dataTransferRequest.getFormat()) && dataTransferRequest.getDataAddress() != null && !dataTransferRequest.getDataAddress().isEmpty()) {
			dataAddressForMessage = TransferSerializer.deserializePlain(dataTransferRequest.getDataAddress().toPrettyString(), DataAddress.class);
		}
		TransferRequestMessage transferRequestMessage = TransferRequestMessage.Builder.newInstance()
				.agreementId(transferProcessInitialized.getAgreementId())
				.callbackAddress(dataTransferProperties.consumerCallbackAddress())
				.consumerPid(transferProcessInitialized.getConsumerPid())
				.format(dataTransferRequest.getFormat())
				.dataAddress(dataAddressForMessage)
				.build();
		
		GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(
				DataTransferCallback.getConsumerDataTransferRequest(transferProcessInitialized.getCallbackAddress()), 
				TransferSerializer.serializeProtocolJsonNode(transferRequestMessage), 
				credentialUtils.getConnectorCredentials());
		log.info("Response received {}", response);
		TransferProcess transferProcessForDB = null;
		if (response.isSuccess()) {
			try {
				JsonNode jsonNode = mapper.readTree(response.getData());
				TransferProcess transferProcessFromResponse = TransferSerializer.deserializeProtocol(jsonNode, TransferProcess.class);
				
				transferProcessForDB = TransferProcess.Builder.newInstance()
						.id(transferProcessInitialized.getId())
						.agreementId(transferProcessInitialized.getAgreementId())
						.consumerPid(transferProcessInitialized.getConsumerPid())
						.providerPid(transferProcessFromResponse.getProviderPid())
						.format(dataTransferRequest.getFormat())
						.dataAddress(dataAddressForMessage)
						.isDownloaded(transferProcessInitialized.isDownloaded())
			   			.dataId(transferProcessInitialized.getDataId())
						.callbackAddress(transferProcessInitialized.getCallbackAddress())
						.role(IConstants.ROLE_CONSUMER)
						.state(transferProcessFromResponse.getState())
						.createdBy(transferProcessInitialized.getCreatedBy())
						.lastModifiedBy(transferProcessInitialized.getLastModifiedBy())
						.version(transferProcessInitialized.getVersion())
						// although not needed on consumer side it is added here to avoid duplicate id exception from mongodb
						.datasetId(transferProcessInitialized.getDatasetId())
						.build();
				
				transferProcessRepository.save(transferProcessForDB);
				log.info("Transfer process {} saved", transferProcessForDB.getId());
			} catch (JsonProcessingException e) {
				log.error("Transfer process from response not valid");
				throw new DataTransferAPIException(e.getLocalizedMessage(), e);
			}
		} else {
			log.info("Error response received!");
			log.error("Transfer process from response not valid");
			JsonNode jsonNode;
			try {
				jsonNode = mapper.readTree(response.getData());
				TransferError transferError = TransferSerializer.deserializeProtocol(jsonNode, TransferError.class);
				throw new DataTransferAPIException(transferError, "Error making request");
			} catch (JsonProcessingException ex) {
				throw new DataTransferAPIException("Error occured");
			}
		}
		return TransferSerializer.serializePlainJsonNode(transferProcessForDB);
	}

	/**
	 * Sends TransferStartMessage.
	 * Updates state for Transfer Process upon successful response to STARTED
	 * @param transferProcessId
	 * @return JsonNode representation of DataTransfer
	 * @throws UnsupportedEncodingException 
	 */
	public JsonNode startTransfer(String transferProcessId) throws UnsupportedEncodingException  {
		TransferProcess transferProcess = findTransferProcessById(transferProcessId);
		
		if (StringUtils.equals(IConstants.ROLE_CONSUMER, transferProcess.getRole()) && TransferState.REQUESTED.equals(transferProcess.getState())) {
			throw new DataTransferAPIException("State transition aborted, consumer can not transit from " + transferProcess.getState().name()
			+ " to " + TransferState.STARTED.name());
		}
		
		stateTransitionCheck(TransferState.STARTED, transferProcess.getState());
		
	   	log.info("Sending TransferStartMessage to {}", transferProcess.getCallbackAddress());
	   	String address = null;
	   	DataAddress dataAddress = null;
	   	
	   	if (StringUtils.equals(IConstants.ROLE_CONSUMER, transferProcess.getRole())) {
	   		address = DataTransferCallback.getProviderDataTransferStart(transferProcess.getCallbackAddress(), transferProcess.getProviderPid());
		}
	   	if (StringUtils.equals(IConstants.ROLE_PROVIDER, transferProcess.getRole())) {
	   		address = DataTransferCallback.getConsumerDataTransferStart(transferProcess.getCallbackAddress(), transferProcess.getConsumerPid());
	   		if (transferProcess.getDataAddress() == null) {
	   			String transactionId = Base64.encodeBase64URLSafeString((transferProcess.getConsumerPid() + "|" + transferProcess.getProviderPid()).getBytes("UTF-8"));
	   			String artifactURL = DataTransferCallback.getValidCallback(dataTransferProperties.providerCallbackAddress()) + "/artifacts/" + transactionId;
	   			
	   			EndpointProperty endpointProperty = EndpointProperty.Builder.newInstance()
	   					.name("https://w3id.org/edc/v0.0.1/ns/endpoint")
	   					.value(artifactURL)
	   					.build();
	   			EndpointProperty endpointTypeProperty = EndpointProperty.Builder.newInstance()
	   					.name("https://w3id.org/edc/v0.0.1/ns/endpointType")
	   					.value("https://w3id.org/idsa/v4.1/HTTP")
	   					.build();
				dataAddress = DataAddress.Builder.newInstance()
						.endpoint(artifactURL)
						.endpointProperties(List.of(endpointProperty, endpointTypeProperty))
						.endpointType("https://w3id.org/idsa/v4.1/HTTP")
						.build();
			}
		}
	   	
	   	TransferStartMessage transferStartMessage = TransferStartMessage.Builder.newInstance()
	   			.consumerPid(transferProcess.getConsumerPid())
	   			.providerPid(transferProcess.getProviderPid())
	   			.dataAddress(transferProcess.getDataAddress() == null ? dataAddress : transferProcess.getDataAddress())
	   			.build();
	   	
		GenericApiResponse<String> response = okHttpRestClient
				.sendRequestProtocol(address,
				TransferSerializer.serializeProtocolJsonNode(transferStartMessage),
				credentialUtils.getConnectorCredentials());
		log.info("Response received {}", response);
		if (response.isSuccess()) {
			TransferProcess transferProcessStarted = TransferProcess.Builder.newInstance()
			.id(transferProcess.getId())
			.agreementId(transferProcess.getAgreementId())
			.consumerPid(transferProcess.getConsumerPid())
			.providerPid(transferProcess.getProviderPid())
			.callbackAddress(transferProcess.getCallbackAddress())
   			.dataAddress(transferStartMessage.getDataAddress())
   			.isDownloaded(transferProcess.isDownloaded())
   			.dataId(transferProcess.getDataId())
			.format(transferProcess.getFormat())
			.state(TransferState.STARTED)
			.role(transferProcess.getRole())
			.datasetId(transferProcess.getDatasetId())
			.createdBy(transferProcess.getCreatedBy())
			.lastModifiedBy(transferProcess.getLastModifiedBy())
			.version(transferProcess.getVersion())
			.build();
			transferProcessRepository.save(transferProcessStarted);
			log.info("Transfer process {} saved", transferProcessStarted.getId());
			return TransferSerializer.serializePlainJsonNode(transferProcessStarted);
		} else {
			log.error("Error response received!");
			throw new DataTransferAPIException(response.getMessage());
		}
	}
	
	/**
	 * Sends TransferCompletionMessage.<br>
	 * Updates state for Transfer Process upon successful response to COMPLETED
	 * @param transferProcessId
	 * @return JsonNode representation of DataTransfer
	 */
	public JsonNode completeTransfer(String transferProcessId) {
		TransferProcess transferProcess = findTransferProcessById(transferProcessId);
		
		stateTransitionCheck(TransferState.COMPLETED, transferProcess.getState());
		
		TransferCompletionMessage transferCompletionMessage = TransferCompletionMessage.Builder.newInstance()
				.consumerPid(transferProcess.getConsumerPid())
				.providerPid(transferProcess.getProviderPid())
				.build();
		
	   	log.info("Sending TransferCompletionMessage to {}", transferProcess.getCallbackAddress());
	   	String address = null;
	   	
	   	if (StringUtils.equals(IConstants.ROLE_CONSUMER, transferProcess.getRole())) {
	   		address = DataTransferCallback.getProviderDataTransferCompletion(transferProcess.getCallbackAddress(), transferProcess.getProviderPid());
		}
	   	if (StringUtils.equals(IConstants.ROLE_PROVIDER, transferProcess.getRole())) {
	   		address = DataTransferCallback.getConsumerDataTransferCompletion(transferProcess.getCallbackAddress(), transferProcess.getConsumerPid());
		}
		GenericApiResponse<String> response = okHttpRestClient
				.sendRequestProtocol(address,
				TransferSerializer.serializeProtocolJsonNode(transferCompletionMessage),
				credentialUtils.getConnectorCredentials());
		log.info("Response received {}", response);
		if (response.isSuccess()) {
			TransferProcess transferProcessStarted = transferProcess.copyWithNewTransferState(TransferState.COMPLETED);
			transferProcessRepository.save(transferProcessStarted);
			log.info("Transfer process {} saved", transferProcessStarted.getId());
			return TransferSerializer.serializePlainJsonNode(transferProcessStarted);
		} else {
			log.error("Error response received!");
			throw new DataTransferAPIException(response.getMessage());
		}
	}
	
	/**
	 * Sends TransferSuspensionMessage.<br>
	 * Updates state for Transfer Process upon successful response to COMPLETED
	 * @param transferProcessId
	 * @return JsonNode representation of DataTransfer
	 */
	public JsonNode suspendTransfer(String transferProcessId) {
		TransferProcess transferProcess = findTransferProcessById(transferProcessId);
		
		stateTransitionCheck(TransferState.SUSPENDED, transferProcess.getState());
		
		TransferSuspensionMessage transferSuspensionMessage = TransferSuspensionMessage.Builder.newInstance()
				.consumerPid(transferProcess.getConsumerPid())
				.providerPid(transferProcess.getProviderPid())
				//TODO which code to add
				.code("200")
				.reason(List.of("Data transfer suspended"))
				.build();
		
	   	log.info("Sending TransferSuspensionMessage to {}", transferProcess.getCallbackAddress());
	   	String address = null;
	   	
	   	if (StringUtils.equals(IConstants.ROLE_CONSUMER, transferProcess.getRole())) {
	   		address = DataTransferCallback.getProviderDataTransferSuspension(transferProcess.getCallbackAddress(), transferProcess.getProviderPid());
		}
	   	if (StringUtils.equals(IConstants.ROLE_PROVIDER, transferProcess.getRole())) {
	   		address = DataTransferCallback.getConsumerDataTransferSuspension(transferProcess.getCallbackAddress(), transferProcess.getConsumerPid());
		}
		GenericApiResponse<String> response = okHttpRestClient
				.sendRequestProtocol(address,
				TransferSerializer.serializeProtocolJsonNode(transferSuspensionMessage),
				credentialUtils.getConnectorCredentials());
		log.info("Response received {}", response);
		if (response.isSuccess()) {
			TransferProcess transferProcessStarted = transferProcess.copyWithNewTransferState(TransferState.SUSPENDED);
			transferProcessRepository.save(transferProcessStarted);
			log.info("Transfer process {} saved", transferProcessStarted.getId());
			return TransferSerializer.serializePlainJsonNode(transferProcessStarted);
		} else {
			log.error("Error response received!");
			throw new DataTransferAPIException(response.getMessage());
		}
	}
	
	/**
	 * Sends TransferTerminationMessage.<br>
	 * Updates state for Transfer Process upon successful response to TERMINATED
	 * @param transferProcessId
	 * @return JsonNode representation of DataTransfer
	 */
	public JsonNode terminateTransfer(String transferProcessId) {
		TransferProcess transferProcess = findTransferProcessById(transferProcessId);
		
		stateTransitionCheck(TransferState.TERMINATED, transferProcess.getState());
		
		TransferTerminationMessage transferTerminationMessage  = TransferTerminationMessage.Builder.newInstance()
				.consumerPid(transferProcess.getConsumerPid())
				.providerPid(transferProcess.getProviderPid())
				//TODO which code to add
				.code("200")
				.reason(List.of("Data transfer terminated"))
				.build();
		
	   	log.info("Sending TransferTerminationMessage to {}", transferProcess.getCallbackAddress());
	   	String address = null;
	   	
	   	if (StringUtils.equals(IConstants.ROLE_CONSUMER, transferProcess.getRole())) {
	   		address = DataTransferCallback.getProviderDataTransferTermination(transferProcess.getCallbackAddress(), transferProcess.getProviderPid());
		}
	   	if (StringUtils.equals(IConstants.ROLE_PROVIDER, transferProcess.getRole())) {
	   		address = DataTransferCallback.getConsumerDataTransferTermination(transferProcess.getCallbackAddress(), transferProcess.getConsumerPid());
		}
		GenericApiResponse<String> response = okHttpRestClient
				.sendRequestProtocol(address,
				TransferSerializer.serializeProtocolJsonNode(transferTerminationMessage),
				credentialUtils.getConnectorCredentials());
		log.info("Response received {}", response);
		if (response.isSuccess()) {
			TransferProcess transferProcessStarted = transferProcess.copyWithNewTransferState(TransferState.TERMINATED);
			transferProcessRepository.save(transferProcessStarted);
			log.info("Transfer process {} saved", transferProcessStarted.getId());
			return TransferSerializer.serializePlainJsonNode(transferProcessStarted);
		} else {
			log.error("Error response received!");
			throw new DataTransferAPIException(response.getMessage());
		}
	}
	
	/**
	 * Download data.<br>
	 * Checks if TransferProcess state is STARTED; enforce policy (validate agreement); download data from provider;
	 * store artifact in local Mongo; update Transfer Process downloaded to true
	 * @param transferProcessId
	 */
	public void downloadData(String transferProcessId) {
		TransferProcess transferProcess = findTransferProcessById(transferProcessId);
		
		if (!transferProcess.getState().equals(TransferState.STARTED)) {
			log.error("Download aborted, Transfer Process is not in STARTED state");
			throw new DataTransferAPIException("Download aborted, Transfer Process is not in STARTED state");
		}
		
		policyCheck(transferProcess);
		
		log.info("Starting download transfer process id - {} data...", transferProcessId);
		
		// get authorization information from Data Address if present
		String authorization = null;
		if (transferProcess.getDataAddress().getEndpointProperties() != null) {
			List<EndpointProperty> properties = transferProcess.getDataAddress().getEndpointProperties();
			String authType = properties.stream().filter(prop -> StringUtils.equals(prop.getName(), IConstants.AUTH_TYPE))
					.findFirst().map(prop -> prop.getValue()).orElse(null);
			String token = properties.stream()
					.filter(prop -> StringUtils.equals(prop.getName(), IConstants.AUTHORIZATION)).findFirst()
					.map(prop -> prop.getValue()).orElse(null);
			
			authorization = authType + " " + token;
		}
		
		GenericApiResponse<ExternalData> response = okHttpRestClient.downloadData(transferProcess.getDataAddress().getEndpoint(), 
				authorization);
		
		if (!response.isSuccess()) {
			log.error("Download aborted, {}", response.getMessage());
			throw new DataTransferAPIException("Download aborted, " + response.getMessage());
		}
		
		log.info("Downloaded transfer process id - {} data!", transferProcessId);
		
		log.info("Storing transfer process id - {} data...", transferProcessId);
		ObjectId dataId = storeFile(response.getData(), transferProcess);
		log.info("Stored transfer process id - {} data!", transferProcessId);
		
		TransferProcess transferProcessWithData = TransferProcess.Builder.newInstance()
				.id(transferProcess.getId())
				.agreementId(transferProcess.getAgreementId())
				.consumerPid(transferProcess.getConsumerPid())
				.providerPid(transferProcess.getProviderPid())
				.callbackAddress(transferProcess.getCallbackAddress())
	   			.dataAddress(transferProcess.getDataAddress())
	   			.isDownloaded(true)
	   			.dataId(dataId.toHexString())
				.format(transferProcess.getFormat())
				.state(transferProcess.getState())
				.role(transferProcess.getRole())
				.datasetId(transferProcess.getDatasetId())
				.createdBy(transferProcess.getCreatedBy())
				.lastModifiedBy(transferProcess.getLastModifiedBy())
				.version(transferProcess.getVersion())
				.build();
		
		transferProcessRepository.save(transferProcessWithData);
		
	}

	/**
	 * View locally stored artifact.<br>
	 * Only for TransferProcess.downloaded == true; enforce policy; read data from Mongo; set data into HttpServletResponse
	 * @param transferProcessId
	 * @param response
	 */
	public void viewData(String transferProcessId, HttpServletResponse response) {
		TransferProcess transferProcess = findTransferProcessById(transferProcessId);
		
		if (!transferProcess.isDownloaded()) {
			log.error("Data not yet downloaded");
			throw new DataTransferAPIException("Data not yet downloaded");
		}
		
		policyCheck(transferProcess);
		
	 	GridFSBucket gridFSBucket = GridFSBuckets.create(mongoTemplate.getDb());
	 	ObjectId fileIdentifier = new ObjectId(transferProcess.getDataId());
	 	Bson query = Filters.eq("_id", fileIdentifier);
	 	GridFSFile file = gridFSBucket.find(query).first();
	 	GridFsResource gridFsResource = null;
        if (file != null) {
            GridFSDownloadStream gridFSDownloadStream = gridFSBucket.openDownloadStream(file.getObjectId());
            gridFsResource = new GridFsResource(file, gridFSDownloadStream);
        } else {
	        log.error("Data not found in database");
			throw new DataTransferAPIException("Data not found in database");
        }
		
		try {
			response.setStatus(HttpStatus.OK.value());
			response.setContentType(gridFsResource.getContentType());
			response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME + gridFsResource.getFilename());
			IOUtils.copyLarge(gridFsResource.getInputStream(), response.getOutputStream());
			response.flushBuffer();
			publisher.publishEvent(new ArtifactConsumedEvent(transferProcess.getAgreementId()));
		} catch (IOException e) {
			log.error("Error while accessing data", e);
			throw new DataTransferAPIException("Error while accessing data" + e.getLocalizedMessage());
		}
		
	}

	private TransferProcess findTransferProcessById (String transferProcessId) {
    	return transferProcessRepository.findById(transferProcessId)
    	        .orElseThrow(() ->
                new DataTransferAPIException("Transfer process with id " + transferProcessId + " not found"));
    }
	
	private void stateTransitionCheck (TransferState newState, TransferState currentState) {
		if (!currentState.canTransitTo(newState)) {
			throw new DataTransferAPIException("State transition aborted, " + currentState.name()
					+ " state can not transition to " + newState.name());
		}
	}

	private void policyCheck(TransferProcess transferProcess) {
		if(usageControlProperties.usageControlEnabled()) {
			String agreementId = transferProcess.getAgreementId();
			String response = okHttpRestClient.sendInternalRequest(ApiEndpoints.NEGOTIATION_AGREEMENTS_V1 + "/" + agreementId + "/enforce", 
					HttpMethod.POST, 
					null);
			TypeReference<GenericApiResponse<String>> typeRef = new TypeReference<GenericApiResponse<String>>() {};
			GenericApiResponse<String> internalResponse = ToolsSerializer.deserializePlain(response, typeRef);
			if (!internalResponse.isSuccess()) {
				log.error("Download aborted, Policy is not valid anymore");
				throw new DataTransferAPIException("Download aborted, Policy is not valid anymore");
			}
		} else {
			log.warn("!!!!! UsageControl DISABLED - will not check if policy is present or valid !!!!!");
		}
	}

	private ObjectId storeFile(ExternalData externalData, TransferProcess transferProcess) {
		try {
			GridFSBucket gridFSBucket = GridFSBuckets.create(mongoTemplate.getDb());
			Document doc = new Document();
			//TODO check what happens if file.getContentType() is null
			doc.append(CONTENT_TYPE_FIELD, externalData.getContentType().toString());
			doc.append(DATASET_ID_METADATA, transferProcess.getDatasetId());
			GridFSUploadOptions options = new GridFSUploadOptions()
			        .chunkSizeBytes(1048576) // 1MB chunk size
			        .metadata(doc);
			// ContentDisposition comes in format "attachment;filename=file.txt", one the line below we are extracting the file name
			String fileName = externalData.getContentDisposition().substring(ATTACHMENT_FILENAME.length());
			ObjectId newDataId = gridFSBucket.uploadFromStream(fileName, new ByteArrayInputStream(externalData.getData()), options);
			if (StringUtils.isNotBlank(transferProcess.getDataId())) {
				ObjectId objectId = new ObjectId(transferProcess.getDataId());
				gridFSBucket.delete(objectId);
			}
			return newDataId;
		}
		catch (Exception e) {
			log.error("Error while storing file", e);
			throw new DataTransferAPIException("Failed to store file. " + e.getLocalizedMessage());
		}
	}
}
