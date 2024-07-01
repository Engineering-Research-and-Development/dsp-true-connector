package it.eng.datatransfer.rest.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import it.eng.datatransfer.exceptions.TransferProcessNotFound;
import it.eng.datatransfer.model.Serializer;
import it.eng.datatransfer.service.DataTransferService;
import it.eng.datatransfer.util.MockObjectUtil;
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
		when(dataTransferService.findTransferProcessByProviderPid(MockObjectUtil.PROVIDER_PID))
			.thenReturn(MockObjectUtil.TRANSFER_PROCESS_REQUESTED);
		assertEquals(HttpStatus.OK, controller.getTransferProcessByProviderPid(MockObjectUtil.PROVIDER_PID).getStatusCode());
	}
	
	@Test
	@DisplayName("Get TransferProcess for ProviderPid - not found")
	public void transferProcessNtFound() {
		when(dataTransferService.findTransferProcessByProviderPid(MockObjectUtil.PROVIDER_PID))
			.thenThrow(new TransferProcessNotFound("Not found"));
		assertThrows(TransferProcessNotFound.class, 
				()-> controller.getTransferProcessByProviderPid(MockObjectUtil.PROVIDER_PID).getStatusCode());
	}
	
	@Test
	@DisplayName("Initiate TransferProcess")
	public void initateDataTransfer() {
		assertEquals(HttpStatus.NOT_IMPLEMENTED, 
				controller.initiateDataTransfer(Serializer.serializeProtocolJsonNode(MockObjectUtil.TRANSFER_REQUEST_MESSAGE)).getStatusCode());
	}
	
	@Test
	@DisplayName("Initiate TransferProcess - invalid request body")
	public void initateDataTransfer_invalidBody() {
		assertThrows(ValidationException.class, () ->
			controller.initiateDataTransfer(Serializer.serializeProtocolJsonNode(MockObjectUtil.TRANSFER_COMPLETION_MESSAGE))
		);
	}
	
	@Test
	@DisplayName("Start TransferProcess")
	public void startDataTransfer() {
		assertEquals(HttpStatus.NOT_IMPLEMENTED, 
				controller.startDataTransfer(MockObjectUtil.PROVIDER_PID,
						Serializer.serializeProtocolJsonNode(MockObjectUtil.TRANSFER_START_MESSAGE)).getStatusCode());
	}
	
	@Test
	@DisplayName("Start TransferProcess - invalid request body")
	public void startDataTransfer_invalidBody() {
		assertThrows(ValidationException.class, () ->
			controller.startDataTransfer(MockObjectUtil.PROVIDER_PID, Serializer.serializeProtocolJsonNode(MockObjectUtil.TRANSFER_COMPLETION_MESSAGE))
		);
	}
	
	@Test
	@DisplayName("Complete TransferProcess")
	public void completeDataTransfer() {
		assertEquals(HttpStatus.NOT_IMPLEMENTED, 
				controller.completeDataTransfer(MockObjectUtil.PROVIDER_PID,
						Serializer.serializeProtocolJsonNode(MockObjectUtil.TRANSFER_COMPLETION_MESSAGE)).getStatusCode());
	}
	
	@Test
	@DisplayName("Complete TransferProcess - invalid request body")
	public void completeDataTransfer_invalidBody() {
		assertThrows(ValidationException.class, () ->
			controller.completeDataTransfer(MockObjectUtil.PROVIDER_PID, Serializer.serializeProtocolJsonNode(MockObjectUtil.TRANSFER_START_MESSAGE))
		);
	}
	
	@Test
	@DisplayName("Terminate TransferProcess")
	public void terminateDataTransfer() {
		assertEquals(HttpStatus.NOT_IMPLEMENTED, 
				controller.terminateDataTransfer(MockObjectUtil.PROVIDER_PID,
						Serializer.serializeProtocolJsonNode(MockObjectUtil.TRANSFER_TERMINATION_MESSAGE)).getStatusCode());
	}
	
	@Test
	@DisplayName("Terminate TransferProcess - invalid request body")
	public void terminateDataTransfer_invalidBody() {
		assertThrows(ValidationException.class, () ->
			controller.terminateDataTransfer(MockObjectUtil.PROVIDER_PID, Serializer.serializeProtocolJsonNode(MockObjectUtil.TRANSFER_START_MESSAGE))
		);
	}
	
	@Test
	@DisplayName("Suspend/pause TransferProcess")
	public void suspenseDataTransfer() {
		assertEquals(HttpStatus.NOT_IMPLEMENTED, 
				controller.suspenseDataTransfer(MockObjectUtil.PROVIDER_PID,
						Serializer.serializeProtocolJsonNode(MockObjectUtil.TRANSFER_SUSPENSION_MESSAGE)).getStatusCode());
	}
	
	@Test
	@DisplayName("Terminate TransferProcess - invalid request body")
	public void suspenseDataTransfer_invalidBody() {
		assertThrows(ValidationException.class, () ->
			controller.suspenseDataTransfer(MockObjectUtil.PROVIDER_PID, Serializer.serializeProtocolJsonNode(MockObjectUtil.TRANSFER_START_MESSAGE))
		);
	}
}
