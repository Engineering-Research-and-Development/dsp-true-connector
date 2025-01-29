package it.eng.datatransfer.rest.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.datatransfer.exceptions.TransferProcessExistsException;
import it.eng.datatransfer.exceptions.TransferProcessNotFoundException;
import it.eng.datatransfer.model.TransferCompletionMessage;
import it.eng.datatransfer.model.TransferRequestMessage;
import it.eng.datatransfer.model.TransferStartMessage;
import it.eng.datatransfer.model.TransferSuspensionMessage;
import it.eng.datatransfer.model.TransferTerminationMessage;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.datatransfer.service.DataTransferService;
import it.eng.datatransfer.util.DataTranferMockObjectUtil;
import jakarta.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
public class ProviderDataTransferControllerTest {
	
	@Mock
	private DataTransferService dataTransferService;

	@InjectMocks
	private ProviderDataTransferController controller;
	
	@Test
	@DisplayName("Get TransferProcess for ProviderPid")
	public void geTransferProcess() {
		when(dataTransferService.findTransferProcessByProviderPid(DataTranferMockObjectUtil.PROVIDER_PID))
			.thenReturn(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER);
		assertEquals(HttpStatus.OK, controller.getTransferProcessByProviderPid(DataTranferMockObjectUtil.PROVIDER_PID).getStatusCode());
	}
	
	@Test
	@DisplayName("Get TransferProcess for ProviderPid - not found")
	public void transferProcessNtFound() {
		when(dataTransferService.findTransferProcessByProviderPid(DataTranferMockObjectUtil.PROVIDER_PID))
			.thenThrow(new TransferProcessNotFoundException("Not found"));
		assertThrows(TransferProcessNotFoundException.class, 
				()-> controller.getTransferProcessByProviderPid(DataTranferMockObjectUtil.PROVIDER_PID).getStatusCode());
	}
	
	// initiate transfer
	@Test
	@DisplayName("Initiate TransferProcess")
	public void initateDataTransfer() {
		when(dataTransferService.initiateDataTransfer(any(TransferRequestMessage.class)))
			.thenReturn(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER);
		ResponseEntity<JsonNode> response = controller.initiateDataTransfer(TransferSerializer.serializeProtocolJsonNode(DataTranferMockObjectUtil.TRANSFER_REQUEST_MESSAGE));
		assertEquals(HttpStatus.CREATED, response.getStatusCode());
	}
	
	@Test
	@DisplayName("Initiate TransferProcess - invalid request body")
	public void initateDataTransfer_invalidBody() {
		assertThrows(ValidationException.class, () ->
			controller.initiateDataTransfer(TransferSerializer.serializeProtocolJsonNode(DataTranferMockObjectUtil.TRANSFER_COMPLETION_MESSAGE))
		);
	}
	
	@Test
	@DisplayName("Initiate TransferProcess - service error")
	public void initateDataTransfer_service_error() {
		when(dataTransferService.initiateDataTransfer(any(TransferRequestMessage.class)))
			.thenThrow(new TransferProcessExistsException("message", DataTranferMockObjectUtil.PROVIDER_PID));
		assertThrows(TransferProcessExistsException.class, () ->
			controller.initiateDataTransfer(TransferSerializer.serializeProtocolJsonNode(DataTranferMockObjectUtil.TRANSFER_REQUEST_MESSAGE))
		);
	}
	// start
	@Test
	@DisplayName("Start TransferProcess")
	public void startDataTransfer() {
	when(dataTransferService.startDataTransfer(any(TransferStartMessage.class), isNull(), any(String.class)))
		.thenReturn(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED);
		
	ResponseEntity<Void> response = controller.startDataTransfer(DataTranferMockObjectUtil.PROVIDER_PID,
			TransferSerializer.serializeProtocolJsonNode(DataTranferMockObjectUtil.TRANSFER_START_MESSAGE));
	
	assertEquals(HttpStatus.OK, response.getStatusCode());
	}
	
	@Test
	@DisplayName("Start TransferProcess - invalid request body")
	public void startDataTransfer_invalidBody() {
		assertThrows(ValidationException.class, () ->
			controller.startDataTransfer(DataTranferMockObjectUtil.PROVIDER_PID, 
					TransferSerializer.serializeProtocolJsonNode(DataTranferMockObjectUtil.TRANSFER_COMPLETION_MESSAGE)));
	}
	
	@Test
	@DisplayName("Start TransferProcess - error service")
	public void startDataTransfer_errorService() {
		when(dataTransferService.startDataTransfer(any(TransferStartMessage.class), isNull(), any(String.class)))
			.thenThrow(new TransferProcessNotFoundException("TransferProcess not found test"));
		assertThrows(TransferProcessNotFoundException.class, () ->
			controller.startDataTransfer(DataTranferMockObjectUtil.PROVIDER_PID, 
					TransferSerializer.serializeProtocolJsonNode(DataTranferMockObjectUtil.TRANSFER_START_MESSAGE)));
	}
	
