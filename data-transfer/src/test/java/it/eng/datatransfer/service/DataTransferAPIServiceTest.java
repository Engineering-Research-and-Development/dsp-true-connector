package it.eng.datatransfer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.model.DataAddress;
import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.model.DataTransferRequest;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.properties.DataTransferProperties;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.serializer.Serializer;
import it.eng.datatransfer.util.MockObjectUtil;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.model.IConstants;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.util.CredentialUtils;

@ExtendWith(MockitoExtension.class)
class DataTransferAPIServiceTest {
	
	@Mock
	private OkHttpRestClient okHttpRestClient;
	@Mock
	private DataTransferProperties properties;
	@Mock
	private GenericApiResponse<String> apiResponse;
	@Mock
    private CredentialUtils credentialUtils;
	@Mock
	private TransferProcessRepository transferProcessRepository;
	
	@Captor
	private ArgumentCaptor<TransferProcess> argCaptorTransferProcess;
	@Captor
	private ArgumentCaptor<DataAddress> argCaptorDataAddress;
	
	@InjectMocks
	private DataTransferAPIService apiService;
	
	private DataTransferRequest dataTransferRequest = new DataTransferRequest(MockObjectUtil.TRANSFER_PROCESS_INITIALIZED.getId(),
			DataTransferFormat.HTTP_PULL.name(),
			null);
	
	@Test
	@DisplayName("Find transfer process by id, state and all")
	public void findDataTransfers() {

		when(transferProcessRepository.findById(anyString())).thenReturn(Optional.of(MockObjectUtil.TRANSFER_PROCESS_REQUESTED));
		Collection<JsonNode> response = apiService.findDataTransfers("test", TransferState.REQUESTED.name(), null);
		assertNotNull(response);
		assertEquals(response.size(), 1);

		when(transferProcessRepository.findById(anyString())).thenReturn(Optional.empty());
		response = apiService.findDataTransfers("test_not_found", null, null);
		assertNotNull(response);
		assertTrue(response.isEmpty());

		when(transferProcessRepository.findByState(anyString())).thenReturn(Arrays.asList(MockObjectUtil.TRANSFER_PROCESS_STARTED, MockObjectUtil.TRANSFER_PROCESS_COMPLETED));
		response =  apiService.findDataTransfers(null, TransferState.STARTED.name(), null);
		assertNotNull(response);
		assertEquals(response.size(), 2);
		
		response =  apiService.findDataTransfers(null, TransferState.STARTED.name(), IConstants.ROLE_PROVIDER);
		assertNotNull(response);
		assertEquals(response.size(), 1);
		
		response =  apiService.findDataTransfers(null, TransferState.STARTED.name(), IConstants.ROLE_CONSUMER);
		assertNotNull(response);
		assertEquals(response.size(), 1);

		when(transferProcessRepository.findAll())
				.thenReturn(Arrays.asList(MockObjectUtil.TRANSFER_PROCESS_REQUESTED, MockObjectUtil.TRANSFER_PROCESS_STARTED));
		response =  apiService.findDataTransfers(null, null, null);
		assertNotNull(response);
		assertEquals(response.size(), 2);
	}
	
	@Test
	@DisplayName("Request transfer process success")
	public void startNegotiation_success() {
		when(transferProcessRepository.findById(anyString())).thenReturn(Optional.of(MockObjectUtil.TRANSFER_PROCESS_INITIALIZED));
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.getData()).thenReturn(Serializer.serializeProtocol(MockObjectUtil.TRANSFER_PROCESS_REQUESTED));
		when(apiResponse.isSuccess()).thenReturn(true);
		when(properties.consumerCallbackAddress()).thenReturn(MockObjectUtil.CALLBACK_ADDRESS);
		when(transferProcessRepository.save(any(TransferProcess.class))).thenReturn(MockObjectUtil.TRANSFER_PROCESS_REQUESTED);
		
		apiService.requestTransfer(dataTransferRequest);
		
