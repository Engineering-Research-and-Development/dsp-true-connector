package it.eng.connector.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SwaggerUiDisabledIT extends BaseIntegrationTest {

    @Test
    @DisplayName("Swagger endpoints are unavailable by default")
    void swaggerEndpointsAreUnavailableByDefault() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
    }
}
