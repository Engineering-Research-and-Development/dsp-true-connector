package it.eng.dataplane.api.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class DataFlowPrepareMessageTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("Serialization includes processId, @context and @type")
    void serializationIncludesExpectedFields() throws Exception {
        DataFlowPrepareMessage msg = DataFlowPrepareMessage.Builder.newInstance()
            .processId("proc-1")
            .build();
        String json = MAPPER.writeValueAsString(msg);
        assertTrue(json.contains("proc-1"));
        assertTrue(json.contains(DSpaceConstants.CONTEXT));
        assertTrue(json.contains(DSpaceConstants.DSPACE_2025_01_CONTEXT));
        assertTrue(json.contains(DSpaceConstants.TYPE));
    }

    @Test
    @DisplayName("Deserialization round-trip preserves all fields")
    void roundTripPreservesAllFields() throws Exception {
        DataFlowPrepareMessage original = DataFlowPrepareMessage.Builder.newInstance()
            .messageId("msg-1")
            .processId("proc-1")
            .agreementId("agr-1")
            .datasetId("ds-1")
            .callbackAddress("http://cp:8080/tenant1/transfers")
            .build();
        String json = MAPPER.writeValueAsString(original);
        DataFlowPrepareMessage restored = MAPPER.readValue(json, DataFlowPrepareMessage.class);
        assertThat(original).usingRecursiveComparison().isEqualTo(restored);
    }

    @Test
    @DisplayName("Missing processId throws ValidationException")
    void missingProcessIdThrowsValidation() {
        assertThrows(ValidationException.class, () ->
            DataFlowPrepareMessage.Builder.newInstance()
                .messageId("msg-1")
                .agreementId("agr-1")
                .datasetId("ds-1")
                .callbackAddress("http://cp:8080/tenant1/transfers")
                .build());
    }

    @Test
    @DisplayName("Empty builder throws ValidationException")
    void emptyBuilderThrowsValidation() {
        assertThrows(ValidationException.class, () ->
            DataFlowPrepareMessage.Builder.newInstance().build());
    }
}
