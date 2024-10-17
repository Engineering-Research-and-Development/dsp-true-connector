package it.eng.datatransfer.rest.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.serializer.Serializer;
import it.eng.datatransfer.service.DataTransferAPIService;
import it.eng.datatransfer.util.MockObjectUtil;
import it.eng.tools.model.DSpaceConstants;
import it.eng.tools.response.GenericApiResponse;

@ExtendWith(MockitoExtension.class)
class DataTransferAPIControllerTest {

	@Mock
	private DataTransferAPIService apiService;
	
	@InjectMocks
	private DataTransferAPIController controller;
	
	private ObjectMapper mapper = new ObjectMapper();
	
	@Test
	@DisplayName("Find transfer process by id, state and all")
	public void getTransfersProcess() {
		when(apiService.findDataTransfers(anyString(), anyString()))
			.thenReturn(Arrays.asList(Serializer.serializePlainJsonNode(MockObjectUtil.TRANSFER_PROCESS_REQUESTED)));
		ResponseEntity<GenericApiResponse<Collection<JsonNode>>> response = controller.getTransfersProcess("test", TransferState.REQUESTED.name());
		assertNotNull(response);
		assertTrue(response.getBody().isSuccess());
		assertFalse(response.getBody().getData().isEmpty());
		
		when(apiService.findDataTransfers(anyString(), isNull()))
			.thenReturn(new ArrayList<>());
		response = controller.getTransfersProcess("test_not_found", null);
		assertNotNull(response);
		assertTrue(response.getBody().isSuccess());
		assertTrue(response.getBody().getData().isEmpty());
		
		when(apiService.findDataTransfers(isNull(), anyString()))
			.thenReturn(Arrays.asList(Serializer.serializePlainJsonNode(MockObjectUtil.TRANSFER_PROCESS_STARTED)));
		response = controller.getTransfersProcess(null, TransferState.STARTED.name());
		assertNotNull(response);
		assertTrue(response.getBody().isSuccess());
		assertFalse(response.getBody().getData().isEmpty());
	
		when(apiService.findDataTransfers(isNull(), isNull()))
			.thenReturn(Arrays.asList(Serializer.serializePlainJsonNode(MockObjectUtil.TRANSFER_PROCESS_REQUESTED),
					Serializer.serializePlainJsonNode(MockObjectUtil.TRANSFER_PROCESS_STARTED)));
		response = controller.getTransfersProcess(null, null);

	}
	
	@Test
	@DisplayName("Request transfer process success")
	public void requestTransfer_success() {
		Map<String, Object> map = new HashMap<>();
		map.put("Forward-To", MockObjectUtil.FORWARD_TO);
		map.put("agreementId", "urn:uuid:AGREEMENT_ID");
		map.put(DSpaceConstants.FORMAT, DataTransferFormat.HTTP_PULL.name());
		map.put(DSpaceConstants.DATA_ADDRESS, Serializer.serializePlainJsonNode(MockObjectUtil.DATA_ADDRESS));
				
		when(apiService.requestTransfer(any(String.class), any(String.class), any(String.class), any(JsonNode.class)))
			.thenReturn(Serializer.serializeProtocolJsonNode(MockObjectUtil.TRANSFER_PROCESS_REQUESTED));
		
		ResponseEntity<GenericApiResponse<JsonNode>> response = controller.requestTransfer(mapper.convertValue(map, JsonNode.class));
		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(apiService).requestTransfer(any(String.class), any(String.class), any(String.class), any(JsonNode.class));
	}
	
	@Test
	@DisplayName("Request transfer process failed")
	public void requestTransfer_failed() {
		Map<String, Object> map = new HashMap<>();
		map.put("Forward-To", MockObjectUtil.FORWARD_TO);
		map.put("agreementId", "some-agreement");
		map.put(DSpaceConstants.FORMAT, DataTransferFormat.HTTP_PULL.name());
		map.put(DSpaceConstants.DATA_ADDRESS, Serializer.serializePlainJsonNode(MockObjectUtil.DATA_ADDRESS));
				
		doThrow(new DataTransferAPIException("Something not correct - tests"))
			.when(apiService).requestTransfer(any(String.class), any(String.class), any(String.class), any(JsonNode.class));
		
		assertThrows(DataTransferAPIException.class, () -> controller.requestTransfer(mapper.convertValue(map, JsonNode.class)));
	}
	
	@Test
	@DisplayName("Start transfer process success")
	public void startTransfer_success() {
				
		ResponseEntity<GenericApiResponse<JsonNode>> response = controller.startTransfer(MockObjectUtil.TRANSFER_PROCESS_STARTED.getId());
		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(apiService).startTransfer(MockObjectUtil.TRANSFER_PROCESS_STARTED.getId());
	}
	
	@Test
	@DisplayName("Start transfer process failed")
	public void startTransfer_failed() {
				
		doThrow(new DataTransferAPIException("Something not correct - tests"))
			.when(apiService).startTransfer(MockObjectUtil.TRANSFER_PROCESS_STARTED.getId());
		
		assertThrows(DataTransferAPIException.class, () -> controller.startTransfer(MockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));
	}
	
	@Test
	@DisplayName("Complete transfer process success")
	public void completeTransfer_success() {
				
		ResponseEntity<GenericApiResponse<JsonNode>> response = controller.completeTransfer(MockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId());
		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(apiService).completeTransfer(MockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId());
	}
	
	@Test
	@DisplayName("Complete transfer process failed")
	public void completeTransfer_failed() {
				
		doThrow(new DataTransferAPIException("Something not correct - tests"))
			.when(apiService).completeTransfer(MockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId());
		
		assertThrows(DataTransferAPIException.class, () -> controller.completeTransfer(MockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId()));
	}
	
	@Test
	@DisplayName("Suspend transfer process success")
	public void suspendTransfer_success() {
				
		ResponseEntity<GenericApiResponse<JsonNode>> response = controller.suspendTransfer(MockObjectUtil.TRANSFER_PROCESS_SUSPENDED.getId());
		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(apiService).suspendTransfer(MockObjectUtil.TRANSFER_PROCESS_SUSPENDED.getId());
	}
	
	@Test
	@DisplayName("Suspend transfer process failed")
	public void suspendTransfer_failed() {
				
		doThrow(new DataTransferAPIException("Something not correct - tests"))
			.when(apiService).suspendTransfer(MockObjectUtil.TRANSFER_PROCESS_SUSPENDED.getId());
		
		assertThrows(DataTransferAPIException.class, () -> controller.suspendTransfer(MockObjectUtil.TRANSFER_PROCESS_SUSPENDED.getId()));
	}
	
	@Test
	@DisplayName("Terminate transfer process success")
	public void terminateTransfer_success() {
				
		ResponseEntity<GenericApiResponse<JsonNode>> response = controller.terminateTransfer(MockObjectUtil.TRANSFER_PROCESS_TERMINATED.getId());
		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(apiService).terminateTransfer(MockObjectUtil.TRANSFER_PROCESS_TERMINATED.getId());
	}
	
	@Test
	@DisplayName("Terminate transfer process failed")
	public void terminateTransfer_failed() {
				
		doThrow(new DataTransferAPIException("Something not correct - tests"))
			.when(apiService).terminateTransfer(MockObjectUtil.TRANSFER_PROCESS_TERMINATED.getId());
		
		assertThrows(DataTransferAPIException.class, () -> controller.terminateTransfer(MockObjectUtil.TRANSFER_PROCESS_TERMINATED.getId()));
	}
}