		verify(transferProcessRepository).save(argCaptorTransferProcess.capture());
		assertEquals(IConstants.ROLE_CONSUMER, argCaptorTransferProcess.getValue().getRole());
	}
	
	@Test
	@DisplayName("Request transfer process failed")
	public void startNegotiation_failed() {
		when(transferProcessRepository.findById(anyString())).thenReturn(Optional.of(MockObjectUtil.TRANSFER_PROCESS_INITIALIZED));
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(properties.consumerCallbackAddress()).thenReturn(MockObjectUtil.CALLBACK_ADDRESS);
		
		assertThrows(DataTransferAPIException.class, ()->
			apiService.requestTransfer(dataTransferRequest));
		
		verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
	}
	
	@Test
	@DisplayName("Request transfer process json exception")
	public void startNegotiation_jsonException() {
		when(transferProcessRepository.findById(anyString())).thenReturn(Optional.of(MockObjectUtil.TRANSFER_PROCESS_INITIALIZED));
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.getData()).thenReturn("not a JSON");
		when(apiResponse.isSuccess()).thenReturn(true);
		when(properties.consumerCallbackAddress()).thenReturn(MockObjectUtil.CALLBACK_ADDRESS);
		
		assertThrows(DataTransferAPIException.class, ()->
			apiService.requestTransfer(dataTransferRequest));
		
		verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
	}
	
	@Test
	@DisplayName("Start transfer process success")
	public void startTransfer_success_requestedState() throws UnsupportedEncodingException {
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(true);
		when(transferProcessRepository.findById(MockObjectUtil.TRANSFER_PROCESS_REQUESTED.getId()))
		.thenReturn(Optional.of(MockObjectUtil.TRANSFER_PROCESS_REQUESTED));
		
		apiService.startTransfer(MockObjectUtil.TRANSFER_PROCESS_REQUESTED.getId());
		
		verify(transferProcessRepository).save(any(TransferProcess.class));
	}
	
	@Test
	@DisplayName("Start transfer process failed - transfer process not found")
	public void startTransfer_failedNegotiationNotFound() {
		assertThrows(DataTransferAPIException.class, ()-> apiService.startTransfer(MockObjectUtil.TRANSFER_PROCESS_REQUESTED.getId()));
		
		verify(okHttpRestClient, times(0)).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
		verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
	}
	
	@ParameterizedTest
	@DisplayName("Start transfer process failed - wrong transfer process state")
	@MethodSource("startTransfer_wrongStates")
	public void startTransfer_wrongNegotiationState(TransferProcess input) {
		
		when(transferProcessRepository.findById(MockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
				.thenReturn(Optional.of(input));

		assertThrows(DataTransferAPIException.class, 
				() -> apiService.startTransfer(MockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));
		
		verify(transferProcessRepository).findById(MockObjectUtil.TRANSFER_PROCESS_STARTED.getId());
		verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
	}
	
	@Test
	@DisplayName("Start transfer process failed - bad request")
	public void startTransfer_failedBadRequest() {
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(false);
		when(transferProcessRepository.findById(MockObjectUtil.TRANSFER_PROCESS_REQUESTED.getId()))
			.thenReturn(Optional.of(MockObjectUtil.TRANSFER_PROCESS_REQUESTED));
		
		assertThrows(DataTransferAPIException.class, ()-> apiService.startTransfer(MockObjectUtil.TRANSFER_PROCESS_REQUESTED.getId()));
	
		verify(okHttpRestClient).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
		verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
	}
	
	@Test
	@DisplayName("Complete transfer process success")
	public void completeTransfer_success_requestedState() {
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(true);
		when(transferProcessRepository.findById(MockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
		.thenReturn(Optional.of(MockObjectUtil.TRANSFER_PROCESS_STARTED));
		
		apiService.completeTransfer(MockObjectUtil.TRANSFER_PROCESS_STARTED.getId());
		
		verify(transferProcessRepository).save(any(TransferProcess.class));
	}
	
	@Test
	@DisplayName("Complete transfer process failed - transfer process not found")
	public void completeTransfer_failedNegotiationNotFound() {
		assertThrows(DataTransferAPIException.class, ()-> apiService.completeTransfer(MockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));
		
		verify(okHttpRestClient, times(0)).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
		verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
	}
	
	@ParameterizedTest
	@DisplayName("Complete transfer process failed - wrong transfer process state")
	@MethodSource("completeTransfer_wrongStates")
	public void completeTransfer_wrongNegotiationState(TransferProcess input) {
		
		when(transferProcessRepository.findById(MockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId()))
				.thenReturn(Optional.of(input));

		assertThrows(DataTransferAPIException.class, 
				() -> apiService.completeTransfer(MockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId()));
		
		verify(transferProcessRepository).findById(MockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId());
		verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
	}
	
	@Test
	@DisplayName("Complete transfer process failed - bad request")
	public void completeTransfer_failedBadRequest() {
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(false);
		when(transferProcessRepository.findById(MockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
			.thenReturn(Optional.of(MockObjectUtil.TRANSFER_PROCESS_STARTED));
		
		assertThrows(DataTransferAPIException.class, ()-> apiService.completeTransfer(MockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));
	
		verify(okHttpRestClient).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
		verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
	}
	
	@Test
	@DisplayName("Suspend transfer process success")
	public void suspendTransfer_success_requestedState() {
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(true);
		when(transferProcessRepository.findById(MockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
		.thenReturn(Optional.of(MockObjectUtil.TRANSFER_PROCESS_STARTED));
		
		apiService.suspendTransfer(MockObjectUtil.TRANSFER_PROCESS_STARTED.getId());
		
		verify(transferProcessRepository).save(any(TransferProcess.class));
	}
	
	@Test
	@DisplayName("Suspend transfer process failed - transfer process not found")
	public void suspendTransfer_failedNegotiationNotFound() {
		assertThrows(DataTransferAPIException.class, ()-> apiService.suspendTransfer(MockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));
		
		verify(okHttpRestClient, times(0)).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
		verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
	}
	
	@ParameterizedTest
	@DisplayName("Suspend transfer process failed - wrong transfer process state")
	@MethodSource("suspendTransfer_wrongStates")
	public void suspendTransfer_wrongNegotiationState(TransferProcess input) {
		
		when(transferProcessRepository.findById(MockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId()))
				.thenReturn(Optional.of(input));

		assertThrows(DataTransferAPIException.class, 
				() -> apiService.suspendTransfer(MockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId()));
		
		verify(transferProcessRepository).findById(MockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId());
		verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
	}
	
	@Test
	@DisplayName("Suspend transfer process failed - bad request")
	public void suspendTransfer_failedBadRequest() {
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(false);
		when(transferProcessRepository.findById(MockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
			.thenReturn(Optional.of(MockObjectUtil.TRANSFER_PROCESS_STARTED));
		
		assertThrows(DataTransferAPIException.class, ()-> apiService.suspendTransfer(MockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));
	
		verify(okHttpRestClient).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
		verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
	}
	
	@Test
	@DisplayName("Terminate transfer process success")
	public void terminateTransfer_success_requestedState() {
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(true);
		when(transferProcessRepository.findById(MockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
		.thenReturn(Optional.of(MockObjectUtil.TRANSFER_PROCESS_STARTED));
		
		apiService.terminateTransfer(MockObjectUtil.TRANSFER_PROCESS_STARTED.getId());
		
		verify(transferProcessRepository).save(any(TransferProcess.class));
	}
	
	@Test
	@DisplayName("Terminate transfer process failed - transfer process not found")
	public void terminateTransfer_failedNegotiationNotFound() {
		assertThrows(DataTransferAPIException.class, ()-> apiService.terminateTransfer(MockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));
		
		verify(okHttpRestClient, times(0)).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
		verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
	}
	
	@ParameterizedTest
	@DisplayName("Terminate transfer process failed - wrong transfer process state")
	@MethodSource("terminateTransfer_wrongStates")
	public void terminateTransfer_wrongNegotiationState(TransferProcess input) {
		
		when(transferProcessRepository.findById(MockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId()))
				.thenReturn(Optional.of(input));

		assertThrows(DataTransferAPIException.class, 
				() -> apiService.terminateTransfer(MockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId()));
		
		verify(transferProcessRepository).findById(MockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId());
		verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
	}
	
	@Test
	@DisplayName("Terminate transfer process failed - bad request")
	public void terminateTransfer_failedBadRequest() {
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(false);
		when(transferProcessRepository.findById(MockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
			.thenReturn(Optional.of(MockObjectUtil.TRANSFER_PROCESS_STARTED));
		
		assertThrows(DataTransferAPIException.class, ()-> apiService.terminateTransfer(MockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));
	
		verify(okHttpRestClient).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
		verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
	}
	
	private static Stream<Arguments> startTransfer_wrongStates() {
	    return Stream.of(
	      Arguments.of(MockObjectUtil.TRANSFER_PROCESS_COMPLETED),
	      Arguments.of(MockObjectUtil.TRANSFER_PROCESS_STARTED),
	      Arguments.of(MockObjectUtil.TRANSFER_PROCESS_TERMINATED)
	    );
	}
	
	private static Stream<Arguments> completeTransfer_wrongStates() {
	    return Stream.of(
	      Arguments.of(MockObjectUtil.TRANSFER_PROCESS_COMPLETED),
	      Arguments.of(MockObjectUtil.TRANSFER_PROCESS_TERMINATED),
	      Arguments.of(MockObjectUtil.TRANSFER_PROCESS_REQUESTED),
	      Arguments.of(MockObjectUtil.TRANSFER_PROCESS_SUSPENDED)
	    );
	}
	
	private static Stream<Arguments> suspendTransfer_wrongStates() {
	    return Stream.of(
	      Arguments.of(MockObjectUtil.TRANSFER_PROCESS_COMPLETED),
	      Arguments.of(MockObjectUtil.TRANSFER_PROCESS_TERMINATED),
	      Arguments.of(MockObjectUtil.TRANSFER_PROCESS_REQUESTED),
	      Arguments.of(MockObjectUtil.TRANSFER_PROCESS_SUSPENDED)
	    );
	}
	
	private static Stream<Arguments> terminateTransfer_wrongStates() {
	    return Stream.of(
	      Arguments.of(MockObjectUtil.TRANSFER_PROCESS_COMPLETED),
	      Arguments.of(MockObjectUtil.TRANSFER_PROCESS_TERMINATED)
	    );
	}
}
