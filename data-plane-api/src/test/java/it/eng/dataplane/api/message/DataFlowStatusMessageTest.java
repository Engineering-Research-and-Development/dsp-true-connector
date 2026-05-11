package it.eng.dataplane.api.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class DataFlowStatusMessageTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("Serialization includes processId, state, @context and @type")
    void serializationIncludesExpectedFields() throws Exception {
        DataFlowStatusMessage msg = DataFlowStatusMessage.Builder.newInstance()
            .processId("proc-1")
            .state(DataFlowState.COMPLETED)
            .build();
        String json = MAPPER.writeValueAsString(msg);
        assertTrue(json.contains("proc-1"));
        assertTrue(json.contains("COMPLETED"));
        assertTrue(json.contains(DSpaceConstants.CONTEXT));
        assertTrue(json.contains(DSpaceConstants.DSPACE_2025_01_CONTEXT));
        assertTrue(json.contains(DSpaceConstants.TYPE));
    }

    @Test
    @DisplayName("Deserialization round-trip preserves all fields including dataAddress")
    void roundTripPreservesAllFields() throws Exception {
        DataFlowStatusMessage original = DataFlowStatusMessage.Builder.newInstance()
            .dataFlowId("df-1")
            .processId("proc-1")
            .state(DataFlowState.STARTED)
            .dataAddress(Map.of("endpoint", "https://example.com/file"))
            .build();
        String json = MAPPER.writeValueAsString(original);
        DataFlowStatusMessage restored = MAPPER.readValue(json, DataFlowStatusMessage.class);
        assertThat(original).usingRecursiveComparison().isEqualTo(restored);
    }

    @Test
    @DisplayName("Missing processId throws ValidationException")
    void missingProcessIdThrowsValidation() {
        assertThrows(ValidationException.class, () ->
            DataFlowStatusMessage.Builder.newInstance().state(DataFlowState.COMPLETED).build());
    }

    @Test
    @DisplayName("Missing state throws ValidationException")
    void missingStateThrowsValidation() {
        assertThrows(ValidationException.class, () ->
            DataFlowStatusMessage.Builder.newInstance().processId("proc-1").build());
    }

    @Test
    @DisplayName("Empty builder throws ValidationException")
    void emptyBuilderThrowsValidation() {
        assertThrows(ValidationException.class, () ->
            DataFlowStatusMessage.Builder.newInstance().build());
    }
}
