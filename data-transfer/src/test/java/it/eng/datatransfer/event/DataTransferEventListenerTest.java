package it.eng.datatransfer.event;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import it.eng.datatransfer.repository.TransferRequestMessageRepository;
import it.eng.datatransfer.util.DataTranferMockObjectUtil;

@ExtendWith(MockitoExtension.class)
class DataTransferEventListenerTest {

	@Mock
	private ApplicationEventPublisher publisher;
	@Mock
	private TransferRequestMessageRepository transferRequestMessageRepository;

	@InjectMocks
	private DataTransferEventListener dataTransferEventListener;
	
	@Test
	@DisplayName("Handle TransferProcessChange event")
	void handleTransferProcessChange() {
		TransferProcessChangeEvent changeEvent = TransferProcessChangeEvent.Builder.newInstance()
				.oldTransferProcess(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER)
				.newTransferProcess(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED)
				.build();
		assertDoesNotThrow(() -> dataTransferEventListener.handleTransferProcessChange(changeEvent));
	}

	@Test
	@DisplayName("Handle TransferStartMessage")
	void handleTransferStartMessage() {
		when(transferRequestMessageRepository.findByConsumerPid(anyString()))
			.thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_REQUEST_MESSAGE));
		
		dataTransferEventListener.handleTransferStartMessage(DataTranferMockObjectUtil.TRANSFER_START_MESSAGE);
		
		verify(publisher, times(0)).publishEvent(any(StartFTPServerEvent.class));
	}
	
	@Test
	@DisplayName("Handle TransferStartMessage - SFTP")
	void handleTransferStartMessage_sftp() {
		when(transferRequestMessageRepository.findByConsumerPid(anyString()))
			.thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_REQUEST_MESSAGE_SFTP));
		
		dataTransferEventListener.handleTransferStartMessage(DataTranferMockObjectUtil.TRANSFER_START_MESSAGE);
		
		verify(publisher).publishEvent(any(StartFTPServerEvent.class));
	}

	@Test
	@DisplayName("Handle TransferSuspensionMessage")
	void handleTransferSuspensionMessage() {
		when(transferRequestMessageRepository.findByConsumerPid(anyString()))
			.thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_REQUEST_MESSAGE));
		
		dataTransferEventListener.handleTransferSuspensionMessage(DataTranferMockObjectUtil.TRANSFER_SUSPENSION_MESSAGE);
		
		verify(publisher, times(0)).publishEvent(any(StopFTPServerEvent.class));
	}
	
	@Test
	@DisplayName("Handle TransferSuspensionMessage - SFTP")
	void handleTransferSuspensionMessage_sftp() {
		when(transferRequestMessageRepository.findByConsumerPid(anyString()))
			.thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_REQUEST_MESSAGE_SFTP));
		
		dataTransferEventListener.handleTransferSuspensionMessage(DataTranferMockObjectUtil.TRANSFER_SUSPENSION_MESSAGE);
		
		verify(publisher).publishEvent(any(StopFTPServerEvent.class));
	}

	@Test
	@DisplayName("Handle TransferCompletionMessage")
	void handleTransferCompletionMessage() {
		when(transferRequestMessageRepository.findByConsumerPid(anyString()))
			.thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_REQUEST_MESSAGE));
	
		dataTransferEventListener.handleTransferCompletionMessage(DataTranferMockObjectUtil.TRANSFER_COMPLETION_MESSAGE);
		
		verify(publisher, times(0)).publishEvent(any(StopFTPServerEvent.class));
	}
	
	@Test
	@DisplayName("Handle TransferCompletionMessage - SFTP")
	void handleTransferCompletionMessage_sftp() {
		when(transferRequestMessageRepository.findByConsumerPid(anyString()))
			.thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_REQUEST_MESSAGE_SFTP));
	
		dataTransferEventListener.handleTransferCompletionMessage(DataTranferMockObjectUtil.TRANSFER_COMPLETION_MESSAGE);
		
		verify(publisher).publishEvent(any(StopFTPServerEvent.class));
	}

}
