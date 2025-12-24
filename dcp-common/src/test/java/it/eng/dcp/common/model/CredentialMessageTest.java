package it.eng.dcp.common.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dcp.common.model.CredentialMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CredentialMessageTest {

    @Test
    @DisplayName("Builder constructs CredentialMessage and JSON roundtrip preserves fields for ISSUED status")
    void builderConstructsMessageAndJsonRoundtripForIssued() throws Exception {
        CredentialMessage.CredentialContainer container = CredentialMessage.CredentialContainer.Builder.newInstance()
                .credentialType("ProofCredential")
                .format("jwt")
                .payload(Map.of("sub", "did:example:holder"))
                .build();

        CredentialMessage msg = CredentialMessage.Builder.newInstance()
                .issuerPid("did:web:example:issuer")
                .holderPid("did:web:example:holder")
                .requestId("req-12345")
                .status("ISSUED")
                .credentials(List.of(container))
                .build();

        assertEquals("CredentialMessage", msg.getType());
        assertEquals("did:web:example:issuer", msg.getIssuerPid());
        assertEquals("did:web:example:holder", msg.getHolderPid());
        assertEquals("req-12345", msg.getRequestId());
        assertEquals("ISSUED", msg.getStatus());
        assertEquals(1, msg.getCredentials().size());

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(msg);
        assertNotNull(json);
        assertTrue(json.contains("ProofCredential"));

        CredentialMessage deserialized = mapper.readValue(json, CredentialMessage.class);
        assertNotNull(deserialized);
        assertEquals(msg.getType(), deserialized.getType());
        assertEquals(msg.getIssuerPid(), deserialized.getIssuerPid());
        assertEquals(msg.getCredentials().size(), deserialized.getCredentials().size());
    }

    @Test
    @DisplayName("REJECTED status requires rejectionReason")
    void rejectedStatusRequiresReason() {
        CredentialMessage.CredentialContainer container = CredentialMessage.CredentialContainer.Builder.newInstance()
                .credentialType("ProofCredential")
                .format("jwt")
                .payload(Map.of("sub", "did:example:holder"))
                .build();

        Exception ex = assertThrows(Exception.class, () ->
                CredentialMessage.Builder.newInstance()
                        .issuerPid("did:web:example:issuer")
                        .holderPid("did:web:example:holder")
                        .status("REJECTED")
                        .credentials(List.of(container))
                        .build()
        );

        assertTrue(ex.getMessage().contains("rejectionReason is required when status is REJECTED"));
    }
}
