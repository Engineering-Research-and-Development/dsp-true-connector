package it.eng.connector.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With the default configuration the DCP dev token generator is not registered, so the endpoint does not exist
 * even for an admin, and it is never reachable without authentication.
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "server.port=0")
class DcpTokenGeneratorDefaultIT extends BaseIntegrationTest {

    private static final String GENERATE_PATH = "/api/dev/token/generate";
    private static final String FORM_GENERATE_PATH = "/api/dev/token/generate/token";
    private static final String BODY = "{\"audienceDid\": \"did:web:example.com:issuer\"}";

    @Test
    @DisplayName("Token generator endpoint is absent by default, even for an admin")
    void generate_defaultConfiguration_adminGets404() throws Exception {
        mockMvc.perform(post(GENERATE_PATH)
                        .with(user("admin@mail.com").password("password").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Token generator form endpoint is absent by default, even for an admin")
    void generateFromForm_defaultConfiguration_adminGets404() throws Exception {
        mockMvc.perform(post(FORM_GENERATE_PATH)
                        .with(user("admin@mail.com").password("password").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("audience", "did:web:example.com:issuer"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Token generator endpoint is not public by default")
    void generate_defaultConfiguration_unauthenticatedGets401() throws Exception {
        mockMvc.perform(post(GENERATE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
    }
}
