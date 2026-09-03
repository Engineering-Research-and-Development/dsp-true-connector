package it.eng.connector.integration.applicationproperties;

import com.fasterxml.jackson.core.type.TypeReference;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.ApplicationProperty;
import it.eng.tools.property.ApplicationPropertyKeys;
import it.eng.tools.repository.ApplicationPropertiesRepository;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.serializer.ToolsSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ApplicationPropertyIT extends BaseIntegrationTest {

    private final String TEST_KEY = "application.test.key";

    @Autowired
    private ApplicationPropertiesRepository repository;

    @AfterEach
    public void cleanup() {
        // Only delete the test-specific property; do not call deleteAll() as the system
        // relies on pre-loaded application properties.
        repository.deleteById(TEST_KEY);
    }

    @Test
    public void getPropertiesSuccessfulTest() throws Exception {
        ResultActions result =
                mockMvc.perform(
                        get(ApiEndpoints.PROPERTIES_V1 + "/")
                                .with(user(TestUtil.SUPER_ADMIN_USER).roles("SUPER_ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON_VALUE)
                                .accept(MediaType.APPLICATION_JSON_VALUE));

        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String json = result.andReturn().getResponse().getContentAsString();
        TypeReference<GenericApiResponse<List<ApplicationProperty>>> typeRef = new TypeReference<GenericApiResponse<List<ApplicationProperty>>>() {
        };
        GenericApiResponse<List<ApplicationProperty>> apiResp = ToolsSerializer.deserializePlain(json, typeRef);

        assertNotNull(apiResp.getData());
        assertTrue(apiResp.getData().size() > 2);

        result =
                mockMvc.perform(
                        get(ApiEndpoints.PROPERTIES_V1 + "/?key_prefix=" + ApplicationPropertyKeys.PROTOCOL_AUTHENTICATION)
                                .with(user(TestUtil.SUPER_ADMIN_USER).roles("SUPER_ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON_VALUE)
                                .accept(MediaType.APPLICATION_JSON_VALUE));
        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        json = result.andReturn().getResponse().getContentAsString();
        apiResp = ToolsSerializer.deserializePlain(json, typeRef);

        assertNotNull(apiResp.getData());
        Optional<ApplicationProperty> shouldBeEmpty = apiResp.getData().stream().filter(prop -> !prop.getKey().contains(ApplicationPropertyKeys.PROTOCOL_AUTHENTICATION)).findAny();
        assertTrue(shouldBeEmpty.isEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/properties as ROLE_ADMIN returns 403")
    @WithUserDetails(TestUtil.ADMIN_USER)
    public void getProperties_asAdmin_returns403() throws Exception {
        mockMvc.perform(get(ApiEndpoints.PROPERTIES_V1 + "/")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isForbidden());
    }

    @Test
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

        String body = ToolsSerializer.serializePlain(Arrays.asList(changedProperty)).toString();

        final ResultActions result =
                mockMvc.perform(
                        put("/api/v1/properties/")
                                .with(user(TestUtil.SUPER_ADMIN_USER).roles("SUPER_ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON_VALUE)
                                .content(body)
                                .accept(MediaType.APPLICATION_JSON_VALUE));

        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String json = result.andReturn().getResponse().getContentAsString();
        TypeReference<GenericApiResponse<List<ApplicationProperty>>> typeRef = new TypeReference<GenericApiResponse<List<ApplicationProperty>>>() {
        };
        GenericApiResponse<List<ApplicationProperty>> apiResp = ToolsSerializer.deserializePlain(json, typeRef);

        assertNotNull(apiResp.getData());
    }

    @Test
    @DisplayName("PUT /api/v1/properties as ROLE_ADMIN returns 403")
    @WithUserDetails(TestUtil.ADMIN_USER)
    public void putProperty_asAdmin_returns403() throws Exception {
        ApplicationProperty changedProperty = ApplicationProperty.Builder.newInstance()
                .key(TEST_KEY)
                .value("blocked")
                .build();

        mockMvc.perform(put(ApiEndpoints.PROPERTIES_V1 + "/")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(ToolsSerializer.serializePlain(Arrays.asList(changedProperty)).toString())
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isForbidden());
    }

}
