package it.eng.connector.integration.applicationproperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.core.type.TypeReference;

import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.tools.model.ApplicationProperty;
import it.eng.tools.repository.ApplicationPropertiesRepository;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.serializer.ToolsSerializer;

public class ApplicationPropertyIntegrationTest extends BaseIntegrationTest {

	private final String TEST_KEY = "application.test.key";
	
	@Autowired
	private ApplicationPropertiesRepository repository;

	@Test
	@WithUserDetails(TestUtil.ADMIN_USER)
	public void getPropertiesSuccessfulTest() throws Exception {
		final ResultActions result =
				mockMvc.perform(
						get("/api/v1/properties/")
						.contentType(MediaType.APPLICATION_JSON_VALUE)
						.accept(MediaType.APPLICATION_JSON_VALUE));

		result.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON));

		String json = result.andReturn().getResponse().getContentAsString();
		TypeReference<GenericApiResponse<List<ApplicationProperty>>> typeRef = new TypeReference<GenericApiResponse<List<ApplicationProperty>>>() {};
		GenericApiResponse<List<ApplicationProperty>> apiResp =  ToolsSerializer.deserializePlain(json, typeRef);
		  
		assertNotNull(apiResp.getData());
		assertTrue(apiResp.getData().size() > 2);
	}

	@Test
	@WithUserDetails(TestUtil.ADMIN_USER)
	public void getPropertySuccessfulTest() throws Exception {
		ApplicationProperty property = ApplicationProperty.Builder.newInstance()
				.key(TEST_KEY)
				.value("abc")
				.build();
		repository.save(property);

		final ResultActions result =
				mockMvc.perform(
						get("/api/v1/properties/{key}", this.TEST_KEY )
						.contentType(MediaType.APPLICATION_JSON_VALUE));

		result.andExpect(status().isOk())
		.andExpect(content().contentType(MediaType.APPLICATION_JSON));
		
		String json = result.andReturn().getResponse().getContentAsString();
		TypeReference<GenericApiResponse<ApplicationProperty>> typeRef = new TypeReference<GenericApiResponse<ApplicationProperty>>() {};
		GenericApiResponse<ApplicationProperty> apiResp =  ToolsSerializer.deserializePlain(json, typeRef);
		  
		assertNotNull(apiResp.getData());
		assertEquals(apiResp.getData().getKey(), TEST_KEY);
		repository.delete(property);
	}

	@Test
	@WithUserDetails(TestUtil.ADMIN_USER)
	public void putPropertySuccessfulTest() throws Exception {
		ApplicationProperty property = ApplicationProperty.Builder.newInstance()
				.key(TEST_KEY)
				.value("abc")
				.build();
		repository.save(property);
		
		String randomValue = UUID.randomUUID().toString();

		ApplicationProperty changedProperty = ApplicationProperty.Builder.newInstance()
				.key(this.TEST_KEY)
				.value(randomValue)
				.build();

		String body = ToolsSerializer.serializeProtocolJsonNode(changedProperty).toString();

		final ResultActions result =
				mockMvc.perform(
						put("/api/v1/properties/")
						.contentType(MediaType.APPLICATION_JSON_VALUE)
						.content(body)
						.accept(MediaType.APPLICATION_JSON_VALUE));

		result.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON));

		String json = result.andReturn().getResponse().getContentAsString();
		TypeReference<GenericApiResponse<ApplicationProperty>> typeRef = new TypeReference<GenericApiResponse<ApplicationProperty>>() {};
		GenericApiResponse<ApplicationProperty> apiResp =  ToolsSerializer.deserializePlain(json, typeRef);
		  
		assertNotNull(apiResp.getData());
		repository.deleteById(changedProperty.getKey());
	}

}
