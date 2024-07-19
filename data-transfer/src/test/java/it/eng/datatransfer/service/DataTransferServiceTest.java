package it.eng.datatransfer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
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

import it.eng.datatransfer.exceptions.TransferProcessExistsException;
import it.eng.datatransfer.exceptions.TransferProcessNotFoundException;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.util.MockObjectUtil;

@ExtendWith(MockitoExtension.class)
public class DataTransferServiceTest {

	@Mock
	private TransferProcessRepository transferProcessRepository;

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
	public void initiateTransferPRocess() {
		when(transferProcessRepository.findByAgreementId(MockObjectUtil.AGREEMENT_ID)).thenReturn(Optional.empty());
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

}
