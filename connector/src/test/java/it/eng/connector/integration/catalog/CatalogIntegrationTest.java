package it.eng.connector.integration.catalog;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;

import it.eng.catalog.model.CatalogError;
import it.eng.catalog.serializer.Serializer;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.tools.model.DSpaceConstants;

class CatalogIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Get catalog - success")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void getCatalogSuccessfulTest() throws Exception {
    	
    	String body = Serializer.serializeProtocol(CatalogMockObjectUtil.CATALOG_REQUEST_MESSAGE);
    	
    	final ResultActions result =
    			mockMvc.perform(
    					post("/catalog/request")
    					.content(body)
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isOk())
    	.andExpect(content().contentType(MediaType.APPLICATION_JSON))
    	.andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(CatalogMockObjectUtil.CATALOG.getType())))
    	.andExpect(jsonPath("['"+DSpaceConstants.CONTEXT+"']", is(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE)));
    }
    
    @Test
    @DisplayName("Get catalog - unauthorized")
    public void getCatalogSUnauthorizedTest() throws Exception {
    	
    	String body = Serializer.serializeProtocol(CatalogMockObjectUtil.CATALOG_REQUEST_MESSAGE);
    	
    	final ResultActions result =
    			mockMvc.perform(
    					post("/catalog/request")
    					.content(body)
    					.contentType(MediaType.APPLICATION_JSON)
    					.header("Authorization", "Basic YXNkckBtYWlsLmNvbTpwYXNzd29yZA=="));
    	result.andExpect(status().isUnauthorized())
    	.andExpect(content().contentType(MediaType.APPLICATION_JSON))
    	.andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(DSpaceConstants.DSPACE + CatalogError.class.getSimpleName())))
    	.andExpect(jsonPath("['"+DSpaceConstants.CONTEXT+"']", is(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE)));
    }
    
    @Test
    @DisplayName("Get catalog - not valid catalog request message")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
	public void notValidCatalogRequestMessageTest() throws Exception {
    	
    	String body = Serializer.serializeProtocol(CatalogMockObjectUtil.DATASET_REQUEST_MESSAGE);
		
		final ResultActions result =
				mockMvc.perform(
		            post("/catalog/request")
					.content(body)
		            .contentType(MediaType.APPLICATION_JSON));
		    result.andExpect(status().isBadRequest())
		        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
		        .andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(CatalogMockObjectUtil.CATALOG_ERROR.getType())))
		        .andExpect(jsonPath("['"+DSpaceConstants.CONTEXT+"']", is(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE)))
		        .andExpect(jsonPath("['"+DSpaceConstants.DSPACE_REASON+"'][0]['"+DSpaceConstants.VALUE+"']", containsString("@type field not correct, expected dspace:CatalogRequestMessage")));
	}
	
	@Test
    @DisplayName("Get dataset - success")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void getDatasetSuccessfulTest() throws Exception {
		
		String body = Serializer.serializeProtocol(CatalogMockObjectUtil.DATASET_REQUEST_MESSAGE);
		
		final ResultActions result =
		        mockMvc.perform(
		            get("/catalog/datasets/" + TestUtil.DATASET_ID)
					.content(body)
		            .contentType(MediaType.APPLICATION_JSON));
		    result.andExpect(status().isOk())
		        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
		        .andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(CatalogMockObjectUtil.DATASET.getType())))
		        .andExpect(jsonPath("['"+DSpaceConstants.CONTEXT+"']", is(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE)));
	}
	
	@Test
    @DisplayName("Get dataset - not valid dataset request message")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void notValidDatasetRequestMessageTest() throws Exception {
		
		String body = Serializer.serializeProtocol(CatalogMockObjectUtil.CATALOG_REQUEST_MESSAGE);
		
		final ResultActions result =
		        mockMvc.perform(
		            get("/catalog/datasets/" + TestUtil.DATASET_ID)
					.content(body)
		            .contentType(MediaType.APPLICATION_JSON));
		    result.andExpect(status().isBadRequest())
		        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
		        .andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(CatalogMockObjectUtil.CATALOG_ERROR.getType())))
		        .andExpect(jsonPath("['"+DSpaceConstants.CONTEXT+"']", is(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE)))
		        .andExpect(jsonPath("['"+DSpaceConstants.DSPACE_REASON+"'][0]['"+DSpaceConstants.VALUE+"']", containsString("@type field not correct, expected dspace:DatasetRequestMessage")));
	}
	
	@Test
    @DisplayName("Get dataset - no dataset found")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void noDatasetFoundTest() throws Exception {
		
		String body = Serializer.serializeProtocol(CatalogMockObjectUtil.DATASET_REQUEST_MESSAGE);
		
		final ResultActions result =
		        mockMvc.perform(
		            get("/catalog/datasets/1")
					.content(body)
		            .contentType(MediaType.APPLICATION_JSON));
		    result.andExpect(status().isNotFound())
		        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
		        .andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(CatalogMockObjectUtil.CATALOG_ERROR.getType())))
		        .andExpect(jsonPath("['"+DSpaceConstants.CONTEXT+"']", is(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE)))
		        .andExpect(jsonPath("['"+DSpaceConstants.DSPACE_REASON+"'][0]['"+DSpaceConstants.VALUE+"']", containsString("Dataset with id: 1 not found")));
	}
	
}
