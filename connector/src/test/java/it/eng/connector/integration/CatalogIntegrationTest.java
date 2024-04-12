package it.eng.connector.integration;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.CatalogError;
import it.eng.catalog.model.CatalogRequestMessage;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.model.DatasetRequestMessage;
import it.eng.catalog.model.Serializer;
import it.eng.catalog.util.MockObjectUtil;
import it.eng.tools.model.DSpaceConstants;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    
	private Catalog mockCatalog = MockObjectUtil.createCatalog();
	
	private Dataset mockDataset = MockObjectUtil.createDataset();
	
	private CatalogError catalogError = CatalogError.Builder.newInstance().build();
	
	private CatalogRequestMessage catalogRequestMessage = CatalogRequestMessage.Builder.newInstance().filter(List.of("some-filter")).build();
	
	private DatasetRequestMessage datasetRequestMessage = DatasetRequestMessage.Builder.newInstance().dataset(Serializer.serializeProtocol(mockDataset)).build();
	
	// this can be found in the initial_data.json
	private final String DATASET_ID = "fdc45798-a222-4955-8baf-ab7fd66ac4d5";
    
    @Test
    @WithUserDetails("milisav@mail.com")
    public void getCatalogSuccessfulTest() throws Exception {
    	
    	JsonNode jsonNode = Serializer.serializeProtocolJsonNode(catalogRequestMessage);
    	
    	final ResultActions result =
    			mockMvc.perform(
    					post("/catalog/request")
    					.content(jsonNode.toPrettyString())
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isOk())
    	.andExpect(content().contentType(MediaType.APPLICATION_JSON))
    	.andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(mockCatalog.getType())));
    }
    
    @Test
    @WithUserDetails("milisav@mail.com")
	public void notValidCatalogRequestMessageTest() throws Exception {
    	
    	JsonNode jsonNode = Serializer.serializeProtocolJsonNode(datasetRequestMessage);
		
		final ResultActions result =
				mockMvc.perform(
		            post("/catalog/request")
					.content(jsonNode.toPrettyString())
		            .contentType(MediaType.APPLICATION_JSON));
		    result.andExpect(status().is4xxClientError())
		        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
		        .andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(catalogError.getType())))
		        .andExpect(jsonPath("['"+DSpaceConstants.DSPACE_REASON+"'][0]['"+DSpaceConstants.VALUE+"']", containsString("@type field not correct, expected dspace:CatalogRequestMessage")));
	}
	
	@Test
	@WithUserDetails("milisav@mail.com")
	public void getDatasetSuccessfulTest() throws Exception {
		
		JsonNode jsonNode = Serializer.serializeProtocolJsonNode(datasetRequestMessage);
		
		final ResultActions result =
		        mockMvc.perform(
		            get("/catalog/datasets/" + DATASET_ID)
					.content(jsonNode.toPrettyString())
		            .contentType(MediaType.APPLICATION_JSON));
		    result.andExpect(status().isOk())
		        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
		        .andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(mockCatalog.getDataset().get(0).getType())))
		        .andExpect(jsonPath("['"+DSpaceConstants.CONTEXT+"']", is(mockCatalog.getDataset().get(0).getContext())));
	}
	
	@Test
	@WithUserDetails("milisav@mail.com")
	public void notValidDatasetRequestMessageTest() throws Exception {
		
		JsonNode jsonNode = Serializer.serializeProtocolJsonNode(catalogRequestMessage);
		
		final ResultActions result =
		        mockMvc.perform(
		            get("/catalog/datasets/" + DATASET_ID)
					.content(jsonNode.toPrettyString())
		            .contentType(MediaType.APPLICATION_JSON));
		    result.andExpect(status().is4xxClientError())
		        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
		        .andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(catalogError.getType())))
		        .andExpect(jsonPath("['"+DSpaceConstants.DSPACE_REASON+"'][0]['"+DSpaceConstants.VALUE+"']", containsString("@type field not correct, expected dspace:DatasetRequestMessage")));	}
	
}