package it.eng.datatransfer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import it.eng.datatransfer.exceptions.AgreementNotFoundException;
import it.eng.datatransfer.exceptions.TransferProcessExistsException;
import it.eng.datatransfer.exceptions.TransferProcessInvalidStateException;
import it.eng.datatransfer.exceptions.TransferProcessNotFoundException;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.properties.DataTransferProperties;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.util.MockObjectUtil;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.util.CredentialUtils;

@ExtendWith(MockitoExtension.class)
public class DataTransferServiceTest {

	@Mock
	private TransferProcessRepository transferProcessRepository;
	@Mock
	private ApplicationEventPublisher publisher;
	@Mock
	private OkHttpRestClient okHttpRestClient;
	@Mock
	private CredentialUtils credentialUtils;
	@Mock
	private DataTransferProperties properties;
    @Mock
	private GenericApiResponse<String> apiResponse;

	@InjectMocks
	private DataTransferService service;
	
	@Captor
	private ArgumentCaptor<TransferProcess> argTransferProcess;

	@Test
	@DisplayName("Data transfer exists and state is started")
	public void dataTransferExistsAndStarted() {
		when(transferProcessRepository.findByConsumerPidAndProviderPid(MockObjectUtil.CONSUMER_PID, MockObjectUtil.PROVIDER_PID))
			.thenReturn(Optional.of(MockObjectUtil.TRANSFER_PROCESS_STARTED));
		assertTrue(service.isDataTransferStarted(MockObjectUtil.CONSUMER_PID, MockObjectUtil.PROVIDER_PID));
	}
	
	@Test
	@DisplayName("Data transfer exists and state is not started")
	public void dataTransferExistsAndNotStarted() {
		when(transferProcessRepository.findByConsumerPidAndProviderPid(MockObjectUtil.CONSUMER_PID, MockObjectUtil.PROVIDER_PID))
			.thenReturn(Optional.of(MockObjectUtil.TRANSFER_PROCESS_REQUESTED));
		assertFalse(service.isDataTransferStarted(MockObjectUtil.CONSUMER_PID, MockObjectUtil.PROVIDER_PID));
	}
	
	@Test
	@DisplayName("Data transfer not found")
	public void dataTransferDoesNotExists() {
		when(transferProcessRepository.findByConsumerPidAndProviderPid(MockObjectUtil.CONSUMER_PID, MockObjectUtil.PROVIDER_PID))
			.thenReturn(Optional.empty());
		assertFalse(service.isDataTransferStarted(MockObjectUtil.CONSUMER_PID, MockObjectUtil.PROVIDER_PID));
	}
	
	@Test
	@DisplayName("Find TransferProcess by providerPid")
	public void getTransferProcessByProviderPid() {
		when(transferProcessRepository.findByProviderPid(MockObjectUtil.PROVIDER_PID)).thenReturn(Optional.of(MockObjectUtil.TRANSFER_PROCESS_STARTED));
		TransferProcess tp = service.findTransferProcessByProviderPid(MockObjectUtil.PROVIDER_PID);
		assertNotNull(tp);
	}

	@Test
	@DisplayName("TransferProcess by providerPid not found")
	public void transferProcessByProviderPid_NotFound() {
		when(transferProcessRepository.findByProviderPid(MockObjectUtil.PROVIDER_PID)).thenReturn(Optional.empty());
		assertThrows(TransferProcessNotFoundException.class, 
				()-> service.findTransferProcessByProviderPid(MockObjectUtil.PROVIDER_PID));
	}
	
	@Test
	@DisplayName("DataTransfer requested - success")
	public void initiateTransferProcess() {
		when(transferProcessRepository.findByAgreementId(MockObjectUtil.AGREEMENT_ID)).thenReturn(Optional.empty());
    	when(okHttpRestClient.sendRequestProtocol(any(String.class), isNull(), isNull()))
    		.thenReturn(apiResponse);
    	when(apiResponse.isSuccess()).thenReturn(true);

    	TransferProcess transferProcessRequested = service.initiateDataTransfer(MockObjectUtil.TRANSFER_REQUEST_MESSAGE);
		
		assertNotNull(transferProcessRequested);
		assertEquals(TransferState.REQUESTED, transferProcessRequested.getState());
		verify(transferProcessRepository).save(argTransferProcess.capture());
		assertEquals(TransferState.REQUESTED, argTransferProcess.getValue().getState());
	}
	
