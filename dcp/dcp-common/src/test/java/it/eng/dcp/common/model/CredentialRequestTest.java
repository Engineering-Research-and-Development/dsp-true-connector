package it.eng.dcp.common.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CredentialRequestTest {

    @Test
    @DisplayName("Client access token is retained internally but omitted from JSON")
    void clientAccessToken_isNotSerialized() throws Exception {
        CredentialRequest request = CredentialRequest.Builder.newInstance()
                .holderPid("holder-request")
                .credentialIds(List.of("membership"))
                .clientAccessToken("holder-access-token")
                .build();

        assertEquals("holder-access-token", request.getClientAccessToken());
        String json = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(request);
        assertFalse(json.contains("clientAccessToken"));
        assertFalse(json.contains("holder-access-token"));
    }
}
