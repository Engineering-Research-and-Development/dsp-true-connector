package it.eng.catalog.rest.api;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import it.eng.catalog.exceptions.CatalogErrorAPIException;
import it.eng.catalog.service.ProxyAPIService;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.response.GenericApiResponse;

@ExtendWith(MockitoExtension.class)
class ProxyAPIControllerTest {
	
	private String forwardTo = "http://forward.to/test";
	
	@Mock
	private ProxyAPIService proxyApiService;
	
	@InjectMocks
	private ProxyAPIController controller;

	private JsonNode requestBody;
	
	@Test
	@DisplayName("Get dataset formats")
	void getFormatsFromDataset() {
		mockJsonRequestBody();
		when(proxyApiService.getFormatsFromDataset(anyString(), anyString()))
			.thenReturn(Arrays.asList("format1", "format2"));
		ResponseEntity<GenericApiResponse<List<String>>> response = 
				controller.getFormatsFromDataset(CatalogMockObjectUtil.DATASET_ID, requestBody);
		assertNotNull(response);
	}

	@Test
	@DisplayName("Get dataset formats - error")
	void getFormatsFromDataset_error() {
		mockJsonRequestBody();
		doThrow(CatalogErrorAPIException.class).when(proxyApiService).getFormatsFromDataset(anyString(), anyString());
		
		assertThrows(CatalogErrorAPIException.class, 
				()-> controller.getFormatsFromDataset(CatalogMockObjectUtil.DATASET_ID, requestBody));
	}
	
	@Test
	@DisplayName("Get catalog")
	void getCatalog() {
		mockJsonRequestBody();
		when(proxyApiService.getCatalog(anyString())).thenReturn(CatalogMockObjectUtil.CATALOG);
		ResponseEntity<GenericApiResponse<JsonNode>> response = controller.getCatalog(requestBody);
		assertNotNull(response);
	}
	
	@Test
	@DisplayName("Get catalog - error")
	void getCatalog_error() {
		mockJsonRequestBody();
		doThrow(CatalogErrorAPIException.class).when(proxyApiService).getCatalog(anyString());
		assertThrows(CatalogErrorAPIException.class, 
				()-> controller.getCatalog(requestBody));
	}
	
	private void mockJsonRequestBody() {
		requestBody = JsonNodeFactory.instance.objectNode();
		((ObjectNode) requestBody).put("Forward-To", forwardTo);
	}
	

}
