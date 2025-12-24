package it.eng.dcp.common.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dcp.common.model.CredentialOfferMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CredentialOfferMessageTest {

    @Test
    @DisplayName("Builder constructs CredentialOfferMessage and JSON roundtrip preserves fields")
    void builderConstructsOfferAndRoundtripPreservesFields() throws Exception {
        CredentialOfferMessage.OfferedCredential offered = CredentialOfferMessage.OfferedCredential.Builder.newInstance()
                .credentialType("ProofCredential")
                .format("jwt")
                .issuancePolicy(Map.of("pol", true))
                .build();

        CredentialOfferMessage msg = CredentialOfferMessage.Builder.newInstance()
                .type("CredentialOffer")
                .offeredCredentials(List.of(offered))
                .build();

        assertEquals("CredentialOffer", msg.getType());
        assertNotNull(msg.getOfferedCredentials());
        assertEquals(1, msg.getOfferedCredentials().size());

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(msg);
        assertNotNull(json);
        assertTrue(json.contains("ProofCredential"));

        CredentialOfferMessage deserialized = mapper.readValue(json, CredentialOfferMessage.class);
        assertNotNull(deserialized);
        assertEquals(msg.getType(), deserialized.getType());
        assertEquals(msg.getOfferedCredentials().size(), deserialized.getOfferedCredentials().size());
    }
}

