package it.eng.datatransfer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.util.MockObjectUtil;

@ExtendWith(MockitoExtension.class)

class DataTransferAPIServiceTest {

	@Mock
	private TransferProcessRepository repository;
	@InjectMocks
	private DataTransferAPIService apiService;

	@Test
	@DisplayName("Find transfer process by id, state and all")
	public void findDataTransfers() {

		when(repository.findById(anyString())).thenReturn(Optional.of(MockObjectUtil.TRANSFER_PROCESS_REQUESTED));
		Collection<JsonNode> response = apiService.findDataTransfers("test", TransferState.REQUESTED.name());
		assertNotNull(response);
		assertEquals(response.size(), 1);

		when(repository.findById(anyString())).thenReturn(Optional.empty());
		response = apiService.findDataTransfers("test_not_found", null);
		assertNotNull(response);
		assertTrue(response.isEmpty());

		when(repository.findByState(anyString())).thenReturn(Arrays.asList(MockObjectUtil.TRANSFER_PROCESS_STARTED));
		response =  apiService.findDataTransfers(null, TransferState.STARTED.name());
		assertNotNull(response);
		assertEquals(response.size(), 1);

		when(repository.findAll())
				.thenReturn(Arrays.asList(MockObjectUtil.TRANSFER_PROCESS_REQUESTED, MockObjectUtil.TRANSFER_PROCESS_STARTED));
		response =  apiService.findDataTransfers(null, null);
		assertNotNull(response);
		assertEquals(response.size(), 2);
	}

}
