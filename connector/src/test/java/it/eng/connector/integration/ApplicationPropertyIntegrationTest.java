package it.eng.connector.integration;

import static org.hamcrest.CoreMatchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;

import it.eng.connector.util.TestUtil;
import it.eng.tools.model.ApplicationProperty;
import it.eng.tools.model.IConstants;
import it.eng.tools.model.Serializer;

public class ApplicationPropertyIntegrationTest extends BaseIntegrationTest {

	private final String TEST_KEY = "application.daps.enabledDapsInteraction";

	@Test
	@WithUserDetails(TestUtil.ADMIN_USER)
	public void getPropertiesSuccessfulTest() throws Exception {

		final ResultActions result =
				mockMvc.perform(
						get("/api/connectorProperty/")
						.contentType(MediaType.APPLICATION_JSON_VALUE)
						.accept(MediaType.APPLICATION_JSON_VALUE));

		result.andExpect(status().isOk())
		.andExpect(content().contentType(MediaType.APPLICATION_JSON))
		.andExpect(jsonPath("$.[0].['"+IConstants.TYPE+"']", is(ApplicationProperty.class.getSimpleName())));
	}

	@Test
	@WithUserDetails(TestUtil.ADMIN_USER)
	public void getPropertySuccessfulTest() throws Exception {

		final ResultActions result =
				mockMvc.perform(
						get("/api/connectorProperty/{key}", this.TEST_KEY )
						.contentType(MediaType.APPLICATION_JSON_VALUE));

		result.andExpect(status().isOk())
		.andExpect(content().contentType(MediaType.APPLICATION_JSON))
		.andExpect(jsonPath("['" + IConstants.KEY + "']", is(this.TEST_KEY)));
	}

	@Test
	@WithUserDetails(TestUtil.ADMIN_USER)
	public void putPropertySuccessfulTest() throws Exception {

		String randomValue = UUID.randomUUID().toString();

		ApplicationProperty changedProperty = ApplicationProperty.Builder.newInstance()
				.key(this.TEST_KEY)
				.value(randomValue)
				.build();

		String body = Serializer.serializeProtocolJsonNode(changedProperty).toString();

		final ResultActions result =
				mockMvc.perform(
						put("/api/connectorProperty/")
						.contentType(MediaType.APPLICATION_JSON_VALUE)
						.content(body)
						.accept(MediaType.APPLICATION_JSON_VALUE));

		result.andExpect(status().isOk())
		.andExpect(content().contentType(MediaType.APPLICATION_JSON))
		.andExpect(jsonPath("['" + IConstants.KEY + "']", is(this.TEST_KEY)))
		.andExpect(jsonPath("['" + IConstants.VALUE + "']", is(randomValue)));
	}

}
