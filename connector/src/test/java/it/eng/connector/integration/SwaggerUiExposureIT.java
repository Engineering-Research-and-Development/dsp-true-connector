package it.eng.connector.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
})
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "server.port=0")
class SwaggerUiExposureIT extends BaseIntegrationTest {

    @Test
    @DisplayName("OpenAPI groups are available when Swagger is enabled")
    void openApiGroupsAreAvailableWhenSwaggerIsEnabled() throws Exception {
        mockMvc.perform(get("/v3/api-docs/management-api"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/v3/api-docs/dsp-protocol"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}
