package it.eng.dcp.issuer.integration;

import com.nimbusds.jose.JOSEException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for IssuerDidDocumentController.
 *
 * Verifies both endpoints serve valid DID documents and required fields are present.
 */
public class IssuerDidDocumentControllerIT extends BaseIssuerIntegrationTest {

    @BeforeEach
    void beforeEach() throws JOSEException {
        super.beforeEach();
        // No-op: add setup if needed for future extensions
    }

    @Test
    void getDidDocument_wellKnownEndpoint_returnsValidDidDocument() throws Exception {
        ResultActions result = mockMvc.perform(get("/.well-known/did.json")
                .accept(MediaType.APPLICATION_JSON));

        result.andExpect(status().isOk())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON))
              .andExpect(jsonPath("$.id", not(emptyOrNullString())))
              .andExpect(jsonPath("$.verificationMethod", notNullValue()))
              .andExpect(jsonPath("$['@context']", notNullValue()));
    }

    @Test
    void getDidDocument_issuerEndpoint_returnsValidDidDocument() throws Exception {
        ResultActions result = mockMvc.perform(get("/issuer/did.json")
                .accept(MediaType.APPLICATION_JSON));

        result.andExpect(status().isOk())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON))
              .andExpect(jsonPath("$.id", not(emptyOrNullString())))
              .andExpect(jsonPath("$.verificationMethod", notNullValue()))
              .andExpect(jsonPath("$['@context']", notNullValue()));
    }
}
