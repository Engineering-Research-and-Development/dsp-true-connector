package it.eng.dataplane.api.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dataplane.api.DataPlaneConstants;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class DataFlowStartMessageTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("Serialization includes processId, transferType, @context and @type")
    void serializationIncludesExpectedFields() throws Exception {
        DataFlowStartMessage msg = DataFlowStartMessage.Builder.newInstance()
            .processId("proc-1")
            .transferType("HttpData-PULL")
            .build();
        String json = MAPPER.writeValueAsString(msg);
        assertTrue(json.contains("proc-1"));
        assertTrue(json.contains("HttpData-PULL"));
        assertTrue(json.contains(DataPlaneConstants.CONTEXT));
        assertTrue(json.contains(DataPlaneConstants.DSPACE_2025_01_CONTEXT));
        assertTrue(json.contains(DataPlaneConstants.TYPE));
    }

    @Test
    @DisplayName("Deserialization round-trip preserves all fields")
    void roundTripPreservesAllFields() throws Exception {
        DataFlowStartMessage original = DataFlowStartMessage.Builder.newInstance()
            .messageId("msg-1")
            .processId("proc-1")
            .transferType("HttpData-PULL")
            .agreementId("agr-1")
            .datasetId("ds-1")
            .callbackAddress("http://cp:8080/tenant1/transfers")
            .build();
        String json = MAPPER.writeValueAsString(original);
        DataFlowStartMessage restored = MAPPER.readValue(json, DataFlowStartMessage.class);
        assertThat(original).usingRecursiveComparison().isEqualTo(restored);
    }

    @Test
    @DisplayName("Missing processId throws ValidationException")
    void missingProcessIdThrowsValidation() {
        assertThrows(ValidationException.class, () ->
            DataFlowStartMessage.Builder.newInstance().transferType("HttpData-PULL").build());
    }

    @Test
    @DisplayName("Missing transferType throws ValidationException")
    void missingTransferTypeThrowsValidation() {
        assertThrows(ValidationException.class, () ->
            DataFlowStartMessage.Builder.newInstance().processId("proc-1").build());
    }

    @Test
    @DisplayName("Empty builder throws ValidationException")
    void emptyBuilderThrowsValidation() {
        assertThrows(ValidationException.class, () ->
            DataFlowStartMessage.Builder.newInstance().build());
    }
}
