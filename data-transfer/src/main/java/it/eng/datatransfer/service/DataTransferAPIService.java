package it.eng.datatransfer.service;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.model.DataAddress;
import it.eng.datatransfer.model.TransferCompletionMessage;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferRequestMessage;
import it.eng.datatransfer.model.TransferStartMessage;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.model.TransferSuspensionMessage;
import it.eng.datatransfer.model.TransferTerminationMessage;
import it.eng.datatransfer.properties.DataTransferProperties;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.rest.protocol.DataTransferCallback;
import it.eng.datatransfer.serializer.Serializer;
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

	public Collection<JsonNode> findDataTransfers(String transferProcessId, String state) {
		if(StringUtils.isNotBlank(transferProcessId)) {
			return transferProcessRepository.findById(transferProcessId)
					.stream()
					.map(dt -> Serializer.serializePlainJsonNode(dt))
					.collect(Collectors.toList());
		} else if(StringUtils.isNoneBlank(state)) {
			return transferProcessRepository.findByState(state)
					.stream()
					.map(dt -> Serializer.serializePlainJsonNode(dt))
					.collect(Collectors.toList());
		}
		return transferProcessRepository.findAll().stream().map(dt -> Serializer.serializePlainJsonNode(dt))
				.collect(Collectors.toList());
		}
	
	/********* CONSUMER ***********/
	
	public JsonNode requestTransfer(String targetConnector, String agreementId, String format, JsonNode dataAddress) {
		DataAddress dataAddressForMessage = null;
		if (StringUtils.isNotBlank(format)) {
			dataAddressForMessage = Serializer.deserializePlain(dataAddress.toPrettyString(), DataAddress.class);
		}
		TransferRequestMessage transferRequestMessage = TransferRequestMessage.Builder.newInstance()
				.agreementId(agreementId)
				.callbackAddress(dataTransferProperties.consumerCallbackAddress())
				.consumerPid("urn:uuid:" + UUID.randomUUID())
				//TODO add format and data address when PUSH format is supported
				.format(null)
				.dataAddress(null)
				.build();
		
		GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(DataTransferCallback.getConsumerDataTransferRequest(targetConnector), 
				Serializer.serializeProtocolJsonNode(transferRequestMessage), credentialUtils.getConnectorCredentials());
		log.info("Response received {}", response);
		TransferProcess transferProcessForDB = null;
		if (response.isSuccess()) {
			try {
				JsonNode jsonNode = mapper.readTree(response.getData());
				TransferProcess transferProcess = Serializer.deserializeProtocol(jsonNode, TransferProcess.class);
				
				transferProcessForDB = TransferProcess.Builder.newInstance()
						.id(transferProcess.getId())
						.agreementId(agreementId)
						.consumerPid(transferProcess.getConsumerPid())
						.providerPid(transferProcess.getProviderPid())
						.format(format)
						.dataAddress(dataAddressForMessage)
						.callbackAddress(targetConnector)
						.role(IConstants.ROLE_CONSUMER)
						.state(transferProcess.getState())
						.build();
				
				transferProcessRepository.save(transferProcessForDB);
				log.info("Transfer process {} saved", transferProcessForDB.getId());
			} catch (JsonProcessingException e) {
				log.error("Transfer process from response not valid");
				throw new DataTransferAPIException(e.getLocalizedMessage(), e);
			}
		} else {
			log.info("Error response received!");
			throw new DataTransferAPIException(response.getMessage());
		}
		return Serializer.serializePlainJsonNode(transferProcessForDB);
	}

	/**
	 * Sends TransferStartMessage and updates state for 
	 * Transfer Process upon successful response to STARTED
	 * @param transferProcessId
	 * @return
	 */
	public JsonNode startTransfer(String transferProcessId) {
		TransferProcess transferProcess = findTransferProcessById(transferProcessId);
		
		stateTransitionCheck(TransferState.STARTED, transferProcess.getState());
		
		TransferStartMessage transferStartMessage = TransferStartMessage.Builder.newInstance()
				.consumerPid(transferProcess.getConsumerPid())
				.providerPid(transferProcess.getProviderPid())
				.dataAddress(transferProcess.getDataAddress())
				.build();
		
	   	log.info("Sending TransferStartMessage to {}", transferProcess.getCallbackAddress());
	   	String address = null;
	   	
	   	if (StringUtils.equals(IConstants.ROLE_CONSUMER, transferProcess.getRole())) {
	   		address = DataTransferCallback.getProviderDataTransferStart(transferProcess.getCallbackAddress(), transferProcess.getProviderPid());
		}
	   	if (StringUtils.equals(IConstants.ROLE_PROVIDER, transferProcess.getRole())) {
	   		address = DataTransferCallback.getConsumerDataTransferStart(transferProcess.getCallbackAddress(), transferProcess.getConsumerPid());
		}
	   	
		GenericApiResponse<String> response = okHttpRestClient
				.sendRequestProtocol(address,
				Serializer.serializeProtocolJsonNode(transferStartMessage),
				credentialUtils.getConnectorCredentials());
		log.info("Response received {}", response);
		if (response.isSuccess()) {
			TransferProcess transferProcessStarted = transferProcess.copyWithNewTransferState(TransferState.STARTED);
			transferProcessRepository.save(transferProcessStarted);
			log.info("Transfer process {} saved", transferProcessStarted.getId());
			return Serializer.serializePlainJsonNode(transferProcessStarted);
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
				Serializer.serializeProtocolJsonNode(transferCompletionMessage),
				credentialUtils.getConnectorCredentials());
		log.info("Response received {}", response);
		if (response.isSuccess()) {
			TransferProcess transferProcessStarted = transferProcess.copyWithNewTransferState(TransferState.COMPLETED);
			transferProcessRepository.save(transferProcessStarted);
			log.info("Transfer process {} saved", transferProcessStarted.getId());
			return Serializer.serializePlainJsonNode(transferProcessStarted);
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
				Serializer.serializeProtocolJsonNode(transferSuspensionMessage),
				credentialUtils.getConnectorCredentials());
		log.info("Response received {}", response);
		if (response.isSuccess()) {
			TransferProcess transferProcessStarted = transferProcess.copyWithNewTransferState(TransferState.SUSPENDED);
			transferProcessRepository.save(transferProcessStarted);
			log.info("Transfer process {} saved", transferProcessStarted.getId());
			return Serializer.serializePlainJsonNode(transferProcessStarted);
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
				Serializer.serializeProtocolJsonNode(transferTerminationMessage),
				credentialUtils.getConnectorCredentials());
		log.info("Response received {}", response);
		if (response.isSuccess()) {
			TransferProcess transferProcessStarted = transferProcess.copyWithNewTransferState(TransferState.TERMINATED);
			transferProcessRepository.save(transferProcessStarted);
			log.info("Transfer process {} saved", transferProcessStarted.getId());
			return Serializer.serializePlainJsonNode(transferProcessStarted);
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
