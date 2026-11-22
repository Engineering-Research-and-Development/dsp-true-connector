package it.eng.connector.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With {@code dcp.dev.token-generator.enabled=true} the DCP dev token generator is registered in the
 * {@code /api/**} zone and requires an admin.
 */
@TestPropertySource(properties = {
        "dcp.dev.token-generator.enabled=true",
        "dcp.connector-did=did:web:localhost%3A8080:test",
        // the packaged jar under test excludes *.p12, so read the keystore from the module directory
        "dcp.keystore.path=src/main/resources/eckey.p12"
})
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "server.port=0")
class DcpTokenGeneratorEnabledIT extends BaseIntegrationTest {

    private static final String GENERATE_PATH = "/api/dev/token/generate";
    private static final String BODY = "{\"audienceDid\": \"did:web:example.com:issuer\"}";

    @Test
    @DisplayName("Token generator rejects unauthenticated callers with 401")
    void generate_enabled_unauthenticatedGets401() throws Exception {
        mockMvc.perform(post(GENERATE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Token generator rejects a non-admin caller with 403")
    void generate_enabled_nonAdminGets403() throws Exception {
        mockMvc.perform(post(GENERATE_PATH)
                        .with(user("connector").password("password").roles("CONNECTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Token generator returns a token for an admin")
    void generate_enabled_adminGetsToken() throws Exception {
        mockMvc.perform(post(GENERATE_PATH)
                        .with(user("admin@mail.com").password("password").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyOrNullString())));
    }
}
