package it.eng.catalog.service;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.catalog.exceptions.CatalogErrorAPIException;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.Multilanguage;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.util.CredentialUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProxyAPIServiceTest {

	private static final String FORWARD_TO = "http://forward.to/test";

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
		List<String> formats = service.getFormatsFromDataset(CatalogMockObjectUtil.DATASET_ID, FORWARD_TO);
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
		Catalog CATALOG = Catalog.Builder.newInstance()
				.conformsTo(CatalogMockObjectUtil.CONFORMSTO)
				.creator(CatalogMockObjectUtil.CREATOR)
				.description(Arrays.asList(Multilanguage.Builder.newInstance().language("en").value("Catalog description").build()).stream().collect(Collectors.toCollection(HashSet::new)))
				.identifier(CatalogMockObjectUtil.IDENTIFIER)
				.issued(CatalogMockObjectUtil.ISSUED)
				.keyword(Arrays.asList("keyword1", "keyword2").stream().collect(Collectors.toCollection(HashSet::new)))
				.modified(CatalogMockObjectUtil.MODIFIED)
				.theme(Arrays.asList("white", "blue", "aqua").stream().collect(Collectors.toCollection(HashSet::new)))
				.title(CatalogMockObjectUtil.TITLE)
				.participantId("urn:example:DataProviderA")
				.service(Arrays.asList(CatalogMockObjectUtil.DATA_SERVICE).stream().collect(Collectors.toCollection(HashSet::new)))
				.dataset(Arrays.asList(CatalogMockObjectUtil.DATASET).stream().collect(Collectors.toCollection(HashSet::new)))
				.distribution(Arrays.asList(CatalogMockObjectUtil.DISTRIBUTION).stream().collect(Collectors.toCollection(HashSet::new)))
				.hasPolicy(Arrays.asList(CatalogMockObjectUtil.OFFER).stream().collect(Collectors.toCollection(HashSet::new)))
				.homepage(CatalogMockObjectUtil.ENDPOINT_URL)
				.build();
		when(credentialUtils.getConnectorCredentials()).thenReturn("ABC");
		when(okHttpClient.sendRequestProtocol(anyString(), any(JsonNode.class), anyString()))
				.thenReturn(genericApiResponse);
		when(genericApiResponse.isSuccess()).thenReturn(true);
		when(genericApiResponse.getData()).thenReturn(CatalogSerializer.serializeProtocol(CATALOG));
	}
}
