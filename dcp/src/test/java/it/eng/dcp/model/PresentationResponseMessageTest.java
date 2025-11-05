package it.eng.dcp.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.tools.model.DSpaceConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PresentationResponseMessageTest {

    @Test
    @DisplayName("Builder constructs response message and JSON round-trip preserves fields")
    void builderConstructsResponseAndRoundtripPreservesFields() throws Exception {
        PresentationResponseMessage msg = PresentationResponseMessage.Builder.newInstance()
                .presentation(List.of(Map.of("vpId", "vp-1")))
                .presentationSubmission(Map.of("descriptor_map", List.of(Map.of("id", "desc-1"))))
                .build();

        assertEquals(PresentationResponseMessage.class.getSimpleName(), msg.getType());
        assertNotNull(msg.getContext());
        assertTrue(msg.getContext().contains(DSpaceConstants.DCP_CONTEXT));
        assertEquals(1, msg.getPresentation().size());

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(msg);
        assertNotNull(json);
        assertTrue(json.contains("vp-1"));

        PresentationResponseMessage deserialized = mapper.readValue(json, PresentationResponseMessage.class);
        assertNotNull(deserialized);
        assertEquals(msg.getType(), deserialized.getType());
        assertEquals(msg.getPresentation(), deserialized.getPresentation());
    }
}
