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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ApplicationPropertyIT extends BaseIntegrationTest {

    private final String TEST_KEY = "application.test.key";

    @Autowired
    private ApplicationPropertiesRepository repository;

    @Test
    @WithUserDetails(TestUtil.ADMIN_USER)
    public void getPropertiesSuccessfulTest() throws Exception {
        ResultActions result =
                mockMvc.perform(
                        get(ApiEndpoints.PROPERTIES_V1 + "/")
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
                        get(ApiEndpoints.PROPERTIES_V1 + "/?key_prefix=" + ApplicationPropertyKeys.DAPS_PREFIX)
                                .contentType(MediaType.APPLICATION_JSON_VALUE)
                                .accept(MediaType.APPLICATION_JSON_VALUE));
        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        json = result.andReturn().getResponse().getContentAsString();
        apiResp = ToolsSerializer.deserializePlain(json, typeRef);

        assertNotNull(apiResp.getData());
        Optional<ApplicationProperty> shouldBeEmpty = apiResp.getData().stream().filter(prop -> !prop.getKey().contains(ApplicationPropertyKeys.DAPS_PREFIX)).findAny();
        assertTrue(shouldBeEmpty.isEmpty());
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

        String body = ToolsSerializer.serializePlain(Arrays.asList(changedProperty)).toString();

        final ResultActions result =
                mockMvc.perform(
                        put("/api/v1/properties/")
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
        repository.deleteById(changedProperty.getKey());
    }

}
