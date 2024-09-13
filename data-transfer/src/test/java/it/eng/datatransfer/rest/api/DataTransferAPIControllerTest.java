package it.eng.datatransfer.rest.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.datatransfer.serializer.Serializer;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.service.DataTransferAPIService;
import it.eng.datatransfer.util.MockObjectUtil;
import it.eng.tools.response.GenericApiResponse;

@ExtendWith(MockitoExtension.class)
class DataTransferAPIControllerTest {

	@Mock
	private DataTransferAPIService apiService;
	
	@InjectMocks
	private DataTransferAPIController controller;
	
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
	

}
