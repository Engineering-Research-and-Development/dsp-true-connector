package it.eng.dcp.issuer.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests of the Issuer filter chains with no administration user configured (the shipped default).
 *
 * <p>{@code inheritProperties = false} drops the administration users that {@link BaseIssuerIntegrationTest}
 * configures, so every {@code /api/issuer/**} request must be denied with 401 (fail closed).
 */
@TestPropertySource(inheritProperties = false, properties = "spring.application.name=dcp-issuer-fail-closed-test")
public class IssuerSecurityFailClosedIT extends BaseIssuerIntegrationTest {

    @ParameterizedTest(name = "POST {0} without credentials")
    @ValueSource(strings = {
            "/api/issuer/requests/unknown-request/approve",
            "/api/issuer/requests/unknown-request/reject",
            "/api/issuer/key/rotate"
    })
    @DisplayName("Without administration users every administration request without credentials gets 401")
    void adminEndpoint_noUsersNoCredentials_returns401(String path) throws Exception {
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"test\"}"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "POST {0} with the test administrator credentials")
    @ValueSource(strings = {
            "/api/issuer/requests/unknown-request/approve",
            "/api/issuer/requests/unknown-request/reject",
            "/api/issuer/key/rotate"
    })
    @DisplayName("Without administration users any Basic credentials get 401")
    void adminEndpoint_noUsersWithCredentials_returns401(String path) throws Exception {
        mockMvc.perform(post(path)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isUnauthorized());
        verify(keyService, never()).rotateKeyAndUpdateMetadata(any(), any(), any());
    }

    @Test
    @DisplayName("Without administration users the public endpoints keep working")
    void publicEndpoints_noUsers_unchanged() throws Exception {
        mockMvc.perform(get("/.well-known/did.json").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        mockMvc.perform(get("/issuer/metadata"))
                .andExpect(status().isUnauthorized());
    }
}
