package it.eng.datatransfer.service.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.model.*;
import it.eng.datatransfer.properties.DataTransferProperties;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.rest.protocol.DataTransferCallback;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.event.policyenforcement.ArtifactConsumedEvent;
import it.eng.tools.model.IConstants;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.serializer.ToolsSerializer;
import it.eng.tools.usagecontrol.UsageControlProperties;
import it.eng.tools.util.CredentialUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DataTransferAPIService {

	private final TransferProcessRepository transferProcessRepository;
	private final OkHttpRestClient okHttpRestClient;
	private final CredentialUtils credentialUtils;
	private final DataTransferProperties dataTransferProperties;
	private final ObjectMapper mapper = new ObjectMapper();
	private final UsageControlProperties usageControlProperties;
	private final ApplicationEventPublisher publisher;
	private final S3ClientService s3ClientService;
	private final S3Properties s3Properties;
	private final DataTransferStrategyFactory dataTransferStrategyFactory;

	public DataTransferAPIService(TransferProcessRepository transferProcessRepository,
                                  OkHttpRestClient okHttpRestClient,
                                  CredentialUtils credentialUtils,
                                  DataTransferProperties dataTransferProperties,
                                  UsageControlProperties usageControlProperties,
                                  ApplicationEventPublisher publisher,
                                  S3ClientService s3ClientService,
                                  S3Properties s3Properties, DataTransferStrategyFactory dataTransferStrategyFactory) {
		super();
		this.transferProcessRepository = transferProcessRepository;
		this.okHttpRestClient = okHttpRestClient;
		this.credentialUtils = credentialUtils;
		this.dataTransferProperties = dataTransferProperties;
		this.usageControlProperties = usageControlProperties;
		this.publisher = publisher;
        this.s3ClientService = s3ClientService;
        this.s3Properties = s3Properties;
        this.dataTransferStrategyFactory = dataTransferStrategyFactory;
    }

	/**
	 * Find dataTransfer based on criteria.<br>
	 * Find by transferProcessId, filter by state, filter by role or find all
	 * @param transferProcessId used for a single result
	 * @param state state of the transfer process (REQUESTED, STARTED, COMPLETED, SUSPENDED, TERMINATED)
	 * @param role role of the transfer process (CONSUMER, PROVIDER)
	 * @return List of JsonNode representations for data transfers
	 */
	public Collection<JsonNode> findDataTransfers(String transferProcessId, String state, String role) {
		  if(StringUtils.isNotBlank(transferProcessId)) {
		   return transferProcessRepository.findById(transferProcessId)
		     .stream()
		     .map(TransferSerializer::serializePlainJsonNode)
		     .collect(Collectors.toList());
		  } else if(StringUtils.isNotBlank(state)) {
		   return transferProcessRepository.findByStateAndRole(state, role)
		     .stream()
		     .map(TransferSerializer::serializePlainJsonNode)
		     .collect(Collectors.toList());
		  }
		  return transferProcessRepository.findByRole(role)
		    .stream()
		    .map(TransferSerializer::serializePlainJsonNode)
		    .collect(Collectors.toList());
	}

	/*###### CONSUMER #########*/

	/**
	 * Request transfer service method.<br>
	 * Check if state transition is OK; sends TransferRequestMessage to provider and based on response update state to REQUESTED.
	 * @param dataTransferRequest DataTransferRequest object containing transferProcessId, format and dataAddress
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

		TransferProcess transferProcessForDB;
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
						.created(transferProcessInitialized.getCreated())
						.createdBy(transferProcessInitialized.getCreatedBy())
						.modified(transferProcessInitialized.getModified())
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
				throw new DataTransferAPIException("Error occurred");
			}
		}
		return TransferSerializer.serializePlainJsonNode(transferProcessForDB);
	}

	/**
	 * Sends TransferStartMessage.
	 * Updates state for Transfer Process upon successful response to STARTED
	 * @param transferProcessId transfer process id
	 * @return JsonNode representation of DataTransfer
	 */
	public JsonNode startTransfer(String transferProcessId)  {
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
				// Generate a presigned URL for S3 with 7 days duration, which will be used as the endpoint for the data transfer
				String artifactURL = s3ClientService.generateGetPresignedUrl(s3Properties.getBucketName(), transferProcess.getDatasetId(), Duration.ofDays(7L));
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
				.created(transferProcess.getCreated())
				.createdBy(transferProcess.getCreatedBy())
				.modified(transferProcess.getModified())
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
	 * @param transferProcessId transfer process id
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
	 * @param transferProcessId transfer process id
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
	 * @param transferProcessId transfer process id
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
	 * store artifact in S3; update Transfer Process downloaded to true
	 * @param transferProcessId transfer process id
	 */
	public void downloadData(String transferProcessId) {
		TransferProcess transferProcess = findTransferProcessById(transferProcessId);

		if (!transferProcess.getState().equals(TransferState.STARTED)) {
			log.error("Download aborted, Transfer Process is not in STARTED state");
			throw new DataTransferAPIException("Download aborted, Transfer Process is not in STARTED state");
		}

		policyCheck(transferProcess);

		log.info("Starting download transfer process id - {} data...", transferProcessId);

        // Get appropriate strategy and execute transfer
        DataTransferStrategy strategy = dataTransferStrategyFactory.getStrategy(transferProcess.getFormat());

        strategy.transfer(transferProcess);

		TransferProcess transferProcessWithData = TransferProcess.Builder.newInstance()
				.id(transferProcess.getId())
				.agreementId(transferProcess.getAgreementId())
				.consumerPid(transferProcess.getConsumerPid())
				.providerPid(transferProcess.getProviderPid())
				.callbackAddress(transferProcess.getCallbackAddress())
	   			.dataAddress(transferProcess.getDataAddress())
	   			.isDownloaded(true)
				.dataId(transferProcessId)
				.format(transferProcess.getFormat())
				.state(transferProcess.getState())
				.role(transferProcess.getRole())
				.datasetId(transferProcess.getDatasetId())
				.created(transferProcess.getCreated())
				.createdBy(transferProcess.getCreatedBy())
				.modified(transferProcess.getModified())
				.lastModifiedBy(transferProcess.getLastModifiedBy())
				.version(transferProcess.getVersion())
				.build();

		transferProcessRepository.save(transferProcessWithData);

	}

	/**
	 * View locally stored artifact.<br>
	 * Only for TransferProcess.downloaded == true; enforce policy; read data from S3
	 *
	 * @param transferProcessId transfer process id
	 * @return String with presigned URL for the artifact in S3
	 */
	public String viewData(String transferProcessId) {
		TransferProcess transferProcess = findTransferProcessById(transferProcessId);

		if (!transferProcess.isDownloaded()) {
			log.error("Data not yet downloaded");
			throw new DataTransferAPIException("Data not yet downloaded");
		}

		policyCheck(transferProcess);

		// Check if file exists in S3
		if (!s3ClientService.fileExists(s3Properties.getBucketName(), transferProcessId)) {
			log.error("Data not found in S3");
			throw new DataTransferAPIException("Data not found in S3");
		}

		try {
			// Download file from S3
//			s3ClientService.downloadFile(s3Properties.getBucketName(), transferProcessId, response);
			String artifactURL = s3ClientService.generateGetPresignedUrl(s3Properties.getBucketName(), transferProcessId, Duration.ofDays(7L));
			publisher.publishEvent(new ArtifactConsumedEvent(transferProcess.getAgreementId()));
			return artifactURL;
		} catch (Exception e) {
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
			if(StringUtils.isBlank(response)) {
				log.error("Policy check error");
				throw new DataTransferAPIException("Policy check error");
			}
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
}
