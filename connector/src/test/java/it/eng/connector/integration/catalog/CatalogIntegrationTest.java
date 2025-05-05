package it.eng.connector.integration.catalog;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;

import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.CatalogError;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;

public class CatalogIntegrationTest extends BaseIntegrationTest {

	@Autowired
	private CatalogRepository catalogRepository;
	@Autowired
	private DatasetRepository datasetRepository;
	private Catalog catalog;
	private Dataset dataset;
	
	@BeforeEach
	public void populateCatalog() {
		dataset = Dataset.Builder.newInstance()
				.hasPolicy(Collections.singleton(CatalogMockObjectUtil.OFFER))
				.build();
		catalog = Catalog.Builder.newInstance()
				.dataset(Collections.singleton(dataset))
				.build();
		
		datasetRepository.save(dataset);
		catalogRepository.save(catalog);
	}
	
	@AfterEach
	public void cleanup() {
		datasetRepository.deleteAll();
		catalogRepository.deleteAll();
	}
    @Test
    @DisplayName("Get catalog - success")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void getCatalogSuccessfulTest() throws Exception {
		
    	String body = CatalogSerializer.serializeProtocol(CatalogMockObjectUtil.CATALOG_REQUEST_MESSAGE);
    	
    	final ResultActions result =
    			mockMvc.perform(
    					post("/catalog/request")
    					.content(body)
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isOk())
    		.andExpect(content().contentType(MediaType.APPLICATION_JSON));
    	
      	String response = result.andReturn().getResponse().getContentAsString();
      	Catalog catalogResponse = CatalogSerializer.deserializeProtocol(response, Catalog.class);
      	assertNotNull(catalogResponse);
    }
    
    @Test
    @DisplayName("Get catalog - unauthorized")
    public void getCatalogSUnauthorizedTest() throws Exception {
    	
    	String body = CatalogSerializer.serializeProtocol(CatalogMockObjectUtil.CATALOG_REQUEST_MESSAGE);
    	
    	final ResultActions result =
    			mockMvc.perform(
    					post("/catalog/request")
    					.content(body)
    					.contentType(MediaType.APPLICATION_JSON)
    					.header("Authorization", "Basic YXNkckBtYWlsLmNvbTpwYXNzd29yZA=="));
    	result.andExpect(status().isUnauthorized())
    		.andExpect(content().contentType(MediaType.APPLICATION_JSON));
    	
     	String response = result.andReturn().getResponse().getContentAsString();
     	CatalogError error = CatalogSerializer.deserializeProtocol(response, CatalogError.class);
     	assertNotNull(error);
	}
    
    @Test
    @DisplayName("Get catalog - not valid catalog request message")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
	public void notValidCatalogRequestMessageTest() throws Exception {
    	
    	String body = CatalogSerializer.serializeProtocol(CatalogMockObjectUtil.DATASET_REQUEST_MESSAGE);
		
		final ResultActions result =
				mockMvc.perform(
		            post("/catalog/request")
					.content(body)
		            .contentType(MediaType.APPLICATION_JSON));
		    result.andExpect(status().isBadRequest())
		        .andExpect(content().contentType(MediaType.APPLICATION_JSON));
		    
		    String response = result.andReturn().getResponse().getContentAsString();
	     	CatalogError error = CatalogSerializer.deserializeProtocol(response, CatalogError.class);
	     	assertNotNull(error);
	     	assertNotNull(error.getReason().stream()
	     			.filter(reason -> reason.getValue().contains("expected dspace:CatalogRequestMessage"))
	     			.findFirst()
	     			.get());
	}
	
	@Test
    @DisplayName("Get dataset - success")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void getDatasetSuccessfulTest() throws Exception {
		String body = CatalogSerializer.serializeProtocol(CatalogMockObjectUtil.DATASET_REQUEST_MESSAGE);
		
		final ResultActions result =
		        mockMvc.perform(
		            get("/catalog/datasets/" + dataset.getId())
					.content(body)
		            .contentType(MediaType.APPLICATION_JSON));
		    result.andExpect(status().isOk())
		        .andExpect(content().contentType(MediaType.APPLICATION_JSON));
		    
		    String response = result.andReturn().getResponse().getContentAsString();
	     	Dataset error = CatalogSerializer.deserializeProtocol(response, Dataset.class);
	     	assertNotNull(error); 
	}
	
	@Test
    @DisplayName("Get dataset - not valid dataset request message")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void notValidDatasetRequestMessageTest() throws Exception {
		
		String body = CatalogSerializer.serializeProtocol(CatalogMockObjectUtil.CATALOG_REQUEST_MESSAGE);
		
		final ResultActions result =
		        mockMvc.perform(
		            get("/catalog/datasets/" + TestUtil.DATASET_ID)
					.content(body)
		            .contentType(MediaType.APPLICATION_JSON));
		    result.andExpect(status().isBadRequest())
		        .andExpect(content().contentType(MediaType.APPLICATION_JSON));
		    
		    String response = result.andReturn().getResponse().getContentAsString();
	     	CatalogError error = CatalogSerializer.deserializeProtocol(response, CatalogError.class);
	     	assertNotNull(error);
	     	assertNotNull(error.getReason().stream()
	     			.filter(reason -> reason.getValue().contains("expected dspace:DatasetRequestMessage"))
	     			.findFirst()
	     			.get()); 
	}
	
	@Test
    @DisplayName("Get dataset - no dataset found")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void noDatasetFoundTest() throws Exception {
		
		String body = CatalogSerializer.serializeProtocol(CatalogMockObjectUtil.DATASET_REQUEST_MESSAGE);
		
		final ResultActions result =
		        mockMvc.perform(
		            get("/catalog/datasets/1")
					.content(body)
		            .contentType(MediaType.APPLICATION_JSON));
		    result.andExpect(status().isNotFound())
		        .andExpect(content().contentType(MediaType.APPLICATION_JSON));
		    
		    String response = result.andReturn().getResponse().getContentAsString();
	     	CatalogError error = CatalogSerializer.deserializeProtocol(response, CatalogError.class);
	     	assertNotNull(error);
	     	assertNotNull(error.getReason().stream()
	     			.filter(reason -> reason.getValue().contains("Dataset with id: 1 not found"))
	     			.findFirst()
	     			.get());  
	}
}