	// complete
	@Test
	@DisplayName("Complete TransferProcess")
	public void completeDataTransfer() {
		when(dataTransferService.completeDataTransfer(any(TransferCompletionMessage.class), isNull(), any(String.class)))
			.thenReturn(DataTranferMockObjectUtil.TRANSFER_PROCESS_COMPLETED);
		ResponseEntity<Void> response =  controller.completeDataTransfer(DataTranferMockObjectUtil.PROVIDER_PID,
				TransferSerializer.serializeProtocolJsonNode(DataTranferMockObjectUtil.TRANSFER_COMPLETION_MESSAGE));
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}
	
	@Test
	@DisplayName("Complete TransferProcess - invalid request body")
	public void completeDataTransfer_invalidBody() {
		assertThrows(ValidationException.class, () ->
			controller.completeDataTransfer(DataTranferMockObjectUtil.PROVIDER_PID, TransferSerializer.serializeProtocolJsonNode(DataTranferMockObjectUtil.TRANSFER_START_MESSAGE))
		);
	}
	
	@Test
	@DisplayName("Complete TransferProcess - error service")
	public void completeDataTransfer_errorService() {
		when(dataTransferService.completeDataTransfer(any(TransferCompletionMessage.class), isNull(), any(String.class)))
			.thenThrow(TransferProcessNotFoundException.class);
		assertThrows(TransferProcessNotFoundException.class, 
				() -> controller.completeDataTransfer(DataTranferMockObjectUtil.PROVIDER_PID,
				TransferSerializer.serializeProtocolJsonNode(DataTranferMockObjectUtil.TRANSFER_COMPLETION_MESSAGE)));
	}
	
	// terminate data transfer
	@Test
	@DisplayName("Terminate TransferProcess")
	public void terminateDataTransfer() {
		when(dataTransferService.terminateDataTransfer(any(TransferTerminationMessage.class), isNull(), any(String.class)))
			.thenReturn(DataTranferMockObjectUtil.TRANSFER_PROCESS_TERMINATED);
		ResponseEntity<Void> response = controller.terminateDataTransfer(DataTranferMockObjectUtil.PROVIDER_PID,
				TransferSerializer.serializeProtocolJsonNode(DataTranferMockObjectUtil.TRANSFER_TERMINATION_MESSAGE));
		assertEquals(HttpStatus.OK, response.getStatusCode());	
	}
	
	@Test
	@DisplayName("Terminate TransferProcess - invalid request body")
	public void terminateDataTransfer_invalidBody() {
		assertThrows(ValidationException.class, () ->
			controller.terminateDataTransfer(DataTranferMockObjectUtil.PROVIDER_PID, TransferSerializer.serializeProtocolJsonNode(DataTranferMockObjectUtil.TRANSFER_START_MESSAGE))
		);
	}
	
	@Test
	@DisplayName("Terminate TransferProcess - error service")
	public void terminateDataTransfer_errorService() {
		when(dataTransferService.terminateDataTransfer(any(TransferTerminationMessage.class), isNull(), any(String.class)))
			.thenThrow(TransferProcessNotFoundException.class);
		assertThrows(TransferProcessNotFoundException.class, 
				() -> controller.terminateDataTransfer(DataTranferMockObjectUtil.PROVIDER_PID,
				TransferSerializer.serializeProtocolJsonNode(DataTranferMockObjectUtil.TRANSFER_TERMINATION_MESSAGE)));
	}
	
	// suspend data transfer
	@Test
	@DisplayName("Suspend/pause TransferProcess")
	public void suspenseDataTransfer() {
		when(dataTransferService.suspendDataTransfer(any(TransferSuspensionMessage.class), isNull(), any(String.class)))
			.thenReturn(DataTranferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_PROVIDER);
		ResponseEntity<Void> response =  controller.suspenseDataTransfer(DataTranferMockObjectUtil.PROVIDER_PID,
				TransferSerializer.serializeProtocolJsonNode(DataTranferMockObjectUtil.TRANSFER_SUSPENSION_MESSAGE));
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}
	
	@Test
	@DisplayName("Suspend TransferProcess - invalid request body")
	public void suspenseDataTransfer_invalidBody() {
		assertThrows(ValidationException.class, () ->
			controller.suspenseDataTransfer(DataTranferMockObjectUtil.PROVIDER_PID, TransferSerializer.serializeProtocolJsonNode(DataTranferMockObjectUtil.TRANSFER_START_MESSAGE))
		);
	}
	
	@Test
	@DisplayName("Suspend TransferProcess - error service")
	public void suspendDataTransfer_errorService() {
		when(dataTransferService.suspendDataTransfer(any(TransferSuspensionMessage.class), isNull(), any(String.class)))
			.thenThrow(TransferProcessNotFoundException.class);
		assertThrows(TransferProcessNotFoundException.class, 
				() -> controller.suspenseDataTransfer(DataTranferMockObjectUtil.PROVIDER_PID,
				TransferSerializer.serializeProtocolJsonNode(DataTranferMockObjectUtil.TRANSFER_SUSPENSION_MESSAGE)));
	}
}
