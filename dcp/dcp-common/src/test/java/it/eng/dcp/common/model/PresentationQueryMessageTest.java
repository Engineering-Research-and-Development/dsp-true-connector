package it.eng.dcp.common.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PresentationQueryMessageTest {

    @Test
    @DisplayName("Builder constructs message and JSON round-trip preserves fields")
    void builderConstructsMessageAndJsonRoundtripPreservesFields() throws Exception {
        PresentationQueryMessage msg = PresentationQueryMessage.Builder.newInstance()
                .scope(List.of("type:Credential", "profile:vc11"))
                .presentationDefinition(Map.of("input_descriptors", List.of(Map.of("id", "desc-1"))))
                .build();

        assertEquals(PresentationQueryMessage.class.getSimpleName(), msg.getType());
        assertNotNull(msg.getContext());
        assertTrue(msg.getContext().contains("https://w3id.org/dspace-dcp/v1.0"));
        assertEquals(2, msg.getScope().size());

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(msg);
        assertNotNull(json);
        assertTrue(json.contains("PresentationQuery"));
        assertTrue(json.contains("desc-1"));

        PresentationQueryMessage deserialized = mapper.readValue(json, PresentationQueryMessage.class);
        assertNotNull(deserialized);
        assertEquals(msg.getType(), deserialized.getType());
        assertEquals(msg.getScope(), deserialized.getScope());
    }
}
