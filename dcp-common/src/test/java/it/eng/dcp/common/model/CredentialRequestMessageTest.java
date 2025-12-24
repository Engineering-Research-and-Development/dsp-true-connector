package it.eng.dcp.common.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dcp.common.model.CredentialRequestMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CredentialRequestMessageTest {

    @Test
    @DisplayName("Builder constructs CredentialRequestMessage and JSON roundtrip preserves fields")
    void builderConstructsMessageAndJsonRoundtripPreservesFields() throws Exception {
        CredentialRequestMessage.CredentialReference ref = CredentialRequestMessage.CredentialReference.Builder.newInstance()
                .id("cred-1")
                .build();

        CredentialRequestMessage msg = CredentialRequestMessage.Builder.newInstance()
                .holderPid("did:web:example:holder")
                .credentials(List.of(ref))
                .build();

        assertEquals(CredentialRequestMessage.class.getSimpleName(), msg.getType());
        assertEquals("did:web:example:holder", msg.getHolderPid());
        assertNotNull(msg.getCredentials());
        assertEquals(1, msg.getCredentials().size());
        assertEquals("cred-1", msg.getCredentials().get(0).getId());

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(msg);
        assertNotNull(json);
        assertTrue(json.contains("cred-1"));

        CredentialRequestMessage deserialized = mapper.readValue(json, CredentialRequestMessage.class);
        assertNotNull(deserialized);
        assertEquals(msg.getType(), deserialized.getType());
        assertEquals(msg.getHolderPid(), deserialized.getHolderPid());
        assertEquals(msg.getCredentials().size(), deserialized.getCredentials().size());
    }
}
