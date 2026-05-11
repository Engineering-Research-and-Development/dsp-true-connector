package it.eng.dataplane.api.model;

import jakarta.validation.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class DataFlowTest {

    @Test
    @DisplayName("Build with required fields succeeds")
    void buildWithRequiredFieldsSucceeds() {
        DataFlow flow = DataFlow.Builder.newInstance()
            .processId("proc-1")
            .transferType("HttpData-PULL")
            .build();
        assertThat(flow.getProcessId()).isEqualTo("proc-1");
        assertThat(flow.getTransferType()).isEqualTo("HttpData-PULL");
    }

    @Test
    @DisplayName("Missing processId throws ValidationException")
    void missingProcessIdThrowsValidation() {
        assertThrows(ValidationException.class, () ->
            DataFlow.Builder.newInstance().transferType("HttpData-PULL").build());
    }

    @Test
    @DisplayName("Missing transferType throws ValidationException")
    void missingTransferTypeThrowsValidation() {
        assertThrows(ValidationException.class, () ->
            DataFlow.Builder.newInstance().processId("proc-1").build());
    }

    @Test
    @DisplayName("Empty builder throws ValidationException")
    void emptyBuilderThrowsValidation() {
        assertThrows(ValidationException.class, () ->
            DataFlow.Builder.newInstance().build());
    }

    @Test
    @DisplayName("Default state is INITIALIZED")
    void defaultStateIsInitialized() {
        DataFlow flow = DataFlow.Builder.newInstance()
            .processId("proc-1")
            .transferType("HttpData-PULL")
            .build();
        assertThat(flow.getState()).isEqualTo(DataFlowState.INITIALIZED);
    }
}
