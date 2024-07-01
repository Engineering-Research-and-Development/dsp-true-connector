package it.eng.datatransfer.rest.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import it.eng.datatransfer.model.Serializer;
import it.eng.datatransfer.service.DataTransferService;
import it.eng.datatransfer.util.MockObjectUtil;
import jakarta.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
public class ConsumerDataTransferCallbackControllerTest {
	
	@Mock
	private DataTransferService dataTransferService;

	@InjectMocks
	private ConsumerDataTransferCallbackController controller;
	
	@Test
	@DisplayName("Start TransferProcess")
	public void startDataTransfer() {
		assertEquals(HttpStatus.NOT_IMPLEMENTED, 
				controller.startDataTransfer(MockObjectUtil.CONSUMER_PID,
						Serializer.serializeProtocolJsonNode(MockObjectUtil.TRANSFER_START_MESSAGE)).getStatusCode());
	}
	
	@Test
	@DisplayName("Start TransferProcess - invalid request body")
	public void startDataTransfer_invalidBody() {
		assertThrows(ValidationException.class, () ->
			controller.startDataTransfer(MockObjectUtil.CONSUMER_PID, Serializer.serializeProtocolJsonNode(MockObjectUtil.TRANSFER_COMPLETION_MESSAGE))
		);
	}
	
	@Test
	@DisplayName("Complete TransferProcess")
	public void completeDataTransfer() {
		assertEquals(HttpStatus.NOT_IMPLEMENTED, 
				controller.completeDataTransfer(MockObjectUtil.CONSUMER_PID,
						Serializer.serializeProtocolJsonNode(MockObjectUtil.TRANSFER_COMPLETION_MESSAGE)).getStatusCode());
	}
	
	@Test
	@DisplayName("Complete TransferProcess - invalid request body")
	public void completeDataTransfer_invalidBody() {
		assertThrows(ValidationException.class, () ->
			controller.completeDataTransfer(MockObjectUtil.CONSUMER_PID, Serializer.serializeProtocolJsonNode(MockObjectUtil.TRANSFER_START_MESSAGE))
		);
	}
	
	@Test
	@DisplayName("Terminate TransferProcess")
	public void terminateDataTransfer() {
		assertEquals(HttpStatus.NOT_IMPLEMENTED, 
				controller.terminateDataTransfer(MockObjectUtil.CONSUMER_PID,
						Serializer.serializeProtocolJsonNode(MockObjectUtil.TRANSFER_TERMINATION_MESSAGE)).getStatusCode());
	}
	
	@Test
	@DisplayName("Terminate TransferProcess - invalid request body")
	public void terminateDataTransfer_invalidBody() {
		assertThrows(ValidationException.class, () ->
			controller.terminateDataTransfer(MockObjectUtil.CONSUMER_PID, Serializer.serializeProtocolJsonNode(MockObjectUtil.TRANSFER_START_MESSAGE))
		);
	}
	
	@Test
	@DisplayName("Suspend/pause TransferProcess")
	public void suspenseDataTransfer() {
		assertEquals(HttpStatus.NOT_IMPLEMENTED, 
				controller.suspenseDataTransfer(MockObjectUtil.CONSUMER_PID,
						Serializer.serializeProtocolJsonNode(MockObjectUtil.TRANSFER_SUSPENSION_MESSAGE)).getStatusCode());
	}
	
	@Test
	@DisplayName("Terminate TransferProcess - invalid request body")
	public void suspenseDataTransfer_invalidBody() {
		assertThrows(ValidationException.class, () ->
			controller.suspenseDataTransfer(MockObjectUtil.CONSUMER_PID, Serializer.serializeProtocolJsonNode(MockObjectUtil.TRANSFER_START_MESSAGE))
		);
	}
}
