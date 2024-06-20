package it.eng.datatransfer.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
	public void dataTransferDOesNotExists() {
		when(transferProcessRepository.findByConsumerPidAndProviderPid(MockObjectUtil.CONSUMER_PID, MockObjectUtil.PROVIDER_PID))
			.thenReturn(Optional.empty());
		assertFalse(service.isDataTransferStarted(MockObjectUtil.CONSUMER_PID, MockObjectUtil.PROVIDER_PID));
	}
}
