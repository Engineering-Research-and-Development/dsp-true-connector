package it.eng.catalog.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.catalog.exceptions.CatalogErrorAPIException;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.util.CredentialUtils;

@ExtendWith(MockitoExtension.class)
class ProxyAPIServiceTest {

	private String forwardTo = "http://forward.to/test";

	@Mock
	private OkHttpRestClient okHttpClient;
	@Mock
	private CredentialUtils credentialUtils;
	@Mock
	private GenericApiResponse<String> genericApiResponse;

	@InjectMocks
	private ProxyAPIService service;

	@Test
	@DisplayName("Get formats success")
	void getFormatsFromDataset() {
		mockCatalogCall();
		List<String> formats = service.getFormatsFromDataset(CatalogMockObjectUtil.DATASET_ID, forwardTo);
		assertNotNull(formats);
		assertEquals(1, formats.size());
	}
	
	@Test
	@DisplayName("Get formats success")
	void getFormatsFromDataset_fail() {
		when(credentialUtils.getConnectorCredentials()).thenReturn("ABC");
		when(okHttpClient.sendRequestProtocol(anyString(), any(JsonNode.class), anyString()))
				.thenReturn(genericApiResponse);
		when(genericApiResponse.isSuccess()).thenReturn(false);
		
		assertThrows(CatalogErrorAPIException.class, 
				() -> service.getFormatsFromDataset(CatalogMockObjectUtil.DATASET_ID, forwardTo));
	}

	@Test
	@DisplayName("Fetch proxy catalog")
	void getCatalog() {
		mockCatalogCall();
		Catalog catalog = service.getCatalog(forwardTo);
		assertNotNull(catalog);
	}

	private void mockCatalogCall() {
		when(credentialUtils.getConnectorCredentials()).thenReturn("ABC");
		when(okHttpClient.sendRequestProtocol(anyString(), any(JsonNode.class), anyString()))
				.thenReturn(genericApiResponse);
		when(genericApiResponse.isSuccess()).thenReturn(true);
		when(genericApiResponse.getData()).thenReturn(CatalogSerializer.serializeProtocol(CatalogMockObjectUtil.CATALOG));
	}
}
