package it.eng.connector.integration;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;


import it.eng.connector.model.Property;
import it.eng.connector.model.Serializer;
import it.eng.connector.util.TestUtil;
import it.eng.tools.model.DSpaceConstants;

@SpringBootTest
@AutoConfigureMockMvc
public class PropertyIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@WithUserDetails(TestUtil.ADMIN_USER)
	public void getPropertySuccessfulTest() throws Exception {

		String key = "test_property";
		final ResultActions result =
				mockMvc.perform(
						get("/api/connector_property/{key}", key )
						.contentType(MediaType.APPLICATION_JSON_VALUE));

		result.andExpect(status().isOk())
		.andExpect(content().contentType(MediaType.APPLICATION_JSON))
		.andExpect(jsonPath("['" + DSpaceConstants.KEY + "']", is("test_property")));
	}
	
	@Test
	@WithUserDetails(TestUtil.ADMIN_USER)
	public void addPropertySuccessfulTest() throws Exception {
		
		String randomKey = UUID.randomUUID().toString();
		String randomValue = UUID.randomUUID().toString();
		
		Property newProperty = Property.Builder.newInstance().key(randomKey).value(randomValue).build();
		
		String body = Serializer.serializeProtocolJsonNode(newProperty).toString();
		
		final ResultActions result =
				mockMvc.perform(
						post("/api/connector_property/")
						.contentType(MediaType.APPLICATION_JSON_VALUE)
						.content(body)
						.accept(MediaType.APPLICATION_JSON_VALUE));

		result.andExpect(status().isOk())
		.andExpect(content().contentType(MediaType.APPLICATION_JSON))
		.andExpect(jsonPath("$." + DSpaceConstants.KEY).value(equalTo(randomKey)))
		.andExpect(jsonPath("$." + DSpaceConstants.VALUE).value(equalTo(randomValue)));
	}
	
	@Test
	@WithUserDetails(TestUtil.ADMIN_USER)
	public void deletePropertySuccessfulTest() throws Exception {
		
		String key = "test_property_2";
		
		final ResultActions result =
				mockMvc.perform(
						delete("/api/connector_property/{key}", key)
						.contentType(MediaType.APPLICATION_JSON_VALUE));

		result.andExpect(status().isOk())
		.andExpect(content().contentType(MediaType.APPLICATION_JSON));
	}

}