	@Test
	@DisplayName("DataTransfer requested - TransferProcess exists")
	public void initiateTransferPRocess_exists() {
		when(transferProcessRepository.findByAgreementId(MockObjectUtil.AGREEMENT_ID))
			.thenReturn(Optional.of(MockObjectUtil.TRANSFER_PROCESS_REQUESTED));
		assertThrows(TransferProcessExistsException.class,
				() -> service.initiateDataTransfer(MockObjectUtil.TRANSFER_REQUEST_MESSAGE));
		
		verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());
	}
	
	@Test
	@DisplayName("DataTransfer requested - agreement not valid")
	public void initiateTransferProcess_agreemen_not_valid() {
		when(transferProcessRepository.findByAgreementId(MockObjectUtil.AGREEMENT_ID)).thenReturn(Optional.empty());
    	when(okHttpRestClient.sendRequestProtocol(any(String.class), isNull(), isNull()))
    		.thenReturn(apiResponse);
    	when(apiResponse.isSuccess()).thenReturn(false);

    	assertThrows(AgreementNotFoundException.class,
    			() -> service.initiateDataTransfer(MockObjectUtil.TRANSFER_REQUEST_MESSAGE));
		
		verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());
	}
	
	// TransferStartMessage
	@Test
	@DisplayName("StartDataTransfer from REQUESTED - provider")
	public void startDataTransfer_fromRequested_provider() {
		when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
			.thenReturn(Optional.of(MockObjectUtil.TRANSFER_PROCESS_REQUESTED));
		
		TransferProcess transferProcessStarted = service.startDataTransfer(MockObjectUtil.TRANSFER_START_MESSAGE, null, MockObjectUtil.PROVIDER_PID);
		
		assertEquals(TransferState.STARTED, transferProcessStarted.getState());
		verify(transferProcessRepository).save(argTransferProcess.capture());
		assertEquals(TransferState.STARTED, argTransferProcess.getValue().getState());
	}
	
	@Test
	@DisplayName("StartDataTransfer from REQUESTED - consumer callback")
	public void startDataTransfer_fromRequested_consumer() {
		when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
			.thenReturn(Optional.of(MockObjectUtil.TRANSFER_PROCESS_REQUESTED));
		
		TransferProcess transferProcessStarted = service.startDataTransfer(MockObjectUtil.TRANSFER_START_MESSAGE, MockObjectUtil.CONSUMER_PID, null);
		
		assertEquals(TransferState.STARTED, transferProcessStarted.getState());
		verify(transferProcessRepository).save(argTransferProcess.capture());
		assertEquals(TransferState.STARTED, argTransferProcess.getValue().getState());
	}
	
	@Test
	@DisplayName("StartDataTransfer from SUSPENDED - provider")
	public void startDataTransfer_fromSuspended_provider() {
		when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
			.thenReturn(Optional.of(MockObjectUtil.TRANSFER_PROCESS_SUSPENDED));
		
		TransferProcess transferProcessStarted = service.startDataTransfer(MockObjectUtil.TRANSFER_START_MESSAGE, null, MockObjectUtil.PROVIDER_PID);
		
		assertEquals(TransferState.STARTED, transferProcessStarted.getState());
		verify(transferProcessRepository).save(argTransferProcess.capture());
		assertEquals(TransferState.STARTED, argTransferProcess.getValue().getState());
	}
	
	@Test
	@DisplayName("StartDataTransfer from SUSPENDED - consumer callback")
	public void startDataTransfer_fromSuspended_consumer() {
		when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
			.thenReturn(Optional.of(MockObjectUtil.TRANSFER_PROCESS_SUSPENDED));
		
		TransferProcess transferProcessStarted = service.startDataTransfer(MockObjectUtil.TRANSFER_START_MESSAGE, MockObjectUtil.CONSUMER_PID, null);
		
		assertEquals(TransferState.STARTED, transferProcessStarted.getState());
		verify(transferProcessRepository).save(argTransferProcess.capture());
		assertEquals(TransferState.STARTED, argTransferProcess.getValue().getState());
	}
	
	@Test
	@DisplayName("StartDataTransfer - transfer process not found - provider")
	public void startDataTransfer_tpNotFound_provider() {
		when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
			.thenReturn(Optional.empty());
		
		assertThrows(TransferProcessNotFoundException.class, 
				() -> service.startDataTransfer(MockObjectUtil.TRANSFER_START_MESSAGE, null, MockObjectUtil.PROVIDER_PID));
		verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());
	}
	
	@Test
	@DisplayName("StartDataTransfer - transfer process not found - consumer callback")
	public void startDataTransfer_tpNotFound_consumer() {
		when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
			.thenReturn(Optional.empty());
		
		assertThrows(TransferProcessNotFoundException.class, 
				() -> service.startDataTransfer(MockObjectUtil.TRANSFER_START_MESSAGE, MockObjectUtil.CONSUMER_PID, null));
		verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());
	}
	
	@Test
	@DisplayName("StartDataTransfer - invalid state")
	public void startDataTransfer_invalidState() {
		when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
			.thenReturn(Optional.of(MockObjectUtil.TRANSFER_PROCESS_STARTED));
		
		assertThrows(TransferProcessInvalidStateException.class, 
				() -> service.startDataTransfer(MockObjectUtil.TRANSFER_START_MESSAGE, null, MockObjectUtil.PROVIDER_PID));
		verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());
	}
	
	// TransferCompletionMessage
	@Test
	@DisplayName("TransferCompletionMessage from STARTED - provider")
	public void completeDataTransfer_fromRequested_provider() {
		when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
			.thenReturn(Optional.of(MockObjectUtil.TRANSFER_PROCESS_STARTED));
		
		TransferProcess transferProcessCompleted = service.completeDataTransfer(MockObjectUtil.TRANSFER_COMPLETION_MESSAGE, null, MockObjectUtil.PROVIDER_PID);
		
		assertEquals(TransferState.COMPLETED, transferProcessCompleted.getState());
		verify(transferProcessRepository).save(argTransferProcess.capture());
		assertEquals(TransferState.COMPLETED, argTransferProcess.getValue().getState());
	}
	
	@Test
	@DisplayName("TransferCompletionMessage from STARTED - consumer callback")
	public void completeDataTransfer_fromRequested_consumer() {
		when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
			.thenReturn(Optional.of(MockObjectUtil.TRANSFER_PROCESS_STARTED));
		
		TransferProcess transferProcessCompleted = service.completeDataTransfer(MockObjectUtil.TRANSFER_COMPLETION_MESSAGE,
				MockObjectUtil.CONSUMER_PID, null);
		
		assertEquals(TransferState.COMPLETED, transferProcessCompleted.getState());
		verify(transferProcessRepository).save(argTransferProcess.capture());
		assertEquals(TransferState.COMPLETED, argTransferProcess.getValue().getState());
	}
	
	@Test
	@DisplayName("TransferCompletionMessage - transfer process not found - provider")
	public void completeDataTransfer_tpNotFound_provider() {
		when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
			.thenReturn(Optional.empty());
		
		assertThrows(TransferProcessNotFoundException.class, 
				() -> service.completeDataTransfer(MockObjectUtil.TRANSFER_COMPLETION_MESSAGE, null, MockObjectUtil.PROVIDER_PID));
		verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());
	}
	
	@Test
	@DisplayName("TransferCompletionMessage - transfer process not found - consumer callback")
	public void completeDataTransfer_tpNotFound_consumer() {
		when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
			.thenReturn(Optional.empty());
		
		assertThrows(TransferProcessNotFoundException.class, 
				() -> service.completeDataTransfer(MockObjectUtil.TRANSFER_COMPLETION_MESSAGE, MockObjectUtil.CONSUMER_PID, null));
		verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());
	}
	
	@Test
	@DisplayName("TransferCompletionMessage - invalid state")
	public void completeDataTransfer_invalidState() {
		when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
			.thenReturn(Optional.of(MockObjectUtil.TRANSFER_PROCESS_REQUESTED));
		
		assertThrows(TransferProcessInvalidStateException.class, 
				() -> service.completeDataTransfer(MockObjectUtil.TRANSFER_COMPLETION_MESSAGE, null, MockObjectUtil.PROVIDER_PID));
		verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());
	}
}
