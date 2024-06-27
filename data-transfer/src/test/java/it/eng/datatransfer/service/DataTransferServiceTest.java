package it.eng.datatransfer.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.eng.datatransfer.exceptions.TransferProcessNotFound;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.util.MockObjectUtil;

@ExtendWith(MockitoExtension.class)
public class DataTransferServiceTest {

	@Mock
	private TransferProcessRepository transferProcessRepository;

	@InjectMocks
	private DataTransferService service;

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
		assertThrows(TransferProcessNotFound.class, 
				()-> service.findTransferProcessByProviderPid(MockObjectUtil.PROVIDER_PID));
	}

}
