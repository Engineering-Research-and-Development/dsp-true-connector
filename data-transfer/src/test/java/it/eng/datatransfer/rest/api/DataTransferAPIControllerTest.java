package it.eng.datatransfer.rest.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.model.DataTransferRequest;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.datatransfer.service.api.DataTransferAPIService;
import it.eng.datatransfer.util.DataTranferMockObjectUtil;
import it.eng.tools.model.DSpaceConstants;
import it.eng.tools.response.GenericApiResponse;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
class DataTransferAPIControllerTest {

	@Mock
	private HttpServletResponse response;
	
	@Mock
	private DataTransferAPIService apiService;
	
	@InjectMocks
	private DataTransferAPIController controller;
	
	private DataTransferRequest dataTransferRequest = new DataTransferRequest(DataTranferMockObjectUtil.TRANSFER_PROCESS_INITIALIZED.getId(),
			DataTransferFormat.HTTP_PULL.name(),
			null);

	
	@Test
	@DisplayName("Find transfer process by id, state and all")
	public void getTransfersProcess() {
		when(apiService.findDataTransfers(anyString(), anyString(), isNull()))
			.thenReturn(Arrays.asList(TransferSerializer.serializePlainJsonNode(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER)));
		ResponseEntity<GenericApiResponse<Collection<JsonNode>>> response = controller.getTransfersProcess("test", TransferState.REQUESTED.name(), null);
		assertNotNull(response);
		assertTrue(response.getBody().isSuccess());
		assertFalse(response.getBody().getData().isEmpty());
		
		when(apiService.findDataTransfers(anyString(), isNull(), isNull()))
			.thenReturn(new ArrayList<>());
		response = controller.getTransfersProcess("test_not_found", null, null);
		assertNotNull(response);
		assertTrue(response.getBody().isSuccess());
		assertTrue(response.getBody().getData().isEmpty());
		
		when(apiService.findDataTransfers(isNull(), anyString(), anyString()))
			.thenReturn(Arrays.asList(TransferSerializer.serializePlainJsonNode(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED)));
		response = controller.getTransfersProcess(null, TransferState.STARTED.name(), DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getRole());
		assertNotNull(response);
		assertTrue(response.getBody().isSuccess());
		assertFalse(response.getBody().getData().isEmpty());
	
		when(apiService.findDataTransfers(isNull(), isNull(), isNull()))
			.thenReturn(Arrays.asList(TransferSerializer.serializePlainJsonNode(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER),
					TransferSerializer.serializePlainJsonNode(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED)));
		response = controller.getTransfersProcess(null, null, null);
		assertNotNull(response);
		assertTrue(response.getBody().isSuccess());
		assertFalse(response.getBody().getData().isEmpty());

	}
	
	@Test
	@DisplayName("Request transfer process success")
	public void requestTransfer_success() {
		Map<String, Object> map = new HashMap<>();
		map.put("transferProcessId", DataTranferMockObjectUtil.FORWARD_TO);
		map.put(DSpaceConstants.FORMAT, DataTransferFormat.HTTP_PULL.name());
		map.put(DSpaceConstants.DATA_ADDRESS, TransferSerializer.serializePlainJsonNode(DataTranferMockObjectUtil.DATA_ADDRESS));
				
		when(apiService.requestTransfer(any(DataTransferRequest.class)))
			.thenReturn(TransferSerializer.serializeProtocolJsonNode(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER));
		
		ResponseEntity<GenericApiResponse<JsonNode>> response = controller.requestTransfer(dataTransferRequest);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(apiService).requestTransfer(dataTransferRequest);
	}
	
	@Test
	@DisplayName("Request transfer process failed")
	public void requestTransfer_failed() {
		Map<String, Object> map = new HashMap<>();
		map.put("transferProcessId", DataTranferMockObjectUtil.FORWARD_TO);
		map.put(DSpaceConstants.FORMAT, DataTransferFormat.HTTP_PULL.name());
		map.put(DSpaceConstants.DATA_ADDRESS, TransferSerializer.serializePlainJsonNode(DataTranferMockObjectUtil.DATA_ADDRESS));
				
		doThrow(new DataTransferAPIException("Something not correct - tests"))
			.when(apiService).requestTransfer(any(DataTransferRequest.class));
		
		assertThrows(DataTransferAPIException.class, () -> controller.requestTransfer(dataTransferRequest));
	}
	
