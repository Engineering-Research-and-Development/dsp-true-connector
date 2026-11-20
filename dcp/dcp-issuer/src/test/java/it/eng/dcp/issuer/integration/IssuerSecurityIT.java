package it.eng.dcp.issuer.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dcp.common.model.CredentialRequestMessage;
import it.eng.dcp.common.model.CredentialRequestMessage.CredentialReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests of the Issuer filter chains ({@code IssuerSecurityConfig}) with administration users configured.
 *
 * <p>The administration API ({@code /api/issuer/**}) needs HTTP Basic and {@code DCP_ISSUER_ADMIN};
 * the DCP protocol endpoints, DID documents and health stay reachable without Basic credentials.
 */
public class IssuerSecurityIT extends BaseIssuerIntegrationTest {

    private static final String APPROVE_PATH = "/api/issuer/requests/unknown-request/approve";
    private static final String REJECT_PATH = "/api/issuer/requests/unknown-request/reject";
    private static final String ROTATE_PATH = "/api/issuer/key/rotate";
    private static final String REJECT_BODY = "{\"reason\":\"test\"}";

    @Autowired
    private ObjectMapper objectMapper;

    @ParameterizedTest(name = "POST {0} without credentials")
    @ValueSource(strings = {APPROVE_PATH, REJECT_PATH, ROTATE_PATH, "/api/issuer/not-yet-mapped"})
    @DisplayName("Administration endpoints answer 401 with a Basic challenge without credentials")
    void adminEndpoint_noCredentials_returns401(String path) throws Exception {
        mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REJECT_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Basic")));
    }

    @ParameterizedTest(name = "POST {0} with a wrong password")
    @ValueSource(strings = {APPROVE_PATH, REJECT_PATH, ROTATE_PATH})
    @DisplayName("Administration endpoints answer 401 for a wrong password")
    void adminEndpoint_wrongPassword_returns401(String path) throws Exception {
        mockMvc.perform(post(path)
                        .with(httpBasic(ADMIN_USERNAME, "wrong-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REJECT_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Administration endpoints answer 401 for an unknown user")
    void adminEndpoint_unknownUser_returns401() throws Exception {
        mockMvc.perform(post(ROTATE_PATH).with(httpBasic("unknown-user", ADMIN_PASSWORD)))
                .andExpect(status().isUnauthorized());
        verify(keyService, never()).rotateKeyAndUpdateMetadata(any(), any(), any());
    }

    @Test
    @DisplayName("Administration endpoints ignore a Bearer token and answer 401")
    void adminEndpoint_bearerToken_returns401() throws Exception {
        mockMvc.perform(post(ROTATE_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer some-dcp-token"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "POST {0} as a user without DCP_ISSUER_ADMIN")
    @ValueSource(strings = {APPROVE_PATH, REJECT_PATH, ROTATE_PATH})
    @DisplayName("Administration endpoints answer 403 for a user without the DCP_ISSUER_ADMIN authority")
    void adminEndpoint_wrongAuthority_returns403(String path) throws Exception {
        mockMvc.perform(post(path)
                        .with(httpBasic(NON_ADMIN_USERNAME, NON_ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REJECT_BODY))
                .andExpect(status().isForbidden());
        verify(keyService, never()).rotateKeyAndUpdateMetadata(any(), any(), any());
    }

    @Test
    @DisplayName("Key rotation succeeds for an administrator and creates no session")
    void rotateKey_admin_returns200() throws Exception {
        when(keyService.rotateKeyAndUpdateMetadata(any(), any(), any())).thenReturn("issuer-rotated");

        String setCookie = mockMvc.perform(post(ROTATE_PATH).with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("issuer-rotated")))
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.SET_COOKIE);

        assertNull(setCookie);
    }

    @Test
    @DisplayName("Approve and reject reach the controller for an administrator")
    void approveAndReject_admin_reachController() throws Exception {
        mockMvc.perform(post(APPROVE_PATH)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(REJECT_PATH)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REJECT_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DCP protocol endpoints keep their token check and ignore Basic credentials")
    void dcpEndpoints_basicCredentials_stillNeedDcpToken() throws Exception {
        mockMvc.perform(get("/issuer/metadata"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("Missing or invalid Authorization header")));
        mockMvc.perform(get("/issuer/metadata").with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("Missing or invalid Authorization header")));
        mockMvc.perform(get("/issuer/requests/{requestId}", "unknown-request"))
                .andExpect(status().isUnauthorized());
        CredentialRequestMessage requestMessage = CredentialRequestMessage.Builder.newInstance()
                .holderPid("did:web:localhost:holder")
                .credentials(List.of(CredentialReference.Builder.newInstance().id("MembershipCredential").build()))
                .build();
        mockMvc.perform(post("/issuer/credentials")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestMessage)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("Missing or invalid Authorization header")));
    }

    @ParameterizedTest(name = "GET {0} anonymously")
    @ValueSource(strings = {"/.well-known/did.json", "/issuer/did.json", "/issuer/.well-known/did.json", "/actuator/health"})
    @DisplayName("DID documents and health are public")
    void publicEndpoints_anonymous_return200(String path) throws Exception {
        mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A DID document path below /api/issuer is not challenged by the administration chain")
    void didDocumentBelowAdminPath_anonymous_notChallenged() throws Exception {
        mockMvc.perform(get("/api/issuer/did.json"))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    @DisplayName("Actuator endpoints ending in /did.json are still denied")
    void actuatorDidJsonPath_anonymous_isDenied() throws Exception {
        mockMvc.perform(get("/actuator/env/did.json"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Paths outside the known zones are denied")
    void unknownPath_anonymous_isDenied() throws Exception {
        mockMvc.perform(get("/some/other/path"))
                .andExpect(status().isForbidden());
    }
}
