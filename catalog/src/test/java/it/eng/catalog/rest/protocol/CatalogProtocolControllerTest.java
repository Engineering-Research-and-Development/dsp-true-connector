package it.eng.catalog.rest.protocol;

import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.CatalogError;
import it.eng.catalog.service.CatalogService;
import it.eng.catalog.service.DatasetService;
import it.eng.catalog.util.MockObjectUtil;
import it.eng.tools.model.DSpaceConstants;

@WebMvcTest(controllers = CatalogProtocolController.class)
@ContextConfiguration(classes = CatalogProtocolController.class)
public class CatalogProtocolControllerTest {
	@Autowired 
	private MockMvc mvc;
	
	@InjectMocks
	private CatalogProtocolController catalogProtocolController;
	
	@MockBean
	private CatalogService catalogService;
	@MockBean
	private DatasetService datasetService;
	
	private Catalog mockCatalog = MockObjectUtil.createCatalog();
	
	private CatalogError catalogError = CatalogError.Builder.newInstance().build();
	
	@BeforeEach
	public void init() {
		MockitoAnnotations.openMocks(this);
	}
	
	@Test
	public void getCatalogSuccessfulTest() throws Exception {
		
		when(catalogService.findByFilter(any())).thenReturn(mockCatalog);
		
		final ResultActions result =
		        mvc.perform(
		            post("/catalog/request")
		                .content("{\r\n"
		                		+ "    \"@context\": \"https://w3id.org/dspace/2024/1/context.json\",\r\n"
		                		+ "    \"@type\": \"dspace:CatalogRequestMessage\",\r\n"
		                		+ "    \"dspace:filter\": [\r\n"
		                		+ "        \"some-filter\"\r\n"
		                		+ "    ]\r\n"
		                		+ "}")
		                .contentType(MediaType.APPLICATION_JSON));
		    result.andExpect(status().isOk())
		        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
		        .andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(mockCatalog.getType())))
		        .andExpect(jsonPath("['"+DSpaceConstants.CONTEXT+"']", is(mockCatalog.getContext())));
	}
	
	@Test
	public void notValidCatalogRequestMessageTest() throws Exception {
		
		final ResultActions result =
		        mvc.perform(
		            post("/catalog/request")
		                .content("{\r\n"
		                		+ "    \"@context\": \"https://w3id.org/dspace/2024/1/context.json\",\r\n"
		                		+ "    \"@type\": \"dspace:CatalogRequestMessag\",\r\n"
		                		+ "    \"dspace:filter\": [\r\n"
		                		+ "        \"some-filter\"\r\n"
		                		+ "    ]\r\n"
		                		+ "}")
		                .contentType(MediaType.APPLICATION_JSON));
		    result.andExpect(status().isOk())
		        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
		        .andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(catalogError.getType())))
		        .andExpect(jsonPath("['"+DSpaceConstants.DSPACE_REASON+"'][0]['"+DSpaceConstants.VALUE+"']", is("Not valid catalog request message")));
	}
	
	@Test
	public void catalogRequestMessageNotPresentTest() throws Exception {
		
		final ResultActions result =
		        mvc.perform(
		            post("/catalog/request")
		                .content("{\r\n"
		                		+ "    \"@context\": \"https://w3id.org/dspace/2024/1/context.json\",\r\n"
		                		+ "    \"dspace:filter\": [\r\n"
		                		+ "        \"some-filter\"\r\n"
		                		+ "    ]\r\n"
		                		+ "}")
		                .contentType(MediaType.APPLICATION_JSON));
		    result.andExpect(status().isOk())
		        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
		        .andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(catalogError.getType())))
		        .andExpect(jsonPath("['"+DSpaceConstants.DSPACE_REASON+"'][0]['"+DSpaceConstants.VALUE+"']", is("Catalog request message not present")));
	}
	
	@Test
	public void getDatasetSuccessfulTest() throws Exception {
		
		when(datasetService.findById(any())).thenReturn(mockCatalog.getDataset().get(0));
		
		final ResultActions result =
		        mvc.perform(
		            get("/catalog/datasets/1")
		                .content("{\r\n"
		                		+ "  \"@context\":  \"https://w3id.org/dspace/2024/1/context.json\",\r\n"
		                		+ "  \"@type\": \"dspace:DatasetRequestMessage\",\r\n"
		                		+ "  \"dspace:dataset\": \"urn:uuid:3dd1add8-4d2d-569e-d634-8394a8836a88\"\r\n"
		                		+ "}")
		                .contentType(MediaType.APPLICATION_JSON));
		    result.andExpect(status().isOk())
		        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
		        .andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(mockCatalog.getDataset().get(0).getType())))
		        .andExpect(jsonPath("['"+DSpaceConstants.CONTEXT+"']", is(mockCatalog.getDataset().get(0).getContext())));
	}
	
	@Test
	public void notValidDatasetRequestMessageTest() throws Exception {
		
		final ResultActions result =
		        mvc.perform(
		            get("/catalog/datasets/1")
		                .content("{\r\n"
		                		+ "  \"@context\":  \"https://w3id.org/dspace/2024/1/context.json\",\r\n"
		                		+ "  \"@type\": \"dspace:DatasetRequestMessag\",\r\n"
		                		+ "  \"dspace:dataset\": \"urn:uuid:3dd1add8-4d2d-569e-d634-8394a8836a88\"\r\n"
		                		+ "}")
		                .contentType(MediaType.APPLICATION_JSON));
		    result.andExpect(status().isOk())
		        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
		        .andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(catalogError.getType())))
		        .andExpect(jsonPath("['"+DSpaceConstants.DSPACE_REASON+"'][0]['"+DSpaceConstants.VALUE+"']", is("Not valid dataset request message")));
	}
	
	@Test
	public void datasetRequestMessageNotPresentTest() throws Exception {
		
		final ResultActions result =
		        mvc.perform(
		            get("/catalog/datasets/1")
		                .content("{\r\n"
		                		+ "  \"@context\":  \"https://w3id.org/dspace/2024/1/context.json\",\r\n"
		                		+ "  \"dspace:dataset\": \"urn:uuid:3dd1add8-4d2d-569e-d634-8394a8836a88\"\r\n"
		                		+ "}")
		                .contentType(MediaType.APPLICATION_JSON));
		    result.andExpect(status().isOk())
		        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
		        .andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(catalogError.getType())))
		        .andExpect(jsonPath("['"+DSpaceConstants.DSPACE_REASON+"'][0]['"+DSpaceConstants.VALUE+"']", is("Dataset request message not present")));
	}
}
