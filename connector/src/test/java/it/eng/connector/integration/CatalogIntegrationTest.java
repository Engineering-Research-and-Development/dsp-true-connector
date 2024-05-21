package it.eng.connector.integration;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import it.eng.catalog.serializer.Serializer;
import it.eng.catalog.util.MockObjectUtil;
import it.eng.connector.util.TestUtil;
import it.eng.tools.model.DSpaceConstants;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    
    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void getCatalogSuccessfulTest() throws Exception {
    	
    	String body = Serializer.serializeProtocol(MockObjectUtil.CATALOG_REQUEST_MESSAGE);
    	
    	final ResultActions result =
    			mockMvc.perform(
    					post("/catalog/request")
    					.content(body)
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isOk())
    	.andExpect(content().contentType(MediaType.APPLICATION_JSON))
    	.andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(MockObjectUtil.CATALOG.getType())))
    	.andExpect(jsonPath("['"+DSpaceConstants.CONTEXT+"']", is(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE)));
    }
    
    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
	public void notValidCatalogRequestMessageTest() throws Exception {
    	
    	String body = Serializer.serializeProtocol(MockObjectUtil.DATASET_REQUEST_MESSAGE);
		
		final ResultActions result =
				mockMvc.perform(
		            post("/catalog/request")
					.content(body)
		            .contentType(MediaType.APPLICATION_JSON));
		    result.andExpect(status().isBadRequest())
		        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
		        .andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(MockObjectUtil.CATALOG_ERROR.getType())))
		        .andExpect(jsonPath("['"+DSpaceConstants.CONTEXT+"']", is(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE)))
		        .andExpect(jsonPath("['"+DSpaceConstants.DSPACE_REASON+"'][0]['"+DSpaceConstants.VALUE+"']", containsString("@type field not correct, expected dspace:CatalogRequestMessage")));
	}
	
	@Test
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void getDatasetSuccessfulTest() throws Exception {
		
		String body = Serializer.serializeProtocol(MockObjectUtil.DATASET_REQUEST_MESSAGE);
		
		final ResultActions result =
		        mockMvc.perform(
		            get("/catalog/datasets/" + TestUtil.DATASET_ID)
					.content(body)
		            .contentType(MediaType.APPLICATION_JSON));
		    result.andExpect(status().isOk())
		        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
		        .andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(MockObjectUtil.DATASET.getType())))
		        .andExpect(jsonPath("['"+DSpaceConstants.CONTEXT+"']", is(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE)));
	}
	
	@Test
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void notValidDatasetRequestMessageTest() throws Exception {
		
		String body = Serializer.serializeProtocol(MockObjectUtil.CATALOG_REQUEST_MESSAGE);
		
		final ResultActions result =
		        mockMvc.perform(
		            get("/catalog/datasets/" + TestUtil.DATASET_ID)
					.content(body)
		            .contentType(MediaType.APPLICATION_JSON));
		    result.andExpect(status().isBadRequest())
		        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
		        .andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(MockObjectUtil.CATALOG_ERROR.getType())))
		        .andExpect(jsonPath("['"+DSpaceConstants.CONTEXT+"']", is(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE)))
		        .andExpect(jsonPath("['"+DSpaceConstants.DSPACE_REASON+"'][0]['"+DSpaceConstants.VALUE+"']", containsString("@type field not correct, expected dspace:DatasetRequestMessage")));
	}
	
	@Test
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void noDatasetFoundTest() throws Exception {
		
		String body = Serializer.serializeProtocol(MockObjectUtil.DATASET_REQUEST_MESSAGE);
		
		final ResultActions result =
		        mockMvc.perform(
		            get("/catalog/datasets/1")
					.content(body)
		            .contentType(MediaType.APPLICATION_JSON));
		    result.andExpect(status().isNotFound())
		        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
		        .andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(MockObjectUtil.CATALOG_ERROR.getType())))
		        .andExpect(jsonPath("['"+DSpaceConstants.CONTEXT+"']", is(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE)))
		        .andExpect(jsonPath("['"+DSpaceConstants.DSPACE_REASON+"'][0]['"+DSpaceConstants.VALUE+"']", containsString("Data Set with id: 1 not found")));
	}
	
}
