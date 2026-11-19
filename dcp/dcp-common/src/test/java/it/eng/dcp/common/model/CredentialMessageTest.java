package it.eng.dcp.common.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CredentialMessageTest {

    private final ObjectMapper jsonMapper = new ObjectMapper();

    @Test
    @DisplayName("Builder constructs CredentialMessage and JSON roundtrip preserves fields for ISSUED status")
    void builderConstructsMessageAndJsonRoundtripForIssued() throws Exception {
        String holderPid = UUID.randomUUID().toString();
        String issuerPid = UUID.randomUUID().toString();

        CredentialMessage.CredentialContainer container = CredentialMessage.CredentialContainer.Builder.newInstance()
                .credentialType("ProofCredential")
                .format("jwt")
                .payload(Map.of("sub", "did:example:holder"))
                .build();

        CredentialMessage msg = CredentialMessage.Builder.newInstance()
                .issuerPid(issuerPid)
                .holderPid(holderPid)
                .requestId("req-12345")
                .status("ISSUED")
                .credentials(List.of(container))
                .build();

        assertEquals("CredentialMessage", msg.getType());
        assertEquals(issuerPid, msg.getIssuerPid());
        assertEquals(holderPid, msg.getHolderPid());
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
    @DisplayName("Spec Example 9 (rejected, no credentials) deserializes")
    void specExample9Deserializes() throws Exception {
        String example9 = """
                { "@context": [ "https://w3id.org/dspace-dcp/v1.0/dcp.jsonld" ], "type": "CredentialMessage",
                  "issuerPid": "issuerPid", "holderPid": "holderPid", "status": "REJECTED",
                  "rejectionReason": "final approval not granted" }
                """;

        CredentialMessage msg = jsonMapper.readValue(example9, CredentialMessage.class);

        assertEquals("issuerPid", msg.getIssuerPid());
        assertEquals("holderPid", msg.getHolderPid());
        assertEquals("REJECTED", msg.getStatus());
        assertEquals("final approval not granted", msg.getRejectionReason());
        assertTrue(msg.getCredentials().isEmpty());
    }

    @Test
    @DisplayName("Rejected message without reason, credentials or holderPid is valid")
    void rejectedWithoutReasonCredentialsOrHolderPidIsValid() throws Exception {
        String minimal = """
                { "@context": [ "https://w3id.org/dspace-dcp/v1.0/dcp.jsonld" ], "type": "CredentialMessage",
                  "issuerPid": "issuerPid", "status": "REJECTED" }
                """;

        CredentialMessage msg = jsonMapper.readValue(minimal, CredentialMessage.class);

        assertNull(msg.getRejectionReason());
        assertNull(msg.getHolderPid());
        assertTrue(msg.getCredentials().isEmpty());
    }

    @Test
    @DisplayName("Absent optional fields are omitted from JSON, rejectionReason is never null")
    void absentOptionalFieldsAreOmitted() throws Exception {
        CredentialMessage msg = CredentialMessage.Builder.newInstance()
                .issuerPid("issuerPid")
                .status("REJECTED")
                .build();

        JsonNode json = jsonMapper.readTree(jsonMapper.writeValueAsString(msg));

        assertFalse(json.has("rejectionReason"));
        assertFalse(json.has("holderPid"));
        assertFalse(json.has("credentials"));
        assertEquals("CredentialMessage", json.get("type").asText());
        assertEquals("REJECTED", json.get("status").asText());
    }

    @Test
    @DisplayName("Missing issuerPid is still rejected")
    void missingIssuerPidRejected() {
        assertThrows(Exception.class, () -> CredentialMessage.Builder.newInstance().status("REJECTED").build());
    }
}
