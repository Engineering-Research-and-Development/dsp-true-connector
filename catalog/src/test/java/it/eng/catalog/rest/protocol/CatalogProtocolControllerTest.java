package it.eng.catalog.rest.protocol;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.CatalogError;
import it.eng.catalog.model.CatalogRequestMessage;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.model.DatasetRequestMessage;
import it.eng.catalog.model.Serializer;
import it.eng.catalog.service.CatalogService;
import it.eng.catalog.service.DatasetService;
import it.eng.catalog.util.MockObjectUtil;

@ExtendWith(MockitoExtension.class)
public class CatalogProtocolControllerTest {
	
	private CatalogProtocolController catalogProtocolController;
	
	@Mock
	private CatalogService catalogService;
	@Mock
	private DatasetService datasetService;
	
	private ObjectMapper mapper = new ObjectMapper(); 
	
	private Catalog mockCatalog = MockObjectUtil.createCatalog();
	
	private Dataset mockDataset = MockObjectUtil.createDataset();
			
	private CatalogError catalogError = CatalogError.Builder.newInstance().build();
	
	private CatalogRequestMessage catalogRequestMessage = CatalogRequestMessage.Builder.newInstance().build();
	
	private DatasetRequestMessage datasetRequestMessage = DatasetRequestMessage.Builder.newInstance().dataset(Serializer.serializeProtocol(mockDataset)).build();
	
	@BeforeEach
	public void init() {
		catalogProtocolController = new CatalogProtocolController(catalogService, datasetService);
	}
	
	//TODO add junit test to cover Validation exception
	
	@Test
	public void getCatalogSuccessfulTest() throws Exception {
		
		when(catalogService.findByFilter(any())).thenReturn(mockCatalog);
		
		String json = Serializer.serializeProtocol(catalogRequestMessage);
		JsonNode jsonNode = mapper.readTree(json);
		
		ResponseEntity<String> response = catalogProtocolController.getCatalog(null, jsonNode);
		
		assertNotNull(response);
		assertTrue(response.getStatusCode().is2xxSuccessful());
		assertTrue(StringUtils.contains(response.getBody(), mockCatalog.getType()));
		assertTrue(StringUtils.contains(response.getBody(), mockCatalog.getContext()));
		
	}
	
	@Test
	public void notValidCatalogRequestMessageTest() throws Exception {
		
		String json = Serializer.serializeProtocol(datasetRequestMessage);
		JsonNode jsonNode = mapper.readTree(json);
		
		ResponseEntity<String> response = catalogProtocolController.getCatalog(null, jsonNode);
		
		assertNotNull(response);
		assertTrue(response.getStatusCode().is2xxSuccessful());
		assertTrue(StringUtils.contains(response.getBody(), catalogError.getType()));
		assertTrue(StringUtils.contains(response.getBody(), "Not valid catalog request message"));
	}
	
	@Test
	public void catalogRequestMessageNotPresentTest() throws Exception {
		
		JsonNode jsonNode = mapper.readTree("{\"some\":\"json\"}");
		
		ResponseEntity<String> response = catalogProtocolController.getCatalog(null, jsonNode);
		
		assertNotNull(response);
		assertTrue(response.getStatusCode().is2xxSuccessful());
		assertTrue(StringUtils.contains(response.getBody(), catalogError.getType()));
		assertTrue(StringUtils.contains(response.getBody(), "Catalog request message not present"));
	}
	
	@Test
	public void getDatasetSuccessfulTest() throws Exception {
		
		when(datasetService.findById(any())).thenReturn(mockCatalog.getDataset().get(0));
		
		String json = Serializer.serializeProtocol(datasetRequestMessage);
		JsonNode jsonNode = mapper.readTree(json);
		
		ResponseEntity<String> response = catalogProtocolController.getDataset(null, "1",jsonNode);
		
		assertNotNull(response);
		assertTrue(response.getStatusCode().is2xxSuccessful());
		assertTrue(StringUtils.contains(response.getBody(), mockDataset.getType()));
		assertTrue(StringUtils.contains(response.getBody(), mockDataset.getContext()));
		
	}
	
	@Test
	public void notValidDatasetRequestMessageTest() throws Exception {
		
		String json = Serializer.serializeProtocol(catalogRequestMessage);
		JsonNode jsonNode = mapper.readTree(json);
		
		ResponseEntity<String> response = catalogProtocolController.getDataset(null, "1",jsonNode);
		
		assertNotNull(response);
		assertTrue(response.getStatusCode().is2xxSuccessful());
		assertTrue(StringUtils.contains(response.getBody(), catalogError.getType()));
		assertTrue(StringUtils.contains(response.getBody(), "Not valid dataset request message"));
		
	}
	
	@Test
	public void datasetRequestMessageNotPresentTest() throws Exception {
		
		JsonNode jsonNode = mapper.readTree("{\"some\":\"json\"}");
		
		ResponseEntity<String> response = catalogProtocolController.getDataset(null, "1",jsonNode);
		
		assertNotNull(response);
		assertTrue(response.getStatusCode().is2xxSuccessful());
		assertTrue(StringUtils.contains(response.getBody(), catalogError.getType()));
		assertTrue(StringUtils.contains(response.getBody(), "Dataset request message not present"));
		
	}
}
