package it.eng.datatransfer.service.api;

import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
import it.eng.tools.model.IConstants;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.util.CredentialUtils;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DataTransferAPIService {

	private final TransferProcessRepository transferProcessRepository;
	private final OkHttpRestClient okHttpRestClient;
	private final CredentialUtils credentialUtils;
	private final DataTransferProperties dataTransferProperties;
	private final ObjectMapper mapper = new ObjectMapper();
	
	public DataTransferAPIService(TransferProcessRepository transferProcessRepository,
			OkHttpRestClient okHttpRestClient,
			CredentialUtils credentialUtils,
			DataTransferProperties dataTransferProperties) {
		super();
		this.transferProcessRepository = transferProcessRepository;
		this.okHttpRestClient = okHttpRestClient;
		this.credentialUtils = credentialUtils;
		this.dataTransferProperties = dataTransferProperties;
	}

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
	
	public JsonNode requestTransfer(DataTransferRequest dataTransferRequest) {
		TransferProcess transferProcess = findTransferProcessById(dataTransferRequest.getTransferProcessId());
		
		stateTransitionCheck(TransferState.REQUESTED, transferProcess.getState());
		DataAddress dataAddressForMessage = null;
		if (StringUtils.isNotBlank(dataTransferRequest.getFormat()) && dataTransferRequest.getDataAddress() != null && !dataTransferRequest.getDataAddress().isEmpty()) {
			dataAddressForMessage = TransferSerializer.deserializePlain(dataTransferRequest.getDataAddress().toPrettyString(), DataAddress.class);
		}
		TransferRequestMessage transferRequestMessage = TransferRequestMessage.Builder.newInstance()
				.agreementId(transferProcess.getAgreementId())
				.callbackAddress(dataTransferProperties.consumerCallbackAddress())
				.consumerPid("urn:uuid:" + UUID.randomUUID())
				.format(dataTransferRequest.getFormat())
				.dataAddress(dataAddressForMessage)
				.build();
		
		GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(
				DataTransferCallback.getConsumerDataTransferRequest(transferProcess.getCallbackAddress()), 
				TransferSerializer.serializeProtocolJsonNode(transferRequestMessage), 
				credentialUtils.getConnectorCredentials());
		log.info("Response received {}", response);
		TransferProcess transferProcessForDB = null;
		if (response.isSuccess()) {
			try {
				JsonNode jsonNode = mapper.readTree(response.getData());
				TransferProcess transferProcessFromResponse = TransferSerializer.deserializeProtocol(jsonNode, TransferProcess.class);
				
				transferProcessForDB = TransferProcess.Builder.newInstance()
						.id(transferProcess.getId())
						.agreementId(transferProcess.getAgreementId())
						.consumerPid(transferProcessFromResponse.getConsumerPid())
						.providerPid(transferProcessFromResponse.getProviderPid())
						.format(dataTransferRequest.getFormat())
						.dataAddress(dataAddressForMessage)
						.callbackAddress(transferProcess.getCallbackAddress())
						.role(IConstants.ROLE_CONSUMER)
						.state(transferProcessFromResponse.getState())
						.createdBy(transferProcess.getCreatedBy())
						.lastModifiedBy(transferProcess.getLastModifiedBy())
						.version(transferProcess.getVersion())
						// although not needed on consumer side it is added here to avoid duplicate id exception from mongodb
						.datasetId(transferProcess.getDatasetId())
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
	 * Sends TransferStartMessage and updates state for 
	 * Transfer Process upon successful response to STARTED
	 * @param transferProcessId
	 * @return
	 * @throws UnsupportedEncodingException 
	 */
	public JsonNode startTransfer(String transferProcessId) throws UnsupportedEncodingException {
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
	   					.value(address)
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
	 * Sends TransferCompletionMessage and updates state for 
	 * Transfer Process upon successful response to COMPLETED
	 * @param transferProcessId
	 * @return
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
	 * Sends TransferSuspensionMessage and updates state for 
	 * Transfer Process upon successful response to COMPLETED
	 * @param transferProcessId
	 * @return
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
	 * Sends TransferTerminationMessage and updates state for 
	 * Transfer Process upon successful response to TERMINATED
	 * @param transferProcessId
	 * @return
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
	
	private void stateTransitionCheck (TransferState newState, TransferState currentState) {
		if (!currentState.canTransitTo(newState)) {
			throw new DataTransferAPIException("State transition aborted, " + currentState.name()
					+ " state can not transition to " + newState.name());
		}
	}
    
	private TransferProcess findTransferProcessById (String transferProcessId) {
    	return transferProcessRepository.findById(transferProcessId)
    	        .orElseThrow(() ->
                new DataTransferAPIException("Transfer process with id " + transferProcessId + " not found"));
    }
}
