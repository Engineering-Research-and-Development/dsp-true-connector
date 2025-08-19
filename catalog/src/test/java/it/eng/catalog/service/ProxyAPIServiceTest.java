package it.eng.catalog.service;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.catalog.exceptions.CatalogErrorAPIException;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.util.CredentialUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProxyAPIServiceTest {

	private static final String FORWARD_TO = "http://forward.to/test";

	private Catalog catalog;

	@Mock
	private OkHttpRestClient okHttpClient;
	@Mock
	private CredentialUtils credentialUtils;
	@Mock
	private GenericApiResponse<String> genericApiResponse;

	@InjectMocks
	private ProxyAPIService service;

	@BeforeEach
	public void setUp() {
		catalog = CatalogMockObjectUtil.createNewCatalog();
	}

	@Test
	@DisplayName("Get formats success")
	void getFormatsFromDataset() {

		mockCatalogCall();
		List<String> formats = service.getFormatsFromDataset(catalog.getDataset().stream().findFirst().get().getId(), FORWARD_TO);
		assertNotNull(formats);
		assertEquals(1, formats.size());
	}
	
	@Test
	@DisplayName("Get formats fail")
	void getFormatsFromDataset_fail() {
		when(credentialUtils.getConnectorCredentials()).thenReturn("ABC");
		when(okHttpClient.sendRequestProtocol(anyString(), any(JsonNode.class), anyString()))
				.thenReturn(genericApiResponse);
		when(genericApiResponse.isSuccess()).thenReturn(false);
		when(genericApiResponse.getData())
				.thenReturn(CatalogSerializer.serializeProtocol(CatalogMockObjectUtil.CATALOG_ERROR));
		
		assertThrows(CatalogErrorAPIException.class, 
				() -> service.getFormatsFromDataset(CatalogMockObjectUtil.DATASET_ID, FORWARD_TO));
	}

	@Test
	@DisplayName("Fetch proxy catalog")
	void getCatalog() {
		mockCatalogCall();
		Catalog catalog = service.getCatalog(FORWARD_TO);
		assertNotNull(catalog);
	}

	private void mockCatalogCall() {
		when(credentialUtils.getConnectorCredentials()).thenReturn("ABC");
		when(okHttpClient.sendRequestProtocol(anyString(), any(JsonNode.class), anyString()))
				.thenReturn(genericApiResponse);
		when(genericApiResponse.isSuccess()).thenReturn(true);
		when(genericApiResponse.getData()).thenReturn(CatalogSerializer.serializeProtocol(catalog));
	}
}
