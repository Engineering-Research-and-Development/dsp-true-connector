package it.eng.dcp.holder.rest.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dcp.common.model.IssuerMetadata;
import it.eng.dcp.holder.service.CredentialIssuanceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for DCPAPIController.
 *
 * <p>Tests all REST endpoints for credential issuance operations including
 * fetching issuer metadata and requesting credentials.
 */
@ExtendWith(MockitoExtension.class)
class DCPAPIControllerTest {

    @Mock
    private CredentialIssuanceClient credentialIssuanceClient;

    @InjectMocks
    private DCPAPIController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    private IssuerMetadata createValidIssuerMetadata() {
        var credentialObject = IssuerMetadata.CredentialObject.Builder.newInstance()
                .id("cred-1")
                .credentialType("MembershipCredential")
                .build();

        return IssuerMetadata.Builder.newInstance()
                .issuer("did:web:issuer.example.com")
                .credentialsSupported(List.of(credentialObject))
                .build();
    }

    // ========== Tests for GET /api/v1/dcp/issuer-metadata ==========

    @DisplayName("GET /issuer-metadata returns 200 with valid metadata")
    @Test
    void getIssuerMetadata_ReturnsMetadata_WhenSuccessful() throws Exception {
        var metadata = createValidIssuerMetadata();
        when(credentialIssuanceClient.getPersonalIssuerMetadata()).thenReturn(metadata);

        mockMvc.perform(get("/api/v1/dcp/issuer-metadata")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.issuer", is("did:web:issuer.example.com")))
                .andExpect(jsonPath("$.credentialsSupported[0].id", is("cred-1")))
                .andExpect(jsonPath("$.credentialsSupported[0].credentialType", is("MembershipCredential")));

        verify(credentialIssuanceClient).getPersonalIssuerMetadata();
    }

    @DisplayName("GET /issuer-metadata returns 400 when IllegalArgumentException is thrown")
    @Test
    void getIssuerMetadata_Returns400_WhenIllegalArgumentException() throws Exception {
        when(credentialIssuanceClient.getPersonalIssuerMetadata())
                .thenThrow(new IllegalArgumentException("Invalid issuer DID"));

        mockMvc.perform(get("/api/v1/dcp/issuer-metadata")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error", is("Invalid issuer DID")));

        verify(credentialIssuanceClient).getPersonalIssuerMetadata();
    }

    @DisplayName("GET /issuer-metadata returns 500 when unexpected exception occurs")
    @Test
    void getIssuerMetadata_Returns500_WhenUnexpectedException() throws Exception {
        when(credentialIssuanceClient.getPersonalIssuerMetadata())
                .thenThrow(new RuntimeException("Network error"));

        mockMvc.perform(get("/api/v1/dcp/issuer-metadata")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error", containsString("Failed to fetch issuer metadata")))
                .andExpect(jsonPath("$.error", containsString("Network error")));

        verify(credentialIssuanceClient).getPersonalIssuerMetadata();
    }

    // ========== Tests for POST /api/v1/dcp/credentials/request ==========

    @DisplayName("POST /credentials/request returns 201 with Location header when successful")
    @Test
    void requestCredentials_Returns201_WhenSuccessful() throws Exception {
        var credentialIds = List.of("cred-1", "cred-2");
        var statusUrl = "https://issuer.example.com/status/req-123";

        when(credentialIssuanceClient.requestCredential(anyList())).thenReturn(statusUrl);

        mockMvc.perform(post("/api/v1/dcp/credentials/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(credentialIds)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", statusUrl))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message", is("Credential request created successfully")))
                .andExpect(jsonPath("$.statusUrl", is(statusUrl)))
                .andExpect(jsonPath("$.credentialCount", is(2)));

        verify(credentialIssuanceClient).requestCredential(credentialIds);
    }

    @DisplayName("POST /credentials/request returns 201 with single credential")
    @Test
    void requestCredentials_Returns201_WithSingleCredential() throws Exception {
        var credentialIds = List.of("cred-1");
        var statusUrl = "https://issuer.example.com/status/req-456";

        when(credentialIssuanceClient.requestCredential(anyList())).thenReturn(statusUrl);

        mockMvc.perform(post("/api/v1/dcp/credentials/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(credentialIds)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", statusUrl))
                .andExpect(jsonPath("$.credentialCount", is(1)));

        verify(credentialIssuanceClient).requestCredential(credentialIds);
    }

    @DisplayName("POST /credentials/request returns 400 when credential IDs list is null")
    @Test
    void requestCredentials_Returns400_WhenCredentialIdsNull() throws Exception {
        // Sending "null" body causes HttpMessageNotReadableException before the controller
        // is reached, so Spring returns a plain 400 with no JSON body.
        mockMvc.perform(post("/api/v1/dcp/credentials/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("null"))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("POST /credentials/request returns 400 when credential IDs list is empty")
    @Test
    void requestCredentials_Returns400_WhenCredentialIdsEmpty() throws Exception {
        mockMvc.perform(post("/api/v1/dcp/credentials/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error", is("credentialIds list is required and must not be empty")));
    }

    @DisplayName("POST /credentials/request returns 400 when IllegalArgumentException is thrown")
    @Test
    void requestCredentials_Returns400_WhenIllegalArgumentException() throws Exception {
        var credentialIds = List.of("cred-1");

        when(credentialIssuanceClient.requestCredential(anyList()))
                .thenThrow(new IllegalArgumentException("Invalid credential ID format"));

        mockMvc.perform(post("/api/v1/dcp/credentials/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(credentialIds)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error", is("Invalid credential ID format")));

        verify(credentialIssuanceClient).requestCredential(credentialIds);
    }

    @DisplayName("POST /credentials/request returns 500 when unexpected exception occurs")
    @Test
    void requestCredentials_Returns500_WhenUnexpectedException() throws Exception {
        var credentialIds = List.of("cred-1");

        when(credentialIssuanceClient.requestCredential(anyList()))
                .thenThrow(new RuntimeException("DID resolution failed"));

        mockMvc.perform(post("/api/v1/dcp/credentials/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(credentialIds)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error", containsString("Failed to request credentials")))
                .andExpect(jsonPath("$.error", containsString("DID resolution failed")));

        verify(credentialIssuanceClient).requestCredential(credentialIds);
    }

    @DisplayName("POST /credentials/request handles multiple credentials correctly")
    @Test
    void requestCredentials_HandlesMultipleCredentials() throws Exception {
        var credentialIds = List.of("cred-1", "cred-2", "cred-3", "cred-4", "cred-5");
        var statusUrl = "https://issuer.example.com/status/req-789";

        when(credentialIssuanceClient.requestCredential(anyList())).thenReturn(statusUrl);

        mockMvc.perform(post("/api/v1/dcp/credentials/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(credentialIds)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", statusUrl))
                .andExpect(jsonPath("$.credentialCount", is(5)));

        verify(credentialIssuanceClient).requestCredential(credentialIds);
    }

    @DisplayName("POST /credentials/request with malformed JSON returns 400")
    @Test
    void requestCredentials_Returns400_WhenMalformedJson() throws Exception {
        String malformedJson = "{invalid json}";
        mockMvc.perform(post("/api/v1/dcp/credentials/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(malformedJson))
                .andExpect(status().isBadRequest());
    }
}