	@Test
	@DisplayName("Start transfer process success")
	public void startTransfer_success() throws UnsupportedEncodingException {
				
		ResponseEntity<GenericApiResponse<JsonNode>> response = controller.startTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());
		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(apiService).startTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());
	}
	
	@Test
	@DisplayName("Start transfer process failed")
	public void startTransfer_failed() throws UnsupportedEncodingException {
				
		doThrow(new DataTransferAPIException("Something not correct - tests"))
			.when(apiService).startTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());
		
		assertThrows(DataTransferAPIException.class, () -> controller.startTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));
	}
	
	@Test
	@DisplayName("Complete transfer process success")
	public void completeTransfer_success() {
				
		ResponseEntity<GenericApiResponse<JsonNode>> response = controller.completeTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId());
		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(apiService).completeTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId());
	}
	
	@Test
	@DisplayName("Complete transfer process failed")
	public void completeTransfer_failed() {
				
		doThrow(new DataTransferAPIException("Something not correct - tests"))
			.when(apiService).completeTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId());
		
		assertThrows(DataTransferAPIException.class, () -> controller.completeTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId()));
	}
	
	@Test
	@DisplayName("Suspend transfer process success")
	public void suspendTransfer_success() {
				
		ResponseEntity<GenericApiResponse<JsonNode>> response = controller.suspendTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_PROVIDER.getId());
		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(apiService).suspendTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_PROVIDER.getId());
	}
	
	@Test
	@DisplayName("Suspend transfer process failed")
	public void suspendTransfer_failed() {
				
		doThrow(new DataTransferAPIException("Something not correct - tests"))
			.when(apiService).suspendTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_PROVIDER.getId());
		
		assertThrows(DataTransferAPIException.class, () -> controller.suspendTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_PROVIDER.getId()));
	}
	
	@Test
	@DisplayName("Terminate transfer process success")
	public void terminateTransfer_success() {
				
		ResponseEntity<GenericApiResponse<JsonNode>> response = controller.terminateTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_TERMINATED.getId());
		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(apiService).terminateTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_TERMINATED.getId());
	}
	
	@Test
	@DisplayName("Terminate transfer process failed")
	public void terminateTransfer_failed() {
				
		doThrow(new DataTransferAPIException("Something not correct - tests"))
			.when(apiService).terminateTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_TERMINATED.getId());
		
		assertThrows(DataTransferAPIException.class, () -> controller.terminateTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_TERMINATED.getId()));
	}
	
	@Test
	@DisplayName("Download data - success")
	public void downloadData_success() throws IllegalStateException, IOException  {
		doNothing().when(apiService).downloadData(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());;

		
		assertDoesNotThrow(() -> controller.downloadData(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));
		
	}
	
	@Test
	@DisplayName("Download data - fail")
	public void downloadData_fail() throws IllegalStateException, IOException {
		doThrow(new DataTransferAPIException("message")).when(apiService).downloadData(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());
		
		assertThrows(DataTransferAPIException.class, () -> controller.downloadData(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));
	}
	
	@Test
	@DisplayName("View data - success")
	public void viewData_success() throws IllegalStateException, IOException  {
		doNothing().when(apiService).viewData(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId(), response);

		
		assertDoesNotThrow(() -> controller.viewData(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId(), response));
		
	}
	
	@Test
	@DisplayName("View data - fail")
	public void viewData_fail() throws IllegalStateException, IOException {
		doThrow(new DataTransferAPIException("message")).when(apiService).viewData(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId(), response);
		
		assertThrows(DataTransferAPIException.class, () -> controller.viewData(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId(), response));
	}
}
