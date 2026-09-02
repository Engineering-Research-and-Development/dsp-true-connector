package it.eng.dataplane.core.model;

import it.eng.dataplane.api.model.DataFlowState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DataFlowEntity} builder and state-transition methods.
 */
class DataFlowEntityTest {

    @Test
    @DisplayName("Builder sets all fields correctly")
    void builderSetsAllFields() {
        Instant now = Instant.now();
        Map<String, String> dataAddress = Map.of("endpoint", "http://example.com");

        DataFlowEntity entity = DataFlowEntity.Builder.newInstance()
                .id("id-1")
                .processId("proc-1")
                .agreementId("agree-1")
                .datasetId("dataset-1")
                .transferType("HttpData-PULL")
                .callbackAddress("http://cp:8080/callback")
                .state(DataFlowState.STARTED)
                .dataAddress(dataAddress)
                .tenantId("tenant-1")
                .participantId("part-1")
                .counterPartyId("counter-1")
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals("id-1", entity.getId());
        assertEquals("proc-1", entity.getProcessId());
        assertEquals("agree-1", entity.getAgreementId());
        assertEquals("dataset-1", entity.getDatasetId());
        assertEquals("HttpData-PULL", entity.getTransferType());
        assertEquals("http://cp:8080/callback", entity.getCallbackAddress());
        assertEquals(DataFlowState.STARTED, entity.getState());
        assertEquals(dataAddress, entity.getDataAddress());
        assertEquals("tenant-1", entity.getTenantId());
        assertEquals("part-1", entity.getParticipantId());
        assertEquals("counter-1", entity.getCounterPartyId());
        assertEquals(now, entity.getCreatedAt());
        assertEquals(now, entity.getUpdatedAt());
    }

    @Test
    @DisplayName("withState returns new instance with updated state and timestamp")
    void withStateReturnsNewInstance() {
        DataFlowEntity original = DataFlowEntity.Builder.newInstance()
                .processId("proc-1")
                .state(DataFlowState.STARTED)
                .build();

        DataFlowEntity updated = original.withState(DataFlowState.COMPLETED);

        assertNotSame(original, updated);
        assertEquals(DataFlowState.COMPLETED, updated.getState());
        assertEquals(DataFlowState.STARTED, original.getState());
        assertNotNull(updated.getUpdatedAt());
        assertEquals("proc-1", updated.getProcessId());
    }

    @Test
    @DisplayName("withError returns new instance with error message and new state")
    void withErrorReturnsNewInstance() {
        DataFlowEntity original = DataFlowEntity.Builder.newInstance()
                .processId("proc-2")
                .state(DataFlowState.STARTED)
                .build();

        DataFlowEntity errored = original.withError("connection failed", DataFlowState.TERMINATED);

        assertNotSame(original, errored);
        assertEquals("connection failed", errored.getErrorMessage());
        assertEquals(DataFlowState.TERMINATED, errored.getState());
        assertEquals(DataFlowState.STARTED, original.getState());
        assertNull(original.getErrorMessage());
        assertNotNull(errored.getUpdatedAt());
    }

    @Test
    @DisplayName("withState copies all other fields from original")
    void withStateCopiesAllFields() {
        DataFlowEntity original = DataFlowEntity.Builder.newInstance()
                .id("id-x")
                .processId("proc-x")
                .agreementId("agree-x")
                .datasetId("ds-x")
                .transferType("HttpData-PUSH")
                .callbackAddress("http://cb")
                .tenantId("t-x")
                .participantId("p-x")
                .counterPartyId("cp-x")
                .state(DataFlowState.STARTED)
                .build();

        DataFlowEntity updated = original.withState(DataFlowState.SUSPENDED);

        assertEquals("id-x", updated.getId());
        assertEquals("proc-x", updated.getProcessId());
        assertEquals("agree-x", updated.getAgreementId());
        assertEquals("ds-x", updated.getDatasetId());
        assertEquals("HttpData-PUSH", updated.getTransferType());
        assertEquals("http://cb", updated.getCallbackAddress());
        assertEquals("t-x", updated.getTenantId());
        assertEquals("p-x", updated.getParticipantId());
        assertEquals("cp-x", updated.getCounterPartyId());
    }

    @Test
    @DisplayName("Builder with minimal fields builds successfully")
    void builderMinimalFields() {
        DataFlowEntity entity = DataFlowEntity.Builder.newInstance()
                .processId("proc-min")
                .state(DataFlowState.STARTED)
                .build();

        assertEquals("proc-min", entity.getProcessId());
        assertEquals(DataFlowState.STARTED, entity.getState());
        assertNull(entity.getId());
        assertNull(entity.getAgreementId());
    }
}
